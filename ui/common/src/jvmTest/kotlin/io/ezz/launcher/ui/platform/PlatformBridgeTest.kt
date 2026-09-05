package io.ezz.launcher.ui.platform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformBridgeTest {

    @Test
    fun testPickSkinFilePassesThroughValidPng() {
        val testFile = File("steve.png")
        val bridge = DefaultPlatformBridge(
            onPickSkinFile = { title ->
                assertEquals("Import Minecraft Skin (*.png)", title)
                testFile
            }
        )

        val result = bridge.pickSkinFile()
        assertEquals(testFile, result)
    }

    @Test
    fun testPickSkinFileCancelReturnsNull() {
        val bridge = DefaultPlatformBridge(
            onPickSkinFile = { null }
        )

        val result = bridge.pickSkinFile()
        assertNull(result)
    }

    @Test
    fun testPickImportInstanceFileRequiresMrpack() {
        val validFile = File("pack.mrpack")
        val bridge = DefaultPlatformBridge(
            onPickImportFile = { title ->
                assertEquals("Select Modrinth Modpack (*.mrpack)", title)
                validFile
            }
        )

        val result = bridge.pickImportInstanceFile()
        assertEquals(validFile, result)
    }

    @Test
    fun testPickImportInstanceFileCancelReturnsNull() {
        val bridge = DefaultPlatformBridge(
            onPickImportFile = { null }
        )

        val result = bridge.pickImportInstanceFile()
        assertNull(result)
    }

    @Test
    fun testPickExportInstanceFileAppendsExtension() {
        val target = File("C:/export/MyPack.mrpack")
        val bridge = DefaultPlatformBridge(
            onPickExportFile = { defaultName, title ->
                assertEquals("MyPack.mrpack", defaultName)
                assertEquals("Export Modrinth Modpack (*.mrpack)", title)
                target
            }
        )

        val result = bridge.pickExportInstanceFile("MyPack")
        assertEquals(target, result)
    }

    @Test
    fun testPickExportInstanceFileCancelReturnsNull() {
        val bridge = DefaultPlatformBridge(
            onPickExportFile = { _, _ -> null }
        )

        val result = bridge.pickExportInstanceFile("MyPack")
        assertNull(result)
    }

    @Test
    fun testPickJavaExecutableCancelReturnsNull() {
        val bridge = DefaultPlatformBridge(
            onPickJavaExecutable = { null }
        )

        val result = bridge.pickJavaExecutable()
        assertNull(result)
    }

    @Test
    fun testPickFileWithSpacesInPath() {
        val spacePathMrpack = File("C:/Users/Cool Player/My Saved Modpacks/Fabulously Optimized 1.21.mrpack")
        val bridgeMrpack = DefaultPlatformBridge(
            onPickImportFile = { spacePathMrpack }
        )
        val pickedMrpack = bridgeMrpack.pickImportInstanceFile()
        assertEquals(spacePathMrpack, pickedMrpack)

        val spacePathJava = File("C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/javaw.exe")
        val bridgeJava = DefaultPlatformBridge(
            onPickJavaExecutable = { spacePathJava }
        )
        val pickedJava = bridgeJava.pickJavaExecutable()
        assertEquals(spacePathJava, pickedJava)
    }

    @Test
    fun testPickImageFileAndCancel() {
        val iconFile = File("C:/Icons/My Custom Icon.png")
        val bridge = DefaultPlatformBridge(
            onPickImageFile = { title ->
                assertEquals("Select Instance Icon (PNG, JPG, WEBP)", title)
                iconFile
            }
        )
        assertEquals(iconFile, bridge.pickImageFile())

        val cancelBridge = DefaultPlatformBridge(
            onPickImageFile = { null }
        )
        assertNull(cancelBridge.pickImageFile())
    }

    @Test
    fun testPickReleaseArtifactAndCancel() {
        val artifactFile = File("C:/Releases/EzzLauncher-Setup-1.0.0.exe")
        val bridge = DefaultPlatformBridge(
            onPickReleaseArtifact = { title ->
                assertEquals("Select Release Artifact (*.zip, *.exe, *.msi)", title)
                artifactFile
            }
        )
        assertEquals(artifactFile, bridge.pickReleaseArtifact())

        val cancelBridge = DefaultPlatformBridge(
            onPickReleaseArtifact = { null }
        )
        assertNull(cancelBridge.pickReleaseArtifact())
    }
}
