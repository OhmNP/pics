package com.photosync.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_config")
data class ServerConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val serverIp: String,
    val serverPort: Int = 50505,
    val deviceId: String,
    val isPaired: Boolean = false,
    val lastConnected: Long = 0
)
