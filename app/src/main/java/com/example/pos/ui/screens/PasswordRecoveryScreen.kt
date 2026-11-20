package com.example.pos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pos.core.security.AuthInputValidator
import com.example.pos.data.Localization
import com.example.pos.ui.components.CompanyLogoView
import com.example.ui.theme.PosBorderGreen
import com.example.ui.theme.PosBorderNeutral
import com.example.ui.theme.PosCharcoalText
import com.example.ui.theme.PosCleanWhite
import com.example.ui.theme.PosDeepGreen
import com.example.ui.theme.PosDueRed
import com.example.ui.theme.PosMintGreen
import com.example.ui.theme.PosMutedText
import com.example.ui.theme.PosPaleGreen

@Composable
fun PasswordRecoveryScreen(
    language: String,
    logoUri: String?,
    initialPhone: String,
    isVerifying: Boolean,
    isResetting: Boolean,
    onVerifyRecoveryCode: (normalizedPhone: String, recoveryCode: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onResetPassword: (normalizedPhone: String, recoveryCode: String, newPassword: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onRecoveryResetCompleted: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isBusy = isVerifying || isResetting

    BackHandler(enabled = !isBusy) {
        onBackToLogin()
    }

    var mobileNumber by remember { mutableStateOf(initialPhone) }
    var recoveryCode by remember { mutableStateOf("") }

    var isCodeVerified by remember { mutableStateOf(false) }
    var verifiedPhone by remember { mutableStateOf("") }
    var verifiedCode by remember { mutableStateOf("") }

    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var phoneError by remember { mutableStateOf<String?>(null) }
    var recoveryCodeError by remember { mutableStateOf<String?>(null) }
    var newPasswordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }

    fun submitVerifyCode() {
        if (isVerifying) return
        serverError = null

        val normalizedPhone = AuthInputValidator.normalizePhone(mobileNumber)
        val resolvedPhoneError = when (AuthInputValidator.validatePhone(normalizedPhone)) {
            is AuthInputValidator.ValidationResult.Valid -> null
            is AuthInputValidator.ValidationResult.Invalid -> when {
                normalizedPhone.isEmpty() -> Localization.get(Localization.Key.REG_ERR_PHONE_REQUIRED, language)
                else -> Localization.get(Localization.Key.REG_ERR_PHONE_INVALID, language)
            }
        }

        val trimmedCode = recoveryCode.trim()
        val resolvedCodeError = if (trimmedCode.isEmpty()) {
            Localization.get(Localization.Key.RECOVERY_ERR_CODE_REQUIRED, language)
        } else {
            null
        }

        phoneError = resolvedPhoneError
        recoveryCodeError = resolvedCodeError

        if (resolvedPhoneError == null && resolvedCodeError == null) {
            focusManager.clearFocus()
            onVerifyRecoveryCode(normalizedPhone, trimmedCode) { ok, err ->
                if (ok) {
                    verifiedPhone = normalizedPhone
                    verifiedCode = trimmedCode
                    recoveryCode = ""
                    isCodeVerified = true
                    serverError = null
                } else {
                    serverError = err ?: Localization.get(Localization.Key.RECOVERY_ERR_INVALID_OR_UNAVAILABLE, language)
                }
            }
        }
    }

    fun submitUpdatePassword() {
        if (isResetting || !isCodeVerified) return
        serverError = null

        val resolvedPasswordError = when (AuthInputValidator.validatePassword(newPassword)) {
            is AuthInputValidator.ValidationResult.Valid -> null
            is AuthInputValidator.ValidationResult.Invalid -> when {
                newPassword.isBlank() -> Localization.get(Localization.Key.REG_ERR_PASSWORD_REQUIRED, language)
                newPassword.length < AuthInputValidator.MIN_PASSWORD_LENGTH ->
                    Localization.get(Localization.Key.REG_ERR_PASSWORD_MIN, language)
                else -> Localization.get(Localization.Key.REG_ERR_PASSWORD_MAX, language)
            }
        }

        val resolvedConfirmError = when {
            confirmNewPassword.isEmpty() -> Localization.get(Localization.Key.REG_ERR_CONFIRM_REQUIRED, language)
            confirmNewPassword != newPassword -> Localization.get(Localization.Key.REG_ERR_PASSWORD_MISMATCH, language)
            else -> null
        }

        newPasswordError = resolvedPasswordError
        confirmPasswordError = resolvedConfirmError

        if (resolvedPasswordError == null && resolvedConfirmError == null) {
            focusManager.clearFocus()
            onResetPassword(verifiedPhone, verifiedCode, newPassword) { ok, err ->
                if (ok) {
                    verifiedCode = ""
                    newPassword = ""
                    confirmNewPassword = ""
                    onRecoveryResetCompleted()
                } else {
                    serverError = err ?: Localization.get(Localization.Key.RECOVERY_ERR_INVALID_OR_UNAVAILABLE, language)
                }
            }
        }
    }

    val fieldShape = RoundedCornerShape(12.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PosDeepGreen,
        unfocusedBorderColor = PosBorderNeutral,
        errorBorderColor = PosDueRed,
        focusedLeadingIconColor = PosDeepGreen,
        unfocusedLeadingIconColor = PosMutedText,
        focusedLabelColor = PosDeepGreen,
        focusedContainerColor = PosCleanWhite,
        unfocusedContainerColor = PosPaleGreen.copy(alpha = 0.45f)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PosPaleGreen, PosCleanWhite, PosCleanWhite)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("recovery_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Branding Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = PosCleanWhite,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.5.dp, PosBorderGreen)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CompanyLogoView(
                            logoUri = logoUri,
                            contentDescription = Localization.get(Localization.Key.CD_APP_LOGO, language),
                            modifier = Modifier.fillMaxSize(),
                            fallback = {
                                Icon(
                                    imageVector = Icons.Default.LocalGasStation,
                                    contentDescription = Localization.get(Localization.Key.CD_APP_LOGO, language),
                                    tint = PosDeepGreen,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = Localization.get(Localization.Key.APP_TITLE, language),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = PosDeepGreen,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = PosMintGreen,
                    border = BorderStroke(1.dp, PosBorderGreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.OfflinePin,
                            contentDescription = null,
                            tint = PosDeepGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Localization.get(Localization.Key.OFFLINE_POS_BADGE, language),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = PosDeepGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Recovery Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PosCleanWhite),
                border = BorderStroke(1.dp, PosBorderGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = PosMintGreen,
                            border = BorderStroke(1.dp, PosBorderGreen)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LockReset,
                                    contentDescription = null,
                                    tint = PosDeepGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!isCodeVerified) {
                                    Localization.get(Localization.Key.RECOVERY_SCREEN_TITLE, language)
                                } else {
                                    Localization.get(Localization.Key.RECOVERY_STEP_NEW_PASSWORD_TITLE, language)
                                },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = PosCharcoalText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (!isCodeVerified) {
                            Localization.get(Localization.Key.RECOVERY_SCREEN_SUBTITLE, language)
                        } else {
                            Localization.get(Localization.Key.RECOVERY_STEP_NEW_PASSWORD_SUBTITLE, language)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = PosMutedText
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Error Banner
                    AnimatedVisibility(visible = !serverError.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                                .semantics(mergeDescendants = true) {}
                                .testTag("recovery_error_banner"),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = PosDueRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = serverError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = PosDueRed
                                )
                            }
                        }
                    }

                    if (!isCodeVerified) {
                        // Step A & B: Identify Account & Verify Recovery Code
                        OutlinedTextField(
                            value = mobileNumber,
                            onValueChange = {
                                mobileNumber = it
                                phoneError = null
                                serverError = null
                            },
                            label = { Text(Localization.get(Localization.Key.RECOVERY_REGISTERED_PHONE, language)) },
                            placeholder = { Text(Localization.get(Localization.Key.LOGIN_MOBILE_HINT, language)) },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            isError = phoneError != null,
                            supportingText = phoneError?.let { err ->
                                {
                                    Text(
                                        text = err,
                                        modifier = Modifier.testTag("recovery_phone_error")
                                    )
                                }
                            },
                            enabled = !isBusy,
                            singleLine = true,
                            shape = fieldShape,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_phone_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = recoveryCode,
                            onValueChange = {
                                recoveryCode = it
                                recoveryCodeError = null
                                serverError = null
                            },
                            label = { Text(Localization.get(Localization.Key.RECOVERY_CODE_INPUT_LABEL, language)) },
                            placeholder = { Text(Localization.get(Localization.Key.RECOVERY_CODE_INPUT_HINT, language)) },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            isError = recoveryCodeError != null,
                            supportingText = recoveryCodeError?.let { err ->
                                {
                                    Text(
                                        text = err,
                                        modifier = Modifier.testTag("recovery_code_error")
                                    )
                                }
                            },
                            enabled = !isBusy,
                            singleLine = true,
                            shape = fieldShape,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitVerifyCode() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_code_input")
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { submitVerifyCode() },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("recovery_verify_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PosDeepGreen,
                                contentColor = Color.White,
                                disabledContainerColor = PosDeepGreen.copy(alpha = 0.55f),
                                disabledContentColor = Color.White.copy(alpha = 0.9f)
                            )
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .testTag("recovery_verifying_indicator"),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = Localization.get(Localization.Key.RECOVERY_VERIFYING, language),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                Text(
                                    text = Localization.get(Localization.Key.BTN_VERIFY_RECOVERY_CODE, language),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    } else {
                        // Verified Badge + Step C: Set New Password
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                                .testTag("recovery_verified_badge"),
                            shape = RoundedCornerShape(10.dp),
                            color = PosMintGreen,
                            border = BorderStroke(1.dp, PosBorderGreen)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = PosDeepGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Localization.get(Localization.Key.RECOVERY_VERIFIED_BADGE, language),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = PosDeepGreen
                                )
                            }
                        }

                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = {
                                newPassword = it
                                newPasswordError = null
                                if (confirmPasswordError != null && confirmNewPassword == it) {
                                    confirmPasswordError = null
                                }
                                serverError = null
                            },
                            label = { Text(Localization.get(Localization.Key.RECOVERY_NEW_PASSWORD, language)) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { newPasswordVisible = !newPasswordVisible },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("recovery_new_password_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = Localization.get(Localization.Key.CD_TOGGLE_PASSWORD, language)
                                    )
                                }
                            },
                            visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            isError = newPasswordError != null,
                            supportingText = newPasswordError?.let { err ->
                                {
                                    Text(
                                        text = err,
                                        modifier = Modifier.testTag("recovery_new_password_error")
                                    )
                                }
                            },
                            enabled = !isBusy,
                            singleLine = true,
                            shape = fieldShape,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_new_password_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = confirmNewPassword,
                            onValueChange = {
                                confirmNewPassword = it
                                confirmPasswordError = null
                                serverError = null
                            },
                            label = { Text(Localization.get(Localization.Key.RECOVERY_CONFIRM_NEW_PASSWORD, language)) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("recovery_confirm_password_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = Localization.get(Localization.Key.CD_TOGGLE_PASSWORD, language)
                                    )
                                }
                            },
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            isError = confirmPasswordError != null,
                            supportingText = confirmPasswordError?.let { err ->
                                {
                                    Text(
                                        text = err,
                                        modifier = Modifier.testTag("recovery_confirm_password_error")
                                    )
                                }
                            },
                            enabled = !isBusy,
                            singleLine = true,
                            shape = fieldShape,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitUpdatePassword() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_confirm_password_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = PosMintGreen,
                            border = BorderStroke(1.dp, PosBorderGreen)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = PosDeepGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Localization.get(Localization.Key.REG_PASSWORD_HINT, language),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PosCharcoalText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { submitUpdatePassword() },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("recovery_update_password_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PosDeepGreen,
                                contentColor = Color.White,
                                disabledContainerColor = PosDeepGreen.copy(alpha = 0.55f),
                                disabledContentColor = Color.White.copy(alpha = 0.9f)
                            )
                        ) {
                            if (isResetting) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .testTag("recovery_resetting_indicator"),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = Localization.get(Localization.Key.RECOVERY_UPDATING_PASSWORD, language),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                Text(
                                    text = Localization.get(Localization.Key.BTN_UPDATE_PASSWORD, language),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = PosBorderNeutral)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                verifiedCode = ""
                                recoveryCode = ""
                                newPassword = ""
                                confirmNewPassword = ""
                                onBackToLogin()
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("recovery_back_to_login_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = PosDeepGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Localization.get(Localization.Key.BTN_RETURN_TO_LOGIN, language),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = PosDeepGreen
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = PosDeepGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = Localization.get(Localization.Key.SECURITY_NOTICE, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = PosMutedText,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
