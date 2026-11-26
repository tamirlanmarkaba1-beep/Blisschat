package com.bliss.blisschat.data.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Thrown when a message cannot be decrypted (missing key, wrong key or tampered data). */
class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Cryptographic primitives. Everything is standard JCA, no home-made algorithms:
 *  - ECDH on NIST P-256 for key agreement
 *  - HKDF-SHA256 to turn the shared secret into a key
 *  - AES-256-GCM for authenticated encryption (random 96-bit IV, prepended to the ciphertext)
 */
object Crypto {
    private val random = SecureRandom()

    fun newKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun privateFromPkcs8(bytes: ByteArray): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes))

    /** Short public identifier of a public key (so keys can be rotated / looked up). */
    fun kidOf(publicX509: ByteArray): String =
        b64url(MessageDigest.getInstance("SHA-256").digest(publicX509)).take(16)

    fun agree(priv: PrivateKey, peerPublicX509: ByteArray): ByteArray {
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPublicX509))
        return KeyAgreement.getInstance("ECDH").run {
            init(priv)
            doPhase(pub, true)
            generateSecret()
        }
    }

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val n = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }
    fun randomKey(): ByteArray = randomBytes(32)

    /** @return iv(12) || ciphertext || tag(16) */
    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val iv = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        if (aad != null) c.updateAAD(aad)
        return iv + c.doFinal(plain)
    }

    fun open(key: ByteArray, blob: ByteArray, aad: ByteArray? = null): ByteArray = try {
        require(blob.size > 28) { "too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, blob, 0, 12))
        if (aad != null) c.updateAAD(aad)
        c.doFinal(blob, 12, blob.size - 12)
    } catch (e: Exception) {
        throw CryptoException("Не удалось расшифровать", e)
    }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
}
