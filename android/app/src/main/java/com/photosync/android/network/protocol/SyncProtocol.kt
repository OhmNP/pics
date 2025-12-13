package com.photosync.android.network.protocol

/**
 * Sync Protocol Commands
 */
object SyncProtocol {
    const val SESSION_START = "HELLO"
    const val BATCH_START = "BEGIN_BATCH"
    const val PHOTO_METADATA = "PHOTO"
    const val DATA_TRANSFER = "DATA_TRANSFER"
    const val BATCH_CHECK = "BATCH_CHECK"
    const val BATCH_END = "BATCH_END"
    const val SESSION_END = "END_SESSION"
    
    // Responses
    const val SESSION_ACK = "SESSION_START"
    const val ACK = "ACK"
    const val SEND_REQ = "SEND"
    const val SKIP = "SKIP"
    const val BATCH_RESULT = "BATCH_RESULT"
    const val ERROR = "ERROR"
}

/**
 * Protocol message builders
 */
object ProtocolMessages {
    
    fun sessionStart(deviceId: String, token: String = ""): String {
        return if (token.isNotEmpty()) {
            "${SyncProtocol.SESSION_START} $deviceId $token\n"
        } else {
            "${SyncProtocol.SESSION_START} $deviceId\n"
        }
    }
    
    fun batchStart(size: Int): String {
        return "${SyncProtocol.BATCH_START} $size\n"
    }
    
    fun photoMetadata(filename: String, size: Long, hash: String): String {
        return "${SyncProtocol.PHOTO_METADATA} $filename $size $hash\n"
    }
    
    fun dataTransfer(size: Long): String {
        return "${SyncProtocol.DATA_TRANSFER} $size\n"
    }
    
    fun batchCheck(count: Int): String {
        return "${SyncProtocol.BATCH_CHECK} $count\n"
    }
    
    fun batchEnd(): String {
        return "${SyncProtocol.BATCH_END}\n"
    }
    
    fun sessionEnd(): String {
        return "${SyncProtocol.SESSION_END}\n"
    }
}

/**
 * Protocol response parser
 */
object ProtocolParser {
    
    fun parseResponse(response: String): ProtocolResponse {
        val parts = response.trim().split(" ")
        val command = parts.getOrNull(0) ?: return ProtocolResponse.Unknown(response)
        
        return when (command) {
            SyncProtocol.SESSION_ACK -> {
                val sessionId = parts.getOrNull(1)?.toIntOrNull() ?: -1
                ProtocolResponse.SessionAck(sessionId)
            }
            SyncProtocol.ACK -> ProtocolResponse.Ack
            SyncProtocol.SEND_REQ -> ProtocolResponse.SendRequest
            SyncProtocol.SKIP -> ProtocolResponse.Skip
            SyncProtocol.BATCH_RESULT -> {
                val count = parts.getOrNull(1)?.toIntOrNull() ?: 0
                ProtocolResponse.BatchResult(count)
            }
            SyncProtocol.ERROR -> {
                val message = parts.drop(1).joinToString(" ")
                ProtocolResponse.Error(message)
            }
            else -> ProtocolResponse.Unknown(response)
        }
    }
}

/**
 * Protocol responses
 */
sealed class ProtocolResponse {
    data class SessionAck(val sessionId: Int) : ProtocolResponse()
    object Ack : ProtocolResponse()
    object SendRequest : ProtocolResponse()
    object Skip : ProtocolResponse()
    data class BatchResult(val count: Int) : ProtocolResponse()
    data class Error(val message: String) : ProtocolResponse()
    data class Unknown(val raw: String) : ProtocolResponse()
}
