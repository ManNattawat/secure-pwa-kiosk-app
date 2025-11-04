package com.gse.securekiosk.location

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.location.Location
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

/**
 * LocationHistoryManager - จัดการประวัติการเคลื่อนที่ของพนักงาน
 * 
 * Features:
 * - เก็บประวัติพิกัด GPS
 * - Geo-fencing (ตรวจสอบเข้า/ออกพื้นที่)
 * - Route tracking
 * - Analytics
 */
class LocationHistoryManager(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val TAG = "LocationHistory"
        private const val DATABASE_NAME = "location_history.db"
        private const val DATABASE_VERSION = 1
        
        // Location History Table
        private const val TABLE_LOCATION = "location_history"
        private const val COL_ID = "id"
        private const val COL_EMPLOYEE_ID = "employee_id"
        private const val COL_LATITUDE = "latitude"
        private const val COL_LONGITUDE = "longitude"
        private const val COL_ACCURACY = "accuracy"
        private const val COL_SPEED = "speed"
        private const val COL_BEARING = "bearing"
        private const val COL_ALTITUDE = "altitude"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_ACTIVITY_TYPE = "activity_type" // still, walking, driving, etc.
        private const val COL_BATTERY_LEVEL = "battery_level"
        private const val COL_IS_SYNCED = "is_synced"
        private const val COL_SYNC_ATTEMPTS = "sync_attempts"
        private const val COL_SYNCED_AT = "synced_at"
        
        // Geo-fence Events Table
        private const val TABLE_GEOFENCE = "geofence_events"
        private const val COL_GEOFENCE_ID = "geofence_id"
        private const val COL_EVENT_TYPE = "event_type" // enter, exit, dwell
        private const val COL_LOCATION_ID = "location_id"
        private const val COL_PLACE_NAME = "place_name"
        private const val COL_PLACE_TYPE = "place_type" // branch, customer, office
        private const val COL_DURATION = "duration_seconds"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Location History Table
        val createLocationTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_LOCATION (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_EMPLOYEE_ID TEXT NOT NULL,
                $COL_LATITUDE REAL NOT NULL,
                $COL_LONGITUDE REAL NOT NULL,
                $COL_ACCURACY REAL,
                $COL_SPEED REAL,
                $COL_BEARING REAL,
                $COL_ALTITUDE REAL,
                $COL_TIMESTAMP TEXT NOT NULL,
                $COL_ACTIVITY_TYPE TEXT,
                $COL_BATTERY_LEVEL INTEGER,
                $COL_IS_SYNCED INTEGER DEFAULT 0,
                $COL_SYNC_ATTEMPTS INTEGER DEFAULT 0,
                $COL_SYNCED_AT TEXT
            )
        """.trimIndent()
        
        db.execSQL(createLocationTable)
        
        // Geo-fence Events Table
        val createGeofenceTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_GEOFENCE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_GEOFENCE_ID TEXT NOT NULL,
                $COL_EMPLOYEE_ID TEXT NOT NULL,
                $COL_EVENT_TYPE TEXT NOT NULL,
                $COL_LOCATION_ID INTEGER,
                $COL_PLACE_NAME TEXT,
                $COL_PLACE_TYPE TEXT,
                $COL_LATITUDE REAL NOT NULL,
                $COL_LONGITUDE REAL NOT NULL,
                $COL_TIMESTAMP TEXT NOT NULL,
                $COL_DURATION INTEGER DEFAULT 0,
                $COL_IS_SYNCED INTEGER DEFAULT 0,
                $COL_SYNC_ATTEMPTS INTEGER DEFAULT 0,
                $COL_SYNCED_AT TEXT,
                FOREIGN KEY($COL_LOCATION_ID) REFERENCES $TABLE_LOCATION($COL_ID)
            )
        """.trimIndent()
        
        db.execSQL(createGeofenceTable)
        
        // Create indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_employee_timestamp ON $TABLE_LOCATION($COL_EMPLOYEE_ID, $COL_TIMESTAMP)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_is_synced ON $TABLE_LOCATION($COL_IS_SYNCED)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_geofence_employee ON $TABLE_GEOFENCE($COL_EMPLOYEE_ID, $COL_TIMESTAMP)")
        
        Log.d(TAG, "Database created successfully")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOCATION")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_GEOFENCE")
        onCreate(db)
    }

    /**
     * บันทึกประวัติพิกัด
     */
    fun saveLocation(
        employeeId: String,
        location: Location,
        activityType: String? = null,
        batteryLevel: Int? = null
    ): Long {
        return try {
            val db = writableDatabase
            val now = getCurrentTimestamp()
            
            val values = ContentValues().apply {
                put(COL_EMPLOYEE_ID, employeeId)
                put(COL_LATITUDE, location.latitude)
                put(COL_LONGITUDE, location.longitude)
                put(COL_ACCURACY, location.accuracy)
                put(COL_SPEED, if (location.hasSpeed()) location.speed else null)
                put(COL_BEARING, if (location.hasBearing()) location.bearing else null)
                put(COL_ALTITUDE, if (location.hasAltitude()) location.altitude else null)
                put(COL_TIMESTAMP, now)
                put(COL_ACTIVITY_TYPE, activityType)
                put(COL_BATTERY_LEVEL, batteryLevel)
                put(COL_IS_SYNCED, 0)
                put(COL_SYNC_ATTEMPTS, 0)
            }
            
            val id = db.insert(TABLE_LOCATION, null, values)
            Log.d(TAG, "Saved location: $id for employee $employeeId")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Error saving location", e)
            -1L
        }
    }

    /**
     * บันทึก Geo-fence Event
     */
    fun saveGeofenceEvent(
        employeeId: String,
        geofenceId: String,
        eventType: String, // enter, exit, dwell
        latitude: Double,
        longitude: Double,
        placeName: String,
        placeType: String,
        duration: Int = 0
    ): Long {
        return try {
            val db = writableDatabase
            val now = getCurrentTimestamp()
            
            val values = ContentValues().apply {
                put(COL_GEOFENCE_ID, geofenceId)
                put(COL_EMPLOYEE_ID, employeeId)
                put(COL_EVENT_TYPE, eventType)
                put(COL_LATITUDE, latitude)
                put(COL_LONGITUDE, longitude)
                put(COL_PLACE_NAME, placeName)
                put(COL_PLACE_TYPE, placeType)
                put(COL_TIMESTAMP, now)
                put(COL_DURATION, duration)
                put(COL_IS_SYNCED, 0)
            }
            
            val id = db.insert(TABLE_GEOFENCE, null, values)
            Log.d(TAG, "Saved geofence event: $eventType at $placeName")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Error saving geofence event", e)
            -1L
        }
    }

    /**
     * ดึงประวัติพิกัดของพนักงาน
     */
    fun getLocationHistory(
        employeeId: String,
        startTime: String? = null,
        endTime: String? = null,
        limit: Int = 1000
    ): JSONArray {
        val result = JSONArray()
        val db = readableDatabase
        
        try {
            val whereClause = buildString {
                append("$COL_EMPLOYEE_ID = ?")
                if (startTime != null) append(" AND $COL_TIMESTAMP >= ?")
                if (endTime != null) append(" AND $COL_TIMESTAMP <= ?")
            }
            
            val whereArgs = buildList {
                add(employeeId)
                if (startTime != null) add(startTime)
                if (endTime != null) add(endTime)
            }.toTypedArray()
            
            val cursor = db.query(
                TABLE_LOCATION,
                null,
                whereClause,
                whereArgs,
                null,
                null,
                "$COL_TIMESTAMP DESC",
                limit.toString()
            )
            
            while (cursor.moveToNext()) {
                val json = JSONObject().apply {
                    put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)))
                    put("latitude", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LATITUDE)))
                    put("longitude", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LONGITUDE)))
                    put("accuracy", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_ACCURACY)))
                    put("timestamp", cursor.getString(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)))
                    put("activity_type", cursor.getString(cursor.getColumnIndexOrThrow(COL_ACTIVITY_TYPE)))
                    put("battery_level", cursor.getInt(cursor.getColumnIndexOrThrow(COL_BATTERY_LEVEL)))
                }
                result.put(json)
            }
            cursor.close()
            
            Log.d(TAG, "Retrieved ${result.length()} location records")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location history", e)
        }
        
        return result
    }

    /**
     * ดึง Geo-fence Events
     */
    fun getGeofenceEvents(
        employeeId: String,
        startTime: String? = null,
        endTime: String? = null,
        limit: Int = 100
    ): JSONArray {
        val result = JSONArray()
        val db = readableDatabase
        
        try {
            val whereClause = buildString {
                append("$COL_EMPLOYEE_ID = ?")
                if (startTime != null) append(" AND $COL_TIMESTAMP >= ?")
                if (endTime != null) append(" AND $COL_TIMESTAMP <= ?")
            }
            
            val whereArgs = buildList {
                add(employeeId)
                if (startTime != null) add(startTime)
                if (endTime != null) add(endTime)
            }.toTypedArray()
            
            val cursor = db.query(
                TABLE_GEOFENCE,
                null,
                whereClause,
                whereArgs,
                null,
                null,
                "$COL_TIMESTAMP DESC",
                limit.toString()
            )
            
            while (cursor.moveToNext()) {
                val json = JSONObject().apply {
                    put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)))
                    put("geofence_id", cursor.getString(cursor.getColumnIndexOrThrow(COL_GEOFENCE_ID)))
                    put("event_type", cursor.getString(cursor.getColumnIndexOrThrow(COL_EVENT_TYPE)))
                    put("place_name", cursor.getString(cursor.getColumnIndexOrThrow(COL_PLACE_NAME)))
                    put("place_type", cursor.getString(cursor.getColumnIndexOrThrow(COL_PLACE_TYPE)))
                    put("latitude", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LATITUDE)))
                    put("longitude", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LONGITUDE)))
                    put("timestamp", cursor.getString(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)))
                    put("duration", cursor.getInt(cursor.getColumnIndexOrThrow(COL_DURATION)))
                }
                result.put(json)
            }
            cursor.close()
            
            Log.d(TAG, "Retrieved ${result.length()} geofence events")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting geofence events", e)
        }
        
        return result
    }

    /**
     * ดึงรายการที่ต้อง Sync
     */
    fun getUnsyncedLocations(limit: Int = 100): JSONArray {
        val result = JSONArray()
        val db = readableDatabase
        
        try {
            val cursor = db.query(
                TABLE_LOCATION,
                null,
                "$COL_IS_SYNCED = ?",
                arrayOf("0"),
                null,
                null,
                "$COL_TIMESTAMP ASC",
                limit.toString()
            )
            
            while (cursor.moveToNext()) {
                val json = JSONObject().apply {
                    put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)))
                    put("employee_id", cursor.getString(cursor.getColumnIndexOrThrow(COL_EMPLOYEE_ID)))
                    put("latitude", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LATITUDE)))
                    put("longitude", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LONGITUDE)))
                    put("accuracy", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_ACCURACY)))
                    put("timestamp", cursor.getString(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)))
                }
                result.put(json)
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unsynced locations", e)
        }
        
        return result
    }

    /**
     * ทำเครื่องหมายว่า Sync แล้ว
     */
    fun markAsSynced(locationId: Long): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_IS_SYNCED, 1)
                put(COL_SYNCED_AT, getCurrentTimestamp())
            }
            
            val rows = db.update(
                TABLE_LOCATION,
                values,
                "$COL_ID = ?",
                arrayOf(locationId.toString())
            )
            
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error marking as synced", e)
            false
        }
    }

    /**
     * ลบข้อมูลเก่า
     */
    fun cleanupOldData(daysToKeep: Int = 30): Int {
        val db = writableDatabase
        val cutoffDate = getCutoffTimestamp(daysToKeep)
        
        try {
            val rows = db.delete(
                TABLE_LOCATION,
                "$COL_SYNCED_AT IS NOT NULL AND $COL_SYNCED_AT < ? AND $COL_IS_SYNCED = 1",
                arrayOf(cutoffDate)
            )
            
            Log.d(TAG, "Cleaned up $rows old location records")
            return rows
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old data", e)
            return 0
        }
    }

    /**
     * วิเคราะห์การเคลื่อนที่
     */
    fun getLocationStats(employeeId: String, date: String): JSONObject {
        val stats = JSONObject()
        val db = readableDatabase
        
        try {
            // Total distance traveled
            val distanceCursor = db.rawQuery("""
                SELECT 
                    COUNT(*) as count,
                    AVG($COL_SPEED) as avg_speed,
                    MAX($COL_SPEED) as max_speed
                FROM $TABLE_LOCATION
                WHERE $COL_EMPLOYEE_ID = ?
                AND DATE($COL_TIMESTAMP) = DATE(?)
            """.trimIndent(), arrayOf(employeeId, date))
            
            if (distanceCursor.moveToFirst()) {
                stats.put("total_points", distanceCursor.getInt(0))
                stats.put("avg_speed", distanceCursor.getDouble(1))
                stats.put("max_speed", distanceCursor.getDouble(2))
            }
            distanceCursor.close()
            
            // Geofence events
            val geofenceCursor = db.rawQuery("""
                SELECT 
                    $COL_EVENT_TYPE,
                    COUNT(*) as count
                FROM $TABLE_GEOFENCE
                WHERE $COL_EMPLOYEE_ID = ?
                AND DATE($COL_TIMESTAMP) = DATE(?)
                GROUP BY $COL_EVENT_TYPE
            """.trimIndent(), arrayOf(employeeId, date))
            
            val events = JSONObject()
            while (geofenceCursor.moveToNext()) {
                events.put(geofenceCursor.getString(0), geofenceCursor.getInt(1))
            }
            geofenceCursor.close()
            stats.put("geofence_events", events)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location stats", e)
        }
        
        return stats
    }

    // Helper functions
    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun getCutoffTimestamp(daysAgo: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(calendar.time)
    }
}

