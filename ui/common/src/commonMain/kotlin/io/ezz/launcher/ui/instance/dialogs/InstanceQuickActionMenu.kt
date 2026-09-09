package io.ezz.launcher.ui.instance.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance

@Composable
fun InstanceQuickActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    instance: Instance,
    isRunning: Boolean,
    onLaunchOrStop: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenFolder: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onRepair: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(Color(0xFF0C0E14))
            .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(12.dp))
    ) {
        // 1. Launch / Stop
        QuickMenuItem(
            icon = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            label = if (isRunning) "Stop Process" else "Launch Game",
            tint = if (isRunning) Color(0xFFEF4444) else Color(0xFF10B981),
            onClick = {
                onDismissRequest()
                onLaunchOrStop()
            }
        )

        // 2. Open Workspace
        QuickMenuItem(
            icon = Icons.Default.Settings,
            label = "Open Workspace",
            tint = Color(0xFF8B5CF6),
            onClick = {
                onDismissRequest()
                onOpenWorkspace()
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = Color(0xFF1B1F2C)
        )

        // 3. Open Folder
        QuickMenuItem(
            icon = Icons.Default.FolderOpen,
            label = "Open Instance Folder",
            tint = Color(0xFF94A3B8),
            onClick = {
                onDismissRequest()
                onOpenFolder()
            }
        )

        // 4. Duplicate
        QuickMenuItem(
            icon = Icons.Default.ContentCopy,
            label = "Duplicate Instance",
            tint = Color(0xFF94A3B8),
            onClick = {
                onDismissRequest()
                onDuplicate()
            }
        )

        // 5. Export
        QuickMenuItem(
            icon = Icons.Default.FileDownload,
            label = "Export as Modpack",
            tint = Color(0xFF94A3B8),
            onClick = {
                onDismissRequest()
                onExport()
            }
        )

        // 6. Rename
        QuickMenuItem(
            icon = Icons.Default.DriveFileRenameOutline,
            label = "Rename Instance",
            tint = Color(0xFF94A3B8),
            onClick = {
                onDismissRequest()
                onRename()
            }
        )

        // 7. Repair
        QuickMenuItem(
            icon = Icons.Default.Build,
            label = "Verify & Repair Files",
            tint = Color(0xFF94A3B8),
            onClick = {
                onDismissRequest()
                onRepair()
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = Color(0xFF1B1F2C)
        )

        // 8. Delete
        QuickMenuItem(
            icon = Icons.Default.Delete,
            label = "Delete Instance",
            tint = Color(0xFFEF4444),
            onClick = {
                onDismissRequest()
                onDelete()
            }
        )
    }
}

@Composable
private fun QuickMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    color = if (tint == Color(0xFFEF4444)) Color(0xFFEF4444) else Color(0xFFE2E8F0),
                    fontSize = 13.sp
                )
            }
        },
        onClick = onClick,
        colors = MenuDefaults.itemColors()
    )
}
