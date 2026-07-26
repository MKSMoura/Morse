package org.mksys.morse.identity

import org.mksys.morse.core.identity.IdentityManager
import org.mksys.morse.core.model.IdentityKeys
import org.bouncycastle.jce.provider.BouncyCastleProvider

class AndroidIdentityManager : IdentityManager {

    override suspend fun generateIdentityKeys(): IdentityKeys {
        val ed25519KeyPair = generateEd25519KeyPair()
        val x25519KeyPair = generateX25519KeyPair()

        return IdentityKeys(
            ed25519PrivateKey = ed25519KeyPair.private.encoded,
            ed25519PublicKey = ed25519KeyPair.public.encoded,
            x25519PrivateKey = x25519KeyPair.private.encoded,
            x25519PublicKey = x25519KeyPair.public.encoded,
            onionAddress = ""
        )
    }

    private fun generateEd25519KeyPair(): java.security.KeyPair {
        val kpg = java.security.KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider())
        return kpg.generateKeyPair()
    }

    private fun generateX25519KeyPair(): java.security.KeyPair {
        val kpg = java.security.KeyPairGenerator.getInstance("X25519", BouncyCastleProvider())
        return kpg.generateKeyPair()
    }
}
