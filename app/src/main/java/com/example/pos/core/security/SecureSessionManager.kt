package com.example.pos.core.security

import android.content.Context
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.security.SecureRandom

private val Context.posSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SecureSessionManager.DATASTORE_NAME
)

data class SessionPayload(
    val userId: String,
    val sessionNonce: String,
    val rememberMe: Boolean,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

/**
 * Authenticated, encrypted local session manager built on Jetpack DataStore Preferences
 * and AndroidKeyStore AES-256-GCM encryption.
 *
 * Security guarantees:
 * - Entire session payload (userId, nonce, rememberMe, timestamps, metadata) is encrypted.
 * - Unique 12-byte IV per write with 128-bit GCM authentication tag.
 * - Never falls back to plaintext or unauthenticated Base64 encoding.
 * - Fails closed (returns null / rejects restore) on decryption, tampering, expiry, or Keystore errors.
 */
class SecureSessionManager internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SessionCipher,
    private val appContext: Context? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        const val DATASTORE_NAME = "pos_secure_session"
        private const val LEGACY_PREFS_NAME = "secure_pos_session_storage"

        private val KEY_ENCRYPTED_SESSION = stringPreferencesKey("encrypted_session_payload")
        private val KEY_ENCRYPTED_REMEMBERED_HINT = stringPreferencesKey("encrypted_remembered_hint")

        const val REMEMBERED_SESSION_TTL_MS = 30L * 24L * 60L * 60L * 1000L // 30 days
        const val EPHEMERAL_SESSION_TTL_MS = 24L * 60L * 60L * 1000L       // 24 hours

        @Volatile
        private var INSTANCE: SecureSessionManager? = null

        fun getInstance(context: Context): SecureSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureSessionManager(
                    dataStore = context.applicationContext.posSessionDataStore,
                    cipher = AndroidKeyStoreSessionCipher(),
                    appContext = context.applicationContext
                ).also { INSTANCE = it }
            }
        }

        @VisibleForTesting
        fun createForTest(
            dataStore: DataStore<Preferences>,
            cipher: SessionCipher = InMemoryAesGcmSessionCipher(),
            timeProvider: () -> Long = { System.currentTimeMillis() }
        ): SecureSessionManager {
            return SecureSessionManager(
                dataStore = dataStore,
                cipher = cipher,
                appContext = null,
                timeProvider = timeProvider
            )
        }
    }

    private val secureRandom = SecureRandom()

    suspend fun saveLoginSession(
        userId: String,
        phone: String,
        rememberMe: Boolean
    ): Result<SessionPayload> {
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID must not be blank."))
        }
        val now = timeProvider()
        val ttl = if (rememberMe) REMEMBERED_SESSION_TTL_MS else EPHEMERAL_SESSION_TTL_MS
        val nonceBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val payload = SessionPayload(
            userId = userId,
            sessionNonce = Base64.encodeToString(nonceBytes, Base64.NO_WRAP),
            rememberMe = rememberMe,
            createdAtMillis = now,
            expiresAtMillis = now + ttl,
            schemaVersion = SessionPayload.CURRENT_SCHEMA_VERSION
        )

        val encryptedSessionResult = cipher.encrypt(serializeSession(payload))
        if (encryptedSessionResult.isFailure) {
            return Result.failure(
                encryptedSessionResult.exceptionOrNull()
                    ?: IllegalStateException("Unable to encrypt session payload.")
            )
        }
        val encryptedSession = encryptedSessionResult.getOrThrow()

        val encryptedHint: String? = if (rememberMe && phone.isNotBlank()) {
            val hintJson = JSONObject().apply {
                put("phone", phone.trim())
                put("rememberMe", true)
                put("updatedAt", now)
            }.toString()
            cipher.encrypt(hintJson).getOrNull()
        } else {
            null
        }

        dataStore.edit { prefs ->
            prefs[KEY_ENCRYPTED_SESSION] = encryptedSession
            if (encryptedHint != null) {
                prefs[KEY_ENCRYPTED_REMEMBERED_HINT] = encryptedHint
            } else if (!rememberMe) {
                prefs.remove(KEY_ENCRYPTED_REMEMBERED_HINT)
            }
        }
        purgeLegacySharedPrefs()
        return Result.success(payload)
    }

    /**
     * Reads and authenticates the stored encrypted session.
     * Returns null if missing, undecryptable, tampered, schema-invalid, or expired.
     */
    suspend fun readValidSession(): SessionPayload? {
        val encoded = try {
            dataStore.data.first()[KEY_ENCRYPTED_SESSION]
        } catch (_: Exception) {
            null
        } ?: return null

        val plainJson = cipher.decrypt(encoded) ?: return null
        val payload = deserializeSession(plainJson) ?: return null

        val now = timeProvider()
        if (payload.userId.isBlank() ||
            payload.sessionNonce.isBlank() ||
            payload.createdAtMillis <= 0L ||
            payload.createdAtMillis > now + 60_000L || // reject invalid future creation
            now > payload.expiresAtMillis
        ) {
            return null
        }

        return payload
    }

    suspend fun getSavedPhoneHint(): String {
        val encoded = try {
            dataStore.data.first()[KEY_ENCRYPTED_REMEMBERED_HINT]
        } catch (_: Exception) {
            null
        } ?: return ""

        val plain = cipher.decrypt(encoded) ?: return ""
        return try {
            val obj = JSONObject(plain)
            if (obj.optBoolean("rememberMe", false)) {
                obj.optString("phone", "")
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun isRememberMeHintEnabled(): Boolean {
        val encoded = try {
            dataStore.data.first()[KEY_ENCRYPTED_REMEMBERED_HINT]
        } catch (_: Exception) {
            null
        } ?: return false

        val plain = cipher.decrypt(encoded) ?: return false
        return try {
            JSONObject(plain).optBoolean("rememberMe", false)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearSession(clearRememberedHint: Boolean = false) {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ENCRYPTED_SESSION)
            if (clearRememberedHint) {
                prefs.remove(KEY_ENCRYPTED_REMEMBERED_HINT)
            }
        }
        purgeLegacySharedPrefs()
    }

    private fun purgeLegacySharedPrefs() {
        try {
            appContext?.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.clear()
                ?.apply()
        } catch (_: Exception) {
        }
    }

    private fun serializeSession(payload: SessionPayload): String {
        return JSONObject().apply {
            put("v", payload.schemaVersion)
            put("uid", payload.userId)
            put("nonce", payload.sessionNonce)
            put("rem", payload.rememberMe)
            put("iat", payload.createdAtMillis)
            put("exp", payload.expiresAtMillis)
        }.toString()
    }

    private fun deserializeSession(jsonStr: String): SessionPayload? {
        return try {
            val obj = JSONObject(jsonStr)
            val version = obj.optInt("v", 0)
            if (version != SessionPayload.CURRENT_SCHEMA_VERSION) return null
            val userId = obj.optString("uid", "")
            val nonce = obj.optString("nonce", "")
            if (!obj.has("rem") || !obj.has("iat") || !obj.has("exp")) return null
            SessionPayload(
                userId = userId,
                sessionNonce = nonce,
                rememberMe = obj.getBoolean("rem"),
                createdAtMillis = obj.getLong("iat"),
                expiresAtMillis = obj.getLong("exp"),
                schemaVersion = version
            )
        } catch (_: Exception) {
            null
        }
    }
}
