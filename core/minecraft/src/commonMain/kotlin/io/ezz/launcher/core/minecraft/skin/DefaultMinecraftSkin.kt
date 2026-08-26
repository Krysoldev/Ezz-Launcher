package io.ezz.launcher.core.minecraft.skin

import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Authentic Canonical Minecraft Default Skin & Head Asset Provider.
 * Provides the official default skin PNG bytes and extracts 2-layer avatar heads.
 */
object DefaultMinecraftSkin {

    // User-specified canonical default skin texture (64x64 PNG)
    private const val DEFAULT_SKIN_BASE64 =
        "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"

    val steveSkinBytes: ByteArray by lazy {
        Base64.getDecoder().decode(DEFAULT_SKIN_BASE64)
    }

    val steveHeadBytes: ByteArray by lazy {
        val img = getSteveSkinBufferedImage()
        val head8 = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val g8: Graphics2D = head8.createGraphics()

        // 1. Base Face at (8, 8, 8, 8)
        if (img.width >= 16 && img.height >= 16) {
            val face = img.getSubimage(8, 8, 8, 8)
            g8.drawImage(face, 0, 0, null)
        }

        // 2. Hat / Overlay Layer at (40, 8, 8, 8)
        if (img.width >= 48 && img.height >= 16) {
            val hat = img.getSubimage(40, 8, 8, 8)
            g8.drawImage(hat, 0, 0, null)
        }
        g8.dispose()

        // 3. Upscale 8x8 -> 64x64 using Nearest-Neighbor for crisp Minecraft pixel art
        val scaled = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val gs: Graphics2D = scaled.createGraphics()
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        gs.drawImage(head8, 0, 0, 64, 64, null)
        gs.dispose()

        val baos = ByteArrayOutputStream()
        ImageIO.write(scaled, "PNG", baos)
        baos.toByteArray()
    }

    fun getSteveSkinBufferedImage(): BufferedImage {
        return ImageIO.read(ByteArrayInputStream(steveSkinBytes))
    }
}
