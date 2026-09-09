package io.ezz.launcher.ui.instance.workspace

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.InstanceManagerTab

@Composable
fun InstanceWorkspaceTabNav(
    activeTab: InstanceManagerTab,
    onTabSelect: (InstanceManagerTab) -> Unit,
    modsCount: Int = 0,
    resourcePacksCount: Int = 0,
    shadersCount: Int = 0,
    worldsCount: Int = 0,
    screenshotsCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0D13))
            .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(0.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InstanceManagerTab.entries.forEach { tab ->
            val isSelected = activeTab == tab
            val count = when (tab) {
                InstanceManagerTab.MODS -> modsCount
                InstanceManagerTab.RESOURCE_PACKS -> resourcePacksCount
                InstanceManagerTab.SHADERS -> shadersCount
                InstanceManagerTab.WORLDS -> worldsCount
                InstanceManagerTab.SCREENSHOTS -> screenshotsCount
                else -> 0
            }

            val icon = when (tab) {
                InstanceManagerTab.OVERVIEW -> Icons.Default.Dashboard
                InstanceManagerTab.MODS -> Icons.Default.Extension
                InstanceManagerTab.RESOURCE_PACKS -> Icons.Default.Style
                InstanceManagerTab.SHADERS -> Icons.Default.Image
                InstanceManagerTab.WORLDS -> Icons.Default.Public
                InstanceManagerTab.SCREENSHOTS -> Icons.Default.CameraAlt
                InstanceManagerTab.SETTINGS -> Icons.Default.Settings
                InstanceManagerTab.FILES -> Icons.Default.Folder
                InstanceManagerTab.LOGS -> Icons.Default.Terminal
            }

            WorkspaceTabButton(
                title = tab.title,
                icon = icon,
                count = count,
                isSelected = isSelected,
                onClick = { onTabSelect(tab) }
            )
        }
    }
}

@Composable
private fun WorkspaceTabButton(
    title: String,
    icon: ImageVector,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val bg = when {
        isSelected -> Color(0xFF161A28)
        isHovered -> Color(0xFF10131A)
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> Color.White
        isHovered -> Color(0xFFE2E8F0)
        else -> Color(0xFF94A3B8)
    }

    val iconColor = when {
        isSelected -> Color(0xFF8B5CF6)
        isHovered -> Color(0xFFCBD5E1)
        else -> Color(0xFF64748B)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFF2E364F) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )

            Text(
                text = title,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            )

            if (count > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFF8B5CF6) else Color(0xFF1B1F2C))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = count.toString(),
                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
