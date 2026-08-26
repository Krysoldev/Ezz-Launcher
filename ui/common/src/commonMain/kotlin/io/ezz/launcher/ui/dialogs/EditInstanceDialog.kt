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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.ui.theme.EzzColors
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun EditInstanceDialog(
    instance: Instance,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(instance.name) }
    var maxRamMb by remember { mutableStateOf(instance.maxMemoryMb.toFloat()) }
    var javaPath by remember { mutableStateOf(instance.javaPath ?: "") }
    var customJvmArgs by remember { mutableStateOf(instance.customJvmArgs.joinToString(" ")) }
    var windowWidth by remember { mutableStateOf(instance.windowWidth.toString()) }
    var windowHeight by remember { mutableStateOf(instance.windowHeight.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .background(EzzColors.Surface)
                .border(1.dp, EzzColors.Border, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Edit Instance",
                    color = EzzColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure memory, Java runtime, and JVM arguments",
                    color = EzzColors.TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Name
                Text("Instance Name", color = EzzColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
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

                Spacer(modifier = Modifier.height(16.dp))

                // RAM Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Allocated RAM", color = EzzColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${maxRamMb.toInt()} MB", color = EzzColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = maxRamMb,
                    onValueChange = { maxRamMb = it },
                    valueRange = 1024f..16384f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = EzzColors.Primary,
                        activeTrackColor = EzzColors.Primary,
                        inactiveTrackColor = EzzColors.SurfaceLight
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Java Executable Path Override
                Text("Java Executable Path (Leave blank to Auto-Detect)", color = EzzColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = javaPath,
                    onValueChange = { javaPath = it },
                    placeholder = { Text("e.g. C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.11\\bin\\java.exe", color = EzzColors.TextMuted, fontSize = 12.sp) },
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

                Spacer(modifier = Modifier.height(16.dp))

                // Custom JVM Args
                Text("Custom JVM Arguments", color = EzzColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = customJvmArgs,
                    onValueChange = { customJvmArgs = it },
                    placeholder = { Text("e.g. -XX:+UseG1GC", color = EzzColors.TextMuted) },
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

                Spacer(modifier = Modifier.height(16.dp))

                // Resolution
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Window Width", color = EzzColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = windowWidth,
                            onValueChange = { windowWidth = it },
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
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Window Height", color = EzzColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = windowHeight,
                            onValueChange = { windowHeight = it },
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
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
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
                            val args = customJvmArgs.split("\\s+".toRegex()).filter { it.isNotBlank() }
                            val updated = instance.copy(
                                name = name.ifBlank { instance.name },
                                maxMemoryMb = maxRamMb.toInt(),
                                javaPath = javaPath.trim().ifBlank { null },
                                customJvmArgs = args,
                                windowWidth = windowWidth.toIntOrNull() ?: instance.windowWidth,
                                windowHeight = windowHeight.toIntOrNull() ?: instance.windowHeight
                            )
                            viewModel.updateInstance(updated)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EzzColors.Primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Changes", color = Color(0xFF0B0F19), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
