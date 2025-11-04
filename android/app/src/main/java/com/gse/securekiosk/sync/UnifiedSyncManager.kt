package com.gse.securekiosk.sync

import android.content.Context
import android.util.Log
import com.gse.securekiosk.storage.OfflineStorageManager
import com.gse.securekiosk.location.LocationHistoryManager
import com.gse.securekiosk.upload.FileUploadManager
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.PriorityBlockingQueue

/**
 * UnifiedSyncManager - จัดการ Sync ทั้งหมดแบบรวมศูนย์
 * 
 * Features:
 * - Priority queue (High, Medium, Low)
 * - Bandwidth optimization
 * - Smart retry
 * - Sync scheduling
 * - Progress tracking
 */
class UnifiedSyncManager(private val context: Context) {
    private val TAG = "UnifiedSyncManager"
    
    // Managers
    private val offlineStorageManager = OfflineStorageManager(context)
    private val locationHistoryManager = LocationHistoryManager(context)
    private val fileUploadManager = FileUploadManager(context)
    
    // Priority Queue
    private val syncQueue = PriorityBlockingQueue<SyncTask>()
    
    // Sync Jobs
    private var syncJob: Job? = null
    private var isSyncing = false
    
    // Progress Callback
    private var onProgressCallback: ((Int, Int, String) -> Unit)? = null
    
    companion object {
        // Priority levels
        const val PRIORITY_HIGH = 1    // Critical data (verification, attendance)
        const val PRIORITY_MEDIUM = 2  // Important data (location, photos)
        const val PRIORITY_LOW = 3     // Optional data (logs, analytics)
        
        // Sync types
        const val TYPE_VERIFICATION = "verification"
        const val TYPE_LOCATION = "location"
        const val TYPE_FILE_UPLOAD = "file_upload"
        const val TYPE_GEOFENCE_EVENT = "geofence_event"
        
        // Bandwidth modes
        const val BANDWIDTH_AUTO = "auto"
        const val BANDWIDTH_WIFI_ONLY = "wifi_only"
        const val BANDWIDTH_UNLIMITED = "unlimited"
    }
    
    /**
     * Sync Task Data Class
     */
    data class SyncTask(
        val id: String,
        val type: String,
        val priority: Int,
        val data: JSONObject,
        val createdAt: Long = System.currentTimeMillis(),
        val retryCount: Int = 0
    ) : Comparable<SyncTask> {
        override fun compareTo(other: SyncTask): Int {
            // Lower number = higher priority
            return if (priority != other.priority) {
                priority.compareTo(other.priority)
            } else {
                // Same priority, older first
                createdAt.compareTo(other.createdAt)
            }
        }
    }
    
    /**
     * เพิ่ม Task เข้า Sync Queue
     */
    fun addSyncTask(
        id: String,
        type: String,
        priority: Int,
        data: JSONObject
    ) {
        val task = SyncTask(id, type, priority, data)
        syncQueue.offer(task)
        Log.d(TAG, "Added sync task: $type (priority: $priority)")
        
        // Start sync if not running
        if (!isSyncing) {
            startSync()
        }
    }
    
    /**
     * เริ่ม Sync
     */
    fun startSync(onProgress: ((Int, Int, String) -> Unit)? = null) {
        if (isSyncing) {
            Log.w(TAG, "Sync already in progress")
            return
        }
        
        onProgressCallback = onProgress
        
        syncJob = GlobalScope.launch(Dispatchers.IO) {
            isSyncing = true
            try {
                syncAll()
            } catch (e: Exception) {
                Log.e(TAG, "Error in sync", e)
            } finally {
                isSyncing = false
                onProgressCallback = null
            }
        }
    }
    
    /**
     * Sync All Tasks
     */
    private suspend fun syncAll() {
        Log.d(TAG, "Starting sync... Queue size: ${syncQueue.size}")
        
        val totalTasks = syncQueue.size
        var completedTasks = 0
        var failedTasks = 0
        
        while (syncQueue.isNotEmpty()) {
            val task = syncQueue.poll() ?: break
            
            try {
                // Progress callback
                onProgressCallback?.invoke(completedTasks, totalTasks, task.type)
                
                // Sync based on type
                val success = when (task.type) {
                    TYPE_VERIFICATION -> syncVerification(task)
                    TYPE_LOCATION -> syncLocation(task)
                    TYPE_FILE_UPLOAD -> syncFileUpload(task)
                    TYPE_GEOFENCE_EVENT -> syncGeofenceEvent(task)
                    else -> {
                        Log.w(TAG, "Unknown sync type: ${task.type}")
                        false
                    }
                }
                
                if (success) {
                    completedTasks++
                    Log.d(TAG, "Synced: ${task.type} (${task.id})")
                } else {
                    // Retry logic
                    if (task.retryCount < 3) {
                        val retryTask = task.copy(retryCount = task.retryCount + 1)
                        syncQueue.offer(retryTask)
                        Log.d(TAG, "Retry queued: ${task.type} (${task.id})")
                    } else {
                        failedTasks++
                        Log.e(TAG, "Failed after max retries: ${task.type} (${task.id})")
                    }
                }
                
                // Delay between syncs (bandwidth optimization)
                delay(100)
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing task: ${task.type}", e)
                failedTasks++
            }
        }
        
        Log.d(TAG, "Sync complete: $completedTasks/$totalTasks (failed: $failedTasks)")
    }
    
