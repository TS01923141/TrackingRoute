package com.example.trackingroute.ui.maps

import android.app.Application
import androidx.lifecycle.*
import com.example.trackingroute.model.repository.LocationRepository
import com.example.trackingroute.model.utils.GPXTransferUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapsViewModel @Inject constructor(
    app: Application,
    private val repository: LocationRepository
) : AndroidViewModel(app) {
    val locationList = repository.locationList
    val _hasLocationPermission = MutableLiveData(false)
    val hasLocationPermission: LiveData<Boolean> = _hasLocationPermission
    private var _currentElevation = MutableLiveData<Double>(0.0)
    private var _currentDistance = MutableLiveData<Float>(0f)
    val currentElevation: LiveData<Double> = _currentElevation
    val currentDistance: LiveData<Float> = _currentDistance

    fun setHasLocationPermission(hasLocationPermission: Boolean) {
        _hasLocationPermission.value = hasLocationPermission
    }

    fun updateElevation(currentElevation: Double) {
        _currentElevation.value = currentElevation
    }

    fun updateDistance(totalDistance: Float) {
        _currentDistance.value = _currentDistance.value?.plus(totalDistance)
    }

    fun clearTrackingInfo() {
        _currentElevation.value = 0.0
        _currentDistance.value = 0f
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun saveTrack(fileName: String) {
        GPXTransferUtils.createGpx(getApplication(), fileName, locationList.value!!)
    }
}