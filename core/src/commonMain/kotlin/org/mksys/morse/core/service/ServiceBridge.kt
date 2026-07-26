package org.mksys.morse.core.service

import kotlinx.coroutines.flow.Flow
import org.mksys.morse.core.model.ConnectionRequestRecord
import org.mksys.morse.core.model.ContactRecord
import org.mksys.morse.core.model.IdentityStatus
import org.mksys.morse.core.model.MessageRecord
import org.mksys.morse.core.p2p.P2PEvent

sealed class TorStatus {
    data object Starting : TorStatus()
    data class Connecting(val message: String) : TorStatus()
    data object Connected : TorStatus()
    data class NeedsAction(val message: String) : TorStatus()
    data object StoppedTrying : TorStatus()
}

sealed class SaveResult {
    data object Success : SaveResult()
    data class Error(val message: String) : SaveResult()
}

interface ServiceBridge {
    fun observeStatus(): Flow<TorStatus>
    fun observeIdentity(): Flow<IdentityStatus>
    fun observeP2PEvents(): Flow<P2PEvent>?
    suspend fun isServiceRunning(): Boolean
    suspend fun startService()
    suspend fun retryConnection()
    suspend fun saveIdentity(name: String, pin: String): SaveResult
    suspend fun verifyIntegrity(): Boolean
    suspend fun confirmRetry()
    suspend fun leaveForLater()

    suspend fun sendMessage(
        recipientPublicKeyB64: String,
        recipientX25519PublicKeyB64: String,
        recipientOnionAddress: String,
        content: String,
        mimeType: String = "",
        messageId: String = ""
    ): Boolean

    suspend fun sendConnectionRequest(targetOnion: String, targetX25519PublicKeyB64: String): Boolean
    suspend fun acceptConnectionRequest(
        theirPublicKeyB64: String,
        theirOnionAddress: String,
        theirX25519PubB64: String,
        displayName: String = ""
    ): Boolean
    suspend fun sendDisconnect(targetOnion: String, targetX25519PublicKeyB64: String): Boolean
    fun setConversationActive(contactPublicKeyB64: String, active: Boolean)

    suspend fun getAllContacts(): List<ContactRecord>
    suspend fun getMessagesBySender(senderPublicKey: String): List<MessageRecord>
    suspend fun getLastMessagePerContact(): List<MessageRecord>
    suspend fun getConnectionRequests(): List<ConnectionRequestRecord>
    suspend fun getUnreadEventCount(): Long
    suspend fun markAllAppEventsAsRead()
    suspend fun deleteContact(publicKey: String)
}