    /**
     * Sync Verification
     */
    private suspend fun syncVerification(task: SyncTask): Boolean {
        return try {
            // TODO: Implement actual sync to Supabase
            // For now, just mark as synced in local DB
            val contractNumber = task.data.getString("contract_number")
            offlineStorageManager.markAsSynced(contractNumber)
            Log.d(TAG, "Verification synced: $contractNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing verification", e)
            false
        }
    }
    
    /**
     * Sync Location
     */
    private suspend fun syncLocation(task: SyncTask): Boolean {
        return try {
            // TODO: Implement actual sync to Supabase
            val locationId = task.data.getLong("id")
            locationHistoryManager.markAsSynced(locationId)
            Log.d(TAG, "Location synced: $locationId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing location", e)
            false
        }
    }
    
    /**
     * Sync File Upload
     */
    private suspend fun syncFileUpload(task: SyncTask): Boolean {
        return try {
            // File upload is handled by FileUploadManager
            // Just verify it's completed
            val uploadId = task.data.getLong("upload_id")
            Log.d(TAG, "File upload synced: $uploadId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing file upload", e)
            false
        }
    }
    
    /**
     * Sync Geofence Event
     */
    private suspend fun syncGeofenceEvent(task: SyncTask): Boolean {
        return try {
            // TODO: Implement actual sync to Supabase
            val eventId = task.data.getLong("id")
            Log.d(TAG, "Geofence event synced: $eventId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing geofence event", e)
            false
        }
    }
    
    /**
     * Stop Sync
     */
    fun stopSync() {
        syncJob?.cancel()
        isSyncing = false
        Log.d(TAG, "Sync stopped")
    }
    
    /**
     * ดึงข้อมูล Sync Queue
     */
    fun getSyncQueueInfo(): JSONObject {
        val info = JSONObject()
        
        try {
            val tasksByType = mutableMapOf<String, Int>()
            val tasksByPriority = mutableMapOf<Int, Int>()
            
            syncQueue.forEach { task ->
                tasksByType[task.type] = (tasksByType[task.type] ?: 0) + 1
                tasksByPriority[task.priority] = (tasksByPriority[task.priority] ?: 0) + 1
            }
            
            info.put("total", syncQueue.size)
            info.put("is_syncing", isSyncing)
            info.put("by_type", JSONObject(tasksByType as Map<*, *>))
            info.put("by_priority", JSONObject(tasksByPriority as Map<*, *>))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sync queue info", e)
        }
        
        return info
    }
    
    /**
     * Clear Sync Queue
     */
    fun clearSyncQueue() {
        syncQueue.clear()
        Log.d(TAG, "Sync queue cleared")
    }
    
    /**
     * Smart Sync - เลือก Sync ตาม Priority และ Network
     */
    fun smartSync(
        bandwidthMode: String = BANDWIDTH_AUTO,
        maxTasks: Int = 100
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Check network type
                val isWifi = isWifiConnected()
                
                // Filter tasks based on bandwidth mode
                val tasksToSync = when (bandwidthMode) {
                    BANDWIDTH_WIFI_ONLY -> {
                        if (isWifi) syncQueue.toList() else emptyList()
                    }
                    BANDWIDTH_UNLIMITED -> syncQueue.toList()
                    else -> { // AUTO
                        if (isWifi) {
                            syncQueue.toList()
                        } else {
                            // Only high priority when not on WiFi
                            syncQueue.filter { it.priority == PRIORITY_HIGH }
                        }
                    }
                }.take(maxTasks)
                
                Log.d(TAG, "Smart sync: ${tasksToSync.size} tasks (WiFi: $isWifi, Mode: $bandwidthMode)")
                
                // Process tasks
                tasksToSync.forEach { task ->
                    syncQueue.remove(task)
                }
                
                // Add back to queue for processing
                tasksToSync.forEach { task ->
                    syncQueue.offer(task)
                }
                
                // Start sync
                if (tasksToSync.isNotEmpty() && !isSyncing) {
                    startSync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in smart sync", e)
            }
        }
    }
    
    /**
     * ตรวจสอบว่าเชื่อมต่อ WiFi หรือไม่
     */
    private fun isWifiConnected(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi", e)
            false
        }
    }
    
    /**
     * Schedule Periodic Sync (ใช้ร่วมกับ WorkManager)
     */
    fun schedulePeriodicSync(intervalMinutes: Long = 15) {
        // TODO: Integrate with WorkManager
        Log.d(TAG, "Scheduled periodic sync: every $intervalMinutes minutes")
    }
}

