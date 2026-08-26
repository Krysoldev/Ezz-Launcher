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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
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
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF282828), RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
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
                        onClick = onDismiss,
                        size = EzzButtonSize.SMALL,
                        variant = EzzButtonVariant.GHOST
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filteredInstances.isNotEmpty()) {
                        item {
                            Text(
                                text = "INSTANCES",
                                color = Color(0xFF777777),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(filteredInstances) { inst ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF141414))
                                    .clickable {
                                        viewModel.selectInstance(inst)
                                        viewModel.navigateTo(NavigationScreen.HOME)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(inst.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Minecraft ${inst.minecraftVersion} • ${inst.loaderType.name}", color = Color(0xFF888888), fontSize = 11.sp)
                                    }
                                }

                                EzzButton(
                                    text = "Play",
                                    icon = Icons.Default.PlayArrow,
                                    onClick = {
                                        viewModel.selectInstance(inst)
                                        viewModel.launchInstance(inst)
                                        viewModel.navigateTo(NavigationScreen.HOME)
                                        onDismiss()
                                    },
                                    variant = EzzButtonVariant.PRIMARY,
                                    size = EzzButtonSize.SMALL
                                )
                            }
                        }
                    }

                    if (filteredMods.isNotEmpty()) {
                        item {
                            Text(
                                text = "MODS",
                                color = Color(0xFF777777),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }

                        items(filteredMods) { mod ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF141414))
                                    .clickable {
                                        viewModel.navigateTo(NavigationScreen.MODS)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = Color(0xFFA0A0A0),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(mod.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(mod.fileName, color = Color(0xFF777777), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
