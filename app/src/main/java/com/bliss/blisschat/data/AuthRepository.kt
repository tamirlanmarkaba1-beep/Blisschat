package com.bliss.blisschat.data

import android.content.Context
import android.util.Patterns
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** E-mail / username + password and Google sign-in on top of Firebase Auth. */
class AuthRepository {

    private val auth get() = FirebaseAuth.getInstance()

    fun isEmail(s: String) = Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches()

    /** @param identifier an e-mail address or a @username */
    suspend fun login(identifier: String, password: String): Result<Unit> = runCatching {
        val id = identifier.trim().removePrefix("@")
        require(id.isNotEmpty() && password.isNotEmpty()) { "Введите логин и пароль" }
        val email = if (isEmail(id)) id else emailForUsername(id)
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }.mapAuth()

    suspend fun register(email: String, password: String): Result<Unit> = runCatching {
        require(isEmail(email)) { "Введите корректный email" }
        require(password.length >= 6) { "Пароль должен быть не короче 6 символов" }
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
        Unit
    }.mapAuth()

    suspend fun google(activityContext: Context): Result<Unit> = runCatching {
        require(FirebaseConfig.WEB_CLIENT_ID.isNotBlank()) {
            "Вход через Google не настроен: укажите WEB_CLIENT_ID в FirebaseConfig.kt"
        }
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(FirebaseConfig.WEB_CLIENT_ID).build())
            .build()
        val cred = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        check(cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Не удалось получить аккаунт Google"
        }
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        Unit
    }.mapAuth()

    suspend fun resetPassword(identifier: String): Result<Unit> = runCatching {
        val id = identifier.trim().removePrefix("@")
        require(id.isNotEmpty()) { "Введите email или username" }
        val email = if (isEmail(id)) id else emailForUsername(id)
        auth.sendPasswordResetEmail(email).await()
        Unit
    }.mapAuth()

    fun signOut() = auth.signOut()

    /** usernames/{name} stores the login e-mail so a username can be used to sign in. */
    private suspend fun emailForUsername(username: String): String {
        val doc = FirebaseFirestore.getInstance().collection("usernames")
            .document(username.lowercase()).get().await()
        return doc.getString("email") ?: error("Пользователь не найден")
    }

    private fun <T> Result<T>.mapAuth(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { e ->
            val msg = when {
                e is GetCredentialCancellationException -> "Вход отменён"
                e is FirebaseAuthException -> when (e.errorCode) {
                    "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND",
                    "ERROR_INVALID_LOGIN_CREDENTIALS" -> "Неверный логин или пароль"
                    "ERROR_EMAIL_ALREADY_IN_USE" -> "Этот email уже зарегистрирован"
                    "ERROR_WEAK_PASSWORD" -> "Слишком простой пароль"
                    "ERROR_TOO_MANY_REQUESTS" -> "Слишком много попыток, попробуйте позже"
                    "ERROR_USER_DISABLED" -> "Аккаунт заблокирован"
                    else -> e.message ?: "Ошибка авторизации"
                }
                e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Неверный логин или пароль"
                e is com.google.firebase.FirebaseNetworkException -> "Нет подключения к интернету"
                e.message.isNullOrBlank() -> "Что-то пошло не так"
                else -> e.message!!
            }
            Result.failure(IllegalStateException(msg, e))
        }
    )
}
