package com.photosync.android.network.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

enum class PacketType(val value: Int) {
    DISCOVERY(0x01),
    PAIRING_REQUEST(0x02),
    PAIRING_RESPONSE(0x03),
    HEARTBEAT(0x04),
    METADATA(0x05),
    TRANSFER_READY(0x06),
    FILE_CHUNK(0x07),
    TRANSFER_COMPLETE(0x08),
    PROTOCOL_ERROR(0x09),
    UNKNOWN(0xFF);

    companion object {
        fun fromInt(value: Int) = values().firstOrNull { it.value == value } ?: UNKNOWN
    }
}

data class PacketHeader(
    val magic: Short = 0x5048, // "PH"
    val version: Byte = 1,
    val type: PacketType,
    val payloadLength: Int
)

class NetworkPacket(
    val header: PacketHeader,
    val payload: ByteArray?
) {
    companion object {
        const val HEADER_SIZE = 8 // 2+1+1+4

        fun create(type: PacketType, jsonPayload: JSONObject? = null): NetworkPacket {
            val payloadBytes = jsonPayload?.toString()?.toByteArray(StandardCharsets.UTF_8)
            return NetworkPacket(
                PacketHeader(
                    type = type,
                    payloadLength = payloadBytes?.size ?: 0
                ),
                payload = payloadBytes
            )
        }
        
        fun createBinary(type: PacketType, data: ByteArray): NetworkPacket {
             return NetworkPacket(
                PacketHeader(
                    type = type,
                    payloadLength = data.size
                ),
                payload = data
            )
        }

        fun parseHeader(data: ByteArray): PacketHeader {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.short
            val version = buffer.get()
            val typeVal = buffer.get().toInt() and 0xFF
            val length = buffer.int
            
            return PacketHeader(magic, version, PacketType.fromInt(typeVal), length)
        }
    }
    
    fun toBytes(): ByteArray {
        val length = payload?.size ?: 0
        val buffer = ByteBuffer.allocate(HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN)
        
        buffer.putShort(header.magic)
        buffer.put(header.version)
        buffer.put(header.type.value.toByte())
        buffer.putInt(length)
        
        payload?.let { buffer.put(it) }
        
        return buffer.array()
    }
    
    fun getJsonPayload(): JSONObject? {
        if (payload == null || payload.isEmpty()) return null
        return try {
            JSONObject(String(payload, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
}
