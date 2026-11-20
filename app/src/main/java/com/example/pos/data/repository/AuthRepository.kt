package com.example.pos.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.core.security.AuthInputValidator
import com.example.pos.core.security.PasswordSecurity
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.audit.AuditSanitizer
import com.example.pos.data.db.AuditLogDao
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.OperatorDao
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.db.PosDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class RegistrationResult(
    val operator: OperatorEntity,
    val recoveryCode: String
)

interface AuthRepository {
    val activeOperator: Flow<OperatorEntity?>
    val operatorCount: Flow<Int>

    suspend fun getActiveOperator(): OperatorEntity?
    suspend fun getOperatorCount(): Int
    suspend fun hasRegisteredOperators(): Boolean
    suspend fun checkAndRestoreSession(): OperatorEntity?

    suspend fun login(phone: String, passwordRaw: String, rememberMe: Boolean): PosResult<OperatorEntity>
    suspend fun register(
        name: String,
        phone: String,
        address: String,
        passwordRaw: String
    ): PosResult<RegistrationResult>

    suspend fun verifyRecoveryCode(phone: String, recoveryCodeRaw: String): PosResult<OperatorEntity>
    suspend fun resetPassword(
        phone: String,
        recoveryCodeRaw: String,
        newPasswordRaw: String
    ): PosResult<Unit>

    suspend fun regenerateRecoveryCode(userId: String): PosResult<String>
    suspend fun updateProfile(name: String, phone: String, address: String): PosResult<Unit>
    suspend fun logout()

    suspend fun getRememberedPhoneHint(): String
    suspend fun isRememberMeHintEnabled(): Boolean
    suspend fun getLockoutRemainingSeconds(phone: String): Long = 0L
}

