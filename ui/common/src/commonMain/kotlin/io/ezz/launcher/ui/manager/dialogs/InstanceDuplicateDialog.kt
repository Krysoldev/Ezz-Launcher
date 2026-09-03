package io.ezz.launcher.ui.manager.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun InstanceDuplicateDialog(
    sourceInstance: Instance,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("${sourceInstance.name} (Copy)") }
    var includeWorlds by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Duplicate Instance",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Create a complete copy of '${sourceInstance.name}' with all mods, resource packs, shaders, and configs.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.5.sp
                )

                EzzTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = "Enter clone instance name",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeWorlds = !includeWorlds },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = includeWorlds,
                        onCheckedChange = { includeWorlds = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.White,
                            checkmarkColor = Color(0xFF07080A),
                            uncheckedColor = Color(0xFF64748B)
                        )
                    )
                    Text(
                        text = "Include Worlds & Saves (May increase clone time)",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.5.sp
                    )
                }
            }
        },
        confirmButton = {
            EzzButton(
                text = "Duplicate",
                onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.duplicateInstanceWithOption(sourceInstance, newName.trim(), includeWorlds)
                    }
                },
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.SMALL
            )
        },
        dismissButton = {
            EzzButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.SMALL
            )
        },
        containerColor = Color(0xFF101318),
        shape = RoundedCornerShape(12.dp)
    )
}
