package com.gse.securekiosk.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

/**
 * OfflineStorageManager - จัดการข้อมูล Offline Verification
 * 
 * Features:
 * - เก็บข้อมูล verification แบบ offline
 * - Sync queue management
 * - Auto-retry mechanism
 */
class OfflineStorageManager(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val TAG = "OfflineStorage"
        private const val DATABASE_NAME = "gse_offline.db"
        private const val DATABASE_VERSION = 1
        
        // Verification Checks Table
        private const val TABLE_VERIFICATION = "verification_checks"
        private const val COL_ID = "id"
        private const val COL_CONTRACT_NUMBER = "contract_number"
        private const val COL_CONTRACT_TYPE = "contract_type"
        private const val COL_BRANCH_CODE = "branch_code"
        private const val COL_SERVICE_LINE = "service_line_no"
        private const val COL_CHECKS_DATA = "checks_data"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_UPDATED_AT = "updated_at"
        private const val COL_NEEDS_SYNC = "needs_sync"
        private const val COL_SYNC_ATTEMPTS = "sync_attempts"
        private const val COL_SYNCED_AT = "synced_at"
        private const val COL_ERROR_MESSAGE = "error_message"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_VERIFICATION (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CONTRACT_NUMBER TEXT NOT NULL UNIQUE,
                $COL_CONTRACT_TYPE TEXT,
                $COL_BRANCH_CODE TEXT,
                $COL_SERVICE_LINE TEXT,
                $COL_CHECKS_DATA TEXT NOT NULL,
                $COL_CREATED_AT TEXT NOT NULL,
                $COL_UPDATED_AT TEXT NOT NULL,
                $COL_NEEDS_SYNC INTEGER DEFAULT 1,
                $COL_SYNC_ATTEMPTS INTEGER DEFAULT 0,
                $COL_SYNCED_AT TEXT,
                $COL_ERROR_MESSAGE TEXT
            )
        """.trimIndent()
        
        db.execSQL(createTable)
        
        // Create indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_contract_number ON $TABLE_VERIFICATION($COL_CONTRACT_NUMBER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_needs_sync ON $TABLE_VERIFICATION($COL_NEEDS_SYNC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_branch_line ON $TABLE_VERIFICATION($COL_BRANCH_CODE, $COL_SERVICE_LINE)")
        
        Log.d(TAG, "Database created successfully")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_VERIFICATION")
        onCreate(db)
    }

    /**
     * บันทึกข้อมูล Verification แบบ Offline
     */
    fun saveVerificationData(
        contractNumber: String,
        contractType: String,
        branchCode: String,
        serviceLineNo: String,
        checksData: JSONObject
    ): Boolean {
        return try {
            val db = writableDatabase
            val now = getCurrentTimestamp()
            
            val values = ContentValues().apply {
                put(COL_CONTRACT_NUMBER, contractNumber)
                put(COL_CONTRACT_TYPE, contractType)
                put(COL_BRANCH_CODE, branchCode)
                put(COL_SERVICE_LINE, serviceLineNo)
                put(COL_CHECKS_DATA, checksData.toString())
                put(COL_CREATED_AT, now)
                put(COL_UPDATED_AT, now)
                put(COL_NEEDS_SYNC, 1)
                put(COL_SYNC_ATTEMPTS, 0)
            }
            
            val result = db.insertWithOnConflict(
                TABLE_VERIFICATION,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            
            Log.d(TAG, "Saved verification for $contractNumber: ${result > 0}")
            result > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error saving verification", e)
            false
        }
    }

    /**
     * ดึงรายการที่ต้อง Sync
     */
    fun getSyncQueue(): JSONArray {
        val result = JSONArray()
        val db = readableDatabase
        
        try {
            val cursor = db.query(
                TABLE_VERIFICATION,
                null,
                "$COL_NEEDS_SYNC = ?",
                arrayOf("1"),
                null,
                null,
                "$COL_UPDATED_AT ASC",
                "100" // Limit 100 records per sync
            )
            
            while (cursor.moveToNext()) {
                val json = JSONObject().apply {
                    put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)))
                    put("contract_number", cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTRACT_NUMBER)))
                    put("contract_type", cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTRACT_TYPE)))
                    put("branch_code", cursor.getString(cursor.getColumnIndexOrThrow(COL_BRANCH_CODE)))
                    put("service_line_no", cursor.getString(cursor.getColumnIndexOrThrow(COL_SERVICE_LINE)))
                    put("checks_data", JSONObject(cursor.getString(cursor.getColumnIndexOrThrow(COL_CHECKS_DATA))))
                    put("created_at", cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATED_AT)))
                    put("updated_at", cursor.getString(cursor.getColumnIndexOrThrow(COL_UPDATED_AT)))
                    put("sync_attempts", cursor.getInt(cursor.getColumnIndexOrThrow(COL_SYNC_ATTEMPTS)))
                }
                result.put(json)
            }
            cursor.close()
            
            Log.d(TAG, "Sync queue size: ${result.length()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sync queue", e)
        }
        
        return result
    }

    /**
     * ทำเครื่องหมายว่า Sync สำเร็จแล้ว
     */
    fun markAsSynced(contractNumber: String): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_NEEDS_SYNC, 0)
                put(COL_SYNCED_AT, getCurrentTimestamp())
                putNull(COL_ERROR_MESSAGE)
            }
            
            val rows = db.update(
                TABLE_VERIFICATION,
                values,
                "$COL_CONTRACT_NUMBER = ?",
                arrayOf(contractNumber)
            )
            
            Log.d(TAG, "Marked as synced: $contractNumber (${rows > 0})")
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error marking as synced", e)
            false
        }
    }

    /**
     * บันทึก Sync Error
     */
    fun recordSyncError(contractNumber: String, errorMessage: String): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_SYNC_ATTEMPTS, "$COL_SYNC_ATTEMPTS + 1")
                put(COL_ERROR_MESSAGE, errorMessage)
                put(COL_UPDATED_AT, getCurrentTimestamp())
            }
            
            val rows = db.update(
                TABLE_VERIFICATION,
                values,
                "$COL_CONTRACT_NUMBER = ?",
                arrayOf(contractNumber)
            )
            
            Log.d(TAG, "Recorded error for $contractNumber: $errorMessage")
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error recording sync error", e)
            false
        }
    }

    /**
     * ดึงข้อมูล Verification ของ Contract Number
     */
    fun getVerificationData(contractNumber: String): JSONObject? {
        val db = readableDatabase
        
        try {
            val cursor = db.query(
                TABLE_VERIFICATION,
                null,
                "$COL_CONTRACT_NUMBER = ?",
                arrayOf(contractNumber),
                null,
                null,
                null
            )
            
            if (cursor.moveToFirst()) {
                val json = JSONObject().apply {
                    put("contract_number", cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTRACT_NUMBER)))
                    put("contract_type", cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTRACT_TYPE)))
                    put("checks_data", JSONObject(cursor.getString(cursor.getColumnIndexOrThrow(COL_CHECKS_DATA))))
                    put("needs_sync", cursor.getInt(cursor.getColumnIndexOrThrow(COL_NEEDS_SYNC)) == 1)
                    put("updated_at", cursor.getString(cursor.getColumnIndexOrThrow(COL_UPDATED_AT)))
                }
                cursor.close()
                return json
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting verification data", e)
        }
        
        return null
    }

    /**
     * นับจำนวนที่รอ Sync
     */
    fun getPendingSyncCount(): Int {
        val db = readableDatabase
        
        try {
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_VERIFICATION WHERE $COL_NEEDS_SYNC = 1",
                null
            )
            
            if (cursor.moveToFirst()) {
                val count = cursor.getInt(0)
                cursor.close()
                return count
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error counting pending sync", e)
        }
        
        return 0
    }

    /**
     * ลบข้อมูลเก่า (เก็บแค่ 30 วัน)
     */
    fun cleanupOldData(daysToKeep: Int = 30): Int {
        val db = writableDatabase
        val cutoffDate = getCutoffTimestamp(daysToKeep)
        
        try {
            val rows = db.delete(
                TABLE_VERIFICATION,
                "$COL_SYNCED_AT IS NOT NULL AND $COL_SYNCED_AT < ? AND $COL_NEEDS_SYNC = 0",
                arrayOf(cutoffDate)
            )
            
            Log.d(TAG, "Cleaned up $rows old records")
            return rows
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old data", e)
            return 0
        }
    }

    /**
     * Reset Sync Attempts (สำหรับ retry)
     */
    fun resetFailedSyncs(): Int {
        val db = writableDatabase
        
        try {
            val values = ContentValues().apply {
                put(COL_SYNC_ATTEMPTS, 0)
                putNull(COL_ERROR_MESSAGE)
            }
            
            val rows = db.update(
                TABLE_VERIFICATION,
                values,
                "$COL_NEEDS_SYNC = 1 AND $COL_SYNC_ATTEMPTS >= 3",
                null
            )
            
            Log.d(TAG, "Reset $rows failed syncs")
            return rows
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting failed syncs", e)
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
}

