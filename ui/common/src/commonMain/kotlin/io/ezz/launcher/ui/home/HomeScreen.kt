package io.ezz.launcher.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.runtime.ProcessState
import io.ezz.launcher.ui.theme.EzzColors
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()
    val processState by viewModel.processState.collectAsState()
    val instances by viewModel.instanceRepository.instances.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EzzColors.Background)
            .padding(32.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Welcome Back!",
                    color = EzzColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedInstance != null) "Ready to play Minecraft" else "Create or select an instance to get started",
                    color = EzzColors.TextSecondary,
                    fontSize = 14.sp
                )
            }

            // Quick Instance Switcher dropdown / Create button
            Button(
                onClick = { viewModel.showCreateInstanceDialog.value = true },
                colors = ButtonDefaults.buttonColors(containerColor = EzzColors.SurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = EzzColors.Primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "New Instance", color = EzzColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        // Hero Instance Card
        if (selectedInstance != null) {
            val inst = selectedInstance!!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(EzzColors.Surface, EzzColors.SurfaceVariant)
                        )
                    )
                    .border(1.dp, EzzColors.Border, RoundedCornerShape(20.dp))
                    .padding(32.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            // Loader & Version Badges
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when (inst.loaderType) {
                                                LoaderType.FABRIC -> Color(0xFF3B82F6)
                                                LoaderType.OPTIFINE -> Color(0xFFEC4899)
                                                LoaderType.VANILLA -> EzzColors.Accent
                                            }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when (inst.loaderType) {
                                            LoaderType.FABRIC -> "Fabric ${inst.loaderVersion ?: ""}"
                                            LoaderType.OPTIFINE -> "OptiFine ${inst.loaderVersion ?: ""}"
                                            LoaderType.VANILLA -> "Vanilla"
                                        },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(EzzColors.SurfaceLight)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "v${inst.minecraftVersion}",
                                        color = EzzColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = inst.name,
                                color = EzzColors.TextPrimary,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        // Open Folder Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(EzzColors.SurfaceLight)
                                .clickable { viewModel.openInstanceFolder(inst.id) }
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Open Directory",
                                tint = EzzColors.TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Instance Specs Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        SpecItem(
                            icon = Icons.Default.Memory,
                            label = "RAM Allocated",
                            value = "${inst.maxMemoryMb} MB"
                        )
                        SpecItem(
                            icon = Icons.Default.Speed,
                            label = "Total Playtime",
                            value = formatPlaytime(inst.totalPlayTimeSeconds)
                        )
                    }
                }
            }
        } else {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(EzzColors.Surface)
                    .border(1.dp, EzzColors.Border, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Minecraft Instances Found",
                        color = EzzColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create your first instance to start playing",
                        color = EzzColors.TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.showCreateInstanceDialog.value = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EzzColors.Primary)
                    ) {
                        Text("Create Instance", color = Color(0xFF0B0F19), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Bottom Launch Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EzzColors.Surface)
                .border(1.dp, EzzColors.Border, RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Account info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(EzzColors.SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedAccount?.username?.take(1)?.uppercase() ?: "?",
                        color = EzzColors.Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = selectedAccount?.username ?: "No Account Selected",
                        color = EzzColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (selectedAccount != null) "Ready to Launch" else "Please add an account in Accounts tab",
                        color = if (selectedAccount != null) EzzColors.Accent else EzzColors.Warning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Big Play Button / Status
            when (processState) {
                is ProcessState.Idle -> {
                    Button(
                        onClick = { viewModel.launchInstance() },
                        enabled = selectedInstance != null && selectedAccount != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EzzColors.Accent,
                            disabledContainerColor = EzzColors.SurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(52.dp).width(200.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF0B0F19))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PLAY",
                            color = Color(0xFF0B0F19),
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                is ProcessState.Preparing -> {
                    val prep = processState as ProcessState.Preparing
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = EzzColors.Primary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Preparing Launch...",
                                color = EzzColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = prep.stage,
                                color = EzzColors.Primary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                is ProcessState.Running -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(EzzColors.AccentGlow)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(EzzColors.Accent)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GAME RUNNING",
                            color = EzzColors.Accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
                is ProcessState.Exited -> {
                    Button(
                        onClick = { viewModel.launchInstance() },
                        colors = ButtonDefaults.buttonColors(containerColor = EzzColors.Accent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(52.dp).width(200.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF0B0F19))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "PLAY AGAIN", color = Color(0xFF0B0F19), fontWeight = FontWeight.Bold)
                    }
                }
                is ProcessState.Failed -> {
                    val fail = processState as ProcessState.Failed
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Launch Failed",
                                color = EzzColors.Danger,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = fail.error.message.take(45),
                                color = EzzColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.navigateTo(NavigationScreen.CONSOLE) },
                            colors = ButtonDefaults.buttonColors(containerColor = EzzColors.SurfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Logs", color = EzzColors.TextPrimary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.launchInstance() },
                            colors = ButtonDefaults.buttonColors(containerColor = EzzColors.Danger),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Retry", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EzzColors.SurfaceLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = EzzColors.Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = label, color = EzzColors.TextSecondary, fontSize = 11.sp)
            Text(text = value, color = EzzColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatPlaytime(seconds: Long): String {
    if (seconds <= 0) return "Never played"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
