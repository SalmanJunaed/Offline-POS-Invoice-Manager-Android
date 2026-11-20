package com.example.pos.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated encryption abstraction for offline session storage.
 * Production always uses [AndroidKeyStoreSessionCipher] backed by AES-256-GCM.
 */
interface SessionCipher {
    fun encrypt(plainText: String): Result<String>
    fun decrypt(encodedCipherText: String): String?
}

/**
 * Hardware-backed AndroidKeyStore AES-256-GCM cipher.
 * Never falls back to plaintext or unauthenticated Base64 encoding.
 */
class AndroidKeyStoreSessionCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : SessionCipher {

    companion object {
        private const val DEFAULT_KEY_ALIAS = "PosSessionMasterKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
        private const val FORMAT_VERSION_AES_GCM: Byte = 0x02
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(keyGenSpec)
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Keystore alias missing SecretKeyEntry.")
        return entry.secretKey
    }

    private fun getExistingSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey
    }

    override fun encrypt(plainText: String): Result<String> {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            require(iv != null && iv.size == IV_LENGTH_BYTES) { "Invalid AES-GCM IV generated." }

            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = FORMAT_VERSION_AES_GCM
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)

            Result.success(Base64.encodeToString(combined, Base64.NO_WRAP))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Session encryption failed.", e))
        }
    }

    override fun decrypt(encodedCipherText: String): String? {
        return try {
            if (encodedCipherText.isBlank()) return null
            val combined = Base64.decode(encodedCipherText, Base64.NO_WRAP)
            if (combined.size <= 1 + IV_LENGTH_BYTES) return null
            if (combined[0] != FORMAT_VERSION_AES_GCM) return null

            val secretKey = getExistingSecretKey() ?: return null
            val iv = ByteArray(IV_LENGTH_BYTES)
            System.arraycopy(combined, 1, iv, 0, IV_LENGTH_BYTES)

            val cipherBytesLength = combined.size - 1 - IV_LENGTH_BYTES
            val cipherBytes = ByteArray(cipherBytesLength)
            System.arraycopy(combined, 1 + IV_LENGTH_BYTES, cipherBytes, 0, cipherBytesLength)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Pure JVM AES-256-GCM cipher for unit/Robolectric tests where AndroidKeyStore daemon is unavailable.
 */
class InMemoryAesGcmSessionCipher(
    keyBytes: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
) : SessionCipher {

    private val secretKey: SecretKey = SecretKeySpec(keyBytes.copyOf(32), "AES")
    private val random = SecureRandom()

    override fun encrypt(plainText: String): Result<String> {
        return try {
            val iv = ByteArray(12).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = 0x02
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            Result.success(Base64.encodeToString(combined, Base64.NO_WRAP))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun decrypt(encodedCipherText: String): String? {
        return try {
            val combined = Base64.decode(encodedCipherText, Base64.NO_WRAP)
            if (combined.size <= 13 || combined[0] != 0x02.toByte()) return null
            val iv = combined.copyOfRange(1, 13)
            val cipherBytes = combined.copyOfRange(13, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
