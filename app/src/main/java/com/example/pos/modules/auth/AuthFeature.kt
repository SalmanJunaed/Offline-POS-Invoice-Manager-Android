package com.example.pos.modules.auth

import com.example.pos.core.error.PosResult
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.repository.AuthRepository
import com.example.pos.data.repository.RegistrationResult

/**
 * Modular foundation for Authentication (Module 1).
 */
interface AuthFeatureFacade {
    suspend fun authenticate(phone: String, pin: String, remember: Boolean): PosResult<OperatorEntity>
    suspend fun register(name: String, phone: String, address: String, pin: String): PosResult<RegistrationResult>
    suspend fun verifyRecoveryCode(phone: String, recoveryCode: String): PosResult<OperatorEntity>
    suspend fun resetPassword(phone: String, recoveryCode: String, newPin: String): PosResult<Unit>
    suspend fun logout()
}

class AuthFeatureFacadeImpl(private val authRepository: AuthRepository) : AuthFeatureFacade {
    override suspend fun authenticate(phone: String, pin: String, remember: Boolean) =
        authRepository.login(phone, pin, remember)

    override suspend fun register(name: String, phone: String, address: String, pin: String) =
        authRepository.register(name, phone, address, pin)

    override suspend fun verifyRecoveryCode(phone: String, recoveryCode: String) =
        authRepository.verifyRecoveryCode(phone, recoveryCode)

    override suspend fun resetPassword(phone: String, recoveryCode: String, newPin: String) =
        authRepository.resetPassword(phone, recoveryCode, newPin)

    override suspend fun logout() = authRepository.logout()
}
