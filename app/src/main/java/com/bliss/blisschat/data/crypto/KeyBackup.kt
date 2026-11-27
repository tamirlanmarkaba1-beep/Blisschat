package com.bliss.blisschat.data.crypto

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Result of [KeyBackup.restore]. */
enum class RestoreResult { RESTORED, NO_BACKUP, WRONG_SECRET }

/** State of the backup as shown in Settings. */
data class BackupInfo(val exists: Boolean = false, val custom: Boolean = false, val ts: Long = 0L)

/**
 * WhatsApp-style encrypted key backup.
 *
 * All private keys of the account are packed into one bundle, encrypted with AES-256-GCM under a key derived from a
 * secret (PBKDF2-HMAC-SHA256, 150 000 rounds) and stored in users/{uid}/backup/keys. The server only sees ciphertext;
 * without the secret nobody (including us) can read it. When the user signs in on a new phone (or after a re-install)
 * the keys are restored from here, so old messages stay readable.
 *
 * The secret is either the account password ("account" mode, completely automatic at sign-in) or a separate recovery
 * code chosen by the user ("custom" mode, needed for Google sign-in).
 */
class KeyBackup(private val keys: LocalKeys) {

    private val db get() = FirebaseFirestore.getInstance()
    private fun ref(uid: String) = db.collection("users").document(uid).collection("backup").document("keys")

    suspend fun info(uid: String): BackupInfo {
        val d = ref(uid).get().await()
        return BackupInfo(d.exists(), d.getString("mode") == "custom", d.getLong("ts") ?: 0L)
    }

    /** Encrypts every local key of [uid] with [secret] and uploads it. @return false when there is nothing to back up. */
    suspend fun save(uid: String, secret: String, custom: Boolean): Boolean {
        val bundle = keys.exportAll(uid) ?: return false
        val salt = Crypto.randomBytes(16)
        val ct = withContext(Dispatchers.Default) {
            Crypto.seal(derive(secret, salt), bundle.toString().toByteArray(), uid.toByteArray())
        }
        ref(uid).set(
            mapOf(
                "salt" to Crypto.b64(salt), "ct" to Crypto.b64(ct), "iter" to ITER,
                "mode" to if (custom) "custom" else "account", "ts" to System.currentTimeMillis()
            )
        ).await()
        return true
    }

    /** Downloads the backup, decrypts it with [secret] and adds the keys to this device. */
    suspend fun restore(uid: String, secret: String): RestoreResult {
        val d = ref(uid).get().await()
        if (!d.exists()) return RestoreResult.NO_BACKUP
        val salt = Crypto.unb64(d.getString("salt") ?: return RestoreResult.NO_BACKUP)
        val ct = Crypto.unb64(d.getString("ct") ?: return RestoreResult.NO_BACKUP)
        val iter = (d.getLong("iter") ?: ITER.toLong()).toInt()
        val bundle = withContext(Dispatchers.Default) {
            runCatching { JSONObject(String(Crypto.open(derive(secret, salt, iter), ct, uid.toByteArray()))) }.getOrNull()
        } ?: return RestoreResult.WRONG_SECRET
        keys.importAll(uid, bundle)
        return RestoreResult.RESTORED
    }

    private fun derive(secret: String, salt: ByteArray, iter: Int = ITER): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iter, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private companion object {
        const val ITER = 150_000
    }
}
