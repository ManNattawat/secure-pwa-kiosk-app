package com.gse.securekiosk.offline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Offline Data Manager
 * จัดการข้อมูล offline และ sync กับ server
 */
class OfflineDataManager(private val context: Context) {
    private val TAG = "OfflineDataManager"
    private val database = OfflineDatabase.getDatabase(context)
    private val dao = database.offlineDataDao()
    
    /**
     * Save data for offline sync
     * @param tableName Table name in Supabase
     * @param operation "INSERT", "UPDATE", "DELETE"
     * @param data JSON object to sync
     */
    suspend fun saveOfflineData(
        tableName: String,
        operation: String,
        data: JSONObject
    ): Long {
        return withContext(Dispatchers.IO) {
            try {
                val entity = OfflineDataEntity(
                    tableName = tableName,
                    operation = operation,
                    data = data.toString()
                )
                dao.insert(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving offline data", e)
                throw e
            }
        }
    }
    
    /**
     * Get all pending sync items
     */
    suspend fun getPendingSyncItems(): List<OfflineDataEntity> {
        return withContext(Dispatchers.IO) {
            try {
                dao.getPendingSyncItems()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting pending sync items", e)
                emptyList()
            }
        }
    }
    
    /**
     * Mark item as synced
     */
    suspend fun markAsSynced(id: Long) {
        withContext(Dispatchers.IO) {
            try {
                dao.markAsSynced(id)
            } catch (e: Exception) {
                Log.e(TAG, "Error marking as synced", e)
            }
        }
    }
    
    /**
     * Mark item as syncing
     */
    suspend fun markAsSyncing(id: Long) {
        withContext(Dispatchers.IO) {
            try {
                dao.markAsSyncing(id)
            } catch (e: Exception) {
                Log.e(TAG, "Error marking as syncing", e)
            }
        }
    }
    
    /**
     * Mark item as failed
     */
    suspend fun markAsFailed(id: Long, errorMessage: String) {
        withContext(Dispatchers.IO) {
            try {
                dao.markAsFailed(id, errorMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Error marking as failed", e)
            }
        }
    }
    
    /**
     * Get count of pending items
     */
    suspend fun countPendingItems(): Int {
        return withContext(Dispatchers.IO) {
            try {
                dao.countPendingItems()
            } catch (e: Exception) {
                Log.e(TAG, "Error counting pending items", e)
                0
            }
        }
    }
    
    /**
     * Delete old synced items (cleanup)
     */
    suspend fun cleanupOldSyncedItems(daysOld: Int = 7) {
        withContext(Dispatchers.IO) {
            try {
                val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
                dao.deleteOldSyncedItems(cutoffTime)
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up old items", e)
            }
        }
    }
    
    /**
     * Get items by table name
     */
    suspend fun getItemsByTable(tableName: String): List<OfflineDataEntity> {
        return withContext(Dispatchers.IO) {
            try {
                dao.getItemsByTable(tableName)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting items by table", e)
                emptyList()
            }
        }
    }
}

