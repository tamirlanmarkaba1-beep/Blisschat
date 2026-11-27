package com.bliss.blisschat.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** An account that was signed in on this phone and can be switched to from Settings. */
data class SavedAccount(val uid: String, val email: String, val title: String, val avatarUrl: String, val method: String) {
    val viaPassword get() = method == "password"
}

/**
 * The accounts of this phone (like in Telegram / Instagram). Firebase keeps only one signed-in user, so for
 * an account that signed in with a password the password is kept here, encrypted with a non-exportable Android Keystore key
 * (it never leaves the phone), and used to sign in again on a switch. Google accounts are switched by signing in with Google again.
 */
class AccountStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("accounts", Context.MODE_PRIVATE)
    private val _list = MutableStateFlow(load())
    val accounts: StateFlow<List<SavedAccount>> = _list.asStateFlow()

    private fun load(): List<SavedAccount> = runCatching {
        val arr = JSONArray(prefs.getString("list", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SavedAccount(o.getString("uid"), o.optString("email"), o.optString("title"), o.optString("avatar"), o.optString("method"))
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<SavedAccount>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("uid", it.uid).put("email", it.email).put("title", it.title).put("avatar", it.avatarUrl).put("method", it.method))
        }
        prefs.edit().putString("list", arr.toString()).apply()
        _list.value = list
    }

    /** @param method "password" | "google" | null (= keep what is known); @param password the typed password, if there is one */
    fun remember(uid: String, email: String, title: String, avatarUrl: String, method: String?, password: String?) {
        val old = _list.value.firstOrNull { it.uid == uid }
        val acc = SavedAccount(uid, email.ifBlank { old?.email.orEmpty() }, title, avatarUrl, method ?: old?.method.orEmpty())
        save(_list.value.filter { it.uid != uid } + acc)
        if (password != null) runCatching { prefs.edit().putString("pw.$uid", encrypt(password)).apply() }
    }

    fun password(uid: String): String? = runCatching { prefs.getString("pw.$uid", null)?.let(::decrypt) }.getOrNull()

    fun forget(uid: String) {
        save(_list.value.filter { it.uid != uid })
        prefs.edit().remove("pw.$uid").apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build()
        )
        return gen.generateKey()
    }

    private fun encrypt(text: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(b64: String): String {
        val blob = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
        return String(c.doFinal(blob, 12, blob.size - 12))
    }

    private companion object { const val ALIAS = "blisschat_accounts_key" }
}
