package io.ezz.launcher.ui.profiles

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terrain
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzCard
import io.ezz.launcher.ui.components.ToastManager
import io.ezz.launcher.ui.components.ToastType
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

data class LauncherPreset(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val recommendedRamMb: Int,
    val targetLoader: String,
    val jvmArgs: List<String>,
    val tags: List<String>
)

@Composable
fun ProfilesScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    val selectedInstance by viewModel.selectedInstance.collectAsState()

    val presets = remember {
        listOf(
            LauncherPreset(
                id = "pvp",
                name = "PvP & Competitive",
                description = "Optimized for maximum framerates, reduced garbage collection stutters, and low input latency.",
                icon = Icons.Default.SportsEsports,
                recommendedRamMb = 4096,
                targetLoader = "Fabric / Vanilla",
                jvmArgs = listOf("-XX:+UseG1GC", "-XX:G1ReservePercent=20", "-XX:MaxGCPauseMillis=50"),
                tags = listOf("High FPS", "Low Latency", "4 GB RAM")
            ),
            LauncherPreset(
                id = "smp_modded",
                name = "SMP & Modded",
                description = "Configured with high heap allocation and multi-threaded background chunk loading for modded multiplayer.",
                icon = Icons.Default.Terrain,
                recommendedRamMb = 8192,
                targetLoader = "Fabric",
                jvmArgs = listOf("-XX:+UseG1GC", "-XX:+UnlockExperimentalVMOptions", "-XX:G1NewSizePercent=20"),
                tags = listOf("8 GB RAM", "Multi-Mod", "Multiplayer")
            ),
            LauncherPreset(
                id = "vanilla_pure",
                name = "Vanilla Pure",
                description = "Balanced, standard memory footprint for clean single-player survival with low battery/CPU consumption.",
                icon = Icons.Default.Speed,
                recommendedRamMb = 2048,
                targetLoader = "Vanilla",
                jvmArgs = emptyList(),
                tags = listOf("2 GB RAM", "Lightweight", "Battery Safe")
            ),
            LauncherPreset(
                id = "shaders_cinematic",
                name = "Shaders & Visuals",
                description = "High texture and geometry memory allocation designed for high-resolution resource packs and raytracing shaders.",
                icon = Icons.Default.Palette,
                recommendedRamMb = 10240,
                targetLoader = "OptiFine / Fabric Iris",
                jvmArgs = listOf("-XX:+UseG1GC", "-XX:G1HeapRegionSize=32M"),
                tags = listOf("10 GB RAM", "HD Shaders", "Raytracing")
            )
        )
    }

    var activePresetId by remember { mutableStateOf("pvp") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(32.dp)
    ) {
        // Header
        Column {
            Text(
                text = "Performance Profiles",
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Apply tuned memory, JVM garbage collection, and runtime configurations to your instances",
                color = colors.textSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Preset Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(presets, key = { it.id }) { preset ->
                val isActive = activePresetId == preset.id
                PresetCard(
                    preset = preset,
                    isActive = isActive,
                    onApply = {
                        activePresetId = preset.id
                        selectedInstance?.let { inst ->
                            viewModel.updateInstance(
                                inst.copy(
                                    maxMemoryMb = preset.recommendedRamMb,
                                    customJvmArgs = preset.jvmArgs
                                )
                            )
                            ToastManager.show(
                                title = "Applied '${preset.name}'",
                                description = "Configured ${preset.recommendedRamMb / 1024} GB RAM on ${inst.name}",
                                type = ToastType.SUCCESS
                            )
                        } ?: run {
                            ToastManager.show(
                                title = "Profile '${preset.name}' Selected",
                                description = "Will be used as default for newly created instances",
                                type = ToastType.INFO
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: LauncherPreset,
    isActive: Boolean,
    onApply: () -> Unit
) {
    val colors = EzzTheme.colors

    EzzCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isActive) colors.primary else colors.border,
        backgroundColor = if (isActive) colors.surfaceVariant else colors.cardBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.primaryGlow)
                            .border(1.dp, colors.primary.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = preset.icon,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (isActive) {
                        EzzBadge(
                            text = "ACTIVE PRESET",
                            variant = EzzBadgeVariant.SUCCESS
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = preset.name,
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = preset.description,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Tags
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    preset.tags.forEach { tag ->
                        EzzBadge(text = tag, variant = EzzBadgeVariant.NEUTRAL)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            EzzButton(
                text = if (isActive) "Applied" else "Apply to Current Instance",
                onClick = onApply,
                variant = if (isActive) EzzButtonVariant.SECONDARY else EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.MEDIUM,
                icon = if (isActive) Icons.Default.Check else null,
                fullWidth = true
            )
        }
    }
}
