package com.gse.securekiosk.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import android.util.Log
import com.gse.securekiosk.storage.OfflineStorageManager
import com.gse.securekiosk.sync.UnifiedSyncManager
import java.util.concurrent.TimeUnit

/**
 * AutoSyncWorker - Background Worker สำหรับ Auto-Sync
 * 
 * Features:
 * - Sync ทุก 15 นาที เมื่อมี Internet
 * - Retry mechanism
 * - Battery optimization
 */
class AutoSyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    private val storageManager = OfflineStorageManager(context)
    private val TAG = "AutoSyncWorker"

    companion object {
        const val WORK_NAME = "auto_sync_verification"
        const val REPEAT_INTERVAL_MINUTES = 15L
        const val MAX_RETRY_ATTEMPTS = 3

        /**
         * Schedule Auto-Sync Worker
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // ต้องมี Internet
                .setRequiresBatteryNotLow(true) // แบตไม่ต่ำ
                .build()

            val syncWorkRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(
                REPEAT_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueue(syncWorkRequest)
            Log.d("AutoSyncWorker", "Scheduled auto-sync every $REPEAT_INTERVAL_MINUTES minutes")
        }

        /**
         * Cancel Auto-Sync Worker
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_NAME)
            Log.d("AutoSyncWorker", "Cancelled auto-sync")
        }
    }

    override fun doWork(): Result {
        Log.d(TAG, "Starting auto-sync...")

        try {
            val pendingCount = storageManager.getPendingSyncCount()
            
            if (pendingCount == 0) {
                Log.d(TAG, "No pending items to sync")
                return Result.success()
            }

            Log.d(TAG, "Found $pendingCount pending items to sync")

            // ✅ ใช้ UnifiedSyncManager สำหรับ Smart Sync
            val syncManager = UnifiedSyncManager(applicationContext)
            
            // Smart Sync - เลือก sync ตาม network
            syncManager.smartSync(
                bandwidthMode = UnifiedSyncManager.BANDWIDTH_AUTO,
                maxTasks = 100
            )
            
            // Trigger PWA sync event (if PWA is loaded)
            triggerPwaSync()

            // Cleanup old data (ทุกครั้งที่ sync)
            val deletedRows = storageManager.cleanupOldData(30)
            if (deletedRows > 0) {
                Log.d(TAG, "Cleaned up $deletedRows old records")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in auto-sync", e)
            
            // Retry if run attempt is less than max
            return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "Retrying... (attempt ${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS)")
                Result.retry()
            } else {
                Log.e(TAG, "Max retry attempts reached")
                Result.failure()
            }
        }
    }

    /**
     * Trigger PWA Sync Event
     * (PWA ต้องมี listener สำหรับรับ event นี้)
     */
    private fun triggerPwaSync() {
        try {
            // ส่ง Local Broadcast หรือ Notification
            // PWA จะต้อง poll getSyncQueue() เอง
            Log.d(TAG, "Triggered PWA sync event")
            
            // TODO: Implement actual PWA notification mechanism
            // Options:
            // 1. LocalBroadcastManager
            // 2. Push Notification
            // 3. WebSocket message
            // 4. Service Worker message
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering PWA sync", e)
        }
    }
}

/**
 * SyncStatus - สถานะการ Sync
 */
data class SyncStatus(
    val totalItems: Int,
    val syncedItems: Int,
    val failedItems: Int,
    val pendingItems: Int
) {
    val progress: Float
        get() = if (totalItems > 0) syncedItems.toFloat() / totalItems else 0f
    
    val isComplete: Boolean
        get() = pendingItems == 0
}

