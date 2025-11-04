package com.gse.securekiosk.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import android.util.Log
import com.gse.securekiosk.upload.FileUploadManager
import com.gse.securekiosk.upload.FileDownloadManager
import org.json.JSONObject

/**
 * FileTransferBridge - JavaScript Bridge สำหรับ File Upload/Download
 * 
 * PWA เรียกใช้ผ่าน: window.AndroidFile.*
 */
class FileTransferBridge(private val context: Context) {
    private val uploadManager = FileUploadManager(context)
    private val downloadManager = FileDownloadManager(context)
    private val TAG = "FileTransferBridge"

    // ========================================
    // UPLOAD APIS
    // ========================================

    /**
     * เพิ่มไฟล์เข้า Upload Queue
     */
    @JavascriptInterface
    fun addUpload(filePath: String, uploadUrl: String, metadataJson: String = "{}"): String {
        return try {
            val metadata = if (metadataJson.isNotEmpty()) JSONObject(metadataJson) else null
            val id = uploadManager.addToQueue(filePath, uploadUrl, metadata)
            
            val result = JSONObject().apply {
                put("success", id > 0)
                put("upload_id", id)
                put("message", if (id > 0) "Added to upload queue" else "Failed to add")
            }
            
            Log.d(TAG, "addUpload: $id")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in addUpload", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * เริ่ม Upload
     */
    @JavascriptInterface
    fun startUpload(uploadId: Long): String {
        return try {
            uploadManager.startUpload(uploadId) { uploaded, total, progress ->
                // Progress callback
                Log.d(TAG, "Upload progress: $uploadId - $progress%")
            }
            
            val result = JSONObject().apply {
                put("success", true)
                put("upload_id", uploadId)
                put("message", "Upload started")
            }
            
            Log.d(TAG, "startUpload: $uploadId")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in startUpload", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Cancel Upload
     */
    @JavascriptInterface
    fun cancelUpload(uploadId: Long): String {
        return try {
            val success = uploadManager.cancelUpload(uploadId)
            
            val result = JSONObject().apply {
                put("success", success)
                put("upload_id", uploadId)
            }
            
            Log.d(TAG, "cancelUpload: $uploadId")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in cancelUpload", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Resume Upload
     */
    @JavascriptInterface
    fun resumeUpload(uploadId: Long): String {
        return try {
            uploadManager.resumeUpload(uploadId) { uploaded, total, progress ->
                Log.d(TAG, "Upload progress: $uploadId - $progress%")
            }
            
            val result = JSONObject().apply {
                put("success", true)
                put("upload_id", uploadId)
                put("message", "Upload resumed")
            }
            
            Log.d(TAG, "resumeUpload: $uploadId")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in resumeUpload", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Upload All Pending
     */
    @JavascriptInterface
    fun uploadAllPending(): String {
        return try {
            uploadManager.uploadAllPending()
            val pending = uploadManager.getPendingUploads()
            
            val result = JSONObject().apply {
                put("success", true)
                put("count", pending.length())
                put("message", "Started ${pending.length()} uploads")
            }
            
            Log.d(TAG, "uploadAllPending: ${pending.length()}")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadAllPending", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ดึงรายการ Pending Uploads
     */
    @JavascriptInterface
    fun getPendingUploads(): String {
        return try {
            val pending = uploadManager.getPendingUploads()
            
            val result = JSONObject().apply {
                put("success", true)
                put("count", pending.length())
                put("data", pending)
            }
            
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getPendingUploads", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ลบ Upload Record
     */
    @JavascriptInterface
    fun deleteUpload(uploadId: Long): String {
        return try {
            val success = uploadManager.deleteUpload(uploadId)
            
            val result = JSONObject().apply {
                put("success", success)
                put("upload_id", uploadId)
            }
            
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in deleteUpload", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Cleanup Completed Uploads
     */
    @JavascriptInterface
    fun cleanupCompletedUploads(daysToKeep: Int = 7): String {
        return try {
            val deletedRows = uploadManager.cleanupCompletedUploads(daysToKeep)
            
            val result = JSONObject().apply {
                put("success", true)
                put("deleted_rows", deletedRows)
            }
            
            Log.d(TAG, "cleanupCompletedUploads: $deletedRows rows")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in cleanupCompletedUploads", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    // ========================================
    // DOWNLOAD APIS
    // ========================================

    /**
     * เพิ่มไฟล์เข้า Download Queue
     */
    @JavascriptInterface
    fun addDownload(downloadUrl: String, destinationPath: String, fileName: String): String {
        return try {
            val id = downloadManager.addToQueue(downloadUrl, destinationPath, fileName)
            
            val result = JSONObject().apply {
                put("success", id > 0)
                put("download_id", id)
                put("message", if (id > 0) "Added to download queue" else "Failed to add")
            }
            
            Log.d(TAG, "addDownload: $id")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in addDownload", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * เริ่ม Download
     */
    @JavascriptInterface
    fun startDownload(downloadId: Long): String {
        return try {
            downloadManager.startDownload(downloadId) { downloaded, total, progress ->
                Log.d(TAG, "Download progress: $downloadId - $progress%")
            }
            
            val result = JSONObject().apply {
                put("success", true)
                put("download_id", downloadId)
                put("message", "Download started")
            }
            
            Log.d(TAG, "startDownload: $downloadId")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in startDownload", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Cancel Download
     */
    @JavascriptInterface
    fun cancelDownload(downloadId: Long): String {
        return try {
            val success = downloadManager.cancelDownload(downloadId)
            
            val result = JSONObject().apply {
                put("success", success)
                put("download_id", downloadId)
            }
            
            Log.d(TAG, "cancelDownload: $downloadId")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in cancelDownload", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Resume Download
     */
    @JavascriptInterface
    fun resumeDownload(downloadId: Long): String {
        return try {
            downloadManager.resumeDownload(downloadId) { downloaded, total, progress ->
                Log.d(TAG, "Download progress: $downloadId - $progress%")
            }
            
            val result = JSONObject().apply {
                put("success", true)
                put("download_id", downloadId)
                put("message", "Download resumed")
            }
            
            Log.d(TAG, "resumeDownload: $downloadId")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in resumeDownload", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Download All Pending
     */
    @JavascriptInterface
    fun downloadAllPending(): String {
        return try {
            downloadManager.downloadAllPending()
            val pending = downloadManager.getPendingDownloads()
            
            val result = JSONObject().apply {
                put("success", true)
                put("count", pending.length())
                put("message", "Started ${pending.length()} downloads")
            }
            
            Log.d(TAG, "downloadAllPending: ${pending.length()}")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in downloadAllPending", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ดึงรายการ Pending Downloads
     */
    @JavascriptInterface
    fun getPendingDownloads(): String {
        return try {
            val pending = downloadManager.getPendingDownloads()
            
            val result = JSONObject().apply {
                put("success", true)
                put("count", pending.length())
                put("data", pending)
            }
            
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in getPendingDownloads", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * ลบ Download Record
     */
    @JavascriptInterface
    fun deleteDownload(downloadId: Long): String {
        return try {
            val success = downloadManager.deleteDownload(downloadId)
            
            val result = JSONObject().apply {
                put("success", success)
                put("download_id", downloadId)
            }
            
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in deleteDownload", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Cleanup Completed Downloads
     */
    @JavascriptInterface
    fun cleanupCompletedDownloads(daysToKeep: Int = 7): String {
        return try {
            val deletedRows = downloadManager.cleanupCompletedDownloads(daysToKeep)
            
            val result = JSONObject().apply {
                put("success", true)
                put("deleted_rows", deletedRows)
            }
            
            Log.d(TAG, "cleanupCompletedDownloads: $deletedRows rows")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in cleanupCompletedDownloads", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    // ========================================
    // UTILITY APIS
    // ========================================

    /**
     * Check if File Transfer Bridge is available
     */
    @JavascriptInterface
    fun isAvailable(): String {
        return JSONObject().apply {
            put("available", true)
            put("version", "1.0.0")
            put("features", org.json.JSONArray().apply {
                put("file_upload")
                put("file_download")
                put("resume_support")
                put("progress_tracking")
                put("retry_mechanism")
            })
        }.toString()
    }
}

