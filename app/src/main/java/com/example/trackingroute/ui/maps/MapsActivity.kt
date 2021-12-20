package com.example.trackingroute.ui.maps

import android.annotation.SuppressLint
import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.example.trackingroute.R

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.example.trackingroute.databinding.ActivityMapsBinding
import com.example.trackingroute.model.database.LocationEntity
import com.example.trackingroute.model.location.LocationUpdatesService
import com.example.trackingroute.model.location.Utils
import com.example.trackingroute.ui.permission.PermissionRequestFragment
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.TimeUnit

/*
    紀錄GPS，存到Room，輸出GPX

    權限要求
    Service，這個參考Google的Sample -> https://github.com/android/location-samples
    參考LocationUpdatesForegroundService
    優化GPS -> https://stackoverflow.com/questions/1134579/smooth-gps-data
    Room保存Location資料
    從Room取出資料並轉換成gpx檔案

    新建MainActivity
    MainActivity檢查Permission，過了才到MapsActivity
    UI的開始暫停停止鍵
    加上按鍵切換動畫
    畫線
    停止鍵onClick後跳dialog輸入gpx名稱

    receiver把location insert到room
    repository從room取得liveData<List<Location>>
    activity透過viewModel的repository取得最後一點更新目前位置

    先完成room, repository
    receiver拆出來

    service不該在activity關閉時跟著關閉
    ＊先確認是不是真的跟著關閉
 */
