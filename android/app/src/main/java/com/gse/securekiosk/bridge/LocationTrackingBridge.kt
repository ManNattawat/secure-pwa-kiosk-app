package com.gse.securekiosk.bridge

import android.content.Context
import android.location.Location
import android.webkit.JavascriptInterface
import android.util.Log
import com.gse.securekiosk.location.LocationHistoryManager
import com.gse.securekiosk.location.GeofencingManager
import org.json.JSONObject
import org.json.JSONArray

/**
 * LocationTrackingBridge - JavaScript Bridge สำหรับ Location Tracking
 * 
 * PWA เรียกใช้ผ่าน: window.AndroidLocation.*
 */
class LocationTrackingBridge(private val context: Context) {
    private val locationHistory = LocationHistoryManager(context)
    private val geofencingManager = GeofencingManager(context)
    private val TAG = "LocationTrackingBridge"

    /**
     * ดึงประวัติพิกัดของพนักงาน
     * 
     * @param employeeId: รหัสพนักงาน
     * @param startTime: เวลาเริ่มต้น (ISO 8601)
     * @param endTime: เวลาสิ้นสุด (ISO 8601)
     * @param limit: จำนวนสูงสุด (default: 1000)
     */
    @JavascriptInterface
    fun getLocationHistory(
        employeeId: String,
        startTime: String? = null,
        endTime: String? = null,
        limit: Int = 1000
    ): String {
        return try {
            val locations = locationHistory.getLocationHistory(
                employeeId,
                startTime,
                endTime,
                limit
            )
            
            val result = JSONObject().apply {
                put("success", true)
                put("count", locations.length())
                put("data", locations)
            }
            
            Log.d(TAG, "getLocationHistory: ${locations.length()} records")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getLocationHistory", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ดึง Geo-fence Events
     */
    @JavascriptInterface
    fun getGeofenceEvents(
        employeeId: String,
        startTime: String? = null,
        endTime: String? = null,
        limit: Int = 100
    ): String {
        return try {
            val events = locationHistory.getGeofenceEvents(
                employeeId,
                startTime,
                endTime,
                limit
            )
            
            val result = JSONObject().apply {
                put("success", true)
                put("count", events.length())
                put("data", events)
            }
            
            Log.d(TAG, "getGeofenceEvents: ${events.length()} events")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getGeofenceEvents", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ดึงสถิติการเคลื่อนที่
     */
    @JavascriptInterface
    fun getLocationStats(employeeId: String, date: String): String {
        return try {
            val stats = locationHistory.getLocationStats(employeeId, date)
            
            val result = JSONObject().apply {
                put("success", true)
                put("stats", stats)
            }
            
            Log.d(TAG, "getLocationStats: $date")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getLocationStats", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * เพิ่ม Geofences
     */
    @JavascriptInterface
    fun addGeofences(geofencesJson: String): String {
        return try {
            val array = JSONArray(geofencesJson)
            geofencingManager.addGeofences(array)
            
            val result = JSONObject().apply {
                put("success", true)
                put("count", array.length())
                put("message", "Added ${array.length()} geofences")
            }
            
            Log.d(TAG, "addGeofences: ${array.length()} geofences")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in addGeofences", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ลบ Geofence
     */
    @JavascriptInterface
    fun removeGeofence(geofenceId: String): String {
        return try {
            geofencingManager.removeGeofence(geofenceId)
            
            val result = JSONObject().apply {
                put("success", true)
                put("geofence_id", geofenceId)
            }
            
            Log.d(TAG, "removeGeofence: $geofenceId")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in removeGeofence", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ลบ Geofences ทั้งหมด
     */
    @JavascriptInterface
    fun clearGeofences(): String {
        return try {
            geofencingManager.clearGeofences()
            
            val result = JSONObject().apply {
                put("success", true)
                put("message", "Cleared all geofences")
            }
            
            Log.d(TAG, "clearGeofences")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in clearGeofences", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ดึงสถานะ Geofences
     */
    @JavascriptInterface
    fun getGeofenceStates(): String {
        return try {
            val states = geofencingManager.getAllStates()
            
            val result = JSONObject().apply {
                put("success", true)
                put("states", states)
            }
            
            Log.d(TAG, "getGeofenceStates: ${states.length()} states")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getGeofenceStates", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ตรวจสอบว่าอยู่ใน Geofence หรือไม่
     */
    @JavascriptInterface
    fun isInsideGeofence(geofenceId: String): String {
        return try {
            val isInside = geofencingManager.isInsideGeofence(geofenceId)
            
            val result = JSONObject().apply {
                put("success", true)
                put("geofence_id", geofenceId)
                put("is_inside", isInside)
            }
            
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in isInsideGeofence", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * หา Geofences ใกล้เคียง
     */
    @JavascriptInterface
    fun findNearbyGeofences(
        latitude: Double,
        longitude: Double,
        maxDistance: Double = 1000.0
    ): String {
        return try {
            val nearby = geofencingManager.findNearbyGeofences(
                latitude,
                longitude,
                maxDistance
            )
            
            val array = JSONArray()
            nearby.forEach { (geofence, distance) ->
                val json = JSONObject().apply {
                    put("id", geofence.id)
                    put("name", geofence.name)
                    put("type", geofence.type)
                    put("distance", distance)
                    put("latitude", geofence.latitude)
                    put("longitude", geofence.longitude)
                }
                array.put(json)
            }
            
            val result = JSONObject().apply {
                put("success", true)
                put("count", array.length())
                put("data", array)
            }
            
            Log.d(TAG, "findNearbyGeofences: ${array.length()} nearby")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in findNearbyGeofences", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * คำนวณระยะทางระหว่าง 2 จุด
     */
    @JavascriptInterface
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): String {
        return try {
            val distance = geofencingManager.calculateDistance(
                lat1, lon1, lat2, lon2
            )
            
            val result = JSONObject().apply {
                put("success", true)
                put("distance_meters", distance)
                put("distance_km", distance / 1000.0)
            }
            
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in calculateDistance", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ดึงรายการที่รอ Sync
     */
    @JavascriptInterface
    fun getUnsyncedLocations(limit: Int = 100): String {
        return try {
            val unsynced = locationHistory.getUnsyncedLocations(limit)
            
            val result = JSONObject().apply {
                put("success", true)
                put("count", unsynced.length())
                put("data", unsynced)
            }
            
            Log.d(TAG, "getUnsyncedLocations: ${unsynced.length()} records")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getUnsyncedLocations", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ทำเครื่องหมายว่า Sync แล้ว
     */
    @JavascriptInterface
    fun markLocationAsSynced(locationId: Long): String {
        return try {
            val success = locationHistory.markAsSynced(locationId)
            
            val result = JSONObject().apply {
                put("success", success)
                put("location_id", locationId)
            }
            
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in markLocationAsSynced", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ลบข้อมูลเก่า
     */
    @JavascriptInterface
    fun cleanupOldLocationData(daysToKeep: Int = 30): String {
        return try {
            val deletedRows = locationHistory.cleanupOldData(daysToKeep)
            
            val result = JSONObject().apply {
                put("success", true)
                put("deleted_rows", deletedRows)
            }
            
            Log.d(TAG, "cleanupOldLocationData: $deletedRows rows deleted")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in cleanupOldLocationData", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Check if Location Tracking Bridge is available
     */
    @JavascriptInterface
    fun isAvailable(): String {
        return JSONObject().apply {
            put("available", true)
            put("version", "1.0.0")
            put("features", JSONArray().apply {
                put("location_history")
                put("geofencing")
                put("location_stats")
                put("nearby_search")
            })
        }.toString()
    }
}

