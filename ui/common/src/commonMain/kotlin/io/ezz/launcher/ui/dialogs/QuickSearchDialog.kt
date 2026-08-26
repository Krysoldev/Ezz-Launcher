package io.ezz.launcher.ui.dialogs

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzSearchField
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun QuickSearchDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val colors = EzzTheme.colors
    val instances by viewModel.instanceRepository.instances.collectAsState()
    val mods by viewModel.installedMods.collectAsState()
    var query by remember { mutableStateOf("") }

    val filteredInstances = remember(query, instances) {
        if (query.isBlank()) instances else instances.filter {
            it.name.contains(query, ignoreCase = true) || it.minecraftVersion.contains(query, ignoreCase = true)
        }
    }

    val filteredMods = remember(query, mods) {
        if (query.isBlank()) emptyList() else mods.filter {
            it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, colors.borderLight, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header search
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    EzzSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search instances, versions, mods...",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    EzzIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Instances section
                    item {
                        Text(
                            text = "INSTANCES (${filteredInstances.size})",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    items(filteredInstances) { instance ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceVariant)
                                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.selectInstance(instance)
                                    viewModel.navigateTo(NavigationScreen.HOME)
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = instance.name,
                                        color = colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Minecraft ${instance.minecraftVersion} • ${instance.loaderType.name}",
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Select",
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Mods section (if query active)
                    if (filteredMods.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "MODS (${filteredMods.size})",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        items(filteredMods) { mod ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceVariant)
                                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.navigateTo(NavigationScreen.MODS)
                                        onDismiss()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = mod.name,
                                        color = colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "v${mod.version} • ${mod.id}",
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
