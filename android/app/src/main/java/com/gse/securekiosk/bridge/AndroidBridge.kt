package com.gse.securekiosk.bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import com.gse.securekiosk.location.LocationHistoryTracker
import com.gse.securekiosk.remote.RemoteControlManager
import com.gse.securekiosk.scanner.BarcodeScannerService
import com.gse.securekiosk.util.DeviceConfig
import com.google.mlkit.vision.common.InputImage
import org.json.JSONArray
import org.json.JSONObject

/**
 * JavaScript Interface Bridge
 * ให้ PWA เรียกใช้ Native App features ได้ผ่าน window.AndroidBridge
 */
class AndroidBridge(
    private val context: Context,
    private val webView: android.webkit.WebView?
) {
    private val TAG = "AndroidBridge"

    /**
     * Lock Device (ล็อกเครื่อง)
     */
    @JavascriptInterface
    fun lockDevice(): String {
        return try {
            val success = RemoteControlManager.lockDevice(context)
            JSONObject().apply {
                put("success", success)
                put("message", if (success) "Device locked successfully" else "Failed to lock device")
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in lockDevice", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Wipe Device (ลบข้อมูลทั้งหมด)
     */
    @JavascriptInterface
    fun wipeDevice(flags: String = "0"): String {
        return try {
            val success = RemoteControlManager.wipeDevice(context, flags.toIntOrNull() ?: 0)
            JSONObject().apply {
                put("success", success)
                put("message", if (success) "Device wipe initiated" else "Failed to wipe device")
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in wipeDevice", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Reboot Device (รีสตาร์ทเครื่อง)
     */
    @JavascriptInterface
    fun rebootDevice(): String {
        return try {
            val success = RemoteControlManager.rebootDevice(context)
            JSONObject().apply {
                put("success", success)
                put("message", if (success) "Device reboot initiated" else "Failed to reboot device")
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in rebootDevice", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Get Device Status (ดูสถานะเครื่อง)
     */
    @JavascriptInterface
    fun getDeviceStatus(): String {
        return try {
            val status = RemoteControlManager.getDeviceStatus(context)
            JSONObject().apply {
                put("success", true)
                put("data", JSONObject().apply {
                    put("isDeviceOwner", status["isDeviceOwner"])
                    put("isDeviceAdmin", status["isDeviceAdmin"])
                    put("androidVersion", status["androidVersion"])
                    put("deviceModel", status["deviceModel"])
                    put("deviceManufacturer", status["deviceManufacturer"])
                    put("packageName", status["packageName"])
                })
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getDeviceStatus", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Get Location History (ดึงประวัติการเดินทาง)
     */
    @JavascriptInterface
    fun getLocationHistory(limitJson: String = "null"): String {
        return try {
            val limit = if (limitJson == "null") null else limitJson.toIntOrNull()
            val history = LocationHistoryTracker.getLocalHistory(context, limit)
            
            val historyArray = JSONArray()
            history.forEach { item ->
                historyArray.put(JSONObject().apply {
                    put("latitude", item.latitude)
                    put("longitude", item.longitude)
                    put("accuracy", item.accuracy)
                    put("bearing", item.bearing)
                    put("speed", item.speed)
                    put("timestamp", item.timestamp)
                    put("provider", item.provider)
                })
            }
            
            JSONObject().apply {
                put("success", true)
                put("data", historyArray)
                put("count", history.size)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getLocationHistory", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Get Last Location (ดึงตำแหน่งล่าสุด)
     */
    @JavascriptInterface
    fun getLastLocation(): String {
        return try {
            val location = LocationHistoryTracker.getLastLocation(context)
            
            if (location != null) {
                JSONObject().apply {
                    put("success", true)
                    put("data", JSONObject().apply {
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("accuracy", location.accuracy)
                        put("bearing", location.bearing)
                        put("speed", location.speed)
                        put("timestamp", location.timestamp)
                        put("provider", location.provider)
                    })
                }.toString()
            } else {
                JSONObject().apply {
                    put("success", false)
                    put("error", "No location history available")
                }.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getLastLocation", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Get Device ID (ดึง Device ID)
     */
    @JavascriptInterface
    fun getDeviceId(): String {
        return try {
            val deviceId = DeviceConfig.getDeviceId(context)
            JSONObject().apply {
                put("success", true)
                put("data", deviceId)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getDeviceId", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Scan Barcode from Image (สแกนบาร์โค้ดจากรูปภาพ)
     * รับ base64 image string และ callback function name
     * 
     * Note: JavaScript Interface ไม่รองรับ async/await โดยตรง
     * ต้องใช้ callback function name แทน
     */
    @JavascriptInterface
    fun scanBarcodeFromImage(base64Image: String, callbackName: String) {
        try {
            // Decode base64 to bitmap
            val base64Data = if (base64Image.contains(",")) {
                base64Image.substringAfter(",") // Remove data:image/...;base64, prefix
            } else {
                base64Image
            }
            
            val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            if (bitmap == null) {
                val errorResult = JSONObject().apply {
                    put("success", false)
                    put("error", "Failed to decode image")
                }.toString()
                callJavaScriptCallback(callbackName, errorResult)
                return
            }
            
            val image = InputImage.fromBitmap(bitmap, 0)
            
            BarcodeScannerService.scanBarcode(
                image = image,
                onSuccess = { barcodes ->
                    val result = JSONObject().apply {
                        put("success", true)
                        if (barcodes.isNotEmpty()) {
                            val barcodesJson = JSONArray()
                            barcodes.forEach { barcode ->
                                barcodesJson.put(JSONObject(BarcodeScannerService.barcodeToJson(barcode)))
                            }
                            put("data", barcodesJson)
                        } else {
                            put("data", JSONArray())
                        }
                        put("count", barcodes.size)
                    }.toString()
                    callJavaScriptCallback(callbackName, result)
                },
                onError = { e ->
                    val errorResult = JSONObject().apply {
                        put("success", false)
                        put("error", e.message ?: "Unknown error")
                    }.toString()
                    callJavaScriptCallback(callbackName, errorResult)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in scanBarcodeFromImage", e)
            val errorResult = JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
            callJavaScriptCallback(callbackName, errorResult)
        }
    }
    
    /**
     * Call JavaScript callback function
     */
    private fun callJavaScriptCallback(callbackName: String, result: String) {
        webView?.post {
            // Escape the JSON string properly for JavaScript
            // Replace all backslashes, quotes, and newlines
            val escaped = result
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            
            val jsCode = """
                (function() {
                    try {
                        var callback = window['$callbackName'];
                        if (typeof callback === 'function') {
                            var resultJson = '$escaped';
                            var result = JSON.parse(resultJson);
                            callback(resultJson);
                        } else {
                            console.warn('Callback function $callbackName not found');
                        }
                    } catch (e) {
                        console.error('Error in callback:', e);
                    }
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(jsCode, null)
        }
    }
}

