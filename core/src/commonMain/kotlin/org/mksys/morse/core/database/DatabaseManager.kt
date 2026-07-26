package org.mksys.morse.core.database

import org.mksys.morse.core.model.ContactRecord
import org.mksys.morse.core.model.IdentityRecord
import org.mksys.morse.core.model.IdentityStatus
import org.mksys.morse.core.model.MessageRecord

interface DatabaseManager {
    suspend fun open(pin: String): Boolean
    suspend fun close()
    fun isOpen(): Boolean
    suspend fun getIdentity(): IdentityRecord?
    suspend fun insertIdentity(
        displayName: String,
        createdAt: Long,
        ed25519PrivateKey: ByteArray,
        ed25519PublicKey: ByteArray,
        x25519PrivateKey: ByteArray,
        x25519PublicKey: ByteArray,
        onionAddress: String
    )
    suspend fun hasRealIdentity(): IdentityStatus
    suspend fun deleteDatabase(): Boolean

    suspend fun insertMessage(
        senderPublicKey: String,
        senderX25519PublicKey: String,
        senderOnionAddress: String,
        content: String,
        isOutgoing: Boolean,
        status: String,
        timestamp: Long,
        mimeType: String,
        audioBytes: ByteArray?,
        durationMs: Int?,
        senderMessageId: String?,
        deliveryTimestamp: Long?,
        createdAt: Long
    )

    suspend fun getMessageBySenderMessageId(senderMessageId: String): Boolean
    suspend fun updateMessageStatus(id: Long, status: String)
    suspend fun updateMessageStatusBySenderMessageId(senderMessageId: String, status: String, deliveryTimestamp: Long?)
    suspend fun deleteMessagesBySender(senderPublicKey: String)

    suspend fun getContactByPublicKey(publicKeyB64: String): ContactRecord?
    suspend fun insertContact(
        publicKey: String,
        x25519PublicKey: String,
        onionAddress: String,
        displayName: String,
        createdAt: Long
    )
    suspend fun deleteContact(publicKey: String)
    suspend fun updateContactLastSeen(publicKey: String, timestamp: Long)
    suspend fun setContactDisconnected(publicKey: String)
    suspend fun setContactReactivated(publicKey: String)
    suspend fun setContactBlocked(publicKey: String, blocked: Boolean)
    suspend fun updateContactDisplayName(publicKey: String, displayName: String)

    suspend fun insertConnectionRequest(
        publicKey: String,
        x25519PublicKey: String,
        onionAddress: String,
        displayName: String,
        timestamp: Long
    )
    suspend fun getConnectionRequests(): List<org.mksys.morse.core.model.ConnectionRequestRecord>
    suspend fun deleteConnectionRequest(publicKey: String)

    suspend fun insertAppEvent(
        type: String,
        senderPublicKey: String?,
        senderDisplayName: String?,
        message: String,
        timestamp: Long
    )
    suspend fun getUnreadEventCount(): Long
    suspend fun markAppEventAsRead(id: Long)
    suspend fun markAllAppEventsAsRead()

    suspend fun getAllContacts(): List<ContactRecord>
    suspend fun getMessagesBySender(senderPublicKey: String): List<MessageRecord>
    suspend fun getLastMessagePerContact(): List<MessageRecord>
    suspend fun getUnreadConnectionRequestCount(): Long
}
