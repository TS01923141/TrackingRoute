package com.example.trackingroute.model.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import android.util.Xml
import com.example.trackingroute.model.database.LocationEntity
import org.xmlpull.v1.XmlSerializer
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "GPXTransferUtils"
object GPXTransferUtils {
    fun createGpx(context: Context, fileName: String, locationList: List<LocationEntity>) {
        var fileos: FileOutputStream? = null
        var serializer: XmlSerializer? = null
        try {
            val directory =
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) , "gpx")
            //build dir
            directory.mkdirs()
            val file = File(directory, "$fileName.gpx")
            Log.d(TAG, "createGpx: file.path: ${file.absolutePath}")
            //build file
            file.createNewFile()
            fileos = FileOutputStream(file)
            //build XmlSerializer
            serializer = Xml.newSerializer()
            //init
            serializer.setOutput(fileos, "UTF-8")
            //date format
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'", Locale.TAIWAN)

            serializer.startDocument("UTF-8", true)
            serializer.startTag("","gpx")
            serializer.attribute("", "xmlns", "http://www.topografix.com/GPX/1/1")
            serializer.startTag("","metadata")
            serializer.startTag("","link")
            serializer.attribute("", "href", "https://github.com/TS01923141/TrackingRoute")
            serializer.startTag("","text")
            serializer.text("github")
            serializer.endTag("","text")
            serializer.endTag("","link")
            //只有一條track
            serializer.startTag("","trk")
            serializer.startTag("","name")
            serializer.text(fileName)
            serializer.endTag("","name")
            //不分段
            serializer.startTag("","trkseg")
            for(locationEntity in locationList) {
                //location--
                serializer.startTag("", "trkpt")
                //lat
                serializer.attribute("", "lat", locationEntity.lat.toString())
                //lng
                serializer.attribute("", "lon", locationEntity.lng.toString())
                //ele
                serializer.startTag("", "ele")
                serializer.text(locationEntity.ele.toString())
                serializer.endTag("", "ele")
                //time
                serializer.startTag("", "time")
                serializer.text(dateFormat.format(locationEntity.time))
                serializer.endTag("", "time")
                //--location
                serializer.endTag("", "trkpt")
            }
            serializer.endTag("","trkseg")
            serializer.endTag("","trk")
            serializer.endTag("","metadata")
            serializer.endTag("","gpx")
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
    }
}