package com.example.trackingroute.model.di

import android.content.Context
import androidx.room.Room
import com.example.trackingroute.model.database.LocationDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class ApplicationModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LocationDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            LocationDatabase::class.java,
            "locations"
        ).build()
}