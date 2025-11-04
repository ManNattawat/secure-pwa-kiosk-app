package com.gse.securekiosk.upload

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okio.buffer
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * FileUploadManager - จัดการ File Upload แบบ Background
 * 
 * Features:
 * - Background upload
 * - Resume support
 * - Multi-file upload
 * - Progress tracking
 * - Retry mechanism
 * - Bandwidth optimization
 */
class FileUploadManager(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    private val context = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private const val TAG = "FileUploadManager"
        private const val DATABASE_NAME = "file_upload.db"
        private const val DATABASE_VERSION = 1
        
        // Upload Queue Table
        private const val TABLE_UPLOAD = "upload_queue"
        private const val COL_ID = "id"
        private const val COL_FILE_PATH = "file_path"
        private const val COL_FILE_NAME = "file_name"
        private const val COL_FILE_SIZE = "file_size"
        private const val COL_MIME_TYPE = "mime_type"
        private const val COL_UPLOAD_URL = "upload_url"
        private const val COL_STATUS = "status" // pending, uploading, completed, failed
        private const val COL_PROGRESS = "progress" // 0-100
        private const val COL_UPLOADED_BYTES = "uploaded_bytes"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_STARTED_AT = "started_at"
        private const val COL_COMPLETED_AT = "completed_at"
        private const val COL_RETRY_COUNT = "retry_count"
        private const val COL_ERROR_MESSAGE = "error_message"
        private const val COL_METADATA = "metadata" // JSON
        
        // Status constants
        const val STATUS_PENDING = "pending"
        const val STATUS_UPLOADING = "uploading"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
        
        // Max retry
        const val MAX_RETRY_COUNT = 3
    }

    private val uploadJobs = mutableMapOf<Long, Job>()
    private val progressCallbacks = mutableMapOf<Long, (Long, Long, Int) -> Unit>()

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_UPLOAD (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FILE_PATH TEXT NOT NULL,
                $COL_FILE_NAME TEXT NOT NULL,
                $COL_FILE_SIZE INTEGER NOT NULL,
                $COL_MIME_TYPE TEXT,
                $COL_UPLOAD_URL TEXT NOT NULL,
                $COL_STATUS TEXT NOT NULL DEFAULT '$STATUS_PENDING',
                $COL_PROGRESS INTEGER DEFAULT 0,
                $COL_UPLOADED_BYTES INTEGER DEFAULT 0,
                $COL_CREATED_AT TEXT NOT NULL,
                $COL_STARTED_AT TEXT,
                $COL_COMPLETED_AT TEXT,
                $COL_RETRY_COUNT INTEGER DEFAULT 0,
                $COL_ERROR_MESSAGE TEXT,
                $COL_METADATA TEXT
            )
        """.trimIndent()
        
        db.execSQL(createTable)
        
        // Create indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_status ON $TABLE_UPLOAD($COL_STATUS)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_created_at ON $TABLE_UPLOAD($COL_CREATED_AT)")
        
        Log.d(TAG, "Database created successfully")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_UPLOAD")
        onCreate(db)
    }

    /**
     * เพิ่มไฟล์เข้า Upload Queue
     */
    fun addToQueue(
        filePath: String,
        uploadUrl: String,
        metadata: JSONObject? = null
    ): Long {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found: $filePath")
                return -1L
            }
            
            val db = writableDatabase
            val now = getCurrentTimestamp()
            
            val values = ContentValues().apply {
                put(COL_FILE_PATH, filePath)
                put(COL_FILE_NAME, file.name)
                put(COL_FILE_SIZE, file.length())
                put(COL_MIME_TYPE, getMimeType(file.name))
                put(COL_UPLOAD_URL, uploadUrl)
                put(COL_STATUS, STATUS_PENDING)
                put(COL_PROGRESS, 0)
                put(COL_UPLOADED_BYTES, 0)
                put(COL_CREATED_AT, now)
                put(COL_RETRY_COUNT, 0)
                put(COL_METADATA, metadata?.toString())
            }
            
            val id = db.insert(TABLE_UPLOAD, null, values)
            Log.d(TAG, "Added to queue: $id - ${file.name}")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to queue", e)
            -1L
        }
    }

    /**
     * เริ่ม Upload
     */
    fun startUpload(uploadId: Long, onProgress: ((Long, Long, Int) -> Unit)? = null) {
        if (uploadJobs.containsKey(uploadId)) {
            Log.w(TAG, "Upload already in progress: $uploadId")
            return
        }
        
        if (onProgress != null) {
            progressCallbacks[uploadId] = onProgress
        }
        
        val job = GlobalScope.launch(Dispatchers.IO) {
            try {
                uploadFile(uploadId)
            } catch (e: Exception) {
                Log.e(TAG, "Error in upload job", e)
                updateStatus(uploadId, STATUS_FAILED, errorMessage = e.message)
            } finally {
                uploadJobs.remove(uploadId)
                progressCallbacks.remove(uploadId)
            }
        }
        
        uploadJobs[uploadId] = job
    }

    /**
     * Upload File
     */
    private suspend fun uploadFile(uploadId: Long) {
        val uploadInfo = getUploadInfo(uploadId) ?: return
        
        val file = File(uploadInfo.getString("file_path"))
        if (!file.exists()) {
            updateStatus(uploadId, STATUS_FAILED, errorMessage = "File not found")
            return
        }
        
        // Update status to uploading
        updateStatus(uploadId, STATUS_UPLOADING, startedAt = getCurrentTimestamp())
        
        try {
            val uploadUrl = uploadInfo.getString("upload_url")
            val mimeType = uploadInfo.optString("mime_type", "application/octet-stream")
            
            val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val progressRequestBody = ProgressRequestBody(requestBody, file.length()) { bytesWritten, contentLength ->
                val progress = ((bytesWritten.toDouble() / contentLength) * 100).toInt()
                updateProgress(uploadId, bytesWritten, progress)
                
                // Callback
                progressCallbacks[uploadId]?.invoke(bytesWritten, contentLength, progress)
            }
            
            val request = Request.Builder()
                .url(uploadUrl)
                .post(progressRequestBody)
                .addHeader("Content-Type", mimeType)
                .build()
            
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            
            if (response.isSuccessful) {
                updateStatus(uploadId, STATUS_COMPLETED, completedAt = getCurrentTimestamp())
                Log.d(TAG, "Upload completed: $uploadId")
            } else {
                throw IOException("Upload failed: ${response.code} ${response.message}")
            }
            
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file", e)
            handleUploadError(uploadId, e.message ?: "Unknown error")
        }
    }

    /**
     * จัดการ Upload Error
     */
    private fun handleUploadError(uploadId: Long, errorMessage: String) {
        val uploadInfo = getUploadInfo(uploadId) ?: return
        val retryCount = uploadInfo.getInt("retry_count")
        
        if (retryCount < MAX_RETRY_COUNT) {
            // Retry
            val newRetryCount = retryCount + 1
            updateRetryCount(uploadId, newRetryCount, errorMessage)
            Log.d(TAG, "Retry upload: $uploadId (attempt $newRetryCount)")
            
            // Retry after delay
            GlobalScope.launch {
                delay(5000L * newRetryCount) // Exponential backoff
                startUpload(uploadId)
            }
        } else {
            // Failed
            updateStatus(uploadId, STATUS_FAILED, errorMessage = errorMessage)
            Log.e(TAG, "Upload failed after $MAX_RETRY_COUNT attempts: $uploadId")
        }
    }

    /**
     * Cancel Upload
     */
    fun cancelUpload(uploadId: Long): Boolean {
        return try {
            uploadJobs[uploadId]?.cancel()
            uploadJobs.remove(uploadId)
            progressCallbacks.remove(uploadId)
            updateStatus(uploadId, STATUS_CANCELLED)
            Log.d(TAG, "Cancelled upload: $uploadId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling upload", e)
            false
        }
    }

    /**
     * Pause Upload (จริงๆ คือ cancel แล้ว resume ได้)
     */
    fun pauseUpload(uploadId: Long): Boolean {
        return cancelUpload(uploadId)
    }

    /**
     * Resume Upload
     */
    fun resumeUpload(uploadId: Long, onProgress: ((Long, Long, Int) -> Unit)? = null) {
        updateStatus(uploadId, STATUS_PENDING)
        startUpload(uploadId, onProgress)
    }

    /**
     * Upload All Pending
     */
    fun uploadAllPending() {
        val pending = getPendingUploads()
        for (i in 0 until pending.length()) {
            val upload = pending.getJSONObject(i)
            val id = upload.getLong("id")
            startUpload(id)
        }
    }

    /**
     * ดึงรายการ Pending
     */
    fun getPendingUploads(): JSONArray {
        return getUploadsByStatus(STATUS_PENDING)
    }

    /**
     * ดึงรายการตาม Status
     */
    private fun getUploadsByStatus(status: String): JSONArray {
        val result = JSONArray()
        val db = readableDatabase
        
        try {
            val cursor = db.query(
                TABLE_UPLOAD,
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
                    put("uploaded_bytes", cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPLOADED_BYTES)))
                }
                result.put(json)
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting uploads by status", e)
        }
        
        return result
    }

    /**
     * ดึงข้อมูล Upload
     */
    private fun getUploadInfo(uploadId: Long): JSONObject? {
        val db = readableDatabase
        
        try {
            val cursor = db.query(
                TABLE_UPLOAD,
                null,
                "$COL_ID = ?",
                arrayOf(uploadId.toString()),
                null,
                null,
                null
            )
            
            if (cursor.moveToFirst()) {
                val json = JSONObject().apply {
                    put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)))
                    put("file_path", cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_PATH)))
                    put("file_name", cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_NAME)))
                    put("file_size", cursor.getLong(cursor.getColumnIndexOrThrow(COL_FILE_SIZE)))
                    put("mime_type", cursor.getString(cursor.getColumnIndexOrThrow(COL_MIME_TYPE)))
                    put("upload_url", cursor.getString(cursor.getColumnIndexOrThrow(COL_UPLOAD_URL)))
                    put("status", cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)))
                    put("progress", cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROGRESS)))
                    put("retry_count", cursor.getInt(cursor.getColumnIndexOrThrow(COL_RETRY_COUNT)))
                }
                cursor.close()
                return json
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting upload info", e)
        }
        
        return null
    }

    /**
     * Update Status
     */
    private fun updateStatus(
        uploadId: Long,
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
            
            db.update(TABLE_UPLOAD, values, "$COL_ID = ?", arrayOf(uploadId.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status", e)
        }
    }

    /**
     * Update Progress
     */
    private fun updateProgress(uploadId: Long, uploadedBytes: Long, progress: Int) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_UPLOADED_BYTES, uploadedBytes)
                put(COL_PROGRESS, progress)
            }
            
            db.update(TABLE_UPLOAD, values, "$COL_ID = ?", arrayOf(uploadId.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating progress", e)
        }
    }

    /**
     * Update Retry Count
     */
    private fun updateRetryCount(uploadId: Long, retryCount: Int, errorMessage: String) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_RETRY_COUNT, retryCount)
                put(COL_ERROR_MESSAGE, errorMessage)
                put(COL_STATUS, STATUS_PENDING) // Reset to pending for retry
            }
            
            db.update(TABLE_UPLOAD, values, "$COL_ID = ?", arrayOf(uploadId.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating retry count", e)
        }
    }

    /**
     * ลบ Upload Record
     */
    fun deleteUpload(uploadId: Long): Boolean {
        return try {
            val db = writableDatabase
            val rows = db.delete(TABLE_UPLOAD, "$COL_ID = ?", arrayOf(uploadId.toString()))
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting upload", e)
            false
        }
    }

    /**
     * ลบ Completed Uploads
     */
    fun cleanupCompletedUploads(daysToKeep: Int = 7): Int {
        val db = writableDatabase
        val cutoffDate = getCutoffTimestamp(daysToKeep)
        
        try {
            val rows = db.delete(
                TABLE_UPLOAD,
                "$COL_STATUS = ? AND $COL_COMPLETED_AT < ?",
                arrayOf(STATUS_COMPLETED, cutoffDate)
            )
            
            Log.d(TAG, "Cleaned up $rows completed uploads")
            return rows
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up", e)
            return 0
        }
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

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}

/**
 * ProgressRequestBody - Track upload progress
 */
class ProgressRequestBody(
    private val requestBody: RequestBody,
    private val contentLength: Long,
    private val onProgress: (Long, Long) -> Unit
) : RequestBody() {

    override fun contentType() = requestBody.contentType()

    override fun contentLength() = contentLength

    override fun writeTo(sink: okio.BufferedSink) {
        val countingSink = CountingSink(sink, contentLength, onProgress)
        val bufferedSink = countingSink.buffer()
        
        requestBody.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}

/**
 * CountingSink - Count bytes written
 */
class CountingSink(
    delegate: okio.Sink,
    private val contentLength: Long,
    private val onProgress: (Long, Long) -> Unit
) : okio.ForwardingSink(delegate) {
    private var bytesWritten = 0L

    override fun write(source: okio.Buffer, byteCount: Long) {
        super.write(source, byteCount)
        bytesWritten += byteCount
        onProgress(bytesWritten, contentLength)
    }
}

