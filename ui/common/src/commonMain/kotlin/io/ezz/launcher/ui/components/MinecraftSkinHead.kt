package io.ezz.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager
import io.ezz.launcher.core.model.account.Account
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Pixel-Perfect Minecraft Skin Head Avatar Component.
 * - Extracts and renders the actual Minecraft skin head with hat overlay.
 * - Scales with FilterQuality.None (Nearest-Neighbor) for razor-sharp pixel art.
 * - Supports any DPI scaling without blur.
 * - Features an elegant, clean fallback player avatar when skin is loading or unavailable.
 */
@Composable
fun MinecraftSkinHead(
    account: Account?,
    skinManager: MinecraftSkinManager,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val skinHeadsMap by skinManager.skinHeads.collectAsState()

    val imageBitmap: ImageBitmap? = remember(account?.id, account?.username, account?.skinUrl, account?.skinHash, skinHeadsMap) {
        try {
            val bytes = skinManager.getHeadBytes(account)
            val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
            bufferedImage?.toComposeImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141720))
            .border(1.dp, Color(0xFF262C3D), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "${account?.username ?: "Player"} Avatar Head",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF161B24)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(size * 0.58f)
                )
            }
        }
    }
}
