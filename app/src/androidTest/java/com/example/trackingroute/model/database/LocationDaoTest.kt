package com.example.trackingroute.model.database

import com.google.common.truth.Truth.assertThat
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import dagger.Module
import com.example.trackingroute.model.di.ApplicationModule
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(ApplicationModule::class)
class LocationDaoTest {

    //hilt order must 0
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val executorRule = InstantTaskExecutorRule()

    @Inject lateinit var database : LocationDatabase
    lateinit var dao : LocationDao

    //provide location database for test
    @Module(includes = [TestDatabaseModule::class])
    @InstallIn(SingletonComponent::class)
    class TestModule

    @Before
    fun setup() {
        hiltRule.inject()
        dao = database.locationDao
    }

    @Test
    fun save_and_get_location_list() {
        runBlockingTest {
            val locationEntity = LocationEntity.empty.copy(id = 1)
            dao.insert(locationEntity)
            dao.getLocationList().test {
                assertThat(awaitItem().first()).isEqualTo(locationEntity)
            }
        }
    }

    @Test
    fun clear_DB() {
        runBlockingTest {
            val locationEntity = LocationEntity.empty.copy(id = 1)
            dao.insert(locationEntity)
            dao.deleteAll()
            val expectedList = mutableListOf<LocationEntity>()
            dao.getLocationList().test {
                assertThat(awaitItem()).isEqualTo(expectedList)
            }
        }
    }
}

