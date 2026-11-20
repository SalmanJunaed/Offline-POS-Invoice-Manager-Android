package com.example.pos.core.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hardened offline password and recovery-code hashing helper.
 *
 * Current format (v2):
 *   pbkdf2_sha256$v2$<iterations>$<salt_base64>$<hash_base64>
 *
 * Configuration:
 *   - Algorithm: PBKDF2WithHmacSHA256
 *   - Iterations: 120,000 (chosen as the lower bound of the 120k-600k defensible range
 *     so login remains responsive on minSdk 24 mobile hardware while imposing strong
 *     offline brute-force cost)
 *   - Salt: 128-bit (16 bytes) per-credential SecureRandom salt
 *   - Key Length: 256 bits
 *   - Comparison: Constant-time MessageDigest.isEqual()
 *
 * Legacy compatibility:
 *   - Supports verifying existing v1 salted PBKDF2 hashes:
 *     pbkdf2_sha256$<salt_base64>$<hash_base64> (10,000 iterations)
 *   - Flags legacy hashes via [needsRehash] so successful login upgrades them to v2.
 *   - Rejects plaintext and unsalted SHA-256 hashes unconditionally.
 */
object PasswordSecurity {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    const val CURRENT_ITERATION_COUNT = 120_000
    private const val LEGACY_V1_ITERATION_COUNT = 10_000
    private const val MIN_ALLOWED_ITERATIONS = 10_000
    private const val MAX_ALLOWED_ITERATIONS = 1_000_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    private const val PREFIX_ALGORITHM = "pbkdf2_sha256"
    private const val VERSION_V2 = "v2"
    private const val PREFIX_V2 = "$PREFIX_ALGORITHM$$VERSION_V2$"
    private const val PREFIX_LEGACY_V1 = "$PREFIX_ALGORITHM$"

    private const val RECOVERY_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val RECOVERY_CODE_LENGTH = 12

    private val secureRandom = SecureRandom()

    /**
     * Generates a unique 16-byte random salt and hashes [password] using PBKDF2WithHmacSHA256.
     * Throws on cryptographic unavailability instead of ever falling back to weak or plaintext storage.
     */
    fun hashPassword(password: String, iterations: Int = CURRENT_ITERATION_COUNT): String {
        require(password.isNotBlank()) { "Password must not be blank." }
        require(iterations in MIN_ALLOWED_ITERATIONS..MAX_ALLOWED_ITERATIONS) {
            "Iteration count out of bounds."
        }

        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val hash = deriveKey(password, salt, iterations)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)

        return "$PREFIX_V2$iterations$$saltB64$$hashB64"
    }

    /**
     * Verifies [password] against [storedHash] in constant time.
     * Supports v2 (`pbkdf2_sha256$v2$<iter>$<salt>$<hash>`) and legacy v1 (`pbkdf2_sha256$<salt>$<hash>`).
     * Never accepts plaintext or unsalted SHA-256.
     */
    fun verifyPassword(password: String, storedHash: String): Boolean {
        if (password.isBlank() || storedHash.isBlank()) return false

        return try {
            when {
                storedHash.startsWith(PREFIX_V2) -> {
                    val parts = storedHash.split("$")
                    // [pbkdf2_sha256, v2, iterations, saltB64, hashB64]
                    if (parts.size != 5) return false
                    val iterations = parts[2].toIntOrNull() ?: return false
                    if (iterations !in MIN_ALLOWED_ITERATIONS..MAX_ALLOWED_ITERATIONS) return false

                    val salt = Base64.decode(parts[3], Base64.NO_WRAP)
                    val expectedHash = Base64.decode(parts[4], Base64.NO_WRAP)
                    if (salt.isEmpty() || expectedHash.isEmpty()) return false

                    val actualHash = deriveKey(password, salt, iterations)
                    MessageDigest.isEqual(actualHash, expectedHash)
                }

                storedHash.startsWith(PREFIX_LEGACY_V1) -> {
                    val parts = storedHash.split("$")
                    // Legacy v1 format: [pbkdf2_sha256, saltB64, hashB64]
                    if (parts.size != 3) return false

                    val salt = Base64.decode(parts[1], Base64.NO_WRAP)
                    val expectedHash = Base64.decode(parts[2], Base64.NO_WRAP)
                    if (salt.isEmpty() || expectedHash.isEmpty()) return false

                    val actualHash = deriveKey(password, salt, LEGACY_V1_ITERATION_COUNT)
                    MessageDigest.isEqual(actualHash, expectedHash)
                }

                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns true if [storedHash] uses a legacy format or fewer iterations than [CURRENT_ITERATION_COUNT].
     */
    fun needsRehash(storedHash: String): Boolean {
        if (!storedHash.startsWith(PREFIX_V2)) return true
        val parts = storedHash.split("$")
        if (parts.size != 5) return true
        val iterations = parts[2].toIntOrNull() ?: return true
        return iterations < CURRENT_ITERATION_COUNT
    }

    /**
     * Generates a cryptographically random 12-character grouped recovery code (XXXX-XXXX-XXXX)
     * with ~60 bits of entropy for offline password reset authorization.
     */
    fun generateRecoveryCode(): String {
        val raw = StringBuilder(RECOVERY_CODE_LENGTH)
        for (i in 0 until RECOVERY_CODE_LENGTH) {
            val idx = secureRandom.nextInt(RECOVERY_ALPHABET.length)
            raw.append(RECOVERY_ALPHABET[idx])
        }
        val code = raw.toString()
        return "${code.substring(0, 4)}-${code.substring(4, 8)}-${code.substring(8, 12)}"
    }

    /**
     * Normalizes user-entered recovery codes (case-insensitive, ignoring hyphens and whitespace).
     */
    fun normalizeRecoveryCode(rawCode: String): String {
        return rawCode.uppercase().filter { it.isLetterOrDigit() }
    }

    fun hashRecoveryCode(rawCode: String): String {
        val normalized = normalizeRecoveryCode(rawCode)
        require(normalized.isNotBlank()) { "Recovery code must not be blank." }
        return hashPassword(normalized)
    }

    fun verifyRecoveryCode(rawCode: String, storedHash: String): Boolean {
        val normalized = normalizeRecoveryCode(rawCode)
        if (normalized.isBlank()) return false
        return verifyPassword(normalized, storedHash)
    }

    private fun deriveKey(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = secret.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }
}
