package io.ezz.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import io.ezz.launcher.ui.theme.EzzColors
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsRepository.settings.collectAsState()
    val detectedJava by viewModel.detectedJavaRuntimes.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var maxRamMb by remember(settings.defaultMaxMemoryMb) { mutableStateOf(settings.defaultMaxMemoryMb.toFloat()) }
    var minRamMb by remember(settings.defaultMinMemoryMb) { mutableStateOf(settings.defaultMinMemoryMb.toFloat()) }
    var customJvmArgs by remember(settings.globalJvmArgs) { mutableStateOf(settings.globalJvmArgs.joinToString(" ")) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EzzColors.Background)
            .padding(32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Launcher Settings",
            color = EzzColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Configure global memory, Java runtimes, and launch behavior",
            color = EzzColors.TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Supabase Database Card
        val isSupabaseConnected by viewModel.isSupabaseConnected.collectAsState()
        SettingsCard(title = "Supabase PostgreSQL Database") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Authoritative Cloud Database",
                            color = EzzColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "https://idywzmspumhahzzfsdjx.supabase.co",
                            color = EzzColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (isSupabaseConnected) {
                                    true -> Color(0xFF10B981).copy(alpha = 0.2f)
                                    false -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                    null -> EzzColors.SurfaceVariant
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = when (isSupabaseConnected) {
                                true -> "Connected"
                                false -> "Disconnected"
                                null -> "Checking..."
                            },
                            color = when (isSupabaseConnected) {
                                true -> Color(0xFF10B981)
                                false -> Color(0xFFEF4444)
                                null -> EzzColors.TextSecondary
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // RAM Allocation Card
        SettingsCard(title = "Default Memory Allocation") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Maximum RAM (Heap -Xmx)", color = EzzColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${maxRamMb.toInt()} MB (${maxRamMb.toInt() / 1024} GB)", color = EzzColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = maxRamMb,
                    onValueChange = { maxRamMb = it },
                    onValueChangeFinished = {
                        coroutineScope.launch {
                            viewModel.settingsRepository.updateSettings { s -> s.copy(defaultMaxMemoryMb = maxRamMb.toInt()) }
                        }
                    },
                    valueRange = 1024f..16384f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = EzzColors.Primary,
                        activeTrackColor = EzzColors.Primary,
                        inactiveTrackColor = EzzColors.SurfaceLight
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Minimum RAM (Heap -Xms)", color = EzzColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${minRamMb.toInt()} MB", color = EzzColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = minRamMb,
                    onValueChange = { minRamMb = it },
                    onValueChangeFinished = {
                        coroutineScope.launch {
                            viewModel.settingsRepository.updateSettings { s -> s.copy(defaultMinMemoryMb = minRamMb.toInt()) }
                        }
                    },
                    valueRange = 512f..8192f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = EzzColors.Secondary,
                        activeTrackColor = EzzColors.Secondary,
                        inactiveTrackColor = EzzColors.SurfaceLight
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Java Runtimes Card
        SettingsCard(title = "Installed Java Runtimes") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detected ${detectedJava.size} Java Virtual Machines on system",
                        color = EzzColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = { viewModel.refreshJavaRuntimes() },
                        colors = ButtonDefaults.buttonColors(containerColor = EzzColors.SurfaceVariant),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = EzzColors.Primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rescan", color = EzzColors.TextPrimary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                for (runtime in detectedJava) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EzzColors.SurfaceLight)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Java ${runtime.majorVersion} (${runtime.vendor})",
                                    color = EzzColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = runtime.path,
                                    color = EzzColors.TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EzzColors.SurfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (runtime.is64Bit) "64-bit" else "32-bit",
                                    color = EzzColors.Primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Global JVM Arguments Card
        SettingsCard(title = "Global JVM Arguments") {
            Column {
                OutlinedTextField(
                    value = customJvmArgs,
                    onValueChange = {
                        customJvmArgs = it
                        val argsList = it.split("\\s+".toRegex()).filter { arg -> arg.isNotBlank() }
                        coroutineScope.launch {
                            viewModel.settingsRepository.updateSettings { s -> s.copy(globalJvmArgs = argsList) }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = EzzColors.SurfaceLight,
                        unfocusedContainerColor = EzzColors.SurfaceLight,
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
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EzzColors.Surface)
            .border(1.dp, EzzColors.Border, RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        Column {
            Text(
                text = title,
                color = EzzColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}
