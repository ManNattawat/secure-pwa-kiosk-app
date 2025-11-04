package com.gse.securekiosk.upload

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * FileDownloadManager - จัดการ File Download แบบ Background
 * 
 * Features:
 * - Background download
 * - Resume support
 * - Progress tracking
 * - Retry mechanism
 * - Bandwidth optimization
 */
class FileDownloadManager(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    private val context = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private const val TAG = "FileDownloadManager"
        private const val DATABASE_NAME = "file_download.db"
        private const val DATABASE_VERSION = 1
        
        // Download Queue Table
        private const val TABLE_DOWNLOAD = "download_queue"
        private const val COL_ID = "id"
        private const val COL_DOWNLOAD_URL = "download_url"
        private const val COL_FILE_PATH = "file_path"
        private const val COL_FILE_NAME = "file_name"
        private const val COL_FILE_SIZE = "file_size"
        private const val COL_STATUS = "status"
        private const val COL_PROGRESS = "progress"
        private const val COL_DOWNLOADED_BYTES = "downloaded_bytes"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_STARTED_AT = "started_at"
        private const val COL_COMPLETED_AT = "completed_at"
        private const val COL_RETRY_COUNT = "retry_count"
        private const val COL_ERROR_MESSAGE = "error_message"
        
        // Status constants
        const val STATUS_PENDING = "pending"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
        
        const val MAX_RETRY_COUNT = 3
    }

    private val downloadJobs = mutableMapOf<Long, Job>()
    private val progressCallbacks = mutableMapOf<Long, (Long, Long, Int) -> Unit>()

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_DOWNLOAD (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DOWNLOAD_URL TEXT NOT NULL,
                $COL_FILE_PATH TEXT NOT NULL,
                $COL_FILE_NAME TEXT NOT NULL,
                $COL_FILE_SIZE INTEGER DEFAULT 0,
                $COL_STATUS TEXT NOT NULL DEFAULT '$STATUS_PENDING',
                $COL_PROGRESS INTEGER DEFAULT 0,
                $COL_DOWNLOADED_BYTES INTEGER DEFAULT 0,
                $COL_CREATED_AT TEXT NOT NULL,
                $COL_STARTED_AT TEXT,
                $COL_COMPLETED_AT TEXT,
                $COL_RETRY_COUNT INTEGER DEFAULT 0,
                $COL_ERROR_MESSAGE TEXT
            )
        """.trimIndent()
        
        db.execSQL(createTable)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_status ON $TABLE_DOWNLOAD($COL_STATUS)")
        
        Log.d(TAG, "Database created successfully")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DOWNLOAD")
        onCreate(db)
    }

    /**
     * เพิ่มไฟล์เข้า Download Queue
     */
    fun addToQueue(
        downloadUrl: String,
        destinationPath: String,
        fileName: String
    ): Long {
        return try {
            val db = writableDatabase
            val now = getCurrentTimestamp()
            
            val values = ContentValues().apply {
                put(COL_DOWNLOAD_URL, downloadUrl)
                put(COL_FILE_PATH, destinationPath)
                put(COL_FILE_NAME, fileName)
                put(COL_STATUS, STATUS_PENDING)
                put(COL_PROGRESS, 0)
                put(COL_DOWNLOADED_BYTES, 0)
                put(COL_CREATED_AT, now)
                put(COL_RETRY_COUNT, 0)
            }
            
            val id = db.insert(TABLE_DOWNLOAD, null, values)
            Log.d(TAG, "Added to queue: $id - $fileName")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to queue", e)
            -1L
        }
    }

    /**
     * เริ่ม Download
     */
    fun startDownload(downloadId: Long, onProgress: ((Long, Long, Int) -> Unit)? = null) {
        if (downloadJobs.containsKey(downloadId)) {
            Log.w(TAG, "Download already in progress: $downloadId")
            return
        }
        
        if (onProgress != null) {
            progressCallbacks[downloadId] = onProgress
        }
        
        val job = GlobalScope.launch(Dispatchers.IO) {
            try {
                downloadFile(downloadId)
            } catch (e: Exception) {
                Log.e(TAG, "Error in download job", e)
                updateStatus(downloadId, STATUS_FAILED, errorMessage = e.message)
            } finally {
                downloadJobs.remove(downloadId)
                progressCallbacks.remove(downloadId)
            }
        }
        
        downloadJobs[downloadId] = job
    }

    /**
     * Download File
     */
    private suspend fun downloadFile(downloadId: Long) {
        val downloadInfo = getDownloadInfo(downloadId) ?: return
        
        val downloadUrl = downloadInfo.getString("download_url")
        val filePath = downloadInfo.getString("file_path")
        val downloadedBytes = downloadInfo.optLong("downloaded_bytes", 0)
        
        updateStatus(downloadId, STATUS_DOWNLOADING, startedAt = getCurrentTimestamp())
        
        try {
            val requestBuilder = Request.Builder().url(downloadUrl)
            
            // Resume support (if partially downloaded)
            if (downloadedBytes > 0) {
                requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
                Log.d(TAG, "Resuming download from byte $downloadedBytes")
            }
            
            val request = requestBuilder.build()
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            
            if (!response.isSuccessful && response.code != 206) {
                throw IOException("Download failed: ${response.code} ${response.message}")
            }
            
            val body = response.body ?: throw IOException("Response body is null")
            val contentLength = body.contentLength()
            val totalBytes = if (downloadedBytes > 0) downloadedBytes + contentLength else contentLength
            
            // Update file size
            updateFileSize(downloadId, totalBytes)
            
            val file = File(filePath)
            file.parentFile?.mkdirs()
            
            val outputStream = FileOutputStream(file, downloadedBytes > 0) // Append if resuming
            val inputStream = body.byteStream()
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = downloadedBytes
            
            while (withContext(Dispatchers.IO) { inputStream.read(buffer).also { bytesRead = it } } != -1) {
                withContext(Dispatchers.IO) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                totalRead += bytesRead
                
                val progress = ((totalRead.toDouble() / totalBytes) * 100).toInt()
                updateProgress(downloadId, totalRead, progress)
                
                // Callback
                progressCallbacks[downloadId]?.invoke(totalRead, totalBytes, progress)
            }
            
            withContext(Dispatchers.IO) {
                outputStream.close()
                inputStream.close()
            }
            response.close()
            
            updateStatus(downloadId, STATUS_COMPLETED, completedAt = getCurrentTimestamp())
            Log.d(TAG, "Download completed: $downloadId")
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file", e)
            handleDownloadError(downloadId, e.message ?: "Unknown error")
        }
    }

    /**
     * จัดการ Download Error
     */
    private fun handleDownloadError(downloadId: Long, errorMessage: String) {
        val downloadInfo = getDownloadInfo(downloadId) ?: return
        val retryCount = downloadInfo.getInt("retry_count")
        
        if (retryCount < MAX_RETRY_COUNT) {
            val newRetryCount = retryCount + 1
            updateRetryCount(downloadId, newRetryCount, errorMessage)
            Log.d(TAG, "Retry download: $downloadId (attempt $newRetryCount)")
            
            GlobalScope.launch {
                delay(5000L * newRetryCount)
                startDownload(downloadId)
            }
        } else {
            updateStatus(downloadId, STATUS_FAILED, errorMessage = errorMessage)
            Log.e(TAG, "Download failed after $MAX_RETRY_COUNT attempts: $downloadId")
        }
    }

    /**
     * Cancel Download
     */
    fun cancelDownload(downloadId: Long): Boolean {
        return try {
            downloadJobs[downloadId]?.cancel()
            downloadJobs.remove(downloadId)
            progressCallbacks.remove(downloadId)
            updateStatus(downloadId, STATUS_CANCELLED)
            Log.d(TAG, "Cancelled download: $downloadId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling download", e)
            false
        }
    }

    /**
     * Resume Download
     */
    fun resumeDownload(downloadId: Long, onProgress: ((Long, Long, Int) -> Unit)? = null) {
        updateStatus(downloadId, STATUS_PENDING)
        startDownload(downloadId, onProgress)
    }

    /**
     * Download All Pending
     */
    fun downloadAllPending() {
        val pending = getPendingDownloads()
        for (i in 0 until pending.length()) {
            val download = pending.getJSONObject(i)
            val id = download.getLong("id")
            startDownload(id)
        }
    }

    /**
     * ดึงรายการ Pending
     */
    fun getPendingDownloads(): JSONArray {
        return getDownloadsByStatus(STATUS_PENDING)
    }

    /**
     * ดึงรายการตาม Status
     */
    private fun getDownloadsByStatus(status: String): JSONArray {
        val result = JSONArray()
        val db = readableDatabase
        
        try {
            val cursor = db.query(
                TABLE_DOWNLOAD,
                null,
                "$COL_STATUS = ?",
                arrayOf(status),
                null,
                null,
                "$COL_CREATED_AT ASC"
            )
            
            while (cursor.moveToNext()) {
                val json = JSONObject().apply {
                    put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)))
                    put("file_name", cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_NAME)))
                    put("file_size", cursor.getLong(cursor.getColumnIndexOrThrow(COL_FILE_SIZE)))
                    put("status", cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)))
                    put("progress", cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROGRESS)))
                    put("downloaded_bytes", cursor.getLong(cursor.getColumnIndexOrThrow(COL_DOWNLOADED_BYTES)))
                }
                result.put(json)
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting downloads by status", e)
        }
        
        return result
    }

    /**
     * ดึงข้อมูล Download
     */
    private fun getDownloadInfo(downloadId: Long): JSONObject? {
        val db = readableDatabase
        
        try {
            val cursor = db.query(
                TABLE_DOWNLOAD,
                null,
                "$COL_ID = ?",
                arrayOf(downloadId.toString()),
                null,
                null,
                null
            )
            
            if (cursor.moveToFirst()) {
                val json = JSONObject().apply {
                    put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)))
                    put("download_url", cursor.getString(cursor.getColumnIndexOrThrow(COL_DOWNLOAD_URL)))
                    put("file_path", cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_PATH)))
                    put("file_name", cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_NAME)))
                    put("file_size", cursor.getLong(cursor.getColumnIndexOrThrow(COL_FILE_SIZE)))
                    put("status", cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)))
                    put("progress", cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROGRESS)))
                    put("downloaded_bytes", cursor.getLong(cursor.getColumnIndexOrThrow(COL_DOWNLOADED_BYTES)))
                    put("retry_count", cursor.getInt(cursor.getColumnIndexOrThrow(COL_RETRY_COUNT)))
                }
                cursor.close()
                return json
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download info", e)
        }
        
        return null
    }

    // Update methods (similar to FileUploadManager)
    private fun updateStatus(
        downloadId: Long,
        status: String,
        startedAt: String? = null,
        completedAt: String? = null,
        errorMessage: String? = null
    ) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_STATUS, status)
                if (startedAt != null) put(COL_STARTED_AT, startedAt)
                if (completedAt != null) put(COL_COMPLETED_AT, completedAt)
                if (errorMessage != null) put(COL_ERROR_MESSAGE, errorMessage)
            }
            
            db.update(TABLE_DOWNLOAD, values, "$COL_ID = ?", arrayOf(downloadId.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status", e)
        }
    }

    private fun updateProgress(downloadId: Long, downloadedBytes: Long, progress: Int) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_DOWNLOADED_BYTES, downloadedBytes)
                put(COL_PROGRESS, progress)
            }
            
            db.update(TABLE_DOWNLOAD, values, "$COL_ID = ?", arrayOf(downloadId.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating progress", e)
        }
    }

    private fun updateFileSize(downloadId: Long, fileSize: Long) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_FILE_SIZE, fileSize)
            }
            
            db.update(TABLE_DOWNLOAD, values, "$COL_ID = ?", arrayOf(downloadId.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating file size", e)
        }
    }

    private fun updateRetryCount(downloadId: Long, retryCount: Int, errorMessage: String) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_RETRY_COUNT, retryCount)
                put(COL_ERROR_MESSAGE, errorMessage)
                put(COL_STATUS, STATUS_PENDING)
            }
            
            db.update(TABLE_DOWNLOAD, values, "$COL_ID = ?", arrayOf(downloadId.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating retry count", e)
        }
    }

    fun deleteDownload(downloadId: Long): Boolean {
        return try {
            val db = writableDatabase
            val rows = db.delete(TABLE_DOWNLOAD, "$COL_ID = ?", arrayOf(downloadId.toString()))
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting download", e)
            false
        }
    }

    fun cleanupCompletedDownloads(daysToKeep: Int = 7): Int {
        val db = writableDatabase
        val cutoffDate = getCutoffTimestamp(daysToKeep)
        
        try {
            val rows = db.delete(
                TABLE_DOWNLOAD,
                "$COL_STATUS = ? AND $COL_COMPLETED_AT < ?",
                arrayOf(STATUS_COMPLETED, cutoffDate)
            )
            
            Log.d(TAG, "Cleaned up $rows completed downloads")
            return rows
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up", e)
            return 0
        }
    }

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

