package com.gse.securekiosk.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gse.securekiosk.offline.OfflineDataEntity
import com.gse.securekiosk.offline.OfflineDataManager
import com.gse.securekiosk.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Background Sync Worker
 * ใช้ WorkManager สำหรับ sync ข้อมูล offline กับ Supabase
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val TAG = "SyncWorker"
    private val offlineDataManager = OfflineDataManager(context)
    
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting sync...")
                
                // Get pending sync items
                val pendingItems = offlineDataManager.getPendingSyncItems()
                
                if (pendingItems.isEmpty()) {
                    Log.d(TAG, "No pending items to sync")
                    return@withContext Result.success()
                }
                
                Log.d(TAG, "Found ${pendingItems.size} pending items")
                
                var successCount = 0
                var failureCount = 0
                
                // Process each item
                for (item in pendingItems) {
                    if (isStopped) {
                        Log.w(TAG, "Worker stopped, aborting sync")
                        return@withContext Result.retry()
                    }
                    
                    try {
                        // Mark as syncing
                        offlineDataManager.markAsSyncing(item.id)
                        
                        // Sync based on operation
                        when (item.operation) {
                            "INSERT" -> {
                                syncInsert(item)
                            }
                            "UPDATE" -> {
                                syncUpdate(item)
                            }
                            "DELETE" -> {
                                syncDelete(item)
                            }
                            else -> {
                                Log.w(TAG, "Unknown operation: ${item.operation}")
                                offlineDataManager.markAsFailed(item.id, "Unknown operation")
                                failureCount++
                                continue
                            }
                        }
                        
                        // Mark as synced
                        offlineDataManager.markAsSynced(item.id)
                        successCount++
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing item ${item.id}", e)
                        
                        // Mark as failed if retry limit reached
                        val maxRetries = 3
                        if (item.syncAttempts >= maxRetries) {
                            offlineDataManager.markAsFailed(
                                item.id,
                                "Max retries reached: ${e.message}"
                            )
                            failureCount++
                        } else {
                            // Will retry later
                            offlineDataManager.markAsFailed(item.id, e.message ?: "Unknown error")
                        }
                    }
                }
                
                Log.d(TAG, "Sync completed: $successCount succeeded, $failureCount failed")
                
                // Cleanup old synced items
                offlineDataManager.cleanupOldSyncedItems(7)
                
                // Return success if all items synced, or retry if some failed
                if (failureCount > 0 && successCount == 0) {
                    Result.retry() // Retry if all failed
                } else {
                    Result.success()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in sync worker", e)
                Result.retry()
            }
        }
    }
    
    /**
     * Sync INSERT operation
     */
    private suspend fun syncInsert(item: OfflineDataEntity) {
        val data = JSONObject(item.data)
        val tableName = item.tableName
        
        val client = SupabaseClient(applicationContext)
        val success = client.insertData(tableName, data)
        
        if (!success) {
            throw Exception("Insert failed")
        }
    }
    
    /**
     * Sync UPDATE operation
     */
    private suspend fun syncUpdate(item: OfflineDataEntity) {
        val data = JSONObject(item.data)
        val tableName = item.tableName
        
        val client = SupabaseClient(applicationContext)
        val success = client.updateData(tableName, data)
        
        if (!success) {
            throw Exception("Update failed")
        }
    }
    
    /**
     * Sync DELETE operation
     */
    private suspend fun syncDelete(item: OfflineDataEntity) {
        val data = JSONObject(item.data)
        val tableName = item.tableName
        
        val client = SupabaseClient(applicationContext)
        val success = client.deleteData(tableName, data)
        
        if (!success) {
            throw Exception("Delete failed")
        }
    }
}

