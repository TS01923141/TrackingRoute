package com.example.trackingroute.model.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("select * from LocationEntity")
    fun getLocationList(): Flow<List<LocationEntity>>

    @Insert
    fun insert(locationEntity: LocationEntity)

    @Query("DELETE FROM LocationEntity")
    fun deleteAll()
}