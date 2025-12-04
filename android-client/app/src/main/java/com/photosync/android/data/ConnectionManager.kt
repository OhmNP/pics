package com.photosync.android.data

import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket

data class ConnectionInfo(
    val socket: Socket,
    val reader: BufferedReader,
    val writer: BufferedWriter
)

class ConnectionManager private constructor() {
    private var connection: ConnectionInfo? = null
    private val lock = Any()
    
    companion object {
        @Volatile
        private var instance: ConnectionManager? = null
        
        fun getInstance(): ConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: ConnectionManager().also { instance = it }
            }
        }
    }
    
    fun setConnection(socket: Socket, reader: BufferedReader, writer: BufferedWriter) {
        synchronized(lock) {
            connection = ConnectionInfo(socket, reader, writer)
        }
    }
    
    fun getConnection(): ConnectionInfo? {
        synchronized(lock) {
            return connection
        }
    }
    
    fun clearConnection() {
        synchronized(lock) {
            connection = null
        }
    }
    
    fun isConnected(): Boolean {
        synchronized(lock) {
            return connection?.socket?.isConnected == true && 
                   connection?.socket?.isClosed == false
        }
    }
}
