package io.ezz.launcher.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Universal image decoder supporting WebP, PNG, JPEG, GIF, BMP, and ICO.
 * Uses Skia/Compose native decode first (which natively decodes WebP and all modern formats),
 * with ImageIO fallback for standard Java formats.
 */
object ImageDecoder {

    fun decodeBytes(bytes: ByteArray?): ImageBitmap? {
        if (bytes == null || bytes.isEmpty()) return null

        // 1. Try Compose / Skia decode (Natively supports WebP, PNG, JPEG, GIF, BMP)
        try {
            val skiaImage = org.jetbrains.skia.Image.makeFromEncoded(bytes)
            return skiaImage.toComposeImageBitmap()
        } catch (e: Throwable) {
            // Proceed to fallback
        }

        // 2. Fallback to javax.imageio.ImageIO (Supports PNG, JPEG, BMP)
        return try {
            val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
            bufferedImage?.toComposeImageBitmap()
        } catch (e: Throwable) {
            null
        }
    }

    fun decodeFile(file: java.io.File?): ImageBitmap? {
        if (file == null || !file.exists() || file.length() == 0L) return null
        return try {
            val bytes = file.readBytes()
            decodeBytes(bytes)
        } catch (e: Throwable) {
            null
        }
    }
}
