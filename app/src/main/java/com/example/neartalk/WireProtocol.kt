package com.example.neartalk

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/** A small, versioned, length-checked binary format for Nearby BYTES payloads. */
internal object WireProtocol {
    private const val MAGIC = 0x4E544B32 // "NTK2"
    private const val MAX_PACKET_BYTES = 24_000
    private const val MAX_TEXT_CHARS = 4_000

    sealed class Frame {
        data class Hello(val peerId: String, val name: String) : Frame()
        data class Message(val id: String, val text: String) : Frame()
        data class Receipt(val id: String) : Frame()
    }

    fun encode(frame: Frame): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { writer ->
            writer.writeInt(MAGIC)
            when (frame) {
                is Frame.Hello -> {
                    require(validUuid(frame.peerId) && frame.name.length in 2..18)
                    writer.writeByte(1)
                    writer.writeUTF(frame.peerId)
                    writer.writeUTF(frame.name)
                }
                is Frame.Message -> {
                    require(validUuid(frame.id) && frame.text.isNotBlank() && frame.text.length <= MAX_TEXT_CHARS)
                    writer.writeByte(2)
                    writer.writeUTF(frame.id)
                    writer.writeUTF(frame.text)
                }
                is Frame.Receipt -> {
                    require(validUuid(frame.id))
                    writer.writeByte(3)
                    writer.writeUTF(frame.id)
                }
            }
        }
        return out.toByteArray().also { require(it.size <= MAX_PACKET_BYTES) }
    }

    fun decode(bytes: ByteArray): Frame? {
        if (bytes.size < 6 || bytes.size > MAX_PACKET_BYTES) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { reader ->
                if (reader.readInt() != MAGIC) return null
                val frame = when (reader.readUnsignedByte()) {
                    1 -> Frame.Hello(reader.readUTF(), reader.readUTF())
                    2 -> Frame.Message(reader.readUTF(), reader.readUTF())
                    3 -> Frame.Receipt(reader.readUTF())
                    else -> return null
                }
                if (reader.available() != 0) return null
                when (frame) {
                    is Frame.Hello -> if (validUuid(frame.peerId) && frame.name.length in 2..18) frame else null
                    is Frame.Message -> if (validUuid(frame.id) && frame.text.isNotBlank() && frame.text.length <= MAX_TEXT_CHARS) frame else null
                    is Frame.Receipt -> if (validUuid(frame.id)) frame else null
                }
            }
        } catch (_: Exception) { null }
    }

    private fun validUuid(s: String): Boolean = try {
        UUID.fromString(s).toString() == s.lowercase()
    } catch (_: IllegalArgumentException) { false }
}
