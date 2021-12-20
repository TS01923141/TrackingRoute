package com.example.trackingroute.model.location

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.*
import android.provider.ContactsContract.Directory.PACKAGE_NAME
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.trackingroute.BuildConfig
import com.example.trackingroute.R
import com.example.trackingroute.model.database.LocationDatabase
import com.example.trackingroute.model.database.LocationEntity
import com.example.trackingroute.model.repository.LocationRepository
import com.example.trackingroute.model.utils.KalmanLatLong
import com.example.trackingroute.model.utils.Utils
import com.example.trackingroute.ui.maps.MapsActivity
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnCompleteListener
import com.squareup.moshi.internal.Util
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

private const val CHANNEL_ID = "tracking_route_channel"
private const val TAG = "LocationUpdatesService"
private const val EXTRA_STARTED_FROM_NOTIFICATION: String =
    "$PACKAGE_NAME.started_from_notification"

@AndroidEntryPoint
class LocationUpdatesService : Service() {

    @Inject lateinit var repository: LocationRepository

    private var insertLocationJob: Job? = null

    private val mBinder: IBinder = LocalBinder()

    private val kalmanLatLong = KalmanLatLong()

    private var trackPausing = false

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000

    /**
     * The fastest rate for active location updates. Updates will never be more frequent
     * than this value.
     */
    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS: Long = UPDATE_INTERVAL_IN_MILLISECONDS / 2

    /**
     * The identifier for the notification displayed for the foreground service.
     */
    private val NOTIFICATION_ID = 12345678

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private var mChangingConfiguration = false

    private lateinit var mNotificationManager: NotificationManager

    /**
     * Contains parameters used by [com.google.android.gms.location.FusedLocationProviderApi].
     */
    private lateinit var mLocationRequest: LocationRequest

    /**
     * Provides access to the Fused Location Provider API.
     */
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    /**
     * Callback for changes in location.
     */
    private lateinit var mLocationCallback: LocationCallback

    private val mServiceHandler: Handler? = null

    /**
     * The current location.
     */
    private var mLocation: Location? = null

