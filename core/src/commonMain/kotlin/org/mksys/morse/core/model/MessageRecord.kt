package org.mksys.morse.core.model

data class MessageRecord(
    val id: Long,
    val senderPublicKey: String,
    val senderX25519PublicKey: String,
    val senderOnionAddress: String,
    val content: String,
    val isOutgoing: Boolean,
    val status: String,
    val timestamp: Long,
    val mimeType: String,
    val audioBytes: ByteArray? = null,
    val durationMs: Int? = null,
    val senderMessageId: String? = null,
    val deliveryTimestamp: Long? = null,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageRecord) return false
        return id == other.id &&
               senderPublicKey == other.senderPublicKey &&
               content == other.content &&
               isOutgoing == other.isOutgoing &&
               status == other.status &&
               timestamp == other.timestamp &&
               senderMessageId == other.senderMessageId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + senderPublicKey.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + isOutgoing.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
