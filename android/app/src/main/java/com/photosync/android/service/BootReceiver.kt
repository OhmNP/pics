package com.photosync.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.photosync.android.data.SettingsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = SettingsManager(context)
            if (settings.autoSyncEnabled) {
                Log.i("BootReceiver", "Boot completed, starting EnhancedSyncService")
                val serviceIntent = Intent(context, EnhancedSyncService::class.java).apply {
                    action = EnhancedSyncService.ACTION_START_MONITORING
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                 Log.i("BootReceiver", "Boot completed, but Auto-Sync disabled")
            }
        }
    }
}
