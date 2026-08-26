package io.ezz.launcher.core.storage.vault

import okio.FileSystem
import okio.Path
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

interface SecureVault {
    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String): String?
    suspend fun remove(key: String)
    suspend fun clear()
}

class InMemorySecureVault : SecureVault {
    private val storage = mutableMapOf<String, String>()

    override suspend fun putString(key: String, value: String) {
        storage[key] = value
    }

    override suspend fun getString(key: String): String? {
        return storage[key]
    }

    override suspend fun remove(key: String) {
        storage.remove(key)
    }

    override suspend fun clear() {
        storage.clear()
    }
}

class EncryptedFileVault(
    private val vaultFilePath: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM
) : SecureVault {

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private val salt = "EzzLauncherSalt2026_SecureStorage".toByteArray(Charsets.UTF_8)
    private val lock = Any()

    private fun getSecretKey(): SecretKeySpec {
        return try {
            val userName = try {
                System.getProperty("user.name")?.takeIf { it.isNotBlank() && it != "null" } ?: "AndroidUser_Ezz"
            } catch (e: Throwable) {
                "AndroidUser_Ezz"
            }
            val password = ("EzzVault_" + userName).toCharArray()

            val factory = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            } catch (e: Throwable) {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            }
            val spec = PBEKeySpec(password, salt, 1000, 256)
            val tmp = factory.generateSecret(spec)
            SecretKeySpec(tmp.encoded, "AES")
        } catch (e: Throwable) {
            try {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val keyBytes = md.digest("EzzVault_Android_Key_2026".toByteArray(Charsets.UTF_8))
                SecretKeySpec(keyBytes, "AES")
            } catch (t: Throwable) {
                val raw = ByteArray(32) { (it * 7).toByte() }
                SecretKeySpec(raw, "AES")
            }
        }
    }

    private fun loadMap(): MutableMap<String, String> = synchronized(lock) {
        if (!fileSystem.exists(vaultFilePath)) return mutableMapOf()
        return try {
            val encryptedBytes = fileSystem.read(vaultFilePath) { readByteArray() }
            if (encryptedBytes.size < 12) return mutableMapOf()
            val iv = encryptedBytes.sliceArray(0 until 12)
            val cipherText = encryptedBytes.sliceArray(12 until encryptedBytes.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
            val plainBytes = cipher.doFinal(cipherText)
            val plainText = plainBytes.toString(Charsets.UTF_8)
            json.decodeFromString<Map<String, String>>(plainText).toMutableMap()
        } catch (e: Throwable) {
            mutableMapOf()
        }
    }

    private fun saveMap(map: Map<String, String>): Unit = synchronized(lock) {
        try {
            val parent = vaultFilePath.parent
            if (parent != null && !fileSystem.exists(parent)) {
                fileSystem.createDirectories(parent)
            }
            val plainText = json.encodeToString(map)
            val plainBytes = plainText.toByteArray(Charsets.UTF_8)

            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
            val cipherText = cipher.doFinal(plainBytes)

            val combined = iv + cipherText
            fileSystem.write(vaultFilePath) {
                write(combined)
            }
        } catch (e: Throwable) {
            println("Warning: failed to save EncryptedFileVault: ${e.message}")
        }
    }

    override suspend fun putString(key: String, value: String) {
        val map = loadMap()
        map[key] = value
        saveMap(map)
    }

    override suspend fun getString(key: String): String? {
        val map = loadMap()
        return map[key]
    }

    override suspend fun remove(key: String) {
        val map = loadMap()
        map.remove(key)
        saveMap(map)
    }

    override suspend fun clear() {
        saveMap(emptyMap())
    }
}
