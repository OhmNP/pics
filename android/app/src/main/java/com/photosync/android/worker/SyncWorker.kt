package com.photosync.android.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import android.content.Intent
import android.os.BatteryManager
import com.photosync.android.data.SettingsManager
import com.photosync.android.service.EnhancedSyncService

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "SyncWorker started")
        
        val settings = SettingsManager(applicationContext)
        
        if (!settings.autoSyncEnabled) {
            Log.i(TAG, "Auto-sync disabled, worker exiting")
            return Result.success()
        }
        
        // 1. Manual Battery Threshold Check
        val batteryThreshold = settings.batteryThreshold
        if (!isCharging(applicationContext)) {
             val batteryPct = getBatteryLevel(applicationContext)
             if (batteryPct < batteryThreshold) {
                 Log.w(TAG, "Battery too low ($batteryPct% < $batteryThreshold%), postponing sync")
                 return Result.retry() 
             }
        }
        
        // 2. Delegate to Service Orchestration
        try {
            val service = EnhancedSyncService.getInstance()
            if (service != null) {
                Log.i(TAG, "Service instance found, requesting sync via orchestrator")
                service.requestSync("BACKGROUND_WORKER")
                return Result.success()
            }
            
            Log.i(TAG, "Service not running, starting via Intent")
            val intent = Intent(applicationContext, EnhancedSyncService::class.java).apply {
                action = EnhancedSyncService.ACTION_START_SYNC
                putExtra("started_from_worker", true)
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sync via service", e)
            return Result.failure()
        }
    }
    
    private fun getBatteryLevel(context: Context): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    
    private fun isCharging(context: Context): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.isCharging
    }

    companion object {
        private const val TAG = "SyncWorker"
    }
}
