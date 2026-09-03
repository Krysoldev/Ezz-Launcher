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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun InstanceRepairDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val report by viewModel.manageRepairReport.collectAsState()
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (report?.isHealthy == true) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (report?.isHealthy == true) Color(0xFF10B981) else Color(0xFFFBBF24),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Instance Health Diagnostic",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
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
                    Text("Running diagnostics...", color = Color(0xFF94A3B8), fontSize = 13.sp)
                } else {
                    // Passed Checks
                    if (report!!.passed.isNotEmpty()) {
                        Text("PASSED CHECKS", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        report!!.passed.forEach { item ->
                            CheckRow(item, Color(0xFF10B981), Icons.Default.CheckCircle)
                        }
                    }

                    // Warnings
                    if (report!!.warnings.isNotEmpty()) {
                        Text("WARNINGS", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        report!!.warnings.forEach { item ->
                            CheckRow(item, Color(0xFFFBBF24), Icons.Default.Warning)
                        }
                    }

                    // Failed Checks
                    if (report!!.failed.isNotEmpty()) {
                        Text("FAILURES", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        report!!.failed.forEach { item ->
                            CheckRow(item, Color(0xFFEF4444), Icons.Default.Error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            EzzButton(
                text = "Close",
                onClick = onDismiss,
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.SMALL
            )
        },
        containerColor = Color(0xFF101318),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun CheckRow(text: String, tint: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF141720))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Text(text = text, color = Color.White, fontSize = 12.sp)
        }
    }
}
