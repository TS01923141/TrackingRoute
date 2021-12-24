package com.example.trackingroute.model.database

import android.location.Location
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity
data class LocationEntity(
    var lat: Double,
    var lng: Double,
    var ele: Double,
    var time: Long,
    @PrimaryKey(autoGenerate = true) val id: Int = 0
) {
    companion object {
        val empty = LocationEntity(
            0.0,
            0.0,
            0.0,
            0
        )
    }
}