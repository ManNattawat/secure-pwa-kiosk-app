package com.gse.securekiosk.location

import android.content.Context
import android.location.Location
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.*

/**
 * GeofencingManager - จัดการ Geo-fence (ตรวจสอบเข้า/ออกพื้นที่)
 * 
 * Features:
 * - Define geo-fence areas
 * - Detect enter/exit events
 * - Calculate dwell time
 * - Distance calculation
 */
class GeofencingManager(private val context: Context) {
    private val TAG = "GeofencingManager"
    private val locationHistory = LocationHistoryManager(context)
    
    // Active geofences (in-memory cache)
    private val activeGeofences = mutableMapOf<String, Geofence>()
    
    // Current geofence states
    private val currentStates = mutableMapOf<String, GeofenceState>()

    companion object {
        // Radius constants (meters)
        const val SMALL_RADIUS = 50.0      // 50m - ลูกค้า
        const val MEDIUM_RADIUS = 100.0    // 100m - สาขา
        const val LARGE_RADIUS = 500.0     // 500m - พื้นที่กว้าง
        
        // Event types
        const val EVENT_ENTER = "enter"
        const val EVENT_EXIT = "exit"
        const val EVENT_DWELL = "dwell"
    }

    /**
     * Geofence data class
     */
    data class Geofence(
        val id: String,
        val name: String,
        val type: String,        // branch, customer, office, etc.
        val latitude: Double,
        val longitude: Double,
        val radius: Double,      // meters
        val metadata: JSONObject = JSONObject()
    )

    /**
     * Geofence state
     */
    data class GeofenceState(
        val geofence: Geofence,
        val isInside: Boolean,
        val enteredAt: Long = 0,
        val exitedAt: Long = 0
    )

    /**
     * เพิ่ม Geofence
     */
    fun addGeofence(geofence: Geofence) {
        activeGeofences[geofence.id] = geofence
        Log.d(TAG, "Added geofence: ${geofence.name} (${geofence.id})")
    }

    /**
     * เพิ่ม Geofences จาก JSON
     */
    fun addGeofences(geofencesJson: JSONArray) {
        try {
            for (i in 0 until geofencesJson.length()) {
                val json = geofencesJson.getJSONObject(i)
                val geofence = Geofence(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    type = json.getString("type"),
                    latitude = json.getDouble("latitude"),
                    longitude = json.getDouble("longitude"),
                    radius = json.optDouble("radius", MEDIUM_RADIUS),
                    metadata = json.optJSONObject("metadata") ?: JSONObject()
                )
                addGeofence(geofence)
            }
            Log.d(TAG, "Added ${geofencesJson.length()} geofences")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding geofences", e)
        }
    }

    /**
     * ลบ Geofence
     */
    fun removeGeofence(geofenceId: String) {
        activeGeofences.remove(geofenceId)
        currentStates.remove(geofenceId)
        Log.d(TAG, "Removed geofence: $geofenceId")
    }

    /**
     * ลบ Geofences ทั้งหมด
     */
    fun clearGeofences() {
        activeGeofences.clear()
        currentStates.clear()
        Log.d(TAG, "Cleared all geofences")
    }

