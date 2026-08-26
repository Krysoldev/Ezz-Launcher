package io.ezz.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.runtime.ProcessState
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun BottomPlayBar(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()
    val processState by viewModel.processState.collectAsState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Live Progress Bar (if preparing or launching)
            when (val state = processState) {
                is ProcessState.Preparing -> {
                    val p = state.progress
                    if (p != null) {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = colors.primary,
                            trackColor = colors.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = colors.primary,
                            trackColor = colors.surfaceVariant
                        )
                    }
                }
                else -> Unit
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Selected Instance & Meta
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.navigateTo(NavigationScreen.INSTANCES) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedInstance?.name ?: "No Instance Selected",
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedInstance != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                EzzLoaderBadge(loaderType = selectedInstance!!.loaderType)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "Minecraft ${selectedInstance?.minecraftVersion ?: "—"}",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "•",
                                color = colors.textMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${(selectedInstance?.maxMemoryMb ?: 4096) / 1024} GB RAM",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Center: Account Selector & Console Shortcut
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Account Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                            .clickable { viewModel.navigateTo(NavigationScreen.ACCOUNTS) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colors.primaryGlow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = selectedAccount?.username ?: "Guest Player",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (selectedAccount?.type == AccountType.MICROSOFT) "Microsoft" else "Offline",
                                color = if (selectedAccount?.type == AccountType.MICROSOFT) colors.accent else colors.textMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Console Shortcut Button
                    EzzIconButton(
                        icon = Icons.Default.Terminal,
                        onClick = { viewModel.navigateTo(NavigationScreen.CONSOLE) },
                        contentDescription = "Console Logs",
                        tint = colors.textSecondary,
                        backgroundColor = colors.surfaceVariant
                    )
                }

                // Right: Major Play Action Button
                Box(
                    modifier = Modifier.width(220.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    when (val state = processState) {
                        is ProcessState.Running -> {
                            EzzButton(
                                text = "STOP (${state.processId ?: "ACTIVE"})",
                                onClick = { viewModel.stopInstance() },
                                variant = EzzButtonVariant.DANGER,
                                size = EzzButtonSize.LARGE,
                                icon = Icons.Default.Stop,
                                fullWidth = true
                            )
                        }
                        is ProcessState.Preparing -> {
                            val p = state.progress
                            val progressText = if (p != null) " (${(p * 100).toInt()}%)" else ""
                            EzzButton(
                                text = "${state.stage}$progressText",
                                onClick = {},
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.LARGE,
                                isLoading = true,
                                enabled = false,
                                fullWidth = true
                            )
                        }
                        is ProcessState.Failed -> {
                            EzzButton(
                                text = "RETRY PLAY",
                                onClick = { viewModel.launchInstance() },
                                variant = EzzButtonVariant.DANGER,
                                size = EzzButtonSize.LARGE,
                                icon = Icons.Default.PlayArrow,
                                fullWidth = true
                            )
                        }
                        is ProcessState.Exited -> {
                            EzzButton(
                                text = "PLAY AGAIN",
                                onClick = { viewModel.launchInstance() },
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.LARGE,
                                icon = Icons.Default.PlayArrow,
                                enabled = selectedInstance != null,
                                fullWidth = true
                            )
                        }
                        ProcessState.Idle -> {
                            EzzButton(
                                text = "PLAY",
                                onClick = { viewModel.launchInstance() },
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.LARGE,
                                icon = Icons.Default.PlayArrow,
                                enabled = selectedInstance != null,
                                fullWidth = true
                            )
                        }
                    }
                }
            }
        }
    }
}
