package com.example.trackingroute.model.utils

import android.content.Context
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.trackingroute.model.database.LocationEntity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.Exception
import java.lang.StringBuilder

class GPXTransferUtilsTest {
    private var fileList: MutableList<File> = mutableListOf()
    private lateinit var context : Context

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun clearFile() {
        fileList.forEach { it.delete() }
    }

    @Test
    fun create_GPX_file() {
        val file = GPXTransferUtils.createGpx(
            context,
            "UnitTest",
            FAKE_LOCATION_LIST
        )
        if (file != null) fileList.add(file)
        assertEquals(file?.exists(), true)
    }

    @Test
    fun create_GPX_file_and_check() {
        val file = GPXTransferUtils.createGpx(
            context,
            "UnitTest",
            FAKE_LOCATION_LIST
        )
        if (file != null) fileList.add(file)
        assertEquals(file?.exists(), true)
        val gpxString = GPXTransferUtils.gpxFileToString(file!!)
        assertEquals(gpxString, fakeGpxString)
    }

    @Test
    fun create_GPX_file_and_parse() {
        val file = GPXTransferUtils.createGpx(
            context,
            "UnitTest",
            FAKE_LOCATION_LIST
        )
        if (file != null) fileList.add(file)
        assertEquals(file?.exists(), true)
        assertEquals(GPXTransferUtils.parseGpx(file!!), FAKE_LOCATION_LIST)
    }

    //fakeData
    val fakeGpxString = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><gpx xmlns=\"http://www.topografix.com/GPX/1/1\"><metadata><link href=\"https://github.com/TS01923141/TrackingRoute\"><text>github</text></link><trk><name>UnitTest</name><trkseg><trkpt lat=\"1.1\" lon=\"1.1\"><ele>1.1</ele><time>1970-01-01T08:00:01Z</time></trkpt><trkpt lat=\"2.2\" lon=\"2.2\"><ele>2.2</ele><time>1970-01-01T08:00:02Z</time></trkpt><trkpt lat=\"3.3\" lon=\"3.3\"><ele>3.3</ele><time>1970-01-01T08:00:03Z</time></trkpt><trkpt lat=\"4.4\" lon=\"4.4\"><ele>4.4</ele><time>1970-01-01T08:00:04Z</time></trkpt><trkpt lat=\"5.5\" lon=\"5.5\"><ele>5.5</ele><time>1970-01-01T08:00:05Z</time></trkpt><trkpt lat=\"6.6\" lon=\"6.6\"><ele>6.6</ele><time>1970-01-01T08:00:06Z</time></trkpt></trkseg></trk></metadata></gpx>"

    val FAKE_LOCATION_LIST: List<LocationEntity> = listOf(
        LocationEntity.empty.copy(lat = 1.1, lng = 1.1, ele = 1.1, time = 1000),
        LocationEntity.empty.copy(lat = 2.2, lng = 2.2, ele = 2.2, time = 2000),
        LocationEntity.empty.copy(lat = 3.3, lng = 3.3, ele = 3.3, time = 3000),
        LocationEntity.empty.copy(lat = 4.4, lng = 4.4, ele = 4.4, time = 4000),
        LocationEntity.empty.copy(lat = 5.5, lng = 5.5, ele = 5.5, time = 5000),
        LocationEntity.empty.copy(lat = 6.6, lng = 6.6, ele = 6.6, time = 6000),
    )
}
