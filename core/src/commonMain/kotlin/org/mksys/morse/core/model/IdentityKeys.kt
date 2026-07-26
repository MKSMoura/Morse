package org.mksys.morse.core.model

data class IdentityKeys(
    val ed25519PrivateKey: ByteArray,
    val ed25519PublicKey: ByteArray,
    val x25519PrivateKey: ByteArray,
    val x25519PublicKey: ByteArray,
    val onionAddress: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdentityKeys) return false
        return ed25519PrivateKey.contentEquals(other.ed25519PrivateKey) &&
               ed25519PublicKey.contentEquals(other.ed25519PublicKey) &&
               x25519PrivateKey.contentEquals(other.x25519PrivateKey) &&
               x25519PublicKey.contentEquals(other.x25519PublicKey) &&
               onionAddress == other.onionAddress
    }

    override fun hashCode(): Int {
        var result = ed25519PrivateKey.contentHashCode()
        result = 31 * result + ed25519PublicKey.contentHashCode()
        result = 31 * result + x25519PrivateKey.contentHashCode()
        result = 31 * result + x25519PublicKey.contentHashCode()
        result = 31 * result + onionAddress.hashCode()
        return result
    }
}
