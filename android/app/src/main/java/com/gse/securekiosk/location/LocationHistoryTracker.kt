package com.gse.securekiosk.location

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.gse.securekiosk.util.DeviceConfig
import com.gse.securekiosk.supabase.SupabaseClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Location History Tracker
 * บันทึกประวัติการเดินทางของเครื่องเพื่อดูว่ามีการเคลื่อนที่ไปทางไหนบ้าง
 */
object LocationHistoryTracker {
    private const val PREFS_NAME = "location_history"
    private const val KEY_HISTORY = "location_history_list"
    private const val MAX_LOCAL_HISTORY = 100 // เก็บประวัติในเครื่องสูงสุด 100 จุด

    /**
     * บันทึกตำแหน่งใหม่ลงในประวัติ
     */
    fun recordLocation(context: Context, location: Location) {
        GlobalScope.launch {
            try {
                // บันทึกลง SharedPreferences (เก็บในเครื่อง)
                saveLocationToLocal(context, location)
                
                // ส่งไปยัง Supabase (เก็บใน cloud)
                sendLocationToServer(context, location)
            } catch (e: Exception) {
                android.util.Log.e("LocationHistory", "Error recording location: ${e.message}", e)
            }
        }
    }

    /**
     * บันทึกตำแหน่งลง SharedPreferences (เก็บในเครื่อง)
     */
    private fun saveLocationToLocal(context: Context, location: Location) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyJson = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        
        val historyArray = JSONArray(historyJson)
        val locationJson = JSONObject().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracy)
            put("bearing", location.bearing)
            put("speed", location.speed)
            put("timestamp", location.time)
            put("provider", location.provider ?: "unknown")
        }
        
        historyArray.put(locationJson)
        
        // จำกัดจำนวนข้อมูลในเครื่อง (เก็บแค่รายการล่าสุด)
        if (historyArray.length() > MAX_LOCAL_HISTORY) {
            val newArray = JSONArray()
            // เอาแค่รายการล่าสุด
            for (i in (historyArray.length() - MAX_LOCAL_HISTORY) until historyArray.length()) {
                newArray.put(historyArray.get(i))
            }
            prefs.edit().putString(KEY_HISTORY, newArray.toString()).apply()
        } else {
            prefs.edit().putString(KEY_HISTORY, historyArray.toString()).apply()
        }
    }

    /**
     * ส่งตำแหน่งไปยัง Supabase
     */
    private suspend fun sendLocationToServer(context: Context, location: Location) {
        val deviceId = DeviceConfig.getDeviceId(context)
        SupabaseClient(context).sendLocationHistory(
            deviceId = deviceId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy.toDouble(),
            bearing = location.bearing.toDouble(),
            speed = location.speed.toDouble(),
            recordedAt = location.time,
            provider = location.provider ?: "unknown"
        )
    }

    /**
     * ดึงประวัติการเดินทางจากเครื่อง
     * @param limit จำนวนรายการที่ต้องการ (default: ทั้งหมด)
     */
    fun getLocalHistory(context: Context, limit: Int? = null): List<LocationHistoryItem> {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyJson = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val historyArray = JSONArray(historyJson)
        
        val historyList = mutableListOf<LocationHistoryItem>()
        val startIndex = if (limit != null && historyArray.length() > limit) {
            historyArray.length() - limit
        } else {
            0
        }
        
        for (i in startIndex until historyArray.length()) {
            val itemJson = historyArray.getJSONObject(i)
            historyList.add(LocationHistoryItem(
                latitude = itemJson.getDouble("latitude"),
                longitude = itemJson.getDouble("longitude"),
                accuracy = itemJson.getDouble("accuracy"),
                bearing = itemJson.getDouble("bearing"),
                speed = itemJson.getDouble("speed"),
                timestamp = itemJson.getLong("timestamp"),
                provider = itemJson.getString("provider")
            ))
        }
        
        return historyList.reversed() // เรียงจากใหม่ไปเก่า
    }

    /**
     * ลบประวัติการเดินทางทั้งหมดในเครื่อง
     */
    fun clearLocalHistory(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, "[]").apply()
    }

    /**
     * ดึงตำแหน่งล่าสุด
     */
    fun getLastLocation(context: Context): LocationHistoryItem? {
        val history = getLocalHistory(context, limit = 1)
        return history.firstOrNull()
    }

    /**
     * ดึงเส้นทาง (route) ระหว่างสองช่วงเวลา
     */
    fun getRouteBetween(context: Context, startTime: Long, endTime: Long): List<LocationHistoryItem> {
        val history = getLocalHistory(context)
        return history.filter { it.timestamp in startTime..endTime }
    }
}

/**
 * ข้อมูลตำแหน่งในประวัติ
 */
data class LocationHistoryItem(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val bearing: Double,
    val speed: Double,
    val timestamp: Long,
    val provider: String
) {
    /**
     * คำนวณระยะทางจากตำแหน่งนี้ไปยังอีกตำแหน่งหนึ่ง (เมตร)
     */
    fun distanceTo(other: LocationHistoryItem): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            latitude, longitude,
            other.latitude, other.longitude,
            results
        )
        return results[0]
    }
}

