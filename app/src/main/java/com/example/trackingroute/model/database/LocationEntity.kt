package com.example.trackingroute.model.database

import android.location.Location
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity
data class LocationEntity(
    val lat: Double,
    val lng: Double,
    val ele: Double,
    val time: Long,
    @PrimaryKey(autoGenerate = true) val id: Int = 0
)