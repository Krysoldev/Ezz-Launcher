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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.core.auth.microsoft.MicrosoftLoginProgress
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
    var username by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0A0A0A))
                .border(1.dp, Color(0xFF282828), RoundedCornerShape(8.dp))
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Add Offline Account",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.3.sp
                        )
                        Text(
                            text = "Play offline without Microsoft authentication",
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }

                    EzzIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        size = EzzButtonSize.SMALL,
                        variant = EzzButtonVariant.GHOST
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

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

                Spacer(modifier = Modifier.height(20.dp))

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

                    Spacer(modifier = Modifier.width(10.dp))

                    EzzButton(
                        text = "Create Profile",
                        onClick = {
                            val trimmed = username.trim()
                            if (trimmed.length < 3) {
                                errorMessage = "Username must be at least 3 characters"
                            } else if (!trimmed.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                                errorMessage = "Username can only contain alphanumeric characters and underscores"
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
    val loginProgress by viewModel.microsoftLoginProgress.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0A0A0A))
                .border(1.dp, Color(0xFF282828), RoundedCornerShape(8.dp))
                .padding(22.dp)
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
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Microsoft Authentication",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    EzzIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        size = EzzButtonSize.SMALL,
                        variant = EzzButtonVariant.GHOST
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                when (val progress = loginProgress) {
                    is MicrosoftLoginProgress.Authenticating -> {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = progress.step,
                            color = Color(0xFFA0A0A0),
                            fontSize = 13.sp
                        )
                    }

                    is MicrosoftLoginProgress.AwaitingUserAction -> {
                        Text(
                            text = "Open the authentication URL and enter this verification code:",
                            color = Color(0xFFA0A0A0),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // User Code Card
                        EzzCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color(0xFF141414),
                            borderColor = Color(0xFF383838)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = progress.userCode,
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 4.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        text = "Open Browser",
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

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Waiting for authorization in browser...",
                                color = Color(0xFF777777),
                                fontSize = 11.sp
                            )
                        }
                    }

                    is MicrosoftLoginProgress.Success -> {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(42.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Authenticated as ${progress.account.username}",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(14.dp))
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
                            color = Color(0xFFEF4444),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = progress.message,
                            color = Color(0xFFA0A0A0),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        EzzButton(
                            text = "Retry",
                            onClick = { viewModel.startMicrosoftLogin() },
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.MEDIUM
                        )
                    }

                    null -> {}
                }
            }
        }
    }
}
