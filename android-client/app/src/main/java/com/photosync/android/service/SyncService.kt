package com.photosync.android.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.net.Socket

class SyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e("SyncService", "onStartCommand entry", )
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.e("SyncService", "onStartCommand: starting connection", )
                val socket = Socket("172.28.248.29", 50505)
                socket.getOutputStream().write("HELLO".toByteArray())
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return START_STICKY
    }
}
