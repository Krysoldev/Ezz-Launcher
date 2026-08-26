package io.ezz.launcher.core.runtime.skin

import okio.FileSystem
import okio.Path
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.tools.JavaCompiler
import javax.tools.ToolProvider

/**
 * Builds the isolated Fabric client skin integration mod (mods/ezz_vault_skin.jar).
 *
 * Guarantees:
 * 1. ONLY overrides skin for the local offline Ezz player (UUID check).
 * 2. Remote players NEVER receive the Vault skin.
 * 3. Server plugins take natural precedence when providing custom textures.
 * 4. Skin is stored under 'assets/ezz/textures/skin.png' namespace without polluting vanilla textures.
 */
object FabricSkinModBuilder {

    private const val FABRIC_MOD_JSON = """{
  "schemaVersion": 1,
  "id": "ezz_vault_skin",
  "version": "1.0.0",
  "name": "Ezz Vault Skin",
  "description": "Local offline Vault skin provider for Ezz Launcher",
  "environment": "client",
  "entrypoints": {
    "client": [
      "io.ezz.vaultskin.EzzVaultSkinClient"
    ]
  },
  "mixins": [
    "ezz_vault_skin.mixins.json"
  ]
}"""

    private const val MIXINS_JSON = """{
  "required": false,
  "package": "io.ezz.vaultskin.mixin",
  "compatibilityLevel": "JAVA_17",
  "client": [
    "AbstractClientPlayerMixin"
  ],
  "injectors": {
    "defaultRequire": 0
  }
}"""

    private const val EZZ_CLIENT_JAVA = """package io.ezz.vaultskin;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

public class EzzVaultSkinClient {
    public static UUID targetPlayerUuid = null;
    public static String targetUsername = "";
    public static String modelType = "CLASSIC";
    public static boolean active = false;

    public static void init() {
        loadConfig();
    }

    public void onInitializeClient() {
        loadConfig();
    }

    public static void loadConfig() {
        try {
            File ezzDir = new File(".ezz");
            File skinJson = new File(ezzDir, "active_skin.json");
            if (skinJson.exists()) {
                String json = new String(Files.readAllBytes(skinJson.toPath()), "UTF-8");
                targetUsername = extractJson(json, "username");
                String uuidStr = extractJson(json, "uuid");
                if (uuidStr != null && !uuidStr.trim().isEmpty()) {
                    targetPlayerUuid = UUID.fromString(uuidStr.trim());
                }
                modelType = extractJson(json, "model");
                active = true;
                System.out.println("[EZZ-SKIN] Fabric Client Mod initialized for local account: " + targetUsername + " (" + targetPlayerUuid + ")");
            }
        } catch (Throwable t) {
            System.out.println("[EZZ-SKIN] Notice: " + t.getMessage());
        }
    }

    private static String extractJson(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return "";
        int colon = json.indexOf(":", idx);
        if (colon == -1) return "";
        int firstQuote = json.indexOf("\"", colon);
        if (firstQuote == -1) return "";
        int secondQuote = json.indexOf("\"", firstQuote + 1);
        if (secondQuote == -1) return "";
        return json.substring(firstQuote + 1, secondQuote);
    }
}"""

    private const val MIXIN_JAVA = """package io.ezz.vaultskin.mixin;

import io.ezz.vaultskin.EzzVaultSkinClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "net.minecraft.class_742")
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "method_52810", at = @At("HEAD"), cancellable = true, require = 0)
    private void onGetSkinTextures(CallbackInfoReturnable<Object> cir) {
        try {
            if (!EzzVaultSkinClient.active || EzzVaultSkinClient.targetPlayerUuid == null) {
                return;
            }

            Object player = this;
            java.lang.reflect.Method getProfileMethod = player.getClass().getMethod("method_5678");
            Object gameProfile = getProfileMethod.invoke(player);
            if (gameProfile == null) return;

            java.lang.reflect.Method getIdMethod = gameProfile.getClass().getMethod("getId");
            UUID playerUuid = (UUID) getIdMethod.invoke(gameProfile);

            // 1. CRITICAL CHECK: MUST BE LOCAL PLAYER UUID
            if (!EzzVaultSkinClient.targetPlayerUuid.equals(playerUuid)) {
                // Remote player -> Do not apply Vault skin
                return;
            }

            // 2. CHECK FOR SERVER TEXTURES
            java.lang.reflect.Method getPropsMethod = gameProfile.getClass().getMethod("getProperties");
            Object properties = getPropsMethod.invoke(gameProfile);
            if (properties != null) {
                java.lang.reflect.Method containsKeyMethod = properties.getClass().getMethod("containsKey", Object.class);
                boolean hasServerTextures = (Boolean) containsKeyMethod.invoke(properties, "textures");
                if (hasServerTextures) {
                    // Server skin plugin has priority
                    return;
                }
            }

            // 3. APPLY LOCAL VAULT SKIN
            Class<?> idClass = Class.forName("net.minecraft.class_2960");
            Object skinId = idClass.getConstructor(String.class, String.class).newInstance("ezz", "textures/skin.png");

            Class<?> modelEnum = Class.forName("net.minecraft.class_8685${'$'}class_8686");
            boolean isSlim = "SLIM".equalsIgnoreCase(EzzVaultSkinClient.modelType) || "ALEX".equalsIgnoreCase(EzzVaultSkinClient.modelType);
            Object model = isSlim
                ? Enum.valueOf((Class<Enum>) modelEnum, "SLIM")
                : Enum.valueOf((Class<Enum>) modelEnum, "WIDE");

            Class<?> skinTexturesClass = Class.forName("net.minecraft.class_8685");
            java.lang.reflect.Constructor<?> ctor = skinTexturesClass.getConstructor(
                idClass, String.class, idClass, idClass, modelEnum, boolean.class
            );
            Object vaultSkinTextures = ctor.newInstance(skinId, null, null, null, model, true);

            cir.setReturnValue(vaultSkinTextures);
        } catch (Throwable t) {
            // Silently allow normal execution
        }
    }

    @Inject(method = "method_3123", at = @At("HEAD"), cancellable = true, require = 0)
    private void onGetSkinTextureLegacy(CallbackInfoReturnable<Object> cir) {
        try {
            if (!EzzVaultSkinClient.active || EzzVaultSkinClient.targetPlayerUuid == null) {
                return;
            }

            Object player = this;
            java.lang.reflect.Method getProfileMethod = player.getClass().getMethod("method_5678");
            Object gameProfile = getProfileMethod.invoke(player);
            if (gameProfile == null) return;

            java.lang.reflect.Method getIdMethod = gameProfile.getClass().getMethod("getId");
            UUID playerUuid = (UUID) getIdMethod.invoke(gameProfile);

            if (!EzzVaultSkinClient.targetPlayerUuid.equals(playerUuid)) {
                return;
            }

            java.lang.reflect.Method getPropsMethod = gameProfile.getClass().getMethod("getProperties");
            Object properties = getPropsMethod.invoke(gameProfile);
            if (properties != null) {
                java.lang.reflect.Method containsKeyMethod = properties.getClass().getMethod("containsKey", Object.class);
                boolean hasServerTextures = (Boolean) containsKeyMethod.invoke(properties, "textures");
                if (hasServerTextures) {
                    return;
                }
            }

            Class<?> idClass = Class.forName("net.minecraft.class_2960");
            Object skinId = idClass.getConstructor(String.class, String.class).newInstance("ezz", "textures/skin.png");
            cir.setReturnValue(skinId);
        } catch (Throwable t) {
            // Silently allow normal execution
        }
    }
}"""

