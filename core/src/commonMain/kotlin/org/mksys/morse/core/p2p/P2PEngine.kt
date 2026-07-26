package org.mksys.morse.core.p2p

import kotlinx.coroutines.flow.SharedFlow

interface P2PEngine {
    val events: SharedFlow<P2PEvent>

    fun start()
    fun stop()

    suspend fun sendMessage(
        recipientPublicKeyB64: String,
        recipientX25519PublicKeyB64: String,
        recipientOnionAddress: String,
        content: String,
        mimeType: String = "",
        messageId: String = ""
    ): Boolean

    suspend fun sendConnectionRequest(
        targetOnion: String,
        targetX25519PublicKeyB64: String
    ): Boolean

    suspend fun sendDisconnect(
        targetOnion: String,
        targetX25519PublicKeyB64: String
    ): Boolean

    suspend fun acceptConnectionRequest(
        theirPublicKeyB64: String,
        theirOnionAddress: String,
        theirX25519PubB64: String,
        displayName: String = ""
    ): Boolean

    fun setConversationActive(contactPublicKeyB64: String, active: Boolean)
}
