package io.ezz.launcher.ui.instances

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.ui.theme.EzzColors
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun InstancesScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val instances by viewModel.instanceRepository.instances.collectAsState()
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredInstances = instances.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.minecraftVersion.contains(searchQuery, ignoreCase = true) ||
        it.loaderType.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EzzColors.Background)
            .padding(32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Instances",
                    color = EzzColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage your isolated Minecraft game installations (${instances.size} installed)",
                    color = EzzColors.TextSecondary,
                    fontSize = 14.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search instances...", color = EzzColors.TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EzzColors.TextSecondary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = EzzColors.Surface,
                        unfocusedContainerColor = EzzColors.Surface,
                        focusedTextColor = EzzColors.TextPrimary,
                        unfocusedTextColor = EzzColors.TextPrimary,
                        focusedIndicatorColor = EzzColors.Primary,
                        unfocusedIndicatorColor = EzzColors.Border
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.width(260.dp).height(48.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = { viewModel.showCreateInstanceDialog.value = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EzzColors.Primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF0B0F19))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Create Instance", color = Color(0xFF0B0F19), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Instances Grid
        if (filteredInstances.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(EzzColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No instances matched '$searchQuery'" else "No instances created yet",
                        color = EzzColors.TextSecondary,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredInstances, key = { it.id }) { instance ->
                    InstanceCard(
                        instance = instance,
                        isSelected = instance.id == selectedInstance?.id,
                        onSelect = { viewModel.selectInstance(instance) },
                        onPlay = {
                            viewModel.selectInstance(instance)
                            viewModel.launchInstance(instance)
                            viewModel.navigateTo(NavigationScreen.HOME)
                        },
                        onEdit = { viewModel.showEditInstanceDialog.value = instance },
                        onDuplicate = { viewModel.duplicateInstance(instance.id, "${instance.name} (Copy)") },
                        onOpenFolder = { viewModel.openInstanceFolder(instance.id) },
                        onDelete = { viewModel.deleteInstance(instance.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun InstanceCard(
    instance: Instance,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isSelected) EzzColors.Primary else EzzColors.Border

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EzzColors.Surface)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Loader Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (instance.loaderType) {
                                LoaderType.FABRIC -> Color(0xFF3B82F6)
                                LoaderType.OPTIFINE -> Color(0xFFEC4899)
                                LoaderType.VANILLA -> EzzColors.Accent
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (instance.loaderType) {
                            LoaderType.FABRIC -> "Fabric"
                            LoaderType.OPTIFINE -> "OptiFine"
                            LoaderType.VANILLA -> "Vanilla"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Version string
                Text(
                    text = "v${instance.minecraftVersion}",
                    color = EzzColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = instance.name,
                color = EzzColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "RAM: ${instance.maxMemoryMb} MB",
                color = EzzColors.TextMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = EzzColors.Accent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF0B0F19), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", color = Color(0xFF0B0F19), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Row {
                    IconButton(onClick = onOpenFolder, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Open Folder", tint = EzzColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = EzzColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = EzzColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = EzzColors.Danger, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
