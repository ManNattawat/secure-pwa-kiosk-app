package com.gse.securekiosk.bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import com.gse.securekiosk.location.LocationHistoryTracker
import com.gse.securekiosk.remote.RemoteControlManager
import com.gse.securekiosk.scanner.BarcodeScannerService
import com.gse.securekiosk.scanner.CameraScannerActivity
import com.gse.securekiosk.ocr.DocumentScannerActivity
import com.gse.securekiosk.ocr.OcrService
import com.gse.securekiosk.offline.OfflineDataManager
import com.gse.securekiosk.sync.SyncManager
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
    private val webView: android.webkit.WebView?,
    private val activity: Activity?
) {
    private val TAG = "AndroidBridge"
    private var cameraScanCallbackName: String? = null
    private var documentScanCallbackName: String? = null

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
     * Open Camera Scanner (เปิดกล้องสแกนบาร์โค้ดแบบ real-time)
     * @param callbackName JavaScript callback function name
     */
    @JavascriptInterface
    fun openCameraScanner(callbackName: String) {
        try {
            if (activity == null || activity !is com.gse.securekiosk.MainActivity) {
                val errorResult = JSONObject().apply {
                    put("success", false)
                    put("error", "Activity not available")
                }.toString()
                callJavaScriptCallback(callbackName, errorResult)
                return
            }
            
            val mainActivity = activity as com.gse.securekiosk.MainActivity
            
            // Check camera permission
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                // Request permission first
                cameraScanCallbackName = callbackName // Store callback for after permission granted
                mainActivity.requestCameraPermission()
                return
            }
            
            // Store callback name for result
            cameraScanCallbackName = callbackName
            
            // Open camera scanner activity - run on UI thread
            mainActivity.runOnUiThread {
                try {
                    val intent = Intent(context, CameraScannerActivity::class.java)
                    mainActivity.startActivityForResult(intent, CameraScannerActivity.REQUEST_CODE)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching camera scanner activity", e)
                    val errorResult = JSONObject().apply {
                        put("success", false)
                        put("error", e.message ?: "Failed to open camera: ${e.javaClass.simpleName}")
                    }.toString()
                    callJavaScriptCallback(callbackName, errorResult)
                    cameraScanCallbackName = null
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera scanner", e)
            val errorResult = JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
            callJavaScriptCallback(callbackName, errorResult)
            cameraScanCallbackName = null
        }
    }
    
    /**
     * Called after camera permission is granted
     */
    fun onCameraPermissionGranted() {
        val callbackName = cameraScanCallbackName ?: return
        
        // Try opening camera scanner again
        val activity = activity ?: return
        if (activity !is com.gse.securekiosk.MainActivity) return
        
        // Run on UI thread to ensure activity is ready
        activity.runOnUiThread {
            try {
                val intent = Intent(context, CameraScannerActivity::class.java)
                activity.startActivityForResult(intent, CameraScannerActivity.REQUEST_CODE)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening camera scanner after permission granted", e)
                val errorResult = JSONObject().apply {
                    put("success", false)
                    put("error", e.message ?: "Failed to open camera: ${e.javaClass.simpleName}")
                }.toString()
                callJavaScriptCallback(callbackName, errorResult)
                cameraScanCallbackName = null
            }
        }
    }
    
    /**
     * Called when camera permission is denied
     */
    fun onCameraPermissionDenied() {
        val callbackName = cameraScanCallbackName ?: return
        cameraScanCallbackName = null
        
        val errorResult = JSONObject().apply {
            put("success", false)
            put("error", "Camera permission denied")
            put("requiresPermission", true)
        }.toString()
        callJavaScriptCallback(callbackName, errorResult)
    }
    
    /**
     * Handle camera scan result (เรียกจาก MainActivity)
     */
    fun handleCameraScanResult(resultCode: Int, data: Intent?) {
        val callbackName = cameraScanCallbackName ?: return
        cameraScanCallbackName = null
        
        if (resultCode == Activity.RESULT_OK && data != null) {
            val barcodeValue = data.getStringExtra(CameraScannerActivity.EXTRA_BARCODE_VALUE)
            val barcodeJson = data.getStringExtra(CameraScannerActivity.EXTRA_RESULT)
            
            if (barcodeValue != null && barcodeJson != null) {
                val result = JSONObject().apply {
                    put("success", true)
                    put("barcodeValue", barcodeValue)
                    put("barcodeData", JSONObject(barcodeJson))
                }.toString()
                callJavaScriptCallback(callbackName, result)
            } else {
                val errorResult = JSONObject().apply {
                    put("success", false)
                    put("error", "No barcode found")
                }.toString()
                callJavaScriptCallback(callbackName, errorResult)
            }
        } else {
            val errorResult = JSONObject().apply {
                put("success", false)
                put("error", "Scan cancelled or failed")
            }.toString()
            callJavaScriptCallback(callbackName, errorResult)
        }
    }
    
    /**
     * Open Document Scanner (เปิดกล้องสแกนเอกสารด้วย OCR)
     * @param callbackName JavaScript callback function name
     */
    @JavascriptInterface
    fun openDocumentScanner(callbackName: String) {
        try {
            if (activity == null || activity !is com.gse.securekiosk.MainActivity) {
                val errorResult = JSONObject().apply {
                    put("success", false)
                    put("error", "Activity not available")
                }.toString()
                callJavaScriptCallback(callbackName, errorResult)
                return
            }
            
            val mainActivity = activity as com.gse.securekiosk.MainActivity
            
            // Check camera permission
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                // Request permission first
                documentScanCallbackName = callbackName
                mainActivity.requestCameraPermission()
                return
            }
            
            // Store callback name for result
            documentScanCallbackName = callbackName
            
            // Open document scanner activity
            mainActivity.runOnUiThread {
                try {
                    val intent = Intent(context, DocumentScannerActivity::class.java)
                    mainActivity.startActivityForResult(intent, DocumentScannerActivity.REQUEST_CODE)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching document scanner activity", e)
                    val errorResult = JSONObject().apply {
                        put("success", false)
                        put("error", e.message ?: "Failed to open document scanner")
                    }.toString()
                    callJavaScriptCallback(callbackName, errorResult)
                    documentScanCallbackName = null
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening document scanner", e)
            val errorResult = JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
            callJavaScriptCallback(callbackName, errorResult)
            documentScanCallbackName = null
        }
    }
    
    /**
     * Extract text from image (OCR) - ใช้กับรูปที่มีอยู่แล้ว
     * @param base64Image Base64 encoded image
     * @param callbackName JavaScript callback function name
     */
    @JavascriptInterface
    fun extractTextFromImage(base64Image: String, callbackName: String) {
        try {
            // Decode base64 to bitmap
            val base64Data = if (base64Image.contains(",")) {
                base64Image.substringAfter(",")
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
            
            // Process with OCR
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            OcrService.extractText(
                image = inputImage,
                onSuccess = { ocrResult ->
                    val result = JSONObject().apply {
                        put("success", true)
                        put("data", JSONObject(ocrResult.toJson()))
                    }.toString()
                    callJavaScriptCallback(callbackName, result)
                },
                onError = { e ->
                    Log.e(TAG, "OCR error", e)
                    val errorResult = JSONObject().apply {
                        put("success", false)
                        put("error", e.message ?: "OCR processing failed")
                    }.toString()
                    callJavaScriptCallback(callbackName, errorResult)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from image", e)
            val errorResult = JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
            callJavaScriptCallback(callbackName, errorResult)
        }
    }
    
    /**
     * Handle document scanner result
     */
    fun handleDocumentScanResult(resultCode: Int, data: android.content.Intent?) {
        val callbackName = documentScanCallbackName ?: return
        documentScanCallbackName = null
        
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            val ocrResultJson = data.getStringExtra(DocumentScannerActivity.EXTRA_OCR_RESULT)
            val imageBase64 = data.getStringExtra(DocumentScannerActivity.EXTRA_IMAGE_BASE64)
            
            if (ocrResultJson != null) {
                val result = JSONObject().apply {
                    put("success", true)
                    put("ocrResult", JSONObject(ocrResultJson))
                    if (imageBase64 != null) {
                        put("imageBase64", imageBase64)
                    }
                }.toString()
                callJavaScriptCallback(callbackName, result)
            } else {
                val errorResult = JSONObject().apply {
                    put("success", false)
                    put("error", "No OCR result")
                }.toString()
                callJavaScriptCallback(callbackName, errorResult)
            }
        } else {
            val errorResult = JSONObject().apply {
                put("success", false)
                put("error", "Scan cancelled")
            }.toString()
            callJavaScriptCallback(callbackName, errorResult)
        }
    }
    
    /**
     * Save data for offline sync
     * @param tableName Table name in Supabase
     * @param operation "INSERT", "UPDATE", "DELETE"
     * @param dataJson JSON string of data to sync
     * @return JSON string with result
     */
    @JavascriptInterface
    fun saveOfflineData(tableName: String, operation: String, dataJson: String): String {
        return try {
            val data = JSONObject(dataJson)
            val manager = OfflineDataManager(context)
            
            // Use coroutine scope to run suspend function
            kotlinx.coroutines.runBlocking {
                val id = manager.saveOfflineData(tableName, operation, data)
                JSONObject().apply {
                    put("success", true)
                    put("id", id)
                    put("message", "Data saved for offline sync")
                }.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving offline data", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }
    
    /**
     * Get count of pending sync items
     */
    @JavascriptInterface
    fun getPendingSyncCount(): String {
        return try {
            val manager = OfflineDataManager(context)
            val count = kotlinx.coroutines.runBlocking {
                manager.countPendingItems()
            }
            JSONObject().apply {
                put("success", true)
                put("count", count)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending sync count", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
                put("count", 0)
            }.toString()
        }
    }
    
    /**
     * Trigger immediate sync
     */
    @JavascriptInterface
    fun triggerSync(): String {
        return try {
            val syncManager = SyncManager(context)
            syncManager.triggerSyncNow()
            JSONObject().apply {
                put("success", true)
                put("message", "Sync triggered")
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering sync", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }
    
    /**
     * Start periodic sync
     */
    @JavascriptInterface
    fun startPeriodicSync(): String {
        return try {
            val syncManager = SyncManager(context)
            syncManager.startPeriodicSync()
            JSONObject().apply {
                put("success", true)
                put("message", "Periodic sync started")
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting periodic sync", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
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

