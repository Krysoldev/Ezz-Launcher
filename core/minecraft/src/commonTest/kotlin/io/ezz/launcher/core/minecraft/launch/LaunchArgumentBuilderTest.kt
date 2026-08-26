package io.ezz.launcher.core.minecraft.launch

import io.ezz.launcher.core.minecraft.resolver.OperatingSystem
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.minecraft.VersionInfo
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertTrue

class LaunchArgumentBuilderTest {

    @Test
    fun testLegacyArgumentBuilding() {
        val instance = Instance(
            id = "test-id",
            name = "Test 1.12.2",
            minecraftVersion = "1.12.2",
            minMemoryMb = 1024,
            maxMemoryMb = 2048,
            customJvmArgs = listOf("-XX:+UseG1GC")
        )

        val account = OfflineAccount(
            id = "acc-id",
            username = "PlayerOne",
            uuid = "12345678-1234-1234-1234-123456789abc"
        )

        val versionInfo = VersionInfo(
            id = "1.12.2",
            mainClass = "net.minecraft.client.main.Main",
            minecraftArguments = "--username \${auth_player_name} --version \${version_name} --gameDir \${game_directory} --assetsDir \${assets_root} --assetIndex \${assets_index_name} --uuid \${auth_uuid} --accessToken \${auth_access_token} --userType \${user_type}"
        )

        val command = LaunchArgumentBuilder.buildLaunchCommand(
            instance = instance,
            account = account,
            versionInfo = versionInfo,
            classpathEntries = listOf("lib1.jar".toPath(), "lib2.jar".toPath()),
            clientJarPath = "client.jar".toPath(),
            nativesDir = "natives".toPath(),
            assetsDir = "assets".toPath(),
            gameDir = "gameDir".toPath(),
            javaBinaryPath = "java",
            os = OperatingSystem.WINDOWS
        )

        assertTrue(command.contains("-Xms1024M"))
        assertTrue(command.contains("-Xmx2048M"))
        assertTrue(command.contains("-XX:+UseG1GC"))
        assertTrue(command.contains("net.minecraft.client.main.Main"))
        assertTrue(command.contains("PlayerOne"))
        assertTrue(command.contains("12345678-1234-1234-1234-123456789abc"))
        assertTrue(command.contains("legacy"))
    }

    @Test
    fun testModernArgumentBuilding() {
        val instance = Instance(
            id = "test-121",
            name = "Test 1.21.4",
            minecraftVersion = "1.21.4",
            minMemoryMb = 2048,
            maxMemoryMb = 6144,
            windowWidth = 1920,
            windowHeight = 1080
        )

        val account = OfflineAccount(
            id = "acc-offline",
            username = "EzzPlayer",
            uuid = "00000000-0000-0000-0000-000000000000"
        )

        val versionInfo = VersionInfo(
            id = "1.21.4",
            mainClass = "net.minecraft.client.main.Main"
        )

        val command = LaunchArgumentBuilder.buildLaunchCommand(
            instance = instance,
            account = account,
            versionInfo = versionInfo,
            classpathEntries = listOf("lib1.jar".toPath()),
            clientJarPath = "client.jar".toPath(),
            nativesDir = "natives".toPath(),
            assetsDir = "assets".toPath(),
            gameDir = "gameDir".toPath(),
            javaBinaryPath = "C:\\Program Files\\Java\\jdk-21\\bin\\java.exe",
            os = OperatingSystem.WINDOWS
        )

        assertTrue(command.contains("-Xms2048M"))
        assertTrue(command.contains("-Xmx6144M"))
        assertTrue(command.contains("-XX:+IgnoreUnrecognizedVMOptions"))
        assertTrue(command.contains("-Dsun.stdout.encoding=UTF-8"))
        assertTrue(command.contains("-Djava.net.preferIPv4Stack=true"))
        assertTrue(command.contains("--width"))
        assertTrue(command.contains("1920"))
        assertTrue(command.contains("--height"))
        assertTrue(command.contains("1080"))
    }
}
