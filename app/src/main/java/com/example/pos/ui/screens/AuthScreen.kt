package com.example.pos.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.pos.data.Localization
import com.example.pos.ui.viewmodel.PosViewModel
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    viewModel: PosViewModel,
    language: String,
    allowRegistrationWhenOperatorsExist: Boolean = false,
    onLoginSuccess: () -> Unit
) {
    val hasRegisteredOperators by viewModel.hasRegisteredOperators.collectAsState()
    val companyHeader by viewModel.companyHeader.collectAsState()
    val isRegistering by viewModel.isRegistering.collectAsState()
    val pendingRecoveryCode by viewModel.pendingRecoveryCode.collectAsState()
    val pendingRecoveryOperatorName by viewModel.pendingRecoveryOperatorName.collectAsState()

    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val lockoutRemainingSeconds by viewModel.loginLockoutSeconds.collectAsState()
    val rememberedPhone by viewModel.rememberedPhoneHint.collectAsState()
    val rememberMeSaved by viewModel.rememberMeHintEnabled.collectAsState()
    val isVerifyingRecovery by viewModel.isVerifyingRecovery.collectAsState()
    val isResettingPassword by viewModel.isResettingPassword.collectAsState()
    val loginBannerMessage by viewModel.loginBannerMessage.collectAsState()

    var registrationError by remember { mutableStateOf<String?>(null) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isRecoveryMode by remember { mutableStateOf(false) }
    var recoveryInitialPhone by remember { mutableStateOf("") }
    var trackedLoginPhone by remember(rememberedPhone) { mutableStateOf(rememberedPhone) }

    var showAuthorizedRegistration by remember(allowRegistrationWhenOperatorsExist) {
        mutableStateOf(allowRegistrationWhenOperatorsExist)
    }
    var isCreateAccountMode by remember { mutableStateOf(false) }

    // 1. One-time recovery confirmation after registration (Step 07C)
    val recoveryCode = pendingRecoveryCode
    if (!recoveryCode.isNullOrBlank()) {
        RecoveryCodeConfirmationScreen(
            language = language,
            recoveryCode = recoveryCode,
            operatorName = pendingRecoveryOperatorName,
            onContinueToPos = {
                viewModel.acknowledgeRecoveryCode()
                onLoginSuccess()
            }
        )
        return
    }

    // 2. First-install Registration, Login "Create New Account" link, or explicitly authorized operator creation
    val shouldShowRegistration = !hasRegisteredOperators ||
        (allowRegistrationWhenOperatorsExist && showAuthorizedRegistration) ||
        isCreateAccountMode

    if (shouldShowRegistration) {
        RegistrationOnboardingView(
            language = language,
            logoUri = companyHeader?.logoUri,
            isSubmitting = isRegistering,
            serverErrorMessage = registrationError,
            showBackToLogin = hasRegisteredOperators,
            onBackToLogin = {
                showAuthorizedRegistration = false
                isCreateAccountMode = false
                registrationError = null
            },
            onClearServerError = { registrationError = null },
            onSubmitRegistration = { name, normalizedPhone, address, password ->
                viewModel.registerWithRecovery(name, normalizedPhone, address, password) { success, _, err ->
                    if (success) {
                        isCreateAccountMode = false
                    } else {
                        registrationError = err
                    }
                }
            }
        )
        return
    }

    // 3. Offline Password Recovery Flow (Step 07D)
    if (isRecoveryMode) {
        PasswordRecoveryScreen(
            language = language,
            logoUri = companyHeader?.logoUri,
            initialPhone = recoveryInitialPhone.ifBlank { trackedLoginPhone },
            isVerifying = isVerifyingRecovery,
            isResetting = isResettingPassword,
            onVerifyRecoveryCode = { normalizedPhone, code, onResult ->
                viewModel.verifyRecoveryCode(normalizedPhone, code, onResult)
            },
            onResetPassword = { normalizedPhone, code, newPassword, onResult ->
                viewModel.resetPassword(normalizedPhone, code, newPassword, onResult)
            },
            onRecoveryResetCompleted = {
                isRecoveryMode = false
                loginError = null
                if (trackedLoginPhone.isNotBlank()) {
                    viewModel.refreshLockoutStatus(trackedLoginPhone)
                }
            },
            onBackToLogin = {
                isRecoveryMode = false
            }
        )
        return
    }

    // 4. Authoritative Lockout Sync & Countdown Ticker while Login Screen is active
    LaunchedEffect(Unit) {
        if (trackedLoginPhone.isNotBlank()) {
            viewModel.refreshLockoutStatus(trackedLoginPhone)
        }
    }

    val isLocked = lockoutRemainingSeconds > 0L
    LaunchedEffect(isLocked, trackedLoginPhone) {
        if (isLocked) {
            while (viewModel.loginLockoutSeconds.value > 0L) {
                delay(1000L)
                viewModel.tickOrRefreshLockout(trackedLoginPhone)
            }
        }
    }

    // 5. Modern Login Screen (Step 07D)
    LoginOnboardingView(
        language = language,
        logoUri = companyHeader?.logoUri,
        initialPhone = rememberedPhone.ifBlank { viewModel.getRememberedPhone() },
        initialRememberMe = rememberMeSaved || viewModel.isRememberMeSaved(),
        isSubmitting = isLoggingIn,
        lockoutRemainingSeconds = lockoutRemainingSeconds,
        authErrorMessage = loginError,
        successBannerMessage = loginBannerMessage,
        onPhoneChanged = { newPhone ->
            trackedLoginPhone = newPhone
            viewModel.refreshLockoutStatus(newPhone)
        },
        onClearMessages = {
            loginError = null
            viewModel.clearLoginBanner()
        },
        onForgotPasswordClick = { currentPhone ->
            loginError = null
            viewModel.clearLoginBanner()
            recoveryInitialPhone = currentPhone
            isRecoveryMode = true
        },
        onCreateNewAccountClick = {
            loginError = null
            registrationError = null
            viewModel.clearLoginBanner()
            isCreateAccountMode = true
        },
        onSubmitLogin = { normalizedPhone, password, rememberMe ->
            trackedLoginPhone = normalizedPhone
            viewModel.login(normalizedPhone, password, rememberMe) { success, err ->
                if (success) {
                    loginError = null
                    onLoginSuccess()
                } else {
                    loginError = err ?: Localization.get(Localization.Key.LOGIN_ERR_GENERIC, language)
                }
            }
        }
    )
}