    /**
     * ตรวจสอบ Location และส่ง Geofence Events
     */
    fun checkLocation(
        employeeId: String,
        location: Location
    ): List<GeofenceEvent> {
        val events = mutableListOf<GeofenceEvent>()
        
        activeGeofences.forEach { (id, geofence) ->
            val distance = calculateDistance(
                location.latitude,
                location.longitude,
                geofence.latitude,
                geofence.longitude
            )
            
            val isInside = distance <= geofence.radius
            val previousState = currentStates[id]
            
            // ตรวจสอบการเปลี่ยนสถานะ
            if (previousState == null) {
                // First time checking this geofence
                if (isInside) {
                    val event = createEnterEvent(employeeId, geofence, location)
                    events.add(event)
                    currentStates[id] = GeofenceState(geofence, true, System.currentTimeMillis())
                } else {
                    currentStates[id] = GeofenceState(geofence, false)
                }
            } else {
                // Check for state change
                if (isInside && !previousState.isInside) {
                    // ENTER event
                    val event = createEnterEvent(employeeId, geofence, location)
                    events.add(event)
                    currentStates[id] = GeofenceState(geofence, true, System.currentTimeMillis())
                } else if (!isInside && previousState.isInside) {
                    // EXIT event
                    val dwellTime = (System.currentTimeMillis() - previousState.enteredAt) / 1000
                    val event = createExitEvent(employeeId, geofence, location, dwellTime.toInt())
                    events.add(event)
                    currentStates[id] = GeofenceState(geofence, false, exitedAt = System.currentTimeMillis())
                }
            }
        }
        
        // Save events to database
        events.forEach { event ->
            locationHistory.saveGeofenceEvent(
                employeeId = event.employeeId,
                geofenceId = event.geofenceId,
                eventType = event.eventType,
                latitude = event.latitude,
                longitude = event.longitude,
                placeName = event.placeName,
                placeType = event.placeType,
                duration = event.dwellTime
            )
        }
        
        return events
    }

    /**
     * สร้าง Enter Event
     */
    private fun createEnterEvent(
        employeeId: String,
        geofence: Geofence,
        location: Location
    ): GeofenceEvent {
        Log.d(TAG, "ENTER: ${geofence.name} ($employeeId)")
        return GeofenceEvent(
            employeeId = employeeId,
            geofenceId = geofence.id,
            eventType = EVENT_ENTER,
            placeName = geofence.name,
            placeType = geofence.type,
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * สร้าง Exit Event
     */
    private fun createExitEvent(
        employeeId: String,
        geofence: Geofence,
        location: Location,
        dwellTime: Int
    ): GeofenceEvent {
        Log.d(TAG, "EXIT: ${geofence.name} ($employeeId) - Dwell: ${dwellTime}s")
        return GeofenceEvent(
            employeeId = employeeId,
            geofenceId = geofence.id,
            eventType = EVENT_EXIT,
            placeName = geofence.name,
            placeType = geofence.type,
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = System.currentTimeMillis(),
            dwellTime = dwellTime
        )
    }

    /**
     * คำนวณระยะทาง (Haversine formula)
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }

    /**
     * ตรวจสอบว่าอยู่ใน Geofence หรือไม่
     */
    fun isInsideGeofence(geofenceId: String): Boolean {
        return currentStates[geofenceId]?.isInside ?: false
    }

    /**
     * ดึงรายการ Geofences ที่อยู่ภายใน
     */
    fun getCurrentGeofences(): List<Geofence> {
        return currentStates
            .filter { it.value.isInside }
            .map { it.value.geofence }
    }

    /**
     * ดึงข้อมูล Geofence State ทั้งหมด
     */
    fun getAllStates(): JSONArray {
        val result = JSONArray()
        currentStates.forEach { (id, state) ->
            val json = JSONObject().apply {
                put("geofence_id", id)
                put("name", state.geofence.name)
                put("type", state.geofence.type)
                put("is_inside", state.isInside)
                if (state.isInside) {
                    val dwellTime = (System.currentTimeMillis() - state.enteredAt) / 1000
                    put("dwell_time_seconds", dwellTime)
                }
            }
            result.put(json)
        }
        return result
    }

    /**
     * หา Geofences ใกล้เคียง
     */
    fun findNearbyGeofences(
        latitude: Double,
        longitude: Double,
        maxDistance: Double = 1000.0 // 1km
    ): List<Pair<Geofence, Double>> {
        return activeGeofences.values
            .map { geofence ->
                val distance = calculateDistance(
                    latitude, longitude,
                    geofence.latitude, geofence.longitude
                )
                Pair(geofence, distance)
            }
            .filter { it.second <= maxDistance }
            .sortedBy { it.second }
    }
}

/**
 * Geofence Event data class
 */
data class GeofenceEvent(
    val employeeId: String,
    val geofenceId: String,
    val eventType: String,
    val placeName: String,
    val placeType: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val dwellTime: Int = 0
)

