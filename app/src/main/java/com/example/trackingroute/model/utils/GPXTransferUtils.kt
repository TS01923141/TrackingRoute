package com.example.trackingroute.model.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import android.util.Xml
import com.example.trackingroute.model.database.LocationEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.*
import java.lang.Exception
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "GPXTransferUtils"

object GPXTransferUtils {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'", Locale.TAIWAN)
    
    internal object GPX_TAG{
        val GPX = "gpx"
        val METADATA = "metadata"
        val LINK = "link"
        val TEXT = "text"
        val TRACK = "trk"
        val NAME = "name"
        val TRACK_SEGMENT = "trkseg"
        val TRACK_POINT = "trkpt"
        val LATITUDE = "lat"
        val LONGITUDE = "lon"
        val ELEVATION = "ele"
        val TIME = "time"
    }

    fun createGpx(context: Context, fileName: String, locationList: List<LocationEntity>): File? {
        var fileos: FileOutputStream? = null
        var serializer: XmlSerializer? = null
        var file : File? = null
        try {
            val directory =
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) , "gpx")
            //build dir
            directory.mkdirs()
            file = File(directory, "$fileName.gpx")
            Log.d(TAG, "createGpx: file.path: ${file.absolutePath}")
            //build file
            file.createNewFile()
            fileos = FileOutputStream(file)
            //build XmlSerializer
            serializer = Xml.newSerializer()
            //init
            serializer.setOutput(fileos, "UTF-8")

            serializer.startDocument("UTF-8", true)
            serializer.startTag("",GPX_TAG.GPX)
            serializer.attribute("", "xmlns", "http://www.topografix.com/GPX/1/1")
            serializer.startTag("",GPX_TAG.METADATA)
            serializer.startTag("",GPX_TAG.LINK)
            serializer.attribute("", "href", "https://github.com/TS01923141/TrackingRoute")
            serializer.startTag("",GPX_TAG.TEXT)
            serializer.text("github")
            serializer.endTag("",GPX_TAG.TEXT)
            serializer.endTag("",GPX_TAG.LINK)
            //只有一條track
            serializer.startTag("",GPX_TAG.TRACK)
            serializer.startTag("",GPX_TAG.NAME)
            serializer.text(fileName)
            serializer.endTag("",GPX_TAG.NAME)
            //不分段
            serializer.startTag("",GPX_TAG.TRACK_SEGMENT)
            for(locationEntity in locationList) {
                //location--
                serializer.startTag("", GPX_TAG.TRACK_POINT)
                //lat
                serializer.attribute("", GPX_TAG.LATITUDE, locationEntity.lat.toString())
                //lng
                serializer.attribute("", GPX_TAG.LONGITUDE, locationEntity.lng.toString())
                //ele
                serializer.startTag("", GPX_TAG.ELEVATION)
                serializer.text(locationEntity.ele.toString())
                serializer.endTag("", GPX_TAG.ELEVATION)
                //time
                serializer.startTag("", GPX_TAG.TIME)
                serializer.text(timeFormat.format(locationEntity.time))
                serializer.endTag("", GPX_TAG.TIME)
                //--location
                serializer.endTag("", GPX_TAG.TRACK_POINT)
            }
            serializer.endTag("",GPX_TAG.TRACK_SEGMENT)
            serializer.endTag("",GPX_TAG.TRACK)
            serializer.endTag("",GPX_TAG.METADATA)
            serializer.endTag("",GPX_TAG.GPX)
            serializer.endDocument()
            Log.d(TAG, "createGpx: file.exists(): ${file.exists()}")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                serializer?.flush()
                fileos?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return file
    }

    /*
        <trkpt lat="00.0" lon="00.0">
            <ele>0.0</ele>
            <time>0.0</time>
        </trkpt>
     */
    fun parsePoint(parser: XmlPullParser): LocationEntity {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        //<trkpt lat="00.0" lon="00.0">
        var point : LocationEntity = LocationEntity.empty.copy(
            lat = parser.getAttributeValue(null, GPX_TAG.LATITUDE).toDouble(),
            lng = parser.getAttributeValue(null, GPX_TAG.LONGITUDE).toDouble()
        )
        //until </trkpt>
        while (parser.next() != XmlPullParser.END_TAG || parser.name != GPX_TAG.TRACK_POINT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                GPX_TAG.ELEVATION -> {
                    //<ele>0.0</ele>
                    if (parser.next() == XmlPullParser.TEXT) {
                        point.ele = parser.text.toDouble()
                    }
                }
                GPX_TAG.TIME -> {
                    //<time>0.0</time>
                    if (parser.next() == XmlPullParser.TEXT) {
                        point.time = timeFormat.parse(parser.text).time
                    }
                }
            }
        }
        return point
    }

    fun parseGpx(file: File): List<LocationEntity> {
        val locationList = mutableListOf<LocationEntity>()
        var fileis : FileInputStream? = null
        try {
            //set parser
            fileis = FileInputStream(file)
            val parser = Xml.newPullParser()
            parser.setInput(fileis, null)
            //parse
            var point : LocationEntity = LocationEntity.empty.copy()
            parser.next()
            //until </gpx>
            while (parser.eventType != XmlPullParser.END_TAG || parser.name != GPX_TAG.GPX) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        GPX_TAG.TRACK_POINT -> {
                            locationList.add(parsePoint(parser))
                        }
                    }
                }
                parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fileis?.close()
        }
//        locationList.forEach {
//            Log.d(TAG, "parseGpx: locationEntity: $it")
//        }
        return locationList
    }

    fun gpxFileToString(gpxFile: File): String {
        var fileis = FileInputStream(gpxFile)
        val stringBuilder = StringBuilder()
        try {
            val br = BufferedReader(InputStreamReader(fileis, "UTF-8"))
            var line = br.readLine()
            while (line != null) {
                stringBuilder.append(line)
                line = br.readLine()
            }
            Log.d(TAG, "printGpx: ${stringBuilder.toString()}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return stringBuilder.toString()
    }
}