package com.example.trackingroute.ui.maps

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackingroute.model.database.LocationDatabase
import com.example.trackingroute.model.repository.LocationRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapsViewModel @Inject constructor(private val repository: LocationRepository): ViewModel() {
    val locationList = repository.locationList
    val _hasLocationPermission = MutableLiveData(false)
    val hasLocationPermission: LiveData<Boolean> = _hasLocationPermission

    fun setHasLocationPermission(hasLocationPermission: Boolean) {
        _hasLocationPermission.value = hasLocationPermission
    }

    fun clearLocationList() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }
}