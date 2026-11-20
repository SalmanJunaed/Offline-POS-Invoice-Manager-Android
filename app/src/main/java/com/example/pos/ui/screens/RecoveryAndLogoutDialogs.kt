package com.example.pos.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pos.data.Localization
import com.example.pos.data.db.OperatorEntity
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
fun RecoveryCodeManagementDialog(
    operator: OperatorEntity?,
    language: String,
    generatedRecoveryCode: String?,
    isRegenerating: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onRequestRegenerate: () -> Unit,
    onAcknowledgeAndClose: () -> Unit,
    onDismissBeforeRegeneration: () -> Unit
) {
    val context = LocalContext.current
    var showRegenConfirmDialog by remember { mutableStateOf(false) }
    var isCopied by remember(generatedRecoveryCode) { mutableStateOf(false) }
    var isAcknowledged by remember(generatedRecoveryCode) { mutableStateOf(false) }

    val hasGeneratedCode = !generatedRecoveryCode.isNullOrBlank()

    DisposableEffect(Unit) {
        onDispose {
            showRegenConfirmDialog = false
            isCopied = false
            isAcknowledged = false
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isRegenerating && !hasGeneratedCode) {
                onDismissBeforeRegeneration()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isRegenerating && !hasGeneratedCode,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(PosPaleGreen, PosCleanWhite, PosCleanWhite)
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("recovery_management_dialog"),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            color = PosMintGreen,
                            border = BorderStroke(1.dp, PosBorderGreen)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = PosDeepGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = Localization.get(Localization.Key.SETTINGS_RECOVERY_MGMT_TITLE, language),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = PosCharcoalText
                        )
                    }

                    if (!hasGeneratedCode) {
                        IconButton(
                            onClick = onDismissBeforeRegeneration,
                            enabled = !isRegenerating,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_close_recovery_mgmt")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = Localization.get(Localization.Key.BTN_CLOSE, language),
                                tint = PosCharcoalText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current Operator Identity Card (Strictly Non-Sensitive Metadata)
                if (operator != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_mgmt_operator_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PosCleanWhite),
                        border = BorderStroke(1.dp, PosBorderGreen),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = PosDeepGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = Localization.get(Localization.Key.RECOVERY_MGMT_CURRENT_OPERATOR, language),
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = PosDeepGreen
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = PosLightGreen,
                                    border = BorderStroke(1.dp, PosBorderGreen)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = PosDeepGreen,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = operator.userId,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = PosDeepGreen
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = PosBorderNeutral)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = operator.operatorName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = PosCharcoalText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${Localization.get(Localization.Key.PHONE_NUMBER, language)}: ${operator.operatorPhone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = PosMutedText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Error Banner
                AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .semantics(mergeDescendants = true) {}
                            .testTag("recovery_mgmt_error_banner"),
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
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = PosDueRed
                            )
                        }
                    }
                }

                if (!hasGeneratedCode) {
                    // Management Overview & Explicit Regeneration Trigger
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
                                .padding(20.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = PosMintGreen,
                                border = BorderStroke(1.dp, PosBorderGreen)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = PosDeepGreen,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = Localization.get(Localization.Key.RECOVERY_MGMT_WHY_IMPORTANT, language),
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                        color = PosCharcoalText
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    onClearError()
                                    showRegenConfirmDialog = true
                                },
                                enabled = !isRegenerating && operator != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_regenerate_recovery_code"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PosDeepGreen,
                                    contentColor = Color.White,
                                    disabledContainerColor = PosDeepGreen.copy(alpha = 0.55f),
                                    disabledContentColor = Color.White
                                )
                            ) {
                                if (isRegenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .testTag("recovery_regen_loading"),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = Localization.get(Localization.Key.RECOVERY_REGENERATING, language),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = Localization.get(Localization.Key.BTN_REGENERATE_RECOVERY_CODE, language),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = onDismissBeforeRegeneration,
                                enabled = !isRegenerating,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_cancel_recovery_mgmt"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, PosBorderNeutral)
                            ) {
                                Text(
                                    text = Localization.get(Localization.Key.BTN_CLOSE, language),
                                    color = PosCharcoalText
                                )
                            }
                        }
                    }
                } else {
                    // Newly Regenerated One-Time Code Display + Mandatory Acknowledgement
                    val newCode = generatedRecoveryCode.orEmpty()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_regenerated_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = PosCleanWhite),
                        border = BorderStroke(1.dp, PosBorderGreen),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(60.dp),
                                shape = CircleShape,
                                color = PosLightGreen,
                                border = BorderStroke(1.5.dp, PosEmerald)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = Localization.get(Localization.Key.RECOVERY_REGEN_SUCCESS, language),
                                        tint = PosDeepGreen,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = Localization.get(Localization.Key.RECOVERY_REGEN_SUCCESS, language),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = PosDeepGreen,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

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
                                            text = newCode,
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 2.sp
                                            ),
                                            color = PosCharcoalText,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.testTag("managed_recovery_code_value")
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Recovery Code", newCode))
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
                                            .testTag("btn_copy_managed_recovery_code")
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
                                            modifier = Modifier.testTag(
                                                if (isCopied) "managed_recovery_copied_badge" else "managed_recovery_copy_label"
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // One-Time Warning Banner
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics(mergeDescendants = true) {},
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

                            // Mandatory Acknowledgement Checkbox Row
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
                                    .testTag("managed_recovery_ack_row"),
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
                                        modifier = Modifier.testTag("managed_recovery_ack_checkbox")
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = Localization.get(Localization.Key.RECOVERY_REGEN_ACK_CHECKBOX, language),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = PosCharcoalText
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    isAcknowledged = false
                                    isCopied = false
                                    onAcknowledgeAndClose()
                                },
                                enabled = isAcknowledged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_ack_close_recovery"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PosDeepGreen,
                                    contentColor = Color.White,
                                    disabledContainerColor = PosBorderNeutral,
                                    disabledContentColor = PosMutedText
                                )
                            ) {
                                Text(
                                    text = Localization.get(Localization.Key.BTN_DONE_CLEAR_CODE, language),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Explicit Confirmation Prompt Before Regenerating Recovery Code
    if (showRegenConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isRegenerating) {
                    showRegenConfirmDialog = false
                }
            },
            modifier = Modifier.testTag("recovery_regen_confirm_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFB45309)
                )
            },
            title = {
                Text(
                    text = Localization.get(Localization.Key.RECOVERY_REGEN_CONFIRM_TITLE, language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = Localization.get(Localization.Key.RECOVERY_REGEN_CONFIRM_MSG, language),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRegenConfirmDialog = false
                        onRequestRegenerate()
                    },
                    enabled = !isRegenerating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PosDeepGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_confirm_regenerate_recovery")
                ) {
                    Text(Localization.get(Localization.Key.BTN_CONFIRM_REGENERATE, language))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRegenConfirmDialog = false },
                    enabled = !isRegenerating,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_cancel_regenerate_recovery")
                ) {
                    Text(Localization.get(Localization.Key.CANCEL, language))
                }
            }
        )
    }
}

@Composable
fun LogoutConfirmationDialog(
    operatorName: String?,
    language: String,
    onDismiss: () -> Unit,
    onConfirmLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("logout_confirmation_dialog"),
        containerColor = PosCleanWhite,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                tint = PosDueRed
            )
        },
        title = {
            Text(
                text = Localization.get(Localization.Key.LOGOUT_CONFIRM_TITLE, language),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PosCharcoalText
            )
        },
        text = {
            Column {
                if (!operatorName.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PosMintGreen,
                        border = BorderStroke(1.dp, PosBorderGreen),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = PosDeepGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = operatorName,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = PosDeepGreen
                            )
                        }
                    }
                }
                Text(
                    text = Localization.get(Localization.Key.LOGOUT_CONFIRM_MSG, language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PosCharcoalText
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PosDueRed,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("btn_confirm_logout")
            ) {
                Text(Localization.get(Localization.Key.BTN_CONFIRM_LOGOUT, language))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("btn_cancel_logout")
            ) {
                Text(
                    text = Localization.get(Localization.Key.CANCEL, language),
                    color = PosCharcoalText
                )
            }
        }
    )
}
