package io.ezz.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.ui.theme.EzzTheme

@Composable
fun EzzEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.FolderOpen,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = EzzTheme.colors
    val btnText = actionButtonText ?: actionLabel
    val btnClick = onActionClick ?: onAction

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant)
                .border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = description,
            color = colors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (btnText != null && btnClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            EzzButton(
                text = btnText,
                onClick = btnClick,
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.SMALL
            )
        }
    }
}

@Composable
fun EzzErrorState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionButtonText: String? = "Try Again",
    onActionClick: (() -> Unit)? = null
) {
    val colors = EzzTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.dangerGlow)
                .border(1.dp, colors.danger, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = description,
            color = colors.danger,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (actionButtonText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            EzzButton(
                text = actionButtonText,
                onClick = onActionClick,
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.SMALL
            )
        }
    }
}
