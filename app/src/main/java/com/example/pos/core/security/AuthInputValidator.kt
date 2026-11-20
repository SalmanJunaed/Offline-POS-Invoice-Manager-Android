package com.example.pos.core.security

/**
 * Validates and normalizes offline operator authentication inputs.
 */
object AuthInputValidator {

    const val MIN_NAME_LENGTH = 2
    const val MAX_NAME_LENGTH = 80
    const val MIN_PHONE_DIGITS = 6
    const val MAX_PHONE_DIGITS = 15
    const val MIN_PASSWORD_LENGTH = 4
    const val MAX_PASSWORD_LENGTH = 128

    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()

        val isValid: Boolean get() = this is Valid
        val errorMessage: String? get() = (this as? Invalid)?.message
    }

    fun normalizePhone(rawPhone: String): String {
        val trimmed = rawPhone.trim()
        if (trimmed.isEmpty()) return ""
        val hasLeadingPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (hasLeadingPlus && digitsOnly.isNotEmpty()) "+$digitsOnly" else digitsOnly
    }

    fun validateName(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.Invalid("Operator name is required.")
            trimmed.length < MIN_NAME_LENGTH -> ValidationResult.Invalid("Operator name must be at least $MIN_NAME_LENGTH characters.")
            trimmed.length > MAX_NAME_LENGTH -> ValidationResult.Invalid("Operator name is too long.")
            else -> ValidationResult.Valid
        }
    }

    fun validatePhone(phone: String): ValidationResult {
        val normalized = normalizePhone(phone)
        val digits = normalized.count { it.isDigit() }
        return when {
            normalized.isEmpty() -> ValidationResult.Invalid("Phone number is required.")
            digits < MIN_PHONE_DIGITS || digits > MAX_PHONE_DIGITS ->
                ValidationResult.Invalid("Enter a valid phone number ($MIN_PHONE_DIGITS-$MAX_PHONE_DIGITS digits).")
            else -> ValidationResult.Valid
        }
    }

    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult.Invalid("Password is required.")
            password.length < MIN_PASSWORD_LENGTH ->
                ValidationResult.Invalid("Password must be at least $MIN_PASSWORD_LENGTH characters.")
            password.length > MAX_PASSWORD_LENGTH ->
                ValidationResult.Invalid("Password must not exceed $MAX_PASSWORD_LENGTH characters.")
            else -> ValidationResult.Valid
        }
    }

    fun validateRegistration(name: String, phone: String, password: String): ValidationResult {
        validateName(name).let { if (!it.isValid) return it }
        validatePhone(phone).let { if (!it.isValid) return it }
        validatePassword(password).let { if (!it.isValid) return it }
        return ValidationResult.Valid
    }
}
