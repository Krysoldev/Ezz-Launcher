package io.ezz.launcher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.core.auth.microsoft.MicrosoftLoginProgress
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzCard
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.components.ToastManager
import io.ezz.launcher.ui.components.ToastType
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun AddOfflineAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = EzzTheme.colors
    var username by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Add Offline Profile",
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Play offline without Microsoft authentication",
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }

                    EzzIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        contentDescription = "Close",
                        tint = colors.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                EzzTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = "Minecraft Username",
                    placeholder = "e.g. Steve",
                    error = errorMessage,
                    leadingIcon = Icons.Default.Person,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    EzzButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = EzzButtonVariant.GHOST,
                        size = EzzButtonSize.MEDIUM
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    EzzButton(
                        text = "Add Profile",
                        onClick = {
                            val trimmed = username.trim()
                            if (trimmed.length < 3) {
                                errorMessage = "Username must be at least 3 characters"
                            } else if (!trimmed.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                                errorMessage = "Username can only contain alphanumeric and underscores"
                            } else {
                                onConfirm(trimmed)
                            }
                        },
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.MEDIUM
                    )
                }
            }
        }
    }
}

@Composable
fun MicrosoftLoginDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val colors = EzzTheme.colors
    val loginProgress by viewModel.microsoftLoginProgress.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Microsoft Authentication",
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    EzzIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        contentDescription = "Close",
                        tint = colors.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                when (val progress = loginProgress) {
                    is MicrosoftLoginProgress.Authenticating -> {
                        CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = progress.step,
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }

                    is MicrosoftLoginProgress.AwaitingUserAction -> {
                        Text(
                            text = "To sign in, open the link below and enter this security code:",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // User Code Card
                        EzzCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = colors.surfaceVariant,
                            borderColor = colors.primary
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = progress.userCode,
                                    color = colors.primary,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 4.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    EzzButton(
                                        text = "Copy Code",
                                        onClick = {
                                            val selection = StringSelection(progress.userCode)
                                            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                            ToastManager.show("Code Copied", "Device code copied to clipboard", ToastType.SUCCESS)
                                        },
                                        variant = EzzButtonVariant.SECONDARY,
                                        size = EzzButtonSize.SMALL,
                                        icon = Icons.Default.ContentCopy
                                    )

                                    EzzButton(
                                        text = "Open Link",
                                        onClick = {
                                            viewModel.platformBridge.openUrl(progress.verificationUrl)
                                        },
                                        variant = EzzButtonVariant.PRIMARY,
                                        size = EzzButtonSize.SMALL,
                                        icon = Icons.Default.OpenInBrowser
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Waiting for authorization in browser...",
                                color = colors.textMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    is MicrosoftLoginProgress.Success -> {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Signed in as ${progress.account.username}",
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        EzzButton(
                            text = "Done",
                            onClick = onDismiss,
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.MEDIUM
                        )
                    }

                    is MicrosoftLoginProgress.Error -> {
                        Text(
                            text = "Authentication Failed",
                            color = colors.danger,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = progress.message,
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        EzzButton(
                            text = "Retry",
                            onClick = { viewModel.startMicrosoftLogin() },
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.MEDIUM
                        )
                    }

                    null -> {
                        // Idle / Initializing
                    }
                }
            }
        }
    }
}