private const val TAG = "MapsActivity"
private const val TRACK_START = 0
private const val TRACK_PAUSE = 1
private const val TRACK_RESUME = 2
private const val TRACK_STOP = 3
@AndroidEntryPoint
class MapsActivity : AppCompatActivity(), OnMapReadyCallback, OnSharedPreferenceChangeListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private val viewModel: MapsViewModel by viewModels()

    private var mService: LocationUpdatesService? = null
    private var mBound = false
    private var polylineOptions = PolylineOptions()
    private var polyline: Polyline? = null

    private var timerJob: Job? = null

    // Monitors the state of the connection to the service.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder: LocationUpdatesService.LocalBinder = service as LocationUpdatesService.LocalBinder
            mService = binder.service
            mBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mService = null
            mBound = false
        }
    }

    //lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //init map
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_maps) as SupportMapFragment
        mapFragment.getMapAsync(this)

        checkAndRequestPermission()
        initButton()
        initObserve()
    }

    override fun onStart() {
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
        // Bind to the service. If the service is in foreground mode, this signals to the service
        // that since this activity is in the foreground, the service can exit foreground mode.
        bindService(
            Intent(this, LocationUpdatesService::class.java), mServiceConnection,
            BIND_AUTO_CREATE
        )
        if (Utils.requestingLocationUpdates(this@MapsActivity) &&
            !Utils.pausingLocationUpdates(this@MapsActivity)) {
            timerJob = startTimerJob()
        }
    }

    override fun onStop() {
        if (mBound) {
            // Unbind from the service. This signals to the service that this activity is no longer
            // in the foreground, and the service can respond by promoting itself to a foreground
            // service.
            unbindService(mServiceConnection)
            mBound = false
        }
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        timerJob?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    //permission

    private fun checkAndRequestPermission() {
        supportFragmentManager.apply {
            //add listener
            setFragmentResultListener(PermissionRequestFragment.PERMISSION_REQUEST_RESULT, this@MapsActivity) {
                    requestKey, bundle ->
                viewModel.setHasLocationPermission(
                    bundle.getBoolean(PermissionRequestFragment.GRANTED_ALL_PERMISSION))
            }

            //the list of permission that you want request or check.
            val requestFragment = PermissionRequestFragment.newInstance(arrayListOf(
                PermissionRequestFragment.FINE_LOCATION
            ))
            //start request permission
            beginTransaction()
                .add(android.R.id.content, requestFragment)
                .commit()
        }
    }

    //map

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(viewModel.locationList.value?.last()?.lat ?: 0.0,
                viewModel.locationList.value?.last()?.lng ?: 0.0), 18f))
        if (viewModel.hasLocationPermission.value == true) mMap.isMyLocationEnabled = true
    }

    private fun drawPolylineListAndUpdateDistance(locationList: List<Location>) {
        //update distance
        var totalDistance = 0f
        if (!polylineOptions.points.isNullOrEmpty()) {
            val lastLocation = Location("").apply {
                latitude = polylineOptions.points.last().latitude
                longitude = polylineOptions.points.last().longitude
            }
            totalDistance += lastLocation.distanceTo(locationList.first())
        }
        if (locationList.size > 1) {
            for(i in 0 until  locationList.size - 1) {
                totalDistance += locationList[i].distanceTo(locationList[i+1])
            }
        }
        viewModel.updateDistance(totalDistance)

        //draw polyline
        polyline?.remove()
        locationList.forEach {
            polylineOptions.add(LatLng(it.latitude, it.longitude))
        }
        polyline = mMap.addPolyline(polylineOptions)
    }

    //job

    private fun startTimerJob(): Job {
        return lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(1000)
                val locationList = viewModel.locationList.value
                if (locationList.isNullOrEmpty()) return@launch
                val currentTime = Calendar.getInstance().timeInMillis - locationList.first().time
                val formatTime = String.format("%02d:%02d:%02d",
                    TimeUnit.MILLISECONDS.toHours(currentTime),
                    TimeUnit.MILLISECONDS.toMinutes(currentTime) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(currentTime)),
                    TimeUnit.MILLISECONDS.toSeconds(currentTime) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(currentTime))
                )
                withContext(Dispatchers.Main) {
                    binding.includeMapsTrackingTopBar.textViewTrackingTopBarTimeContent.text =
                        formatTime
                }
            }
        }
    }

    //lifeData observe

    private fun initObserve() {
        with(viewModel) {
            hasLocationPermission.observe(this@MapsActivity, ::handleHasLocationPermission)
            locationList.observe(this@MapsActivity, ::handleLocationList)
            currentElevation.observe(this@MapsActivity, ::handleCurrentElevation)
            currentDistance.observe(this@MapsActivity, ::handleCurrentDistance)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleHasLocationPermission(hasLocationPermission: Boolean?) {
        if (hasLocationPermission == null) return
        if (this::mMap.isInitialized) mMap.isMyLocationEnabled = hasLocationPermission
    }

    private fun handleLocationList(locationEntityList: List<LocationEntity>?) {
        if (locationEntityList.isNullOrEmpty()) return
        val locationList = mutableListOf<Location>()
        for (i in polylineOptions.points.size until locationEntityList.size) {
            val locationEntity = locationEntityList[i]
            locationList.add(Location("").apply {
                latitude = locationEntity.lat
                longitude = locationEntity.lng
                altitude = locationEntity.ele
                time = locationEntity.time
            })
        }
        viewModel.updateElevation(locationList.last().altitude)
        if (this@MapsActivity::mMap.isInitialized) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(locationEntityList.last().lat,
                locationEntityList.last().lng), 18f))
            drawPolylineListAndUpdateDistance(locationList)
        }
    }

    private fun handleCurrentElevation(currentElevation: Double) {
        val ele = "${Math.floor(currentElevation).toInt()}m"
        binding.includeMapsTrackingTopBar.textViewTrackingTopBarElevationContent.text = ele
    }

    private fun handleCurrentDistance(currentDistance: Float) {
        binding.includeMapsTrackingTopBar.textViewTrackingTopBarDistanceContent.text =
            String.format("%.2fkm", currentDistance / 1000)
    }

    //ui

    private fun defaultTrackingUi() {
        //default TrackingTopBar
        binding.includeMapsTrackingTopBar.textViewTrackingTopBarElevationContent.text = "0m"
        binding.includeMapsTrackingTopBar.textViewTrackingTopBarDistanceContent.text = "0.00km"
        binding.includeMapsTrackingTopBar.textViewTrackingTopBarTimeContent.text = "00:00:00"
        //clear polyline
        polylineOptions = PolylineOptions()
        polyline?.remove()
    }

    private fun initButton() {
        val tracking = Utils.requestingLocationUpdates(this)
        val pausing = Utils.pausingLocationUpdates(this)
        val currentState =
            if (tracking) {
                if (pausing) TRACK_PAUSE else TRACK_RESUME
            } else {
                TRACK_STOP
            }
        setButtonState(currentState)

        binding.apply {
            fabMapsStart.setOnClickListener {
                if (viewModel.hasLocationPermission.value == true) {
                    mService?.requestLocationUpdates()
                    timerJob = startTimerJob()
                } else checkAndRequestPermission()
            }
            fabMapsPause.setOnClickListener {
                mService?.pauseLocationUpdates()
            }
            fabMapsResume.setOnClickListener {
                mService?.resumeLocationUpdates()
            }
            fabMapsStop.setOnClickListener {
                //show end dialog
                mService?.resumeLocationUpdates()
                mService?.removeLocationUpdates()
                viewModel.clearTrackingInfo()
                defaultTrackingUi()
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, s: String?) {
        if (sharedPreferences == null) return
        // Update the buttons state depending on whether location updates are being requested.
        if (s == Utils.KEY_REQUESTING_LOCATION_UPDATES) {
            setButtonState(
                if (sharedPreferences.getBoolean(Utils.KEY_REQUESTING_LOCATION_UPDATES, false))
                    TRACK_START
                else
                    TRACK_STOP
            )
        } else if (s == Utils.KEY_PAUSING_LOCATION_UPDATES) {
            setButtonState(
                if (sharedPreferences.getBoolean(Utils.KEY_PAUSING_LOCATION_UPDATES, false))
                    TRACK_PAUSE
                else
                    TRACK_RESUME
            )
        }
    }

    private fun setButtonState(state: Int) {
        binding.apply {
            when(state) {
                TRACK_START -> {
                    includeMapsTrackingTopBar.constraintLayoutTrackingTopBarFrame.visibility = View.VISIBLE
                    imageViewMapsPausingBackground.visibility = View.GONE
                    fabMapsStart.visibility = View.GONE
                    fabMapsPause.visibility = View.VISIBLE
                    fabMapsResume.visibility = View.GONE
                    fabMapsStop.visibility = View.GONE
                }
                TRACK_PAUSE -> {
                    includeMapsTrackingTopBar.constraintLayoutTrackingTopBarFrame.visibility = View.VISIBLE
                    imageViewMapsPausingBackground.visibility = View.VISIBLE
                    fabMapsStart.visibility = View.GONE
                    fabMapsPause.visibility = View.GONE
                    fabMapsResume.visibility = View.VISIBLE
                    fabMapsStop.visibility = View.VISIBLE
                }
                TRACK_RESUME -> {
                    includeMapsTrackingTopBar.constraintLayoutTrackingTopBarFrame.visibility = View.VISIBLE
                    imageViewMapsPausingBackground.visibility = View.GONE
                    fabMapsStart.visibility = View.GONE
                    fabMapsPause.visibility = View.VISIBLE
                    fabMapsResume.visibility = View.GONE
                    fabMapsStop.visibility = View.GONE
                }
                TRACK_STOP -> {
                    includeMapsTrackingTopBar.constraintLayoutTrackingTopBarFrame.visibility = View.GONE
                    imageViewMapsPausingBackground.visibility = View.GONE
                    fabMapsStart.visibility = View.VISIBLE
                    fabMapsPause.visibility = View.GONE
                    fabMapsResume.visibility = View.GONE
                    fabMapsStop.visibility = View.GONE
                }
            }
        }
    }
}