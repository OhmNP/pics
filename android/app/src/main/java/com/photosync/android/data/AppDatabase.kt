package com.photosync.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.photosync.android.data.dao.ServerConfigDao
import com.photosync.android.data.dao.SyncStatusDao
import com.photosync.android.data.entity.ServerConfigEntity
import com.photosync.android.data.entity.SyncStatusEntity

@Database(entities = [com.photosync.android.data.entity.SyncStatusEntity::class, com.photosync.android.data.entity.ServerConfigEntity::class], version = 5, exportSchema = false)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun syncStatusDao(): SyncStatusDao
    abstract fun serverConfigDao(): ServerConfigDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "photosync_app_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
