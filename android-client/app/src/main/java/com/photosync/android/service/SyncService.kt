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
        val serverIp = intent?.getStringExtra("server_ip") ?: "192.168.0.10"
        val serverPort = intent?.getIntExtra("server_port", 50505) ?: 50505
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                syncPhotos(serverIp, serverPort)
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
        
        Log.i(TAG, "Starting sync for device: $deviceId")
        
        // Get existing connection from ConnectionManager
        val connMgr = ConnectionManager.getInstance()
        val conn = connMgr.getConnection()
        
        if (conn == null || !connMgr.isConnected()) {
            Log.e(TAG, "No active connection to server. Please ensure ConnectionService is running.")
            return
        }

        try {
            val reader = conn.reader
            val writer = conn.writer
            val outputStream = conn.socket.getOutputStream() // For binary data

            Log.i(TAG, "Using existing connection for sync")

            // Collect photos for batch
            var batch = mutableListOf<PhotoMeta>()
            for (photo in scanner.scanIncremental()) {
                batch.add(photo)
                if (batch.size >= batchSize) {
                    processBatch(batch, reader, writer, outputStream)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                processBatch(batch, reader, writer, outputStream)
            }
            
            db.setLastSync(System.currentTimeMillis())
            Log.i(TAG, "Sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            throw e
        }
    }

    private fun processBatch(
        batch: List<PhotoMeta>,
        reader: BufferedReader,
        writer: BufferedWriter,
        outputStream: OutputStream
    ) {
        // Start Batch
        writeCommand(writer, "BEGIN_BATCH ${batch.size}")
        val batchAck = readResponse(reader)
        if (batchAck != "ACK READY") {
            Log.e(TAG, "Batch start failed: $batchAck")
            return
        }

        for (photo in batch) {
            // Send Metadata
            writeCommand(writer, "PHOTO ${photo.name} ${photo.size} ${photo.hash}")
            val response = readResponse(reader)

            if (response.startsWith("SEND")) {
                var offset: Long = 0
                if (response.length > 5) {
                    offset = response.substring(5).toLongOrNull() ?: 0
                }
                
                Log.d(TAG, "Uploading ${photo.name} from offset $offset")
                uploadFile(photo, offset, writer, outputStream, reader)
            } else if (response == "SKIP") {
                Log.d(TAG, "Skipping ${photo.name} (already exists)")
            } else {
                Log.w(TAG, "Unexpected response for photo: $response")
            }
        }

        // End Batch
        writeCommand(writer, "BATCH_END")
        readResponse(reader) // Expect ACK
    }

    private fun uploadFile(
        photo: PhotoMeta,
        offset: Long,
        writer: BufferedWriter,
        outputStream: OutputStream,
        reader: BufferedReader
    ) {
        val fileStream = contentResolver.openInputStream(photo.uri) ?: return
        val fileSize = photo.size
        val uploadSize = fileSize - offset

        if (uploadSize <= 0) {
            Log.w(TAG, "Invalid upload size: $uploadSize")
            return
        }

        // Send DATA command
        writeCommand(writer, "DATA_TRANSFER $uploadSize")

        // Send binary data
        fileStream.use { input ->
            if (offset > 0) {
                input.skip(offset)
            }
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalSent: Long = 0
            
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
