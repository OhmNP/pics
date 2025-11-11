package com.photosync.android.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import java.net.Socket

class SyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val socket = Socket("192.168.0.10", 50505)
                socket.getOutputStream().write("HELLO".toByteArray())
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return START_STICKY
    }
}
