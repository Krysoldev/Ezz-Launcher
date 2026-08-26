package io.ezz.launcher.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.LaunchErrorData

@Composable
fun LaunchErrorDialog(
    data: LaunchErrorData?,
    onDismiss: () -> Unit,
    onViewLogs: () -> Unit,
    onRepair: () -> Unit,
    onCopyDiagnostics: (String) -> Unit
) {
    if (data == null) return
    val colors = EzzTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, colors.danger.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.danger.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = colors.danger,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Minecraft Launch Issue",
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Instance: ${data.instanceName} • Minecraft ${data.minecraftVersion}",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error Summary Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceVariant)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "ERROR SUMMARY",
                            color = colors.danger,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = data.errorSummary,
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Java Runtime: ${data.javaVersion}",
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                if (data.details != null && data.details.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.background)
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = data.details,
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EzzButton(
                            text = "Copy Diagnostics",
                            icon = Icons.Default.ContentCopy,
                            onClick = {
                                val diag = "Instance: ${data.instanceName}\nMC: ${data.minecraftVersion}\nJava: ${data.javaVersion}\nError: ${data.errorSummary}\nDetails: ${data.details ?: "None"}"
                                onCopyDiagnostics(diag)
                            },
                            variant = EzzButtonVariant.OUTLINE,
                            size = EzzButtonSize.SMALL
                        )
                        EzzButton(
                            text = "View Logs",
                            icon = Icons.Default.Terminal,
                            onClick = onViewLogs,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EzzButton(
                            text = "Repair Files",
                            icon = Icons.Default.Build,
                            onClick = onRepair,
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.SMALL
                        )
                        EzzButton(
                            text = "Dismiss",
                            onClick = onDismiss,
                            variant = EzzButtonVariant.GHOST,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }
            }
        }
    }
}
