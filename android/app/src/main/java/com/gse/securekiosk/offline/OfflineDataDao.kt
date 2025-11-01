package com.gse.securekiosk.offline

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Offline Data
 */
@Dao
interface OfflineDataDao {
    
    /**
     * Get all pending sync items
     */
    @Query("SELECT * FROM offline_data WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getPendingSyncItems(): List<OfflineDataEntity>
    
    /**
     * Get all pending sync items as Flow (for reactive updates)
     */
    @Query("SELECT * FROM offline_data WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED' ORDER BY createdAt ASC")
    fun getPendingSyncItemsFlow(): Flow<List<OfflineDataEntity>>
    
    /**
     * Get all items for a specific table
     */
    @Query("SELECT * FROM offline_data WHERE tableName = :tableName ORDER BY createdAt DESC")
    suspend fun getItemsByTable(tableName: String): List<OfflineDataEntity>
    
    /**
     * Insert new offline data
     */
    @Insert
    suspend fun insert(offlineData: OfflineDataEntity): Long
    
    /**
     * Update offline data
     */
    @Update
    suspend fun update(offlineData: OfflineDataEntity)
    
    /**
     * Delete offline data
     */
    @Delete
    suspend fun delete(offlineData: OfflineDataEntity)
    
    /**
     * Mark item as synced
     */
    @Query("UPDATE offline_data SET syncStatus = 'SUCCESS', syncedAt = :syncedAt WHERE id = :id")
    suspend fun markAsSynced(id: Long, syncedAt: Long = System.currentTimeMillis())
    
    /**
     * Mark item as syncing
     */
    @Query("UPDATE offline_data SET syncStatus = 'SYNCING' WHERE id = :id")
    suspend fun markAsSyncing(id: Long)
    
    /**
     * Mark item as failed
     */
    @Query("UPDATE offline_data SET syncStatus = 'FAILED', syncAttempts = syncAttempts + 1, errorMessage = :errorMessage WHERE id = :id")
    suspend fun markAsFailed(id: Long, errorMessage: String)
    
    /**
     * Delete all synced items older than specified days
     */
    @Query("DELETE FROM offline_data WHERE syncStatus = 'SUCCESS' AND syncedAt < :cutoffTime")
    suspend fun deleteOldSyncedItems(cutoffTime: Long)
    
    /**
     * Count pending items
     */
    @Query("SELECT COUNT(*) FROM offline_data WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    suspend fun countPendingItems(): Int
    
    /**
     * Get item by ID
     */
    @Query("SELECT * FROM offline_data WHERE id = :id")
    suspend fun getItemById(id: Long): OfflineDataEntity?
}

