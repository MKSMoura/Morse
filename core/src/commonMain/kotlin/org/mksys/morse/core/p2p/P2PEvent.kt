package org.mksys.morse.core.p2p

sealed class P2PEvent {
    data class MessageReceived(
        val senderPublicKeyB64: String,
        val timestamp: Long
    ) : P2PEvent()

    data class ConnectionRequest(
        val senderPublicKeyB64: String,
        val senderX25519PublicKeyB64: String,
        val senderOnionAddress: String,
        val senderDisplayName: String
    ) : P2PEvent()

    data class ConnectionAccepted(
        val senderPublicKeyB64: String,
        val senderX25519PublicKeyB64: String,
        val senderOnionAddress: String,
        val senderDisplayName: String
    ) : P2PEvent()

    data class TerminationReceived(val senderPublicKeyB64: String) : P2PEvent()

    data object ConnectionError : P2PEvent()

    data object TorNotAvailable : P2PEvent()
}
