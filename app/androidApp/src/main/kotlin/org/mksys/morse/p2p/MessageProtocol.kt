package org.mksys.morse.p2p

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

data class WireMessage(
    val senderPublicKey: ByteArray,
    val senderX25519PublicKey: ByteArray,
    val senderOnionAddress: String,
    val timestamp: Long,
    val senderDisplayName: String = "",
    val content: String,
    val mimeType: String = "",
    val audioBytes: ByteArray = ByteArray(0),
    val messageId: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WireMessage) return false
        return senderPublicKey.contentEquals(other.senderPublicKey) &&
                senderX25519PublicKey.contentEquals(other.senderX25519PublicKey) &&
                senderOnionAddress == other.senderOnionAddress &&
                timestamp == other.timestamp &&
                senderDisplayName == other.senderDisplayName &&
                content == other.content &&
                mimeType == other.mimeType &&
                audioBytes.contentEquals(other.audioBytes) &&
                messageId == other.messageId
    }

    override fun hashCode(): Int {
        var result = senderPublicKey.contentHashCode()
        result = 31 * result + senderX25519PublicKey.contentHashCode()
        result = 31 * result + senderOnionAddress.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + senderDisplayName.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + audioBytes.contentHashCode()
        result = 31 * result + messageId.hashCode()
        return result
    }
}

object MessageProtocol {
    private const val EPHEMERAL_KEY_SIZE = 32
    private val MAGIC_V5 = byteArrayOf(0x4D, 0x52, 0x53, 0x35)
    private const val MAX_MESSAGE_SIZE = 20 * 1024 * 1024

    fun encrypt(
        msg: WireMessage,
        recipientX25519Public: ByteArray
    ): ByteArray {
        val (ephemeralPriv, ephemeralPub) = CryptoUtils.generateX25519KeyPair()
        val sharedSecret = CryptoUtils.ecdh(ephemeralPriv, recipientX25519Public)
        val aesKey = CryptoUtils.deriveKey(sharedSecret, "MorseMessage")
        val plaintext = serializePlaintext(msg)
        val encrypted = CryptoUtils.encryptMessage(plaintext, aesKey)
        return ephemeralPub + encrypted
    }

    fun decrypt(
        data: ByteArray,
        recipientX25519Private: ByteArray
    ): WireMessage {
        val ephemPub = data.copyOfRange(0, EPHEMERAL_KEY_SIZE)
        val nonceAndCipher = data.copyOfRange(EPHEMERAL_KEY_SIZE, data.size)
        val sharedSecret = CryptoUtils.ecdh(recipientX25519Private, ephemPub)
        val aesKey = CryptoUtils.deriveKey(sharedSecret, "MorseMessage")
        val plaintext = CryptoUtils.decryptMessage(nonceAndCipher, aesKey)
        return deserializePlaintext(plaintext)
    }

    fun readMessage(inputStream: InputStream): ByteArray? {
        return try {
            val dis = DataInputStream(inputStream)
            val length = dis.readInt()
            if (length <= 0 || length > MAX_MESSAGE_SIZE) return null
            val data = ByteArray(length)
            dis.readFully(data)
            data
        } catch (e: Exception) {
            Log.w("MessageProtocol", "readMessage failed", e)
            null
        }
    }

    fun writeMessage(outputStream: OutputStream, data: ByteArray) {
        val dos = DataOutputStream(outputStream)
        dos.writeInt(data.size)
        dos.write(data)
        dos.flush()
    }

    fun serializePlaintext(msg: WireMessage): ByteArray {
        val onionBytes = msg.senderOnionAddress.toByteArray(Charsets.UTF_8)
        val nameBytes = msg.senderDisplayName.toByteArray(Charsets.UTF_8)
        val mimeBytes = msg.mimeType.toByteArray(Charsets.UTF_8)
        val msgIdBytes = msg.messageId.toByteArray(Charsets.UTF_8)
        val contentBytes = msg.content.toByteArray(Charsets.UTF_8)
        val hasMsgId = msg.messageId.isNotBlank()

        val totalSize = 4 + 32 + 32 + 1 + onionBytes.size + 8 + 2 + nameBytes.size +
                1 + mimeBytes.size + 4 + msg.audioBytes.size +
                (if (hasMsgId) 2 + msgIdBytes.size else 0) + contentBytes.size

        val buf = ByteBuffer.allocate(totalSize)
        buf.put(MAGIC_V5)
        buf.put(msg.senderPublicKey)
        buf.put(msg.senderX25519PublicKey)
        buf.put(onionBytes.size.toByte())
        buf.put(onionBytes)
        buf.putLong(msg.timestamp)
        buf.putShort(nameBytes.size.toShort())
        buf.put(nameBytes)
        buf.put(mimeBytes.size.toByte())
        buf.put(mimeBytes)
        buf.putInt(msg.audioBytes.size)
        if (msg.audioBytes.isNotEmpty()) buf.put(msg.audioBytes)
        if (hasMsgId) {
            buf.putShort(msgIdBytes.size.toShort())
            buf.put(msgIdBytes)
        }
        buf.put(contentBytes)
        return buf.array()
    }

    fun deserializePlaintext(data: ByteArray): WireMessage {
        val buf = ByteBuffer.wrap(data)
        val magic = ByteArray(4)
        buf.get(magic)

        if (!magic.contentEquals(MAGIC_V5)) {
            throw IllegalArgumentException("Unsupported protocol version")
        }

        val senderEd = ByteArray(32).also { buf.get(it) }
        val senderX = ByteArray(32).also { buf.get(it) }
        val onionLen = buf.get().toInt() and 0xFF
        val onionBytes = ByteArray(onionLen).also { buf.get(it) }
        val senderOnion = String(onionBytes, Charsets.UTF_8)
        val timestamp = buf.getLong()
        val nameLen = buf.getShort().toInt() and 0xFFFF
        val nameBytes = ByteArray(nameLen).also { buf.get(it) }
        val senderDisplayName = String(nameBytes, Charsets.UTF_8)
        val mimeLen = buf.get().toInt() and 0xFF
        val mimeBytes = ByteArray(mimeLen).also { buf.get(it) }
        val mimeType = String(mimeBytes, Charsets.UTF_8)
        val audioLen = buf.getInt()
        val audioBytes = if (audioLen > 0) ByteArray(audioLen).also { buf.get(it) } else ByteArray(0)
        val msgIdLen = buf.getShort().toInt() and 0xFFFF
        val msgIdBytes = ByteArray(msgIdLen).also { buf.get(it) }
        val messageId = String(msgIdBytes, Charsets.UTF_8)
        val contentBytes = ByteArray(buf.remaining()).also { buf.get(it) }

        return WireMessage(
            senderPublicKey = senderEd,
            senderX25519PublicKey = senderX,
            senderOnionAddress = senderOnion,
            timestamp = timestamp,
            senderDisplayName = senderDisplayName,
            content = String(contentBytes, Charsets.UTF_8),
            mimeType = mimeType,
            audioBytes = audioBytes,
            messageId = messageId
        )
    }
}
