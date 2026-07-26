package org.mksys.morse.core.model

data class ContactRecord(
    val publicKeyB64: String,
    val x25519PublicKeyB64: String,
    val onionAddress: String,
    val displayName: String,
    val isBlocked: Boolean,
    val isDisconnected: Boolean,
    val createdAt: Long
)
