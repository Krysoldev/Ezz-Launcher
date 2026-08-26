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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.core.auth.microsoft.MicrosoftLoginProgress
import io.ezz.launcher.ui.theme.EzzColors
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun AddOfflineAccountDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .background(EzzColors.Surface)
                .border(1.dp, EzzColors.Border, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Add Offline Account",
                    color = EzzColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Play offline without Microsoft authentication",
                    color = EzzColors.TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text("Minecraft Username", color = EzzColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    placeholder = { Text("e.g. Steve", color = EzzColors.TextMuted) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = EzzColors.SurfaceVariant,
                        unfocusedContainerColor = EzzColors.SurfaceVariant,
                        focusedTextColor = EzzColors.TextPrimary,
                        unfocusedTextColor = EzzColors.TextPrimary,
                        focusedIndicatorColor = EzzColors.Primary,
                        unfocusedIndicatorColor = EzzColors.Border
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorMessage!!, color = EzzColors.Danger, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = EzzColors.SurfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = EzzColors.TextPrimary)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            val trimmed = username.trim()
                            if (trimmed.length < 3) {
                                errorMessage = "Username must be at least 3 characters"
                            } else if (!trimmed.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                                errorMessage = "Username can only contain alphanumeric and underscores"
                            } else {
                                viewModel.addOfflineAccount(trimmed)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EzzColors.Primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add Account", color = Color(0xFF0B0F19), fontWeight = FontWeight.Bold)
                    }
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
    val progress by viewModel.microsoftLoginProgress.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .background(EzzColors.Surface)
                .border(1.dp, EzzColors.Border, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Microsoft Authentication",
                    color = EzzColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(20.dp))

                when (val p = progress) {
                    is MicrosoftLoginProgress.AwaitingUserAction -> {
                        Text(
                            text = "To log in, open the link below and enter this code:",
                            color = EzzColors.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Big Code Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(EzzColors.SurfaceVariant)
                                .border(2.dp, EzzColors.Primary, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p.userCode,
                                color = EzzColors.Primary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(p.userCode))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EzzColors.SurfaceLight),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Copy Code", color = EzzColors.TextPrimary)
                            }

                            Button(
                                onClick = {
                                    try {
                                        uriHandler.openUri(p.verificationUrl)
                                    } catch (e: Exception) {}
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EzzColors.Primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text("Open Link", color = Color(0xFF0B0F19), fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = EzzColors.Primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Waiting for authorization in browser...", color = EzzColors.TextMuted, fontSize = 12.sp)
                        }
                    }
                    is MicrosoftLoginProgress.Authenticating -> {
                        CircularProgressIndicator(color = EzzColors.Primary, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = p.step, color = EzzColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    is MicrosoftLoginProgress.Error -> {
                        Text(text = "Error: ${p.message}", color = EzzColors.Danger, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.startMicrosoftLogin() },
                            colors = ButtonDefaults.buttonColors(containerColor = EzzColors.Danger)
                        ) {
                            Text("Try Again", color = Color.White)
                        }
                    }
                    else -> {
                        CircularProgressIndicator(color = EzzColors.Primary, modifier = Modifier.size(36.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = EzzColors.SurfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = EzzColors.TextSecondary)
                }
            }
        }
    }
}
