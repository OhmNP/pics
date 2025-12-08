package com.photosync.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PhotoSyncApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Create notification channels for services
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Sync service channel
            val syncChannel = NotificationChannel(
                "sync_channel",
                "Photo Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "PhotoSync background synchronization"
            }
            notificationManager.createNotificationChannel(syncChannel)
            
            // Connection service channel
            val connectionChannel = NotificationChannel(
                "connection_channel",
                "Server Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "PhotoSync server connection status"
            }
            notificationManager.createNotificationChannel(connectionChannel)
        }
    }
}
