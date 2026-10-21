// ЛР2. Вход по почте и Яндекс ID, регистрация, загрузка документов и сохранение сессии.
package com.example.drivenext.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.drivenext.BuildConfig
import com.example.drivenext.domain.AuthRepository
import com.example.drivenext.domain.AuthValidation
import com.example.drivenext.domain.RegistrationDraft
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.FlowType
import io.github.jan.supabase.gotrue.SessionManager
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.providers.OAuthProvider
import io.github.jan.supabase.gotrue.user.UserSession
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private object Yandex : OAuthProvider() { override val name = "custom:yandex" }

private class EncryptedSession(context: Context) : SessionManager {
    private val preferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("drivenext.auth", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("drivenext.auth", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encoded = cipher.iv + cipher.doFinal(Json.encodeToString(session).toByteArray())
        check(preferences.edit().putString("value", Base64.encodeToString(encoded, Base64.NO_WRAP)).commit())
    }
    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        val value = preferences.getString("value", null) ?: return@withContext null
        try {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
            Json.decodeFromString<UserSession>(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString())
        } catch (_: Exception) { preferences.edit().clear().commit(); null }
    }
    override suspend fun deleteSession() = withContext(Dispatchers.IO) { check(preferences.edit().clear().commit()) }
}

class SupabaseAuthRepository(context: Context) : AuthRepository {
    private val appContext = context.applicationContext
    internal val client by lazy {
        check(BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_KEY.isNotBlank()) { "backend_not_configured" }
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
            install(Auth) { flowType = FlowType.PKCE; scheme = "drivenext"; host = "auth"; sessionManager = EncryptedSession(context.applicationContext) }
            install(Postgrest)
            install(Storage)
        }
    }
    private var initialized = false
    override fun hasValidSession() = initialized && (client.auth.currentSessionOrNull()?.expiresAt?.epochSeconds ?: 0) > System.currentTimeMillis() / 1000
    override suspend fun initialize() {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_KEY.isBlank()) return
        client.auth.awaitInitialization()
        initialized = true
    }
    override suspend fun login(email: String, password: String) { client.auth.signInWith(Email) { this.email = email.trim(); this.password = password }; initialized = true }
    override suspend fun register(draft: RegistrationDraft): Boolean {
        client.auth.signUpWith(Email) { email = draft.email.trim(); password = draft.password }
        initialized = true
        return client.auth.currentSessionOrNull() != null
    }
    override suspend fun saveProfile(draft: RegistrationDraft) = withContext(Dispatchers.IO) {
        val uid = checkNotNull(client.auth.currentUserOrNull()?.id)
        suspend fun upload(name: String, file: String?): String? {
            if (file == null) return null
            val path = "$uid/$name.jpg"
            client.storage.from("identity-documents").upload(path, File(file).readBytes(), upsert = true)
            return path
        }
        val avatar = upload("avatar", draft.avatar)
        val license = upload("license", draft.licensePhoto)
        val passport = upload("passport", draft.passportPhoto)
        client.from("profiles").upsert(buildJsonObject {
            put("id", uid); put("surname", draft.surname.trim()); put("name", draft.name.trim()); put("patronymic", draft.patronymic.trim())
            put("birthday", AuthValidation.date(draft.birthday, true).toString()); put("gender", draft.gender)
            put("license_number", draft.license.trim()); put("license_issued", AuthValidation.date(draft.issued, false).toString())
            put("license_photo", license); put("passport_photo", passport); put("avatar", avatar)
        })
        Unit
    }
    override suspend fun yandex() { client.auth.signInWith(Yandex) }
    override suspend fun acceptCode(code: String) { client.auth.exchangeCodeForSession(code); initialized = true }
    override suspend fun logout() {
        client.auth.signOut()
        withContext(Dispatchers.IO) {
            File(appContext.filesDir, "auth-draft").listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        }
    }
}
