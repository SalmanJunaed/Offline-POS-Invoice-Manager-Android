package com.example.pos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import java.util.Locale

fun formatLockoutCountdown(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60L
    val seconds = safeSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
fun LoginOnboardingView(
    language: String,
    logoUri: String?,
    initialPhone: String,
    initialRememberMe: Boolean,
    isSubmitting: Boolean,
    lockoutRemainingSeconds: Long,
    authErrorMessage: String?,
    successBannerMessage: String?,
    onPhoneChanged: (String) -> Unit,
    onClearMessages: () -> Unit,
    onForgotPasswordClick: (currentPhone: String) -> Unit,
    onCreateNewAccountClick: () -> Unit = {},
    onSubmitLogin: (normalizedPhone: String, password: String, rememberMe: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    var mobileNumber by remember { mutableStateOf(initialPhone) }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(initialRememberMe) }
    var passwordVisible by remember { mutableStateOf(false) }

    var hasUserEditedPhone by remember { mutableStateOf(false) }
    var hasUserEditedRemember by remember { mutableStateOf(false) }

    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialPhone) {
        if (!hasUserEditedPhone && mobileNumber.isEmpty() && initialPhone.isNotBlank()) {
            mobileNumber = initialPhone
            onPhoneChanged(initialPhone)
        }
    }

    LaunchedEffect(initialRememberMe) {
        if (!hasUserEditedRemember) {
            rememberMe = initialRememberMe
        }
    }

    val isLocked = lockoutRemainingSeconds > 0L

    fun validateAndSubmit() {
        if (isSubmitting || isLocked) return
        onClearMessages()

        val normalizedPhone = AuthInputValidator.normalizePhone(mobileNumber)
        val resolvedPhoneError = when (AuthInputValidator.validatePhone(normalizedPhone)) {
            is AuthInputValidator.ValidationResult.Valid -> null
            is AuthInputValidator.ValidationResult.Invalid -> when {
                normalizedPhone.isEmpty() -> Localization.get(Localization.Key.REG_ERR_PHONE_REQUIRED, language)
                else -> Localization.get(Localization.Key.REG_ERR_PHONE_INVALID, language)
            }
        }

        val resolvedPasswordError = if (password.isBlank()) {
            Localization.get(Localization.Key.REG_ERR_PASSWORD_REQUIRED, language)
        } else {
            null
        }

        phoneError = resolvedPhoneError
        passwordError = resolvedPasswordError

        if (resolvedPhoneError == null && resolvedPasswordError == null) {
            focusManager.clearFocus()
            onSubmitLogin(normalizedPhone, password, rememberMe)
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
            .testTag("auth_screen"),
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
            // 1. Branding Header (matching Step 07C Registration visual language)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_branding_header"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
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
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = Localization.get(Localization.Key.APP_TITLE, language),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = PosDeepGreen,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = Localization.get(Localization.Key.APP_SUBTITLE_MANAGE, language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PosMutedText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.testTag("login_offline_badge"),
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

            // 2. Main Login Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_screen"),
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
                    Text(
                        text = Localization.get(Localization.Key.LOGIN_WELCOME_TITLE, language),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = PosCharcoalText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Localization.get(Localization.Key.LOGIN_WELCOME_SUBTITLE, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = PosMutedText
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Password Reset Success Banner (after returning from Offline Recovery)
                    AnimatedVisibility(visible = !successBannerMessage.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                                .semantics(mergeDescendants = true) {}
                                .testTag("login_success_banner"),
                            shape = RoundedCornerShape(12.dp),
                            color = PosMintGreen,
                            border = BorderStroke(1.dp, PosBorderGreen)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = PosDeepGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = successBannerMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = PosDeepGreen
                                )
                            }
                        }
                    }

                    // Authoritative Account Lockout Banner with Live MM:SS Countdown
                    AnimatedVisibility(visible = isLocked) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                                .semantics(mergeDescendants = true) {}
                                .testTag("login_lockout_banner"),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFFBEB),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockClock,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Localization.get(Localization.Key.LOCKOUT_TITLE, language),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF78350F)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = Localization.get(Localization.Key.LOCKOUT_DESC, language),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF92400E)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = Localization.get(Localization.Key.LOCKOUT_RETRY_IN, language),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color(0xFF78350F)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFEF3C7),
                                            border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                        ) {
                                            Text(
                                                text = formatLockoutCountdown(lockoutRemainingSeconds),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.ExtraBold
                                                ),
                                                color = Color(0xFF78350F),
                                                modifier = Modifier
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                                    .testTag("login_lockout_countdown")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Inline Authentication Error Banner
                    AnimatedVisibility(visible = !authErrorMessage.isNullOrBlank() && !isLocked) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                                .semantics(mergeDescendants = true) {}
                                .testTag("login_error_banner"),
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
                                    text = authErrorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = PosDueRed
                                )
                            }
                        }
                    }

                    // Mobile Number Input
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = {
                            hasUserEditedPhone = true
                            mobileNumber = it
                            phoneError = null
                            onClearMessages()
                            onPhoneChanged(it)
                        },
                        label = { Text(Localization.get(Localization.Key.USERNAME_OR_PHONE, language)) },
                        placeholder = { Text(Localization.get(Localization.Key.LOGIN_MOBILE_HINT, language)) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        isError = phoneError != null,
                        supportingText = phoneError?.let { err ->
                            {
                                Text(
                                    text = err,
                                    modifier = Modifier.testTag("login_phone_error")
                                )
                            }
                        },
                        enabled = !isSubmitting,
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
                            .testTag("login_phone_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                            onClearMessages()
                        },
                        label = { Text(Localization.get(Localization.Key.PASSWORD, language)) },
                        placeholder = { Text(Localization.get(Localization.Key.LOGIN_PASSWORD_HINT, language)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("login_password_toggle")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = Localization.get(Localization.Key.CD_TOGGLE_PASSWORD, language)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = passwordError != null,
                        supportingText = passwordError?.let { err ->
                            {
                                Text(
                                    text = err,
                                    modifier = Modifier.testTag("login_password_error")
                                )
                            }
                        },
                        enabled = !isSubmitting,
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { validateAndSubmit() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Remember Me Checkbox + Forgot Password Link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    enabled = !isSubmitting,
                                    role = Role.Checkbox
                                ) {
                                    hasUserEditedRemember = true
                                    rememberMe = !rememberMe
                                }
                                .minimumInteractiveComponentSize()
                                .padding(end = 6.dp)
                                .testTag("login_remember_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = {
                                    hasUserEditedRemember = true
                                    rememberMe = it
                                },
                                enabled = !isSubmitting,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = PosDeepGreen,
                                    uncheckedColor = PosMutedText,
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.testTag("login_remember_checkbox")
                            )
                            Text(
                                text = Localization.get(Localization.Key.REMEMBER_ME, language),
                                style = MaterialTheme.typography.bodySmall,
                                color = PosCharcoalText
                            )
                        }

                        TextButton(
                            onClick = { onForgotPasswordClick(AuthInputValidator.normalizePhone(mobileNumber)) },
                            enabled = !isSubmitting,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("login_forgot_password_btn")
                        ) {
                            Text(
                                text = Localization.get(Localization.Key.LOGIN_FORGOT_PASSWORD, language),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = PosDeepGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Full-Width Primary Green Login CTA
                    Button(
                        onClick = { validateAndSubmit() },
                        enabled = !isSubmitting && !isLocked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("submit_login_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PosDeepGreen,
                            contentColor = Color.White,
                            disabledContainerColor = if (isLocked) PosBorderNeutral else PosDeepGreen.copy(alpha = 0.55f),
                            disabledContentColor = if (isLocked) PosMutedText else Color.White.copy(alpha = 0.9f)
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .testTag("login_loading_indicator"),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = Localization.get(Localization.Key.LOGIN_SIGNING_IN, language),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        } else {
                            Text(
                                text = Localization.get(Localization.Key.BTN_LOGIN, language),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = PosBorderNeutral)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Create New Account Link
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = Localization.get(Localization.Key.LOGIN_NO_ACCOUNT, language),
                            style = MaterialTheme.typography.bodySmall,
                            color = PosMutedText,
                            textAlign = TextAlign.Center
                        )
                        TextButton(
                            onClick = onCreateNewAccountClick,
                            enabled = !isSubmitting,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("login_create_account_btn")
                        ) {
                            Text(
                                text = Localization.get(Localization.Key.BTN_CREATE_NEW_ACCOUNT, language),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = PosDeepGreen,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Security Notice
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
