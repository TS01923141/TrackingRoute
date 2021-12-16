package com.example.trackingroute.ui.maps

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MapsViewModel: ViewModel() {
    val _hasLocationPermission = MutableLiveData(false)
    val hasLocationPermission: LiveData<Boolean> = _hasLocationPermission

    fun setHasLocationPermission(hasLocationPermission: Boolean) {
        _hasLocationPermission.value = hasLocationPermission
    }
}