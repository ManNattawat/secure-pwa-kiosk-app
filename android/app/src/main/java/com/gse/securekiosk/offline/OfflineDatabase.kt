package com.gse.securekiosk.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Offline Database
 * SQLite database สำหรับเก็บข้อมูลแบบ offline
 */
@Database(
    entities = [OfflineDataEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OfflineDatabase : RoomDatabase() {
    
    abstract fun offlineDataDao(): OfflineDataDao
    
    companion object {
        @Volatile
        private var INSTANCE: OfflineDatabase? = null
        
        fun getDatabase(context: Context): OfflineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "offline_database"
                )
                    .fallbackToDestructiveMigration() // สำหรับ development - จะลบข้อมูลเมื่อ upgrade version
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