    fun buildFabricModJar(
        outputJarPath: Path,
        skinBytes: ByteArray,
        packFormat: Int,
        fileSystem: FileSystem = FileSystem.SYSTEM
    ): Boolean {
        return try {
            val tempDir = File.createTempFile("ezz_mod_build", "").apply {
                delete()
                mkdirs()
            }

            val srcDir = File(tempDir, "src")
            val binDir = File(tempDir, "bin")
            srcDir.mkdirs()
            binDir.mkdirs()

            // 1. Write Source Files
            val clientPkg = File(srcDir, "io/ezz/vaultskin")
            val mixinPkg = File(srcDir, "io/ezz/vaultskin/mixin")
            clientPkg.mkdirs()
            mixinPkg.mkdirs()

            File(clientPkg, "EzzVaultSkinClient.java").writeText(EZZ_CLIENT_JAVA)
            File(mixinPkg, "AbstractClientPlayerMixin.java").writeText(MIXIN_JAVA)

            // 2. Compile Java sources
            val compiler: JavaCompiler? = ToolProvider.getSystemJavaCompiler()
            val compiledFiles = mutableMapOf<String, ByteArray>()

            if (compiler != null) {
                val sources = listOf(
                    File(clientPkg, "EzzVaultSkinClient.java").absolutePath,
                    File(mixinPkg, "AbstractClientPlayerMixin.java").absolutePath
                )

                val outStream = ByteArrayOutputStream()
                val errStream = ByteArrayOutputStream()
                val result = compiler.run(null, outStream, errStream, "-d", binDir.absolutePath, *sources.toTypedArray())

                if (result == 0) {
                    binDir.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.forEach { classFile ->
                        val relPath = classFile.relativeTo(binDir).path.replace('\\', '/')
                        compiledFiles[relPath] = classFile.readBytes()
                    }
                }
            }

            // 3. Assemble Fabric Mod JAR
            val outFile = outputJarPath.toFile()
            outFile.parentFile?.mkdirs()

            ZipOutputStream(FileOutputStream(outFile)).use { zos ->
                // Mod Manifests
                addZipEntry(zos, "fabric.mod.json", FABRIC_MOD_JSON.toByteArray(Charsets.UTF_8))
                addZipEntry(zos, "ezz_vault_skin.mixins.json", MIXINS_JSON.toByteArray(Charsets.UTF_8))

                val packMcmeta = """{"pack":{"pack_format":$packFormat,"description":"Ezz Vault Skin Integration"}}"""
                addZipEntry(zos, "pack.mcmeta", packMcmeta.toByteArray(Charsets.UTF_8))

                // Compiled Classes
                for ((classPath, classBytes) in compiledFiles) {
                    addZipEntry(zos, classPath, classBytes)
                }

                // Isolated Skin Texture in 'ezz' namespace
                addZipEntry(zos, "assets/ezz/textures/skin.png", skinBytes)
            }

            tempDir.deleteRecursively()
            true
        } catch (e: Exception) {
            println("[FabricSkinModBuilder] Warning during mod build: ${e.message}")
            false
        }
    }

    private fun addZipEntry(zos: ZipOutputStream, entryName: String, data: ByteArray) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(data)
        zos.closeEntry()
    }
}
