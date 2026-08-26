package io.ezz.launcher.ui.manager.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import io.ezz.launcher.core.model.instance.InstanceRepairReport
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun InstanceRepairDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val colors = EzzTheme.colors
    val report by viewModel.manageRepairReport.collectAsState()
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (report?.isHealthy == true) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (report?.isHealthy == true) Color(0xFF10B981) else colors.warning,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Instance Health Diagnostic",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (report == null) {
                    Text("Running diagnostics...", color = colors.textSecondary)
                } else {
                    // Passed Checks
                    if (report!!.passed.isNotEmpty()) {
                        Text("PASSED CHECKS", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                        report!!.passed.forEach { item ->
                            CheckRow(item, Color(0xFF10B981), Icons.Default.CheckCircle)
                        }
                    }

                    // Warnings
                    if (report!!.warnings.isNotEmpty()) {
                        Text("WARNINGS", color = colors.warning, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                        report!!.warnings.forEach { item ->
                            CheckRow(item, colors.warning, Icons.Default.Warning)
                        }
                    }

                    // Failed Checks
                    if (report!!.failed.isNotEmpty()) {
                        Text("FAILURES", color = colors.danger, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                        report!!.failed.forEach { item ->
                            CheckRow(item, colors.danger, Icons.Default.Error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun CheckRow(text: String, tint: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = EzzTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(text = text, color = colors.textPrimary, fontSize = 12.sp)
        }
    }
}
