package com.photosync.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocalDB(ctx: Context) : SQLiteOpenHelper(ctx, "photosync.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS photos(" +
                    "path TEXT PRIMARY KEY," +
                    "lastModified INTEGER," +
                    "hash TEXT)"
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT)")
        db.execSQL("INSERT OR IGNORE INTO meta VALUES('lastSync','0')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {}

    fun getLastSync(): Long {
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key='lastSync'", null).use {
            return if (it.moveToFirst()) it.getString(0).toLong() else 0
        }
    }
    fun setLastSync(time: Long) {
        writableDatabase.execSQL("UPDATE meta SET value=? WHERE key='lastSync'", arrayOf(time.toString()))
    }
}
