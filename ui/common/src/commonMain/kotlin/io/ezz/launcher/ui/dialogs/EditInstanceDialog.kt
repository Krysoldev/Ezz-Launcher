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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzLoaderBadge
import io.ezz.launcher.ui.components.EzzSlider
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun EditInstanceDialog(
    instance: Instance,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val colors = EzzTheme.colors
    var name by remember { mutableStateOf(instance.name) }
    var maxRamMb by remember { mutableStateOf(instance.maxMemoryMb.toFloat()) }
    var javaPath by remember { mutableStateOf(instance.javaPath ?: "") }
    var customJvmArgs by remember { mutableStateOf(instance.customJvmArgs.joinToString(" ")) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Edit Instance Settings",
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            EzzLoaderBadge(loaderType = instance.loaderType)
                            Spacer(modifier = Modifier.width(6.dp))
                            EzzBadge(text = "MC ${instance.minecraftVersion}", variant = EzzBadgeVariant.NEUTRAL)
                        }
                    }

                    EzzIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        contentDescription = "Close",
                        tint = colors.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Instance Name
                EzzTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Instance Display Name",
                    placeholder = "Instance name...",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // RAM Slider
                EzzSlider(
                    value = maxRamMb,
                    onValueChange = { maxRamMb = it },
                    valueRange = 1024f..16384f,
                    steps = 15,
                    label = "Maximum Memory (RAM)",
                    valueDisplay = "${(maxRamMb / 1024).toInt()} GB"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Java Path
                EzzTextField(
                    value = javaPath,
                    onValueChange = { javaPath = it },
                    label = "Custom Java Binary Path (Optional)",
                    placeholder = "Default auto-detected Java",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Custom JVM Arguments
                EzzTextField(
                    value = customJvmArgs,
                    onValueChange = { customJvmArgs = it },
                    label = "Custom JVM Arguments (Optional)",
                    placeholder = "-XX:+UseG1GC -XX:MaxGCPauseMillis=50",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
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
                        text = "Save Changes",
                        onClick = {
                            val updated = instance.copy(
                                name = name.ifBlank { instance.name },
                                maxMemoryMb = maxRamMb.toInt(),
                                javaPath = javaPath.ifBlank { null },
                                customJvmArgs = if (customJvmArgs.isBlank()) emptyList() else customJvmArgs.split(" ")
                            )
                            viewModel.updateInstance(updated)
                            onDismiss()
                        },
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.MEDIUM
                    )
                }
            }
        }
    }
}
