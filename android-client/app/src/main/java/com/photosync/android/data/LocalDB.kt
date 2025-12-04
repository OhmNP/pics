package com.photosync.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.photosync.android.model.SyncHistory

class LocalDB(ctx: Context) : SQLiteOpenHelper(ctx, "photosync.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS photos(" +
                    "path TEXT PRIMARY KEY," +
                    "lastModified INTEGER," +
                    "hash TEXT)"
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT)")
        db.execSQL("INSERT OR IGNORE INTO meta VALUES('lastSync','0')")
        
        // Sync history table
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sync_history(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "timestamp INTEGER," +
                    "photos_synced INTEGER," +
                    "bytes_transferred INTEGER," +
                    "success INTEGER," +
                    "error_message TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        if (oldV < 2) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_history(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "timestamp INTEGER," +
                        "photos_synced INTEGER," +
                        "bytes_transferred INTEGER," +
                        "success INTEGER," +
                        "error_message TEXT)"
            )
        }
    }

    fun getLastSync(): Long {
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key='lastSync'", null).use {
            return if (it.moveToFirst()) it.getString(0).toLong() else 0
        }
    }
    
    fun setLastSync(time: Long) {
        writableDatabase.execSQL("UPDATE meta SET value=? WHERE key='lastSync'", arrayOf(time.toString()))
    }
    
    fun addSyncHistory(photosSynced: Int, bytesTransferred: Long, success: Boolean, errorMessage: String? = null) {
        val values = ContentValues().apply {
            put("timestamp", System.currentTimeMillis())
            put("photos_synced", photosSynced)
            put("bytes_transferred", bytesTransferred)
            put("success", if (success) 1 else 0)
            put("error_message", errorMessage)
        }
        writableDatabase.insert("sync_history", null, values)
    }
    
    fun getSyncHistory(limit: Int = 20): List<SyncHistory> {
        val history = mutableListOf<SyncHistory>()
        readableDatabase.rawQuery(
            "SELECT id, timestamp, photos_synced, bytes_transferred, success, error_message " +
                    "FROM sync_history ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                history.add(
                    SyncHistory(
                        id = cursor.getLong(0),
                        timestamp = cursor.getLong(1),
                        photosSynced = cursor.getInt(2),
                        bytesTransferred = cursor.getLong(3),
                        success = cursor.getInt(4) == 1,
                        errorMessage = cursor.getString(5)
                    )
                )
            }
        }
        return history
    }
    
    fun getTotalPhotosSynced(): Int {
        readableDatabase.rawQuery(
            "SELECT SUM(photos_synced) FROM sync_history WHERE success = 1",
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }
}
