package com.example.pos.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
import com.example.ui.theme.PosEmerald
import com.example.ui.theme.PosLightGreen
import com.example.ui.theme.PosMintGreen
import com.example.ui.theme.PosMutedText
import com.example.ui.theme.PosPaleGreen

@Composable
fun RegistrationOnboardingView(
    language: String,
    logoUri: String?,
    isSubmitting: Boolean,
    serverErrorMessage: String?,
    showBackToLogin: Boolean,
    onBackToLogin: () -> Unit,
    onClearServerError: () -> Unit,
    onSubmitRegistration: (name: String, normalizedPhone: String, address: String, password: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = showBackToLogin && !isSubmitting) {
        onBackToLogin()
    }

    var fullName by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var businessAddress by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    fun validateAndSubmit() {
        if (isSubmitting) return
        onClearServerError()

        val trimmedName = fullName.trim()
        val resolvedNameError = when (AuthInputValidator.validateName(trimmedName)) {
            is AuthInputValidator.ValidationResult.Valid -> null
            is AuthInputValidator.ValidationResult.Invalid -> when {
                trimmedName.isEmpty() -> Localization.get(Localization.Key.REG_ERR_NAME_REQUIRED, language)
                trimmedName.length < AuthInputValidator.MIN_NAME_LENGTH ->
                    Localization.get(Localization.Key.REG_ERR_NAME_TOO_SHORT, language)
                else -> Localization.get(Localization.Key.REG_ERR_NAME_TOO_LONG, language)
            }
        }

        val normalizedPhone = AuthInputValidator.normalizePhone(mobileNumber)
        val resolvedPhoneError = when (AuthInputValidator.validatePhone(normalizedPhone)) {
            is AuthInputValidator.ValidationResult.Valid -> null
            is AuthInputValidator.ValidationResult.Invalid -> when {
                normalizedPhone.isEmpty() -> Localization.get(Localization.Key.REG_ERR_PHONE_REQUIRED, language)
                else -> Localization.get(Localization.Key.REG_ERR_PHONE_INVALID, language)
            }
        }

        val resolvedPasswordError = when (AuthInputValidator.validatePassword(password)) {
            is AuthInputValidator.ValidationResult.Valid -> null
            is AuthInputValidator.ValidationResult.Invalid -> when {
                password.isBlank() -> Localization.get(Localization.Key.REG_ERR_PASSWORD_REQUIRED, language)
                password.length < AuthInputValidator.MIN_PASSWORD_LENGTH ->
                    Localization.get(Localization.Key.REG_ERR_PASSWORD_MIN, language)
                else -> Localization.get(Localization.Key.REG_ERR_PASSWORD_MAX, language)
            }
        }

        val resolvedConfirmError = when {
            confirmPassword.isEmpty() -> Localization.get(Localization.Key.REG_ERR_CONFIRM_REQUIRED, language)
            confirmPassword != password -> Localization.get(Localization.Key.REG_ERR_PASSWORD_MISMATCH, language)
            else -> null
        }

        nameError = resolvedNameError
        phoneError = resolvedPhoneError
        passwordError = resolvedPasswordError
        confirmPasswordError = resolvedConfirmError

        if (resolvedNameError == null &&
            resolvedPhoneError == null &&
            resolvedPasswordError == null &&
            resolvedConfirmError == null
        ) {
            focusManager.clearFocus()
            onSubmitRegistration(trimmedName, normalizedPhone, businessAddress.trim(), password)
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
            .testTag("registration_screen"),
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
            // 1. Header / Branding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reg_branding_header"),
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
                    modifier = Modifier.testTag("reg_offline_badge"),
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

            // 2 & 3. Main Registration Card (Welcome + Form)
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
                    // Welcome Section
                    Text(
                        text = Localization.get(Localization.Key.REG_WELCOME_TITLE, language),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = PosCharcoalText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Localization.get(Localization.Key.REG_WELCOME_SUBTITLE, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = PosMutedText
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Server / Repository Error Banner
                    AnimatedVisibility(visible = !serverErrorMessage.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                                .semantics(mergeDescendants = true) {}
                                .testTag("reg_error_banner"),
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
                                    text = serverErrorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = PosDueRed
                                )
                            }
                        }
                    }

                    // A. Full Name (Required)
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            nameError = null
                            onClearServerError()
                        },
                        label = { Text(Localization.get(Localization.Key.FULL_NAME, language)) },
                        placeholder = { Text(Localization.get(Localization.Key.REG_FULL_NAME_HINT, language)) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        isError = nameError != null,
                        supportingText = nameError?.let { err ->
                            {
                                Text(
                                    text = err,
                                    modifier = Modifier.testTag("reg_name_error")
                                )
                            }
                        },
                        enabled = !isSubmitting,
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_name_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // B. Mobile Number (Required)
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = {
                            mobileNumber = it
                            phoneError = null
                            onClearServerError()
                        },
                        label = { Text(Localization.get(Localization.Key.PHONE_NUMBER, language)) },
                        placeholder = { Text(Localization.get(Localization.Key.REG_MOBILE_HINT, language)) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        isError = phoneError != null,
                        supportingText = phoneError?.let { err ->
                            {
                                Text(
                                    text = err,
                                    modifier = Modifier.testTag("reg_phone_error")
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
                            .testTag("reg_phone_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // C. Business Address / Branch (Optional)
                    OutlinedTextField(
                        value = businessAddress,
                        onValueChange = {
                            businessAddress = it
                            onClearServerError()
                        },
                        label = { Text(Localization.get(Localization.Key.REG_BUSINESS_ADDRESS, language)) },
                        placeholder = { Text(Localization.get(Localization.Key.REG_BUSINESS_ADDRESS_HINT, language)) },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        enabled = !isSubmitting,
                        minLines = 2,
                        maxLines = 3,
                        shape = fieldShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_address_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // D. Password (Required)
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                            if (confirmPasswordError != null && confirmPassword == it) {
                                confirmPasswordError = null
                            }
                            onClearServerError()
                        },
                        label = { Text(Localization.get(Localization.Key.PASSWORD, language)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("reg_password_toggle")
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
                                    modifier = Modifier.testTag("reg_password_error")
                                )
                            }
                        },
                        enabled = !isSubmitting,
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
                            .testTag("reg_password_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // E. Confirm Password (Required)
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            confirmPasswordError = null
                            onClearServerError()
                        },
                        label = { Text(Localization.get(Localization.Key.CONFIRM_PASSWORD, language)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("reg_confirm_password_toggle")
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
                                    modifier = Modifier.testTag("reg_confirm_password_error")
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
                            .testTag("reg_confirm_password_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Subtle Password Guidance Strip
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_password_guidance"),
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

                    // 5. Primary Create Account Button
                    Button(
                        onClick = { validateAndSubmit() },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("reg_submit_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PosDeepGreen,
                            contentColor = Color.White,
                            disabledContainerColor = PosDeepGreen.copy(alpha = 0.55f),
                            disabledContentColor = Color.White.copy(alpha = 0.9f)
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .testTag("reg_loading_indicator"),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = Localization.get(Localization.Key.REG_CREATING_ACCOUNT, language),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        } else {
                            Text(
                                text = Localization.get(Localization.Key.BTN_CREATE_ACCOUNT, language),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // 6. Optional Return to Login (only when authorized/operators exist)
                    if (showBackToLogin) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = PosBorderNeutral)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = Localization.get(Localization.Key.REG_ALREADY_HAVE_ACCOUNT, language),
                                style = MaterialTheme.typography.bodySmall,
                                color = PosMutedText
                            )
                            TextButton(
                                onClick = onBackToLogin,
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("reg_back_to_login_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = Localization.get(Localization.Key.REG_BACK_TO_LOGIN, language),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = PosDeepGreen
                                )
                            }
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

@Composable
fun RecoveryCodeConfirmationScreen(
    language: String,
    recoveryCode: String,
    operatorName: String?,
    onContinueToPos: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isCopied by remember(recoveryCode) { mutableStateOf(false) }
    var isAcknowledged by remember(recoveryCode) { mutableStateOf(false) }

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
            .testTag("recovery_confirmation_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PosCleanWhite),
                border = BorderStroke(1.dp, PosBorderGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Success Badge
                    Surface(
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("recovery_success_badge"),
                        shape = CircleShape,
                        color = PosLightGreen,
                        border = BorderStroke(1.5.dp, PosEmerald)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = Localization.get(Localization.Key.REG_SUCCESS_MSG, language),
                                tint = PosDeepGreen,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = Localization.get(Localization.Key.REG_SUCCESS_MSG, language),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = PosDeepGreen,
                        textAlign = TextAlign.Center
                    )

                    if (!operatorName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = operatorName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = PosCharcoalText,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = Localization.get(Localization.Key.RECOVERY_CODE_TITLE, language),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = PosCharcoalText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = Localization.get(Localization.Key.RECOVERY_CODE_SUBTITLE, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = PosMutedText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // One-Time Recovery Code Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = PosMintGreen,
                        border = BorderStroke(1.5.dp, PosDeepGreen.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = PosDeepGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Localization.get(Localization.Key.RECOVERY_CODE_LABEL, language),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = PosDeepGreen
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            SelectionContainer {
                                Text(
                                    text = recoveryCode,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 2.sp
                                    ),
                                    color = PosCharcoalText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("recovery_code_value")
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Recovery Code", recoveryCode))
                                    isCopied = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, PosDeepGreen),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = PosCleanWhite,
                                    contentColor = PosDeepGreen
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("recovery_copy_btn")
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isCopied) {
                                        Localization.get(Localization.Key.RECOVERY_CODE_COPIED, language)
                                    } else {
                                        Localization.get(Localization.Key.BTN_COPY_RECOVERY_CODE, language)
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.testTag(if (isCopied) "recovery_copied_badge" else "recovery_copy_label")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Explicit Non-Repeatable Warning Banner
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .testTag("recovery_warning_banner"),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = Localization.get(Localization.Key.RECOVERY_CODE_WARNING, language),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 19.sp
                                ),
                                color = Color(0xFF78350F)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Required Acknowledgement Checkbox Row
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = if (isAcknowledged) PosDeepGreen else PosBorderNeutral,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(role = Role.Checkbox) { isAcknowledged = !isAcknowledged }
                            .minimumInteractiveComponentSize()
                            .testTag("recovery_ack_row"),
                        color = if (isAcknowledged) PosMintGreen else PosCleanWhite
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isAcknowledged,
                                onCheckedChange = { isAcknowledged = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = PosDeepGreen,
                                    uncheckedColor = PosMutedText,
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.testTag("recovery_ack_checkbox")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = Localization.get(Localization.Key.RECOVERY_ACK_CHECKBOX, language),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = PosCharcoalText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Continue to POS Primary CTA
                    Button(
                        onClick = onContinueToPos,
                        enabled = isAcknowledged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("recovery_continue_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PosDeepGreen,
                            contentColor = Color.White,
                            disabledContainerColor = PosBorderNeutral,
                            disabledContentColor = PosMutedText
                        )
                    ) {
                        Text(
                            text = Localization.get(Localization.Key.BTN_CONTINUE_TO_POS, language),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
