package com.bliss.blisschat.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.PrivateKey
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalKey(val kid: String, val publicX509: ByteArray, val priv: PrivateKey)

/**
 * Stores each user's P-256 private key on this device only.
 * The private key is wrapped (AES-GCM) with a non-exportable key living in the Android Keystore,
 * so the stored blob is useless if the app data is copied off the phone.
 * The private key NEVER leaves the device; only the public key is uploaded.
 */
class LocalKeys(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("blisschat_e2ee", Context.MODE_PRIVATE)

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, wrapKey())
        return c.iv + c.doFinal(plain)
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, blob, 0, 12))
        return c.doFinal(blob, 12, blob.size - 12)
    }

    /** The key currently used for new messages, or null on a fresh install. */
    fun current(uid: String): LocalKey? {
        val kid = prefs.getString("$uid.cur", null) ?: return null
        return find(uid, kid)
    }

    /** Any key (current or older) by id, to read old messages. */
    fun find(uid: String, kid: String): LocalKey? = runCatching {
        val priv = prefs.getString("$uid.$kid.priv", null) ?: return null
        val pub = prefs.getString("$uid.$kid.pub", null) ?: return null
        LocalKey(kid, Crypto.unb64(pub), Crypto.privateFromPkcs8(unwrap(Crypto.unb64(priv))))
    }.getOrNull()

    fun create(uid: String): LocalKey {
        val pair = Crypto.newKeyPair()
        val pub = pair.public.encoded
        val kid = Crypto.kidOf(pub)
        prefs.edit()
            .putString("$uid.$kid.priv", Crypto.b64(wrap(pair.private.encoded)))
            .putString("$uid.$kid.pub", Crypto.b64(pub))
            .putString("$uid.cur", kid)
            .apply()
        return LocalKey(kid, pub, pair.private)
    }

    private companion object {
        const val ALIAS = "blisschat_wrap_key"
    }
}