    private fun LocationUpdatesService() {}

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        val service: LocationUpdatesService
            get() = this@LocationUpdatesService
    }

    /**
     * Returns true if this is a foreground service.
     *
     * @param context The [Context].
     */
    fun serviceIsRunningInForeground(context: Context): Boolean {
        val manager = context.getSystemService(
            ACTIVITY_SERVICE
        ) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (javaClass.name == service.service.className) {
                if (service.foreground) {
                    return true
                }
            }
        }
        return false
    }

    //lifecycle

    override fun onCreate() {
        super.onCreate()
        //pausing status
        trackPausing = Utils.pausingLocationUpdates(this)
        //init location setting
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                onNewLocation(locationResult.lastLocation)
            }
        }
        createLocationRequest()
        getLastLocation()
        //init notification setting
        mNotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager// Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.app_name)
            // Create the channel for the notification
            val mChannel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_DEFAULT
            )

            // Set the Notification Channel for the Notification Manager.
            mNotificationManager.createNotificationChannel(mChannel)
        }
        //check tracking status and auto recover tracking
        if (Utils.requestingLocationUpdates(this)) requestLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startedFromNotification = intent!!.getBooleanExtra(
            EXTRA_STARTED_FROM_NOTIFICATION,
            false
        )
        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification) {
            removeLocationUpdates()
            stopSelf()
        }
        // if service be killed, auto recover when memory is enough.
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        stopForeground(true)
        mChangingConfiguration = false
        return mBinder
    }

    override fun onRebind(intent: Intent?) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        stopForeground(true)
        mChangingConfiguration = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration && Utils.requestingLocationUpdates(this)) {
            startForeground(NOTIFICATION_ID, getNotification())
        }
        return true // Ensures onRebind() is called when a client re-binds.
    }

    override fun onDestroy() {
        insertLocationJob?.cancel()
        super.onDestroy()
    }

    // location request

    /**
     * Makes a request for location updates. Note that in this sample we merely log the
     * [SecurityException].
     */
    fun requestLocationUpdates() {
        Utils.setRequestingLocationUpdates(this, true)
        startService(Intent(applicationContext, LocationUpdatesService::class.java))
        try {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback, Looper.myLooper()!!
            )
        } catch (unlikely: SecurityException) {
            Utils.setRequestingLocationUpdates(this, false)
            Log.e(TAG, "Lost location permission. Could not request updates. $unlikely")
        }
    }

    fun pauseLocationUpdates() {
        trackPausing = true
        Utils.setPausingLocationUpdates(this, trackPausing)
    }

    fun resumeLocationUpdates() {
        trackPausing = false
        Utils.setPausingLocationUpdates(this, trackPausing)
    }

    /**
     * Removes location updates. Note that in this sample we merely log the
     * [SecurityException].
     */
    fun removeLocationUpdates() {
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback)
            Utils.setRequestingLocationUpdates(this, false)
            stopSelf()
        } catch (unlikely: SecurityException) {
            Utils.setRequestingLocationUpdates(this, true)
            Log.e(
                TAG,
                "Lost location permission. Could not remove updates. $unlikely"
            )
        }
    }

    private fun getLastLocation() {
        try {
            mFusedLocationClient.lastLocation
                .addOnCompleteListener(OnCompleteListener<Location> { task ->
                    if (task.isSuccessful && task.result != null) {
                        Log.d(TAG, "getLastLocation: ")
                        filterAndUpdateLocation(task.result)
                    } else {
                        Log.w(TAG, "Failed to get location.")
                    }
                })
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission.$unlikely")
        }
    }

    private fun onNewLocation(location: Location) {

        filterAndUpdateLocation(location)

        if (trackPausing) return

        insertLocationJob = CoroutineScope(Dispatchers.IO).launch {
            repository.insert(location)
        }

        // Update notification content if running as a foreground service.
        if (serviceIsRunningInForeground(this)) {
            mNotificationManager.notify(NOTIFICATION_ID, getNotification())
        }
    }

    /**
     * Sets the location request parameters.
     */
    private fun createLocationRequest() {
        mLocationRequest = LocationRequest.create().apply {
            interval = UPDATE_INTERVAL_IN_MILLISECONDS
            fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun filterAndUpdateLocation(location: Location) {
        //kalman filter
        if (kalmanLatLong.get_lat() == 0.0 && kalmanLatLong.get_lng() == 0.0) {
            location.apply {
                kalmanLatLong.SetState(
                    latitude,
                    longitude,
                    accuracy,
                    time
                )
            }
        } else {
            location.apply {
                kalmanLatLong.Process(
                    latitude,
                    longitude,
                    accuracy,
                    time
                )
                latitude = kalmanLatLong.get_lat()
                longitude = kalmanLatLong.get_lng()
                accuracy = kalmanLatLong.get_accuracy()
            }
        }

        if (mLocation != null) {
            if (location.time - mLocation!!.time < 0) return
            if (!location.hasAccuracy() || location.accuracy > 100 || location.accuracy == 0f) return
            if (!location.hasSpeed() || location.speed == 0f) return
        }
        mLocation = location
    }


    //notification

    /**
     * Returns the [NotificationCompat] used as part of the foreground service.
     */
    private fun getNotification(): Notification? {
        val intent = Intent(this, LocationUpdatesService::class.java)
        val text: CharSequence = Utils.getLocationText(mLocation)

        // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)

        // The PendingIntent that leads to a call to onStartCommand() in this service.
        val servicePendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        // The PendingIntent to launch activity.
//        val activityPendingIntent = PendingIntent.getActivity(
//            this, 0,
//            Intent(this, MapsActivity::class.java), 0
//        )
        val builder = NotificationCompat.Builder(this)
//            .addAction(
//                R.drawable.ic_baseline_launch_24, getString(R.string.launch_activity),
//                activityPendingIntent
//            )
//            .addAction(
//                R.drawable.ic_cancel, getString(R.string.remove_location_updates),
//                servicePendingIntent
//            )
            .setContentText(text)
            .setContentTitle(Utils.getLocationTitle(this))
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker(text)
            .setWhen(System.currentTimeMillis())

        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID) // Channel ID
        }
        return builder.build()
    }
}