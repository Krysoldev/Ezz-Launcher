package io.ezz.launcher.ui.manager.dialogs

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.LocalWorld
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorldBackupRestoreDialog(
    world: LocalWorld,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val backups by viewModel.worldBackupsList.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.History, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(
                    text = "Backups for '${world.name}'",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${backups.size} backup archive(s)", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    EzzButton(
                        text = "Create Backup Now",
                        onClick = { viewModel.backupWorld(world.folderName) },
                        icon = Icons.Default.Archive,
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                }

                if (backups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141720))
                            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No previous backups found for this world", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(backups, key = { it.filePath }) { bkp ->
                            val dateStr = SimpleDateFormat("MMM d, yyyy • HH:mm:ss", Locale.getDefault()).format(Date(bkp.createdAt))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF141720))
                                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(text = dateStr, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text(text = "${bkp.sizeBytes / 1024 / 1024} MB • ${bkp.fileName}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }

                                    EzzButton(
                                        text = "Restore",
                                        onClick = { viewModel.restoreWorldBackup(bkp.filePath, world.folderName) },
                                        icon = Icons.Default.Restore,
                                        variant = EzzButtonVariant.PRIMARY,
                                        size = EzzButtonSize.SMALL
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            EzzButton(
                text = "Close",
                onClick = onDismiss,
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.SMALL
            )
        },
        containerColor = Color(0xFF101318),
        shape = RoundedCornerShape(12.dp)
    )
}
