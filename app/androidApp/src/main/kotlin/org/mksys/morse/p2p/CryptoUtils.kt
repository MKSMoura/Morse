package org.mksys.morse.p2p

import android.util.Base64
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private val secureRandom = SecureRandom.getInstanceStrong()
    private const val NONCE_SIZE = 12
    private const val GCM_TAG_BITS = 128

    fun generateX25519KeyPair(): Pair<ByteArray, ByteArray> {
        val privateKey = X25519PrivateKeyParameters(SecureRandom())
        val publicKey = privateKey.generatePublicKey()
        return Pair(privateKey.encoded, publicKey.encoded)
    }

    fun generateEd25519KeyPair(): Pair<ByteArray, ByteArray> {
        val privateKey = Ed25519PrivateKeyParameters(SecureRandom())
        val publicKey = privateKey.generatePublicKey()
        return Pair(privateKey.encoded, publicKey.encoded)
    }

    fun getEd25519PublicKey(privateKey: ByteArray): ByteArray {
        val priv = Ed25519PrivateKeyParameters(privateKey, 0)
        return priv.generatePublicKey().encoded
    }

    fun ecdh(myPrivate: ByteArray, theirPublic: ByteArray): ByteArray {
        val priv = X25519PrivateKeyParameters(myPrivate, 0)
        val pub = X25519PublicKeyParameters(theirPublic, 0)
        val agreement = X25519Agreement()
        agreement.init(priv)
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(pub, shared, 0)
        return shared
    }

    fun encryptMessage(plaintext: ByteArray, key: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also { secureRandom.nextBytes(it) }
        return nonce + encryptMessageKey(plaintext, key, nonce)
    }

    fun decryptMessage(data: ByteArray, key: ByteArray): ByteArray {
        val nonce = data.copyOfRange(0, NONCE_SIZE)
        val ciphertext = data.copyOfRange(NONCE_SIZE, data.size)
        return decryptMessageKey(ciphertext, key, nonce)
    }

    private fun encryptMessageKey(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(plaintext)
    }

    private fun decryptMessageKey(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }

    fun deriveKey(sharedSecret: ByteArray, info: String, length: Int = 32): ByteArray {
        val salt = "MorseSalt_v1".toByteArray()
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(sharedSecret, salt, info.toByteArray()))
        val key = ByteArray(length)
        hkdf.generateBytes(key, 0, length)
        return key
    }

    fun sign(privateKey: ByteArray, data: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        signer.update(data, 0, data.size)
        return signer.verifySignature(signature)
    }

    fun encodeKey(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE)

    fun decodeKey(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE)
}