class AuthRepositoryImpl(
    private val operatorDao: OperatorDao,
    private val sessionManager: SecureSessionManager,
    private val db: PosDatabase? = null,
    private val maxFailedAttempts: Int = DEFAULT_MAX_FAILED_ATTEMPTS,
    private val lockoutDurationMs: Long = DEFAULT_LOCKOUT_DURATION_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val auditLogDao: AuditLogDao? = db?.auditLogDao()
) : AuthRepository {

    companion object {
        const val DEFAULT_MAX_FAILED_ATTEMPTS = 5
        const val DEFAULT_LOCKOUT_DURATION_MS = 5L * 60L * 1000L // 5 minutes
    }

    override val activeOperator: Flow<OperatorEntity?> = operatorDao.getActiveOperatorFlow()
    override val operatorCount: Flow<Int> = operatorDao.getOperatorCountFlow()

    private suspend fun <T> runAtomic(block: suspend () -> T): T {
        return if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }

    private suspend fun appendAuthAudit(
        operatorId: String,
        operatorName: String,
        actionType: String,
        entityId: String,
        summaryEn: String,
        summaryBn: String,
        beforeData: String? = null,
        afterData: String? = null,
        isSuccess: Boolean = true,
        metadataJson: String? = null,
        now: Long = timeProvider()
    ) {
        val dao = auditLogDao ?: return
        dao.insertAuditLog(
            AuditLogEntity(
                eventId = "AUD-" + UUID.randomUUID().toString(),
                timestamp = now,
                operatorId = operatorId,
                operatorName = operatorName,
                actionType = actionType,
                entityType = AuditEntityType.OPERATOR,
                entityId = entityId,
                summaryEn = summaryEn,
                summaryBn = summaryBn,
                beforeData = beforeData,
                afterData = afterData,
                isSuccess = isSuccess,
                metadataJson = metadataJson
            )
        )
    }

    override suspend fun getActiveOperator(): OperatorEntity? {
        db?.awaitInitialized()
        return operatorDao.getActiveOperator()
    }

    override suspend fun getOperatorCount(): Int {
        db?.awaitInitialized()
        return operatorDao.getOperatorCount()
    }

    override suspend fun hasRegisteredOperators(): Boolean = getOperatorCount() > 0

    override suspend fun checkAndRestoreSession(): OperatorEntity? {
        db?.awaitInitialized()
        val session = sessionManager.readValidSession()
        val now = timeProvider()

        if (session == null || !session.rememberMe) {
            operatorDao.clearAllActiveOperators()
            if (session != null && !session.rememberMe) {
                val keepHint = runCatching { sessionManager.isRememberMeHintEnabled() }.getOrDefault(false)
                sessionManager.clearSession(clearRememberedHint = !keepHint)
            }
            return null
        }

        val op = operatorDao.getOperatorById(session.userId)
        if (op == null || op.lockedUntilMillis > now) {
            operatorDao.clearAllActiveOperators()
            sessionManager.clearSession(clearRememberedHint = false)
            return null
        }

        return operatorDao.switchActiveOperator(op.userId, rememberMe = true, now = now)
    }

    override suspend fun login(
        phone: String,
        passwordRaw: String,
        rememberMe: Boolean
    ): PosResult<OperatorEntity> {
        db?.awaitInitialized()

        val normalizedPhone = AuthInputValidator.normalizePhone(phone)
        if (normalizedPhone.isBlank() || passwordRaw.isBlank()) {
            return PosResult.Error(PosError.ValidationError("Phone number and password are required."))
        }

        val now = timeProvider()
        val maskedPhone = AuditSanitizer.maskPhone(normalizedPhone)
        val op = operatorDao.getOperatorByPhone(normalizedPhone)
        if (op == null) {
            runCatching {
                runAtomic {
                    appendAuthAudit(
                        operatorId = AuditEntityType.SYSTEM_OPERATOR_ID,
                        operatorName = AuditEntityType.SYSTEM_OPERATOR_NAME_EN,
                        actionType = AuditActionType.AUTH_LOGIN_FAILED,
                        entityId = maskedPhone,
                        summaryEn = "Failed login attempt for unregistered phone ($maskedPhone)",
                        summaryBn = "অনিবন্ধিত নম্বরে ($maskedPhone) ব্যর্থ লগইন প্রচেষ্টা",
                        isSuccess = false,
                        metadataJson = AuditSanitizer.buildSnapshotJson(
                            mapOf("maskedPhone" to maskedPhone, "reason" to "invalid_credentials")
                        ),
                        now = now
                    )
                }
            }
            return PosResult.Error(PosError.InvalidCredentials())
        }

        if (op.lockedUntilMillis > now) {
            val remainingSeconds = ((op.lockedUntilMillis - now) + 999L) / 1000L
            val clampedRemaining = remainingSeconds.coerceAtLeast(1L)
            runCatching {
                runAtomic {
                    appendAuthAudit(
                        operatorId = op.userId,
                        operatorName = op.operatorName,
                        actionType = AuditActionType.AUTH_LOGIN_LOCKED,
                        entityId = op.userId,
                        summaryEn = "Blocked login attempt for locked operator ${op.operatorName}",
                        summaryBn = "লক করা অপারেটর ${op.operatorName}-এর লগইন প্রচেষ্টা প্রতিহত",
                        isSuccess = false,
                        metadataJson = AuditSanitizer.buildSnapshotJson(
                            mapOf(
                                "maskedPhone" to maskedPhone,
                                "reason" to "account_locked",
                                "lockoutSeconds" to clampedRemaining
                            )
                        ),
                        now = now
                    )
                }
            }
            return PosResult.Error(PosError.AccountLocked(clampedRemaining))
        }

        val isValid = PasswordSecurity.verifyPassword(passwordRaw, op.passwordHash)
        if (!isValid) {
            val newAttempts = op.failedAttempts + 1
            val newLockedUntil = if (newAttempts >= maxFailedAttempts) now + lockoutDurationMs else 0L
            runAtomic {
                operatorDao.updateLoginAttempts(op.userId, newAttempts, newLockedUntil, now)
                if (newLockedUntil > now) {
                    val remainingSeconds = ((newLockedUntil - now) + 999L) / 1000L
                    appendAuthAudit(
                        operatorId = op.userId,
                        operatorName = op.operatorName,
                        actionType = AuditActionType.AUTH_LOGIN_LOCKED,
                        entityId = op.userId,
                        summaryEn = "Operator ${op.operatorName} account locked after $newAttempts failed attempts",
                        summaryBn = "${AuditSanitizer.localizeCount(newAttempts, "bn")} বার ভুল চেষ্টার পর অপারেটর ${op.operatorName}-এর অ্যাকাউন্ট লক হয়েছে",
                        isSuccess = false,
                        metadataJson = AuditSanitizer.buildSnapshotJson(
                            mapOf(
                                "maskedPhone" to maskedPhone,
                                "failedAttempts" to newAttempts,
                                "lockoutSeconds" to remainingSeconds.coerceAtLeast(1L),
                                "reason" to "account_locked"
                            )
                        ),
                        now = now
                    )
                } else {
                    appendAuthAudit(
                        operatorId = op.userId,
                        operatorName = op.operatorName,
                        actionType = AuditActionType.AUTH_LOGIN_FAILED,
                        entityId = op.userId,
                        summaryEn = "Failed login attempt #$newAttempts for operator ${op.operatorName}",
                        summaryBn = "অপারেটর ${op.operatorName}-এর ব্যর্থ লগইন প্রচেষ্টা (#${AuditSanitizer.localizeCount(newAttempts, "bn")})",
                        isSuccess = false,
                        metadataJson = AuditSanitizer.buildSnapshotJson(
                            mapOf(
                                "maskedPhone" to maskedPhone,
                                "failedAttempts" to newAttempts,
                                "reason" to "invalid_credentials"
                            )
                        ),
                        now = now
                    )
                }
            }
            return if (newLockedUntil > now) {
                val remainingSeconds = ((newLockedUntil - now) + 999L) / 1000L
                PosResult.Error(PosError.AccountLocked(remainingSeconds.coerceAtLeast(1L)))
            } else {
                PosResult.Error(PosError.InvalidCredentials())
            }
        }

        // Compute password upgrade outside transaction if legacy hash needs rehash
        val upgradedHash: String? = if (PasswordSecurity.needsRehash(op.passwordHash)) {
            try {
                PasswordSecurity.hashPassword(passwordRaw)
            } catch (e: Exception) {
                return PosResult.Error(PosError.UnexpectedError("Security module error during credential upgrade.", e))
            }
        } else null

        // Persist encrypted session before activating operator in DB (fail-closed)
        val sessionSave = sessionManager.saveLoginSession(op.userId, normalizedPhone, rememberMe)
        if (sessionSave.isFailure) {
            operatorDao.clearAllActiveOperators()
            return PosResult.Error(
                PosError.UnexpectedError(
                    "Unable to initialize encrypted operator session.",
                    sessionSave.exceptionOrNull()
                )
            )
        }

        val activated = try {
            runAtomic {
                if (upgradedHash != null) {
                    operatorDao.updatePasswordHash(op.userId, upgradedHash, now)
                } else if (op.failedAttempts > 0 || op.lockedUntilMillis > 0L) {
                    operatorDao.updateLoginAttempts(op.userId, 0, 0L, now)
                }
                val switched = operatorDao.switchActiveOperator(op.userId, rememberMe, now)
                    ?: throw PosErrorException(PosError.RecordNotFound("Operator", op.userId))
                appendAuthAudit(
                    operatorId = switched.userId,
                    operatorName = switched.operatorName,
                    actionType = AuditActionType.AUTH_LOGIN_SUCCESS,
                    entityId = switched.userId,
                    summaryEn = "Operator ${switched.operatorName} signed in",
                    summaryBn = "অপারেটর ${switched.operatorName} লগইন করেছেন",
                    isSuccess = true,
                    metadataJson = AuditSanitizer.buildSnapshotJson(
                        mapOf("maskedPhone" to maskedPhone, "rememberMe" to rememberMe)
                    ),
                    now = now
                )
                switched
            }
        } catch (e: PosErrorException) {
            runCatching { sessionManager.clearSession(clearRememberedHint = false) }
            return PosResult.Error(e.posError)
        } catch (e: Exception) {
            runCatching { sessionManager.clearSession(clearRememberedHint = false) }
            return PosResult.Error(PosError.DatabaseError("Failed to finalize login: ${e.message}", e))
        }

        return PosResult.Success(activated)
    }

    override suspend fun register(
        name: String,
        phone: String,
        address: String,
        passwordRaw: String
    ): PosResult<RegistrationResult> {
        db?.awaitInitialized()

        val validation = AuthInputValidator.validateRegistration(name, phone, passwordRaw)
        if (validation is AuthInputValidator.ValidationResult.Invalid) {
            return PosResult.Error(PosError.ValidationError(validation.message))
        }

        val normalizedPhone = AuthInputValidator.normalizePhone(phone)
        val existing = operatorDao.getOperatorByPhone(normalizedPhone)
        if (existing != null) {
            return PosResult.Error(PosError.UserAlreadyExists())
        }

        val recoveryCode = PasswordSecurity.generateRecoveryCode()
        val passwordHash: String
        val recoveryCodeHash: String
        try {
            passwordHash = PasswordSecurity.hashPassword(passwordRaw)
            recoveryCodeHash = PasswordSecurity.hashRecoveryCode(recoveryCode)
        } catch (e: Exception) {
            return PosResult.Error(PosError.UnexpectedError("Unable to secure credentials.", e))
        }

        val now = timeProvider()
        val newOp = OperatorEntity(
            userId = "OP-" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(),
            operatorName = name.trim(),
            operatorPhone = normalizedPhone,
            operatorAddress = address.trim(),
            passwordHash = passwordHash,
            isActive = true,
            rememberMe = true,
            recoveryCodeHash = recoveryCodeHash,
            failedAttempts = 0,
            lockedUntilMillis = 0L,
            createdAt = now,
            updatedAt = now
        )

        val persisted = try {
            runAtomic {
                val created = operatorDao.insertAndActivateOperator(newOp)
                appendAuthAudit(
                    operatorId = created.userId,
                    operatorName = created.operatorName,
                    actionType = AuditActionType.AUTH_REGISTER,
                    entityId = created.userId,
                    summaryEn = "Registered new operator ${created.operatorName} (${created.userId})",
                    summaryBn = "নতুন অপারেটর ${created.operatorName} (${created.userId}) নিবন্ধিত হয়েছেন",
                    afterData = AuditSanitizer.buildSnapshotJson(
                        mapOf(
                            "operatorName" to created.operatorName,
                            "maskedPhone" to AuditSanitizer.maskPhone(created.operatorPhone),
                            "address" to created.operatorAddress
                        )
                    ),
                    isSuccess = true,
                    now = now
                )
                created
            }
        } catch (_: SQLiteConstraintException) {
            return PosResult.Error(PosError.UserAlreadyExists())
        }

        val sessionSave = sessionManager.saveLoginSession(persisted.userId, normalizedPhone, rememberMe = true)
        if (sessionSave.isFailure) {
            operatorDao.clearAllActiveOperators()
            return PosResult.Error(
                PosError.UnexpectedError(
                    "Operator created, but secure session storage failed.",
                    sessionSave.exceptionOrNull()
                )
            )
        }

        return PosResult.Success(RegistrationResult(operator = persisted, recoveryCode = recoveryCode))
    }

    override suspend fun verifyRecoveryCode(
        phone: String,
        recoveryCodeRaw: String
    ): PosResult<OperatorEntity> {
        db?.awaitInitialized()

        val normalizedPhone = AuthInputValidator.normalizePhone(phone)
        if (normalizedPhone.isBlank()) {
            return PosResult.Error(PosError.ValidationError("Phone number is required."))
        }
        if (recoveryCodeRaw.isBlank()) {
            return PosResult.Error(PosError.ValidationError("Recovery code is required."))
        }

        val op = operatorDao.getOperatorByPhone(normalizedPhone)
            ?: return PosResult.Error(PosError.RecordNotFound("Operator", normalizedPhone))

        if (op.recoveryCodeHash.isBlank()) {
            return PosResult.Error(PosError.RecoveryNotConfigured())
        }

        val matches = PasswordSecurity.verifyRecoveryCode(recoveryCodeRaw, op.recoveryCodeHash)
        if (!matches) {
            return PosResult.Error(PosError.InvalidRecoveryCode())
        }

        return PosResult.Success(op)
    }

    override suspend fun resetPassword(
        phone: String,
        recoveryCodeRaw: String,
        newPasswordRaw: String
    ): PosResult<Unit> {
        db?.awaitInitialized()

        val passwordValidation = AuthInputValidator.validatePassword(newPasswordRaw)
        if (passwordValidation is AuthInputValidator.ValidationResult.Invalid) {
            return PosResult.Error(PosError.ValidationError(passwordValidation.message))
        }

        val verified = when (val res = verifyRecoveryCode(phone, recoveryCodeRaw)) {
            is PosResult.Success -> res.data
            is PosResult.Error -> {
                val now = timeProvider()
                val normalized = AuthInputValidator.normalizePhone(phone)
                if (normalized.isNotBlank()) {
                    val masked = AuditSanitizer.maskPhone(normalized)
                    val targetOp = operatorDao.getOperatorByPhone(normalized)
                    runCatching {
                        runAtomic {
                            appendAuthAudit(
                                operatorId = targetOp?.userId ?: AuditEntityType.SYSTEM_OPERATOR_ID,
                                operatorName = targetOp?.operatorName ?: AuditEntityType.SYSTEM_OPERATOR_NAME_EN,
                                actionType = AuditActionType.AUTH_PASSWORD_RESET,
                                entityId = targetOp?.userId ?: masked,
                                summaryEn = "Failed offline password reset attempt ($masked)",
                                summaryBn = "ব্যর্থ অফলাইন পাসওয়ার্ড রিসেট প্রচেষ্টা ($masked)",
                                isSuccess = false,
                                metadataJson = AuditSanitizer.buildSnapshotJson(
                                    mapOf("maskedPhone" to masked, "reason" to "invalid_recovery_code")
                                ),
                                now = now
                            )
                        }
                    }
                }
                return res
            }
        }

        val newHash = try {
            PasswordSecurity.hashPassword(newPasswordRaw)
        } catch (e: Exception) {
            return PosResult.Error(PosError.UnexpectedError("Unable to hash new password.", e))
        }

        val now = timeProvider()
        val maskedPhone = AuditSanitizer.maskPhone(verified.operatorPhone)
        var clearedSession = false
        runAtomic {
            operatorDao.updatePasswordHash(verified.userId, newHash, now)
            val currentActive = operatorDao.getActiveOperator()
            if (currentActive == null || currentActive.userId == verified.userId) {
                operatorDao.clearAllActiveOperators()
                clearedSession = true
            }
            appendAuthAudit(
                operatorId = verified.userId,
                operatorName = verified.operatorName,
                actionType = AuditActionType.AUTH_PASSWORD_RESET,
                entityId = verified.userId,
                summaryEn = "Password reset via offline recovery code for ${verified.operatorName}",
                summaryBn = "${verified.operatorName}-এর পাসওয়ার্ড রিকভারি কোডের মাধ্যমে রিসেট হয়েছে",
                isSuccess = true,
                metadataJson = AuditSanitizer.buildSnapshotJson(
                    mapOf("maskedPhone" to maskedPhone)
                ),
                now = now
            )
        }
        if (clearedSession) {
            sessionManager.clearSession(clearRememberedHint = false)
        }

        return PosResult.Success(Unit)
    }

    override suspend fun regenerateRecoveryCode(userId: String): PosResult<String> {
        db?.awaitInitialized()

        val op = operatorDao.getOperatorById(userId)
            ?: return PosResult.Error(PosError.RecordNotFound("Operator", userId))

        val newCode = PasswordSecurity.generateRecoveryCode()
        val newHash = try {
            PasswordSecurity.hashRecoveryCode(newCode)
        } catch (e: Exception) {
            return PosResult.Error(PosError.UnexpectedError("Unable to generate recovery code.", e))
        }

        val now = timeProvider()
        runAtomic {
            operatorDao.updateRecoveryCodeHash(op.userId, newHash, now)
            appendAuthAudit(
                operatorId = op.userId,
                operatorName = op.operatorName,
                actionType = AuditActionType.AUTH_RECOVERY_CODE_REGENERATED,
                entityId = op.userId,
                summaryEn = "Recovery code regenerated for operator ${op.operatorName}",
                summaryBn = "অপারেটর ${op.operatorName}-এর নতুন রিকভারি কোড তৈরি হয়েছে",
                isSuccess = true,
                now = now
            )
        }
        return PosResult.Success(newCode)
    }

    override suspend fun updateProfile(name: String, phone: String, address: String): PosResult<Unit> {
        db?.awaitInitialized()

        val nameValidation = AuthInputValidator.validateName(name)
        if (nameValidation is AuthInputValidator.ValidationResult.Invalid) {
            return PosResult.Error(PosError.ValidationError(nameValidation.message))
        }
        val phoneValidation = AuthInputValidator.validatePhone(phone)
        if (phoneValidation is AuthInputValidator.ValidationResult.Invalid) {
            return PosResult.Error(PosError.ValidationError(phoneValidation.message))
        }

        val trimmedName = name.trim()
        val normalizedPhone = AuthInputValidator.normalizePhone(phone)
        val trimmedAddress = address.trim()

        val outcome = try {
            runAtomic {
                val active = operatorDao.getActiveOperator()
                    ?: return@runAtomic PosResult.Error(PosError.Unauthenticated())

                val phoneOwner = operatorDao.getOperatorByPhone(normalizedPhone)
                if (phoneOwner != null && phoneOwner.userId != active.userId) {
                    return@runAtomic PosResult.Error(PosError.UserAlreadyExists())
                }

                val (beforeJson, afterJson) = AuditSanitizer.computeDiffSnapshots(
                    before = mapOf(
                        "operatorName" to active.operatorName,
                        "operatorPhone" to active.operatorPhone,
                        "address" to active.operatorAddress
                    ),
                    after = mapOf(
                        "operatorName" to trimmedName,
                        "operatorPhone" to normalizedPhone,
                        "address" to trimmedAddress
                    )
                )

                if (beforeJson == null && afterJson == null) {
                    return@runAtomic PosResult.Success(active)
                }

                val now = timeProvider()
                operatorDao.updateOperatorProfile(
                    userId = active.userId,
                    name = trimmedName,
                    phone = normalizedPhone,
                    address = trimmedAddress,
                    now = now
                )
                appendAuthAudit(
                    operatorId = active.userId,
                    operatorName = trimmedName,
                    actionType = AuditActionType.AUTH_PROFILE_UPDATED,
                    entityId = active.userId,
                    summaryEn = "Updated operator profile for $trimmedName",
                    summaryBn = "অপারেটর $trimmedName-এর প্রোফাইল তথ্য হালনাগাদ হয়েছে",
                    beforeData = beforeJson,
                    afterData = afterJson,
                    isSuccess = true,
                    now = now
                )
                PosResult.Success(active)
            }
        } catch (_: SQLiteConstraintException) {
            return PosResult.Error(PosError.UserAlreadyExists())
        }

        val activeBeforeUpdate = when (outcome) {
            is PosResult.Success -> outcome.data
            is PosResult.Error -> return outcome
        }

        if (activeBeforeUpdate.rememberMe) {
            sessionManager.saveLoginSession(activeBeforeUpdate.userId, normalizedPhone, rememberMe = true)
        }

        return PosResult.Success(Unit)
    }

    override suspend fun logout() {
        val now = timeProvider()
        try {
            runAtomic {
                val active = operatorDao.getActiveOperator()
                operatorDao.clearAllActiveOperators()
                if (active != null) {
                    appendAuthAudit(
                        operatorId = active.userId,
                        operatorName = active.operatorName,
                        actionType = AuditActionType.AUTH_LOGOUT,
                        entityId = active.userId,
                        summaryEn = "Operator ${active.operatorName} signed out",
                        summaryBn = "অপারেটর ${active.operatorName} লগআউট করেছেন",
                        isSuccess = true,
                        now = now
                    )
                }
            }
        } finally {
            val keepHint = runCatching { sessionManager.isRememberMeHintEnabled() }.getOrDefault(false)
            runCatching { sessionManager.clearSession(clearRememberedHint = !keepHint) }
        }
    }

    override suspend fun getRememberedPhoneHint(): String = sessionManager.getSavedPhoneHint()

    override suspend fun isRememberMeHintEnabled(): Boolean = sessionManager.isRememberMeHintEnabled()

    override suspend fun getLockoutRemainingSeconds(phone: String): Long {
        db?.awaitInitialized()
        val normalizedPhone = AuthInputValidator.normalizePhone(phone)
        if (normalizedPhone.isBlank()) return 0L
        val op = operatorDao.getOperatorByPhone(normalizedPhone) ?: return 0L
        val now = timeProvider()
        if (op.lockedUntilMillis <= now) return 0L
        return ((op.lockedUntilMillis - now) + 999L) / 1000L
    }
}
