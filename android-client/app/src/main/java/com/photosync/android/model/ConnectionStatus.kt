package com.photosync.android.model

sealed class ConnectionStatus {
    data object Connected : ConnectionStatus()
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}
