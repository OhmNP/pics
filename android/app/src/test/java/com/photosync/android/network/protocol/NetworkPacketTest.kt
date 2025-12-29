package com.photosync.android.network.protocol

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NetworkPacketTest {

    @Test
    fun testSerialization() {
        val json = JSONObject()
        json.put("test", "value")
        val packet = NetworkPacket.create(PacketType.METADATA, json)
        
        val bytes = packet.toBytes()
        
        // Header (8) + Payload
        assertTrue(bytes.size > 8)
        
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals(0x5048.toShort(), buffer.short) // Magic
        assertEquals(1.toByte(), buffer.get())       // Version
        assertEquals(PacketType.METADATA.value.toByte(), buffer.get()) // Type
        
        val payloadLen = buffer.int
        assertEquals(packet.payload!!.size, payloadLen)
    }

    @Test
    fun testParseHeader() {
        // Construct raw bytes
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(0x5048.toShort())
        buffer.put(1.toByte())
        buffer.put(PacketType.HEARTBEAT.value.toByte())
        buffer.putInt(99) // Payload len
        
        val header = NetworkPacket.parseHeader(buffer.array())
        
        assertEquals(0x5048.toShort(), header.magic)
        assertEquals(1.toByte(), header.version)
        assertEquals(PacketType.HEARTBEAT, header.type)
        assertEquals(99, header.payloadLength)
    }

    @Test
    fun testJsonPayload() {
        val json = JSONObject()
        json.put("key", 123)
        val packet = NetworkPacket.create(PacketType.DISCOVERY, json)
        
        val parsedJson = packet.getJsonPayload()
        assertNotNull(parsedJson)
        assertEquals(123, parsedJson!!.getInt("key"))
    }
    
    @Test
    fun testBinaryPayload() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val packet = NetworkPacket.createBinary(PacketType.FILE_CHUNK, data)
        
        assertEquals(3, packet.header.payloadLength)
        assertArrayEquals(data, packet.payload)
    }
}
