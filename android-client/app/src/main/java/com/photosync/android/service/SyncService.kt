package com.photosync.android.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.photosync.android.data.*
import kotlinx.coroutines.*
import java.io.*
import java.nio.charset.StandardCharsets

class SyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    private val batchSize = 10
    private val TAG = "SyncService"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Default to intent extra or fallback
        val defaultIp = intent?.getStringExtra("server_ip") ?: "192.168.0.10"
        val serverPort = intent?.getIntExtra("server_port", 50505) ?: 50505
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Try to discover server first
                val discoveryManager = com.photosync.android.network.DiscoveryManager()
                val discoveredIp = discoveryManager.discoverServer()
                
                val targetIp = discoveredIp ?: defaultIp
                if (discoveredIp != null) {
                    Log.i(TAG, "Using discovered server IP: $targetIp")
                } else {
                    Log.w(TAG, "Discovery failed, using default IP: $targetIp")
                }

                syncPhotos(targetIp, serverPort)
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun syncPhotos(serverIp: String, serverPort: Int) {
        val db = LocalDB(applicationContext)
        val scanner = MediaScanner(applicationContext, db)
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "android_device"
        val syncRepo = com.photosync.android.repository.SyncRepository.getInstance(applicationContext)
        
        Log.i(TAG, "Starting sync for device: $deviceId")
        syncRepo.startSync()
        
        val connMgr = ConnectionManager.getInstance()
        
        while (true) {
            // 1. Wait for connection
            if (!connMgr.isConnected()) {
                Log.i(TAG, "Waiting for connection to server...")
                try {
                    connMgr.connectionStatus.collect { status ->
                        if (status is com.photosync.android.model.ConnectionStatus.Connected) {
                            throw CancellationException("Connected") // Break out of collect
                        }
                    }
                } catch (e: CancellationException) {
                    // Connected
                }
            }
            
            val conn = connMgr.getConnection()
            if (conn == null) {
                delay(1000)
                continue
            }

            // 2. Try to sync
            connMgr.setSyncing(true)
            
            try {
                val reader = conn.reader
                val writer = conn.writer
                val outputStream = conn.socket.getOutputStream() // For binary data

                Log.i(TAG, "Using existing connection for sync")

                // First, collect all photos to get total count
                val allPhotos = scanner.scanIncremental().toList()
                val totalPhotos = allPhotos.size
                var photoIndex = 0
                var totalBytesSynced = 0L
                var maxModifiedTime = 0L

                // Collect photos for batch
                var batch = mutableListOf<PhotoMeta>()
                for (photo in allPhotos) {
                    batch.add(photo)
                    // Track the latest modification time (convert seconds to millis)
                    val photoTime = photo.modified * 1000
                    if (photoTime > maxModifiedTime) {
                        maxModifiedTime = photoTime
                    }
                    
                    if (batch.size >= batchSize) {
                        val bytesSynced = processBatch(batch, reader, writer, outputStream, photoIndex, totalPhotos, syncRepo)
                        totalBytesSynced += bytesSynced
                        photoIndex += batch.size
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) {
                    val bytesSynced = processBatch(batch, reader, writer, outputStream, photoIndex, totalPhotos, syncRepo)
                    totalBytesSynced += bytesSynced
                }
                
                syncRepo.completeSyncSuccess(totalPhotos, totalBytesSynced, if (totalPhotos > 0) maxModifiedTime else null)
                Log.i(TAG, "Sync completed successfully")
                return // Exit loop and function on success
                
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed, retrying in 5s...", e)
                // Do NOT completeSyncError, just retry
                
                // Close connection to force reconnect if it was a socket error
                try {
                    conn.socket.close()
                } catch (ignore: Exception) { }
                
                delay(5000)
            } finally {
                // Reset syncing flag
                connMgr.setSyncing(false)
            }
        }
    }

    private fun processBatch(
        batch: List<PhotoMeta>,
        reader: BufferedReader,
        writer: BufferedWriter,
        outputStream: OutputStream,
        startIndex: Int,
        totalPhotos: Int,
        syncRepo: com.photosync.android.repository.SyncRepository
    ): Long {
        var batchBytesSynced = 0L
        
        // Start Batch
        writeCommand(writer, "BEGIN_BATCH ${batch.size}")
        val batchAck = readResponse(reader)
        if (batchAck != "ACK READY") {
            Log.e(TAG, "Batch start failed: $batchAck")
            return 0L
        }

        batch.forEachIndexed { index, photo ->
            val currentIndex = startIndex + index + 1
            syncRepo.updateCurrentFile(photo.name, currentIndex, totalPhotos)
            
            // Calculate hash on demand before sending
            Log.d(TAG, "Calculating hash for ${photo.name}")
            val scanner = MediaScanner(applicationContext, LocalDB(applicationContext))
            try {
                val hash = scanner.calculateSha256(photo.uri)
                photo.hash = hash
                Log.d(TAG, "Hash calculated: $hash")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to calculate hash for ${photo.name}", e)
                // Skip this photo or fail batch?
                // For now, let's skip sending this photo but continue batch if possible
                // But protocol expects batch size...
                // We must send something or fail.
                // Let's send a dummy hash to fail on server side or handle gracefully?
                // Better to throw and fail batch.
                throw e
            }
            
            // Send Metadata
            writeCommand(writer, "PHOTO ${photo.name} ${photo.size} ${photo.hash}")
            val response = readResponse(reader)

            if (response.startsWith("SEND")) {
                var offset: Long = 0
                if (response.length > 5) {
                    offset = response.substring(5).toLongOrNull() ?: 0
                }
                
                Log.d(TAG, "Uploading ${photo.name} from offset $offset")
                val bytesUploaded = uploadFile(photo, offset, writer, outputStream, reader)
                batchBytesSynced += bytesUploaded
            } else if (response == "SKIP") {
                Log.d(TAG, "Skipping ${photo.name} (already exists)")
            } else {
                Log.w(TAG, "Unexpected response for photo: $response")
            }
        }

        // End Batch
        writeCommand(writer, "BATCH_END")
        val response = readResponse(reader) // Expect ACK
        Log.e(TAG, "batch completed:  $response")

        return batchBytesSynced
    }

    private fun uploadFile(
        photo: PhotoMeta,
        offset: Long,
        writer: BufferedWriter,
        outputStream: OutputStream,
        reader: BufferedReader
    ): Long {
        val fileStream = contentResolver.openInputStream(photo.uri) ?: return 0L
        val fileSize = photo.size
        val uploadSize = fileSize - offset

        if (uploadSize <= 0) {
            Log.w(TAG, "Invalid upload size: $uploadSize")
            return 0L
        }

        // Send DATA command
        writeCommand(writer, "DATA_TRANSFER $uploadSize")

        // Send binary data
        var totalSent = 0L
        fileStream.use { input ->
            if (offset > 0) {
                input.skip(offset)
            }
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            while (input.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalSent += bytesRead
            }
            outputStream.flush()
        }

        // Wait for ACK
        val ack = readResponse(reader)
        if (ack != "ACK") {
            Log.e(TAG, "Upload failed for ${photo.name}: $ack")
        }
        
        return totalSent
    }

    private fun writeCommand(writer: BufferedWriter, command: String) {
        writer.write(command)
        writer.write("\n")
        writer.flush()
    }

    private fun readResponse(reader: BufferedReader): String {
        return reader.readLine() ?: ""
    }
}
