package io.ezz.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.ui.theme.EzzTheme

data class TabItem<T>(
    val value: T,
    val title: String,
    val icon: ImageVector? = null,
    val badge: String? = null
)

@Composable
fun <T> EzzTabs(
    items: List<TabItem<T>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { tab ->
            val isSelected = tab.value == selectedItem
            val bg by animateColorAsState(
                targetValue = if (isSelected) colors.surfaceVariant else Color.Transparent
            )
            val fg by animateColorAsState(
                targetValue = if (isSelected) colors.primary else colors.textSecondary
            )

            val interactionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onItemSelected(tab.value) }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tab.icon != null) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = tab.title,
                        color = fg,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )

                    if (tab.badge != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.primary.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = tab.badge,
                                color = colors.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EzzTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(tabs) {
        tabs.mapIndexed { index, title -> TabItem(index, title) }
    }
    EzzTabs(
        items = items,
        selectedItem = selectedIndex,
        onItemSelected = onTabSelected,
        modifier = modifier
    )
}

/**
 * Premium Underline Tabs for section navigation (e.g. Instance Manager).
 * Clean horizontal navigation with active accent underline, smooth transitions, and no oversized pills or badges.
 */
@Composable
fun <T> EzzUnderlineTabs(
    items: List<TabItem<T>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colors.border,
                shape = RoundedCornerShape(8.dp)
            )
            .background(colors.cardBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { tab ->
            val isSelected = tab.value == selectedItem
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()

            val contentColor by animateColorAsState(
                targetValue = when {
                    isSelected -> colors.textPrimary
                    isHovered -> colors.textPrimary
                    else -> colors.textSecondary
                },
                animationSpec = tween(150)
            )

            val indicatorColor by animateColorAsState(
                targetValue = if (isSelected) colors.primary else Color.Transparent,
                animationSpec = tween(180)
            )

            val itemBg by animateColorAsState(
                targetValue = when {
                    isSelected -> colors.elevatedCard.copy(alpha = 0.5f)
                    isHovered -> colors.elevatedCard.copy(alpha = 0.3f)
                    else -> Color.Transparent
                },
                animationSpec = tween(150)
            )

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(itemBg)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onItemSelected(tab.value) }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (tab.icon != null) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) colors.primary else contentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Text(
                        text = tab.title,
                        color = contentColor,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }

                // Active Underline Accent Indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(indicatorColor)
                )
            }
        }
    }
}

@Composable
fun <T> EzzDropdown(
    items: List<T>,
    selectedItem: T?,
    itemToString: (T) -> String,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Select an option..."
) {
    val colors = EzzTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, if (expanded) colors.primary else colors.border, RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedItem != null) itemToString(selectedItem) else placeholder,
                    color = if (selectedItem != null) colors.textPrimary else colors.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.textSecondary
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(colors.surfaceVariant)
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = itemToString(item),
                                color = if (item == selectedItem) colors.primary else colors.textPrimary,
                                fontWeight = if (item == selectedItem) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onItemSelected(item)
                            expanded = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = colors.textPrimary
                        )
                    )
                }
            }
        }
    }
}
