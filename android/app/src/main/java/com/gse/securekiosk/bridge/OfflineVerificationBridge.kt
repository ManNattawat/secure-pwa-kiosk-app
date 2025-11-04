package com.gse.securekiosk.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import android.util.Log
import com.gse.securekiosk.storage.OfflineStorageManager
import org.json.JSONObject
import org.json.JSONArray

/**
 * OfflineVerificationBridge - JavaScript Bridge สำหรับ Offline Verification
 * 
 * PWA เรียกใช้ผ่าน: window.Android.saveOfflineVerification(jsonData)
 */
class OfflineVerificationBridge(private val context: Context) {
    private val storageManager = OfflineStorageManager(context)
    private val TAG = "OfflineVerificationBridge"

    /**
     * บันทึกข้อมูล Verification แบบ Offline
     * 
     * @param jsonData: {
     *   "contract_number": "1066026808001",
     *   "contract_type": "gold",
     *   "branch_code": "066",
     *   "service_line_no": "02",
     *   "checks_data": { ... }
     * }
     */
    @JavascriptInterface
    fun saveOfflineVerification(jsonData: String): String {
        return try {
            val json = JSONObject(jsonData)
            val contractNumber = json.getString("contract_number")
            val contractType = json.optString("contract_type", "unknown")
            val branchCode = json.optString("branch_code", "")
            val serviceLineNo = json.optString("service_line_no", "")
            val checksData = json.getJSONObject("checks_data")
            
            val success = storageManager.saveVerificationData(
                contractNumber,
                contractType,
                branchCode,
                serviceLineNo,
                checksData
            )
            
            val result = JSONObject().apply {
                put("success", success)
                put("contract_number", contractNumber)
                put("message", if (success) "Saved successfully" else "Failed to save")
                put("timestamp", System.currentTimeMillis())
            }
            
            Log.d(TAG, "saveOfflineVerification: $success for $contractNumber")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveOfflineVerification", e)
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
    fun getSyncQueue(): String {
        return try {
            val queue = storageManager.getSyncQueue()
            val result = JSONObject().apply {
                put("success", true)
                put("count", queue.length())
                put("data", queue)
            }
            
            Log.d(TAG, "getSyncQueue: ${queue.length()} items")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getSyncQueue", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ทำเครื่องหมายว่า Sync สำเร็จแล้ว
     */
    @JavascriptInterface
    fun markAsSynced(contractNumber: String): String {
        return try {
            val success = storageManager.markAsSynced(contractNumber)
            val result = JSONObject().apply {
                put("success", success)
                put("contract_number", contractNumber)
            }
            
            Log.d(TAG, "markAsSynced: $success for $contractNumber")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in markAsSynced", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * บันทึก Sync Error
     */
    @JavascriptInterface
    fun recordSyncError(contractNumber: String, errorMessage: String): String {
        return try {
            val success = storageManager.recordSyncError(contractNumber, errorMessage)
            val result = JSONObject().apply {
                put("success", success)
                put("contract_number", contractNumber)
            }
            
            Log.d(TAG, "recordSyncError: $errorMessage for $contractNumber")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in recordSyncError", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ดึงข้อมูล Verification ของ Contract Number
     */
    @JavascriptInterface
    fun getVerificationData(contractNumber: String): String {
        return try {
            val data = storageManager.getVerificationData(contractNumber)
            val result = JSONObject().apply {
                put("success", data != null)
                if (data != null) {
                    put("data", data)
                }
            }
            
            Log.d(TAG, "getVerificationData: ${data != null} for $contractNumber")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getVerificationData", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * นับจำนวนที่รอ Sync
     */
    @JavascriptInterface
    fun getPendingSyncCount(): String {
        return try {
            val count = storageManager.getPendingSyncCount()
            val result = JSONObject().apply {
                put("success", true)
                put("count", count)
            }
            
            Log.d(TAG, "getPendingSyncCount: $count")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getPendingSyncCount", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
                put("count", 0)
            }.toString()
        }
    }

    /**
     * ลบข้อมูลเก่า
     */
    @JavascriptInterface
    fun cleanupOldData(daysToKeep: Int = 30): String {
        return try {
            val deletedRows = storageManager.cleanupOldData(daysToKeep)
            val result = JSONObject().apply {
                put("success", true)
                put("deleted_rows", deletedRows)
            }
            
            Log.d(TAG, "cleanupOldData: $deletedRows rows deleted")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in cleanupOldData", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Reset Failed Syncs
     */
    @JavascriptInterface
    fun resetFailedSyncs(): String {
        return try {
            val resetRows = storageManager.resetFailedSyncs()
            val result = JSONObject().apply {
                put("success", true)
                put("reset_rows", resetRows)
            }
            
            Log.d(TAG, "resetFailedSyncs: $resetRows rows reset")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in resetFailedSyncs", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Sync All Pending (Helper สำหรับ PWA)
     * PWA จะเรียก getSyncQueue() แล้ว sync ทีละรายการ แล้วเรียก markAsSynced()
     */
    @JavascriptInterface
    fun syncAllPending(): String {
        return try {
            val queue = storageManager.getSyncQueue()
            val result = JSONObject().apply {
                put("success", true)
                put("message", "Please sync ${queue.length()} items using getSyncQueue()")
                put("count", queue.length())
            }
            
            Log.d(TAG, "syncAllPending: ${queue.length()} items to sync")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncAllPending", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Check if Native Bridge is available
     */
    @JavascriptInterface
    fun isAvailable(): String {
        return JSONObject().apply {
            put("available", true)
            put("version", "1.0.0")
            put("features", JSONArray().apply {
                put("offline_verification")
                put("sync_queue")
                put("auto_sync")
            })
        }.toString()
    }
}

