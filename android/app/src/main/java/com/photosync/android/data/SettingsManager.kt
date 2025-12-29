package com.photosync.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsManager(context: Context) {
    // secure_prefs for sensitive data, photosync_prefs for validation/legacy check?
    // User requested "Move to: Android Keystore + encrypted storage".
    // We will migrate existing keys if found in plain prefs, then use EncryptedSharedPreferences.
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    // Legacy prefs for migration
    private val legacyPrefs = context.getSharedPreferences("photosync_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_AUTH_TOKEN = "auth_token"
        
        // Reliability
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_CHARGING_ONLY = "charging_only"
        private const val KEY_BATTERY_THRESHOLD = "battery_threshold"
        private const val KEY_EXCLUDED_FOLDERS = "excluded_folders"
        private const val KEY_LAST_BACKUP = "last_successful_backup"
        
        private const val DEFAULT_IP = ""
        private const val DEFAULT_PORT = 50505
    }
    
    init {
        migrateLegacyPrefs()
    }
    
    private fun migrateLegacyPrefs() {
        if (legacyPrefs.contains(KEY_SERVER_IP) || legacyPrefs.contains(KEY_USER_NAME)) {
            // Migrate
            if (legacyPrefs.contains(KEY_SERVER_IP)) {
                serverIp = legacyPrefs.getString(KEY_SERVER_IP, DEFAULT_IP) ?: DEFAULT_IP
            }
            if (legacyPrefs.contains(KEY_SERVER_PORT)) {
                serverPort = legacyPrefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
            }
            if (legacyPrefs.contains(KEY_USER_NAME)) {
                userName = legacyPrefs.getString(KEY_USER_NAME, "") ?: ""
            }
             if (legacyPrefs.contains("auto_sync_enabled")) {
                autoSyncEnabled = legacyPrefs.getBoolean("auto_sync_enabled", true)
            }
             if (legacyPrefs.contains("onboarding_completed")) {
                isOnboardingCompleted = legacyPrefs.getBoolean("onboarding_completed", false)
            }
            
            // Clear legacy (Dangerous to keep plaintext)
            legacyPrefs.edit().clear().apply()
        }
    }

    var autoSyncEnabled: Boolean
        get() = prefs.getBoolean("auto_sync_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_sync_enabled", value).apply()

    var serverIp: String
        get() = prefs.getString(KEY_SERVER_IP, DEFAULT_IP) ?: DEFAULT_IP
        set(value) = prefs.edit().putString(KEY_SERVER_IP, value).apply()

    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_SERVER_PORT, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var authToken: String
        get() = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false) || serverIp != DEFAULT_IP
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()
        
    // Reliability Settings
    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        
    var chargingOnly: Boolean
        get() = prefs.getBoolean(KEY_CHARGING_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_CHARGING_ONLY, value).apply()
        
    var batteryThreshold: Int
        get() = prefs.getInt(KEY_BATTERY_THRESHOLD, 15)
        set(value) = prefs.edit().putInt(KEY_BATTERY_THRESHOLD, value).apply()
        
    var excludedFolders: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDED_FOLDERS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_EXCLUDED_FOLDERS, value).apply()
        
    var lastSuccessfulBackupTimestamp: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP, value).apply()
}
