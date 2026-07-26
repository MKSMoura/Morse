package org.mksys.morse.core.model

data class ConnectionRequestRecord(
    val publicKey: String,
    val x25519PublicKey: String,
    val onionAddress: String,
    val displayName: String,
    val timestamp: Long
)
