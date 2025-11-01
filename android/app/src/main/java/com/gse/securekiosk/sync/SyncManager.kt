package com.gse.securekiosk.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Sync Manager
 * จัดการ background sync ด้วย WorkManager
 */
class SyncManager(private val context: Context) {
    
    private val TAG = "SyncManager"
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * Start periodic sync (every 15 minutes when online)
     */
    fun startPeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flexible window
        )
            .setConstraints(constraints)
            .addTag("periodic_sync")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        
        Log.d(TAG, "Periodic sync started")
    }
    
    /**
     * Trigger immediate sync
     */
    fun triggerSyncNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .addTag("immediate_sync")
            .build()
        
        workManager.enqueue(syncRequest)
        
        Log.d(TAG, "Immediate sync triggered")
    }
    
    /**
     * Cancel all sync work
     */
    fun cancelAllSync() {
        workManager.cancelAllWorkByTag("periodic_sync")
        workManager.cancelAllWorkByTag("immediate_sync")
        Log.d(TAG, "All sync work cancelled")
    }
    
    /**
     * Get sync status
     */
    fun getSyncStatus(callback: (String) -> Unit) {
        workManager.getWorkInfosByTagLiveData("periodic_sync")
            .observeForever { workInfos ->
                if (workInfos.isNotEmpty()) {
                    val state = workInfos[0].state
                    callback(state.name)
                } else {
                    callback("UNKNOWN")
                }
            }
    }
}

