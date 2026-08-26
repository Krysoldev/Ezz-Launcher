package io.ezz.launcher.core.network.checksum

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object ChecksumVerifier {
    fun verifySha1(filePath: String, expectedSha1: String?): Boolean {
        if (expectedSha1.isNullOrBlank()) return true
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return false

        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            actualHash.equals(expectedSha1, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    fun computeSha1(filePath: String): String? {
        val file = File(filePath)
        if (!file.exists()) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
