package io.ezz.launcher.core.minecraft.version

import io.ezz.launcher.core.minecraft.launch.LaunchArgumentBuilder
import io.ezz.launcher.core.minecraft.manifest.VersionMerger
import io.ezz.launcher.core.minecraft.mod.FabricSkinModManager
import io.ezz.launcher.core.minecraft.mod.ModBytecodeValidator
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.minecraft.Library
import io.ezz.launcher.core.model.minecraft.VersionInfo
import io.ezz.launcher.core.model.skin.SkinModelType
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive compatibility, stability, and classpath integrity test suite
 * across all Minecraft versions supported by Ezz Launcher (1.16.x through 1.26.x).
 */
class VersionMatrixCompatibilityTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private val fileSystem = FileSystem.SYSTEM

    // All Minecraft versions supported across all version families in Ezz Launcher
    private val allSupportedVersions = listOf(
        // 1.16.x family (Java 8)
        "1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5",
        // 1.17.x family (Java 17)
        "1.17", "1.17.1",
        // 1.18.x family (Java 17)
        "1.18", "1.18.1", "1.18.2",
        // 1.19.x family (Java 17)
        "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
        // 1.20.x family (Java 17 for <=1.20.4, Java 21 for 1.20.5+)
        "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
        // 1.21.x family (Java 21)
        "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.11",
        // 1.26.x snapshot/experimental family (Java 21)
        "26.1", "26.2", "26.3", "1.26", "1.26.1", "1.26.2"
    )

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "ezz_version_matrix_test_${System.nanoTime()}")
        tempDir.mkdirs()
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testVersionMergerDeduplication_OverridesOlderAsm() {
        // Reproduce the previous 1.21.4 failure:
        // Child (Fabric 0.19.5): asm 9.10.1
        // Parent (Minecraft 1.21.4): asm 9.6
        val child = VersionInfo(
            id = "fabric-loader-0.19.5-1.21.4",
            libraries = listOf(
                Library(name = "org.ow2.asm:asm:9.10.1"),
                Library(name = "org.ow2.asm:asm-analysis:9.10.1"),
                Library(name = "org.ow2.asm:asm-commons:9.10.1"),
                Library(name = "org.ow2.asm:asm-tree:9.10.1"),
                Library(name = "org.ow2.asm:asm-util:9.10.1"),
                Library(name = "net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7"),
                Library(name = "net.fabricmc:fabric-loader:0.19.5")
            )
        )

        val parent = VersionInfo(
            id = "1.21.4",
            libraries = listOf(
                Library(name = "org.ow2.asm:asm:9.6"), // Older ASM in vanilla!
                Library(name = "com.google.guava:guava:33.3.1-jre"),
                Library(name = "com.google.code.gson:gson:2.11.0")
            )
        )

        val merged = VersionMerger.merge(child, parent)

        // Verify ASM deduplication
        val asmBaseLibs = merged.libraries.filter { it.name.startsWith("org.ow2.asm:asm:") }
        assertEquals(1, asmBaseLibs.size, "Merged libraries must contain exactly ONE org.ow2.asm:asm library")
        assertEquals("org.ow2.asm:asm:9.10.1", asmBaseLibs.first().name, "Child loader's ASM must override parent's older ASM")

        // Verify total ASM libraries
        val allAsmLibs = merged.libraries.filter { it.name.startsWith("org.ow2.asm:") }
        assertEquals(5, allAsmLibs.size, "Must have exactly 5 ASM libraries from child, none from parent")
        assertTrue(allAsmLibs.none { it.name.contains("9.6") }, "Parent's older asm 9.6 must be completely excluded")
    }

    @Test
    fun testVersionMergerDeduplication_NettyGsonGuavaLog4j() {
        val child = VersionInfo(
            id = "loader-test",
            libraries = listOf(
                Library(name = "com.google.guava:guava:33.5.0-jre"),
                Library(name = "com.google.code.gson:gson:2.13.2"),
                Library(name = "io.netty:netty-buffer:4.1.118.Final"),
                Library(name = "org.apache.logging.log4j:log4j-core:2.24.2")
            )
        )

        val parent = VersionInfo(
            id = "vanilla-test",
            libraries = listOf(
                Library(name = "com.google.guava:guava:32.1.2-jre"),
                Library(name = "com.google.code.gson:gson:2.10.1"),
                Library(name = "io.netty:netty-buffer:4.1.97.Final"),
                Library(name = "org.apache.logging.log4j:log4j-core:2.19.0"),
                Library(name = "com.mojang:authlib:6.0.57")
            )
        )

        val merged = VersionMerger.merge(child, parent)

        assertEquals(1, merged.libraries.count { it.name.startsWith("com.google.guava:guava:") })
        assertEquals("com.google.guava:guava:33.5.0-jre", merged.libraries.first { it.name.startsWith("com.google.guava:guava:") }.name)

        assertEquals(1, merged.libraries.count { it.name.startsWith("com.google.code.gson:gson:") })
        assertEquals("com.google.code.gson:gson:2.13.2", merged.libraries.first { it.name.startsWith("com.google.code.gson:gson:") }.name)

        assertEquals(1, merged.libraries.count { it.name.startsWith("io.netty:netty-buffer:") })
        assertEquals("io.netty:netty-buffer:4.1.118.Final", merged.libraries.first { it.name.startsWith("io.netty:netty-buffer:") }.name)

        assertEquals(1, merged.libraries.count { it.name.startsWith("org.apache.logging.log4j:log4j-core:") })
        assertEquals("org.apache.logging.log4j:log4j-core:2.24.2", merged.libraries.first { it.name.startsWith("org.apache.logging.log4j:log4j-core:") }.name)

        // Parent unique library preserved
        assertTrue(merged.libraries.any { it.name.startsWith("com.mojang:authlib:") })
    }

    @Test
    fun testVersionMergerDeduplication_PreservesDistinctNativeClassifiers() {
        val parent = VersionInfo(
            id = "vanilla-lwjgl",
            libraries = listOf(
                Library(name = "org.lwjgl:lwjgl:3.3.3"),
                Library(name = "org.lwjgl:lwjgl:3.3.3:natives-windows"),
                Library(name = "org.lwjgl:lwjgl:3.3.3:natives-linux"),
                Library(name = "org.lwjgl:lwjgl-glfw:3.3.3"),
                Library(name = "org.lwjgl:lwjgl-glfw:3.3.3:natives-windows")
            )
        )

        val child = VersionInfo(id = "loader-empty", libraries = emptyList())
        val merged = VersionMerger.merge(child, parent)

        // All distinct classifier libraries must be preserved!
        assertEquals(5, merged.libraries.size)
        assertTrue(merged.libraries.any { it.name == "org.lwjgl:lwjgl:3.3.3" })
        assertTrue(merged.libraries.any { it.name == "org.lwjgl:lwjgl:3.3.3:natives-windows" })
        assertTrue(merged.libraries.any { it.name == "org.lwjgl:lwjgl:3.3.3:natives-linux" })
    }

    @Test
    fun testAllSupportedVersionsResolveToValidEntries() {
        for (version in allSupportedVersions) {
            val entry = FabricSkinModManager.resolveModEntry(version)
            assertNotNull(entry, "Version $version must resolve to a valid ModVersionEntry")
            assertTrue(entry.jarName.endsWith(".jar"), "Entry for $version must have valid JAR name")

            // Verify Java requirements mapping
            val requiredJava = JavaCompatibility.getRequiredJavaMajorVersion(version)
            val expectedMinJava = when {
                version.startsWith("1.16") -> 8
                version.startsWith("1.17") || version.startsWith("1.18") || version.startsWith("1.19") -> 17
                version.startsWith("1.20") && (version == "1.20.5" || version == "1.20.6") -> 21
                version.startsWith("1.20") -> 17
                else -> 21 // 1.21+, 1.26+
            }
            assertEquals(expectedMinJava, requiredJava, "Java requirement for $version must be $expectedMinJava")

            // Verify bytecode limits of the selected artifact
            val javaRelease = when {
                version.startsWith("1.16") -> 8
                version.startsWith("1.17") || version.startsWith("1.18") || version.startsWith("1.19") || version.startsWith("1.20") -> 17
                else -> 21
            }

            val jarBytes = FabricSkinModManager.getModJarBytes(entry)
            assertTrue(jarBytes.isNotEmpty(), "Mod JAR bytes for ${entry.jarName} must not be empty")

            val validation = ModBytecodeValidator.validateJarBytes(
                modName = entry.jarName,
                jarBytes = jarBytes,
                javaMajorVersion = javaRelease
            )
            assertEquals(
                io.ezz.launcher.core.minecraft.mod.ModCompatibilityResult.Compatible,
                validation
            )
        }
    }

    @Test
    fun testInstanceIsolationAcrossSequentialLaunches() {
        val testVersions = listOf("1.16.5", "1.17.1", "1.18.2", "1.19.4", "1.20.1", "1.21.4", "1.26")
        val instance = Instance(
            id = "isolation-test-inst",
            name = "Sequential Test",
            minecraftVersion = "1.16.5",
            loaderType = LoaderType.FABRIC,
            ezzSkinEnabled = true
        )

        val account = OfflineAccount(
            id = "acc-qa",
            username = "QA_Tester",
            uuid = "12345678-1234-1234-1234-123456789abc"
        )

        val skin = VaultSkin(
            id = "skin-qa",
            name = "QA Skin",
            fileName = "qa.png",
            fileHash = "qa_hash",
            modelType = SkinModelType.STEVE
        )

        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
        val modsDir = gameDir.resolve("mods").toFile()

        for (mcVersion in testVersions) {
            val currentInstance = instance.copy(minecraftVersion = mcVersion)
            val prepResult = FabricSkinModManager.prepareInstanceSkinMod(
                instance = currentInstance,
                account = account,
                skin = skin,
                skinBytes = "QA_SKIN_BYTES".toByteArray(),
                pathProvider = pathProvider,
                fileSystem = fileSystem
            )

            assertTrue(prepResult.isSuccess, "Preparation must succeed for $mcVersion")

            // Verify that ONLY the current version family's JAR exists in mods/
            val activeJars = modsDir.listFiles { _, name -> name.endsWith(".jar") } ?: emptyArray()
            assertEquals(1, activeJars.size, "There must be exactly 1 active mod JAR in mods/ for $mcVersion")

            val expectedEntry = FabricSkinModManager.resolveModEntry(mcVersion)!!
            assertEquals(expectedEntry.jarName, activeJars.first().name, "Active mod JAR must match $mcVersion")

            // Verify no leftover .disabled or wrong version family JARs
            val allModFiles = modsDir.listFiles() ?: emptyArray()
            assertEquals(1, allModFiles.size, "No stale, disabled, or leaked mod files from previous version should remain in mods/")
        }
    }

    @Test
    fun testEzzSkinDisabledStateStaysDisabledAcrossRestarts() {
        val instance = Instance(
            id = "disabled-test-inst",
            name = "Disabled Test",
            minecraftVersion = "1.21.4",
            loaderType = LoaderType.FABRIC,
            ezzSkinEnabled = false // DISABLED
        )

        val account = OfflineAccount(
            id = "acc-qa",
            username = "QA_Tester",
            uuid = "12345678-1234-1234-1234-123456789abc"
        )

        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
        val modsDir = gameDir.resolve("mods").toFile()

        // 1. Prepare with disabled
        FabricSkinModManager.prepareInstanceSkinMod(
            instance = instance,
            account = account,
            skin = null,
            skinBytes = null,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        val activeJars = modsDir.listFiles { _, name -> name.endsWith(".jar") } ?: emptyArray()
        assertEquals(0, activeJars.size, "No active .jar should exist when Ezz Skin is disabled")

        val disabledJars = modsDir.listFiles { _, name -> name.endsWith(".jar.disabled") } ?: emptyArray()
        assertEquals(1, disabledJars.size, "One .disabled jar should exist to preserve installation")

        // 2. Simulate launcher restart / second launch attempt
        FabricSkinModManager.prepareInstanceSkinMod(
            instance = instance,
            account = account,
            skin = null,
            skinBytes = null,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        val activeJarsAfter = modsDir.listFiles { _, name -> name.endsWith(".jar") } ?: emptyArray()
        assertEquals(0, activeJarsAfter.size, "Ezz Skin must NOT re-enable itself upon second launch")
    }

    @Test
    fun testLaunchArgumentBuilderClasspathDeduplication() {
        val instance = Instance(
            id = "cp-test",
            name = "Classpath Test",
            minecraftVersion = "1.21.4"
        )

        val account = OfflineAccount(
            id = "acc-test",
            username = "Player",
            uuid = "00000000-0000-0000-0000-000000000000"
        )

        val versionInfo = VersionInfo(
            id = "1.21.4",
            mainClass = "net.minecraft.client.main.Main"
        )

        // Classpath with intentional duplicates
        val duplicatePaths = listOf(
            tempDir.resolve("libraries/asm-9.10.1.jar").absolutePath.toPath(),
            tempDir.resolve("libraries/guava-33.3.1.jar").absolutePath.toPath(),
            tempDir.resolve("libraries/asm-9.10.1.jar").absolutePath.toPath(), // Duplicate!
            tempDir.resolve("libraries/gson-2.11.0.jar").absolutePath.toPath()
        )

        val clientJar = tempDir.resolve("versions/1.21.4/1.21.4.jar").absolutePath.toPath()

        val command = LaunchArgumentBuilder.buildLaunchCommand(
            instance = instance,
            account = account,
            versionInfo = versionInfo,
            classpathEntries = duplicatePaths,
            clientJarPath = clientJar,
            nativesDir = tempDir.resolve("natives").absolutePath.toPath(),
            assetsDir = tempDir.resolve("assets").absolutePath.toPath(),
            gameDir = tempDir.resolve("gameDir").absolutePath.toPath(),
            javaBinaryPath = "java"
        )

        val cpIndex = command.indexOf("-cp")
        assertTrue(cpIndex != -1, "Command must contain -cp")
        val cpString = command[cpIndex + 1]

        val entries = cpString.split(File.pathSeparator)
        val asmCount = entries.count { it.contains("asm-9.10.1.jar") }
        assertEquals(1, asmCount, "Classpath must deduplicate duplicate paths, found $asmCount instances")
    }

    @Test
    fun testEzzSkinMixinIntegrity_NoMethod3118CollisionInModernJars() {
        val resourcesDir = listOf(
            File(System.getProperty("user.dir"), "src/commonMain/resources"),
            File(System.getProperty("user.dir"), "core/minecraft/src/commonMain/resources")
        ).firstOrNull { it.exists() } ?: File("src/commonMain/resources")

        val jar121 = File(resourcesDir, "ezz-skin-mod-1.21.jar")
        assertTrue(jar121.exists(), "ezz-skin-mod-1.21.jar must exist")

        java.util.zip.ZipFile(jar121).use { zip ->
            val mixinEntry = zip.getEntry("ezzskin.mixins.json")
            assertNotNull(mixinEntry, "ezzskin.mixins.json must exist in 1.21 jar")
            val mixinJson = zip.getInputStream(mixinEntry).bufferedReader().readText()

            assertTrue(mixinJson.contains("AbstractClientPlayerEntityModernMixin"), "1.21 must use Modern Mixin")
            assertFalse(mixinJson.contains("AbstractClientPlayerEntityLegacyMixin"), "1.21 must NOT use Legacy Mixin")

            // Inspect AbstractClientPlayerEntityModernMixin.class bytecode
            val modernClassEntry = zip.getEntry("io/ezz/skinmod/mixin/AbstractClientPlayerEntityModernMixin.class")
            assertNotNull(modernClassEntry, "AbstractClientPlayerEntityModernMixin.class must exist")
            val modernBytes = zip.getInputStream(modernClassEntry).readBytes()
            val modernString = String(modernBytes, Charsets.ISO_8859_1)

            // CRITICAL: Must target method_52814 and NEVER target method_3118 (which caused the 1.21.4 ClassCastException)
            assertTrue(modernString.contains("method_52814"), "Modern mixin must target method_52814")
            assertFalse(modernString.contains("method_3118"), "Modern mixin must NEVER target method_3118")
        }

        val jar116 = File(resourcesDir, "ezz-skin-mod-1.16.jar")
        assertTrue(jar116.exists(), "ezz-skin-mod-1.16.jar must exist")

        java.util.zip.ZipFile(jar116).use { zip ->
            val mixinEntry = zip.getEntry("ezzskin.mixins.json")
            assertNotNull(mixinEntry, "ezzskin.mixins.json must exist in 1.16 jar")
            val mixinJson = zip.getInputStream(mixinEntry).bufferedReader().readText()

            assertTrue(mixinJson.contains("AbstractClientPlayerEntityLegacyMixin"), "1.16 must use Legacy Mixin")
            assertFalse(mixinJson.contains("AbstractClientPlayerEntityModernMixin"), "1.16 must NOT use Modern Mixin")

            // Inspect AbstractClientPlayerEntityLegacyMixin.class bytecode
            val legacyClassEntry = zip.getEntry("io/ezz/skinmod/mixin/AbstractClientPlayerEntityLegacyMixin.class")
            assertNotNull(legacyClassEntry, "AbstractClientPlayerEntityLegacyMixin.class must exist")
            val legacyBytes = zip.getInputStream(legacyClassEntry).readBytes()
            val legacyString = String(legacyBytes, Charsets.ISO_8859_1)

            // CRITICAL: Must target method_3117 (getSkinTexture) and method_3121 (getModel), NEVER method_3118
            assertTrue(legacyString.contains("method_3117"), "Legacy mixin must target method_3117")
            assertTrue(legacyString.contains("method_3121"), "Legacy mixin must target method_3121")
            assertFalse(legacyString.contains("method_3118"), "Legacy mixin must NEVER target method_3118")
        }
    }
}

