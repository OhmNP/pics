package com.photosync.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.*
import com.photosync.android.data.SettingsManager
import com.photosync.android.worker.SyncWorker
import java.util.concurrent.TimeUnit

class PhotoSyncApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Create notification channels for services
        createNotificationChannels()
        
        // Schedule background sync
        scheduleSyncWorker()
    }
    
    fun scheduleSyncWorker() {
        val settings = SettingsManager(this)
        
        if (!settings.autoSyncEnabled) {
            WorkManager.getInstance(this).cancelUniqueWork("PhotoSyncWorker")
            return
        }
        
        val constraintsBuilder = Constraints.Builder()
            
        if (settings.wifiOnly) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
        } else {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }
        
        if (settings.chargingOnly) {
            constraintsBuilder.setRequiresCharging(true)
        }
        
        val constraints = constraintsBuilder.build()
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PhotoSyncWorker",
            ExistingPeriodicWorkPolicy.UPDATE, // Update ensures constraints are refreshed
            syncRequest
        )
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
