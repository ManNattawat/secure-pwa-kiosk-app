package com.gse.securekiosk.offline

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Offline Data Entity
 * เก็บข้อมูลที่ต้อง sync กับ server แบบ offline
 */
@Entity(tableName = "offline_data")
data class OfflineDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Table name in Supabase (e.g., "customers", "inventory", "scans")
     */
    val tableName: String,
    
    /**
     * Operation type: "INSERT", "UPDATE", "DELETE"
     */
    val operation: String,
    
    /**
     * JSON data to sync
     */
    val data: String,
    
    /**
     * Timestamp when this record was created
     */
    val createdAt: Long = System.currentTimeMillis(),
    
    /**
     * Timestamp when this record was synced (null if not synced yet)
     */
    val syncedAt: Long? = null,
    
    /**
     * Number of sync attempts
     */
    val syncAttempts: Int = 0,
    
    /**
     * Sync status: "PENDING", "SYNCING", "SUCCESS", "FAILED"
     */
    val syncStatus: String = "PENDING",
    
    /**
     * Error message if sync failed
     */
    val errorMessage: String? = null
)

