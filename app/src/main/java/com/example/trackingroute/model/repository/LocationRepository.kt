package com.example.trackingroute.model.repository

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.example.trackingroute.model.database.LocationDatabase
import com.example.trackingroute.model.database.LocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocationRepository @Inject constructor(private val database: LocationDatabase) {
    val locationList: LiveData<List<LocationEntity>> =
        database.locationDao.getLocationList().asLiveData()

    suspend fun insert(location: Location) = withContext(Dispatchers.IO) {
        val locationEntity = LocationEntity(
            location.latitude,
            location.longitude,
            location.altitude,
            location.time
        )
        database.locationDao.insert(locationEntity)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        database.locationDao.deleteAll()
    }
}