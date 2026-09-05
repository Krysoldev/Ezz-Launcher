package io.ezz.launcher.core.runtime.discord

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DiscordRpcPayloadTest {

    private val service = DiscordRpcService()

    @Test
    fun testLauncherPresencePayloadWithAccount() {
        val payloadJson = service.buildLauncherPayload(
            username = "KrysolDev",
            avatarUrl = null,
            uuid = "ad17221c-781d-4ec5-aca6-f5069fbced7b",
            processId = 1000L,
            nonce = "test-launcher-nonce"
        )

        val json = Json.parseToJsonElement(payloadJson).jsonObject
        assertEquals("SET_ACTIVITY", json["cmd"]?.jsonPrimitive?.content)
        assertEquals("test-launcher-nonce", json["nonce"]?.jsonPrimitive?.content)

        val args = json["args"]?.jsonObject
        assertNotNull(args)

        val activity = args["activity"]?.jsonObject
        assertNotNull(activity)
        assertEquals("Ezz Launcher", activity["name"]?.jsonPrimitive?.content)
        assertEquals(0, activity["type"]?.jsonPrimitive?.long?.toInt())
        assertEquals("KrysolDev", activity["details"]?.jsonPrimitive?.content)
        assertEquals("Ready to play", activity["state"]?.jsonPrimitive?.content)

        // No session timestamps for launcher presence
        assertNull(activity["timestamps"])

        val assets = activity["assets"]?.jsonObject
        assertNotNull(assets)
        assertEquals("ezzlauncher", assets["large_image"]?.jsonPrimitive?.content)
        assertEquals("Ezz Launcher", assets["large_text"]?.jsonPrimitive?.content)
        assertEquals(
            "https://minotar.net/helm/ad17221c781d4ec5aca6f5069fbced7b/128.png",
            assets["small_image"]?.jsonPrimitive?.content
        )
        assertEquals("KrysolDev", assets["small_text"]?.jsonPrimitive?.content)
    }

    @Test
    fun testLauncherPresencePayloadWithoutAccount() {
        val payloadJson = service.buildLauncherPayload(
            username = null,
            avatarUrl = null,
            uuid = null,
            processId = 1000L,
            nonce = "test-guest-nonce"
        )

        val json = Json.parseToJsonElement(payloadJson).jsonObject
        val activity = json["args"]?.jsonObject?.get("activity")?.jsonObject
        assertNotNull(activity)
        assertEquals("Ezz Launcher", activity["name"]?.jsonPrimitive?.content)
        assertEquals("Ready to play", activity["details"]?.jsonPrimitive?.content)
        assertNull(activity["state"])
        assertNull(activity["timestamps"])

        val assets = activity["assets"]?.jsonObject
        assertNotNull(assets)
        assertEquals("ezzlauncher", assets["large_image"]?.jsonPrimitive?.content)
        assertEquals("Ezz Launcher", assets["large_text"]?.jsonPrimitive?.content)
        assertNull(assets["small_image"])
    }

    @Test
    fun testMicrosoftAccountMinecraftPayload() {
        val payloadJson = service.buildActivityPayload(
            playerUsername = "KrysolDev",
            minecraftVersion = "1.21.11",
            playerUuid = "ad17221c-781d-4ec5-aca6-f5069fbced7b",
            startedAtMs = 1788528996000L,
            processId = 12345L,
            nonce = "test-nonce-1"
        )

        val json = Json.parseToJsonElement(payloadJson).jsonObject
        assertEquals("SET_ACTIVITY", json["cmd"]?.jsonPrimitive?.content)
        assertEquals("test-nonce-1", json["nonce"]?.jsonPrimitive?.content)

        val args = json["args"]?.jsonObject
        assertNotNull(args)
        assertEquals(12345L, args["pid"]?.jsonPrimitive?.long)

        val activity = args["activity"]?.jsonObject
        assertNotNull(activity)
        assertEquals("Ezz Launcher", activity["name"]?.jsonPrimitive?.content)
        assertEquals(0, activity["type"]?.jsonPrimitive?.long?.toInt())
        assertEquals("Playing Minecraft", activity["details"]?.jsonPrimitive?.content)
        assertEquals("Minecraft 1.21.11", activity["state"]?.jsonPrimitive?.content)

        val timestamps = activity["timestamps"]?.jsonObject
        assertNotNull(timestamps)
        assertEquals(1788528996L, timestamps["start"]?.jsonPrimitive?.long)

        val assets = activity["assets"]?.jsonObject
        assertNotNull(assets)
        assertEquals("ezzlauncher", assets["large_image"]?.jsonPrimitive?.content)
        assertEquals("Ezz Launcher", assets["large_text"]?.jsonPrimitive?.content)
        assertEquals(
            "https://minotar.net/helm/ad17221c781d4ec5aca6f5069fbced7b/128.png",
            assets["small_image"]?.jsonPrimitive?.content
        )
        assertEquals("KrysolDev", assets["small_text"]?.jsonPrimitive?.content)
    }

    @Test
    fun testOfflineAccountPayloadAndSwitching() {
        val payloadAlice = service.buildActivityPayload(
            playerUsername = "AlicePlayer",
            minecraftVersion = "1.20.4",
            playerUuid = null,
            startedAtMs = 1700000000000L,
            processId = 2222L,
            nonce = "nonce-alice"
        )

        val jsonAlice = Json.parseToJsonElement(payloadAlice).jsonObject
        val actAlice = jsonAlice["args"]?.jsonObject?.get("activity")?.jsonObject
        assertNotNull(actAlice)
        assertEquals("Ezz Launcher", actAlice["name"]?.jsonPrimitive?.content)
        assertEquals("Playing Minecraft", actAlice["details"]?.jsonPrimitive?.content)
        assertEquals("Minecraft 1.20.4", actAlice["state"]?.jsonPrimitive?.content)
        assertEquals(
            "https://minotar.net/helm/AlicePlayer/128.png",
            actAlice["assets"]?.jsonObject?.get("small_image")?.jsonPrimitive?.content
        )
        assertEquals(
            "AlicePlayer",
            actAlice["assets"]?.jsonObject?.get("small_text")?.jsonPrimitive?.content
        )

        // Switch to Bob
        val payloadBob = service.buildActivityPayload(
            playerUsername = "BobBuilder",
            minecraftVersion = "1.16.5",
            playerUuid = null,
            startedAtMs = 1700000050000L,
            processId = 3333L,
            nonce = "nonce-bob"
        )

        val jsonBob = Json.parseToJsonElement(payloadBob).jsonObject
        val actBob = jsonBob["args"]?.jsonObject?.get("activity")?.jsonObject
        assertNotNull(actBob)
        assertEquals("Ezz Launcher", actBob["name"]?.jsonPrimitive?.content)
        assertEquals("Playing Minecraft", actBob["details"]?.jsonPrimitive?.content)
        assertEquals("Minecraft 1.16.5", actBob["state"]?.jsonPrimitive?.content)
        assertEquals(
            "https://minotar.net/helm/BobBuilder/128.png",
            actBob["assets"]?.jsonObject?.get("small_image")?.jsonPrimitive?.content
        )
        assertEquals(
            "BobBuilder",
            actBob["assets"]?.jsonObject?.get("small_text")?.jsonPrimitive?.content
        )
    }

    @Test
    fun testAccountSwitchingRetainsEzzLauncherIdentity() {
        // KrysolDev -> Steve in Launcher Mode
        val krysolLauncherJson = service.buildLauncherPayload(username = "KrysolDev", avatarUrl = null)
        val krysolLauncherAct = Json.parseToJsonElement(krysolLauncherJson).jsonObject["args"]?.jsonObject?.get("activity")?.jsonObject
        assertNotNull(krysolLauncherAct)
        assertEquals("Ezz Launcher", krysolLauncherAct["name"]?.jsonPrimitive?.content)
        assertEquals("KrysolDev", krysolLauncherAct["details"]?.jsonPrimitive?.content)
        assertEquals("Ready to play", krysolLauncherAct["state"]?.jsonPrimitive?.content)

        val steveLauncherJson = service.buildLauncherPayload(username = "Steve", avatarUrl = null)
        val steveLauncherAct = Json.parseToJsonElement(steveLauncherJson).jsonObject["args"]?.jsonObject?.get("activity")?.jsonObject
        assertNotNull(steveLauncherAct)
        assertEquals("Ezz Launcher", steveLauncherAct["name"]?.jsonPrimitive?.content)
        assertEquals("Steve", steveLauncherAct["details"]?.jsonPrimitive?.content)
        assertEquals("Ready to play", steveLauncherAct["state"]?.jsonPrimitive?.content)

        // KrysolDev -> Steve in Minecraft Mode
        val krysolMcJson = service.buildActivityPayload(playerUsername = "KrysolDev", minecraftVersion = "1.21.11")
        val krysolMcAct = Json.parseToJsonElement(krysolMcJson).jsonObject["args"]?.jsonObject?.get("activity")?.jsonObject
        assertNotNull(krysolMcAct)
        assertEquals("Ezz Launcher", krysolMcAct["name"]?.jsonPrimitive?.content)
        assertEquals("Playing Minecraft", krysolMcAct["details"]?.jsonPrimitive?.content)
        assertEquals("Minecraft 1.21.11", krysolMcAct["state"]?.jsonPrimitive?.content)
        assertEquals("KrysolDev", krysolMcAct["assets"]?.jsonObject?.get("small_text")?.jsonPrimitive?.content)

        val steveMcJson = service.buildActivityPayload(playerUsername = "Steve", minecraftVersion = "1.21.11")
        val steveMcAct = Json.parseToJsonElement(steveMcJson).jsonObject["args"]?.jsonObject?.get("activity")?.jsonObject
        assertNotNull(steveMcAct)
        assertEquals("Ezz Launcher", steveMcAct["name"]?.jsonPrimitive?.content)
        assertEquals("Playing Minecraft", steveMcAct["details"]?.jsonPrimitive?.content)
        assertEquals("Minecraft 1.21.11", steveMcAct["state"]?.jsonPrimitive?.content)
        assertEquals("Steve", steveMcAct["assets"]?.jsonObject?.get("small_text")?.jsonPrimitive?.content)
    }

    @Test
    fun testInstanceNameCustomState() {
        val payload = service.buildMinecraftPayload(
            playerUsername = "KrysolDev",
            minecraftVersion = "1.21.11",
            instanceName = "Fabric Speedrun"
        )
        val json = Json.parseToJsonElement(payload).jsonObject
        val act = json["args"]?.jsonObject?.get("activity")?.jsonObject
        assertNotNull(act)
        assertEquals("Ezz Launcher", act["name"]?.jsonPrimitive?.content)
        assertEquals("Playing Minecraft", act["details"]?.jsonPrimitive?.content)
        assertEquals("Fabric Speedrun (Minecraft 1.21.11)", act["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun testResolveAvatarUrlFallbacks() {
        // Explicit avatarUrl
        val custom = service.resolveAvatarUrl("https://custom.com/avatar.png", null, "SomeUser")
        assertEquals("https://custom.com/avatar.png", custom)

        // UUID
        val byUuid = service.resolveAvatarUrl(null, "069a79f4-44e9-4726-a5be-fca90e38aaf5", "Notch")
        assertEquals("https://minotar.net/helm/069a79f444e94726a5befca90e38aaf5/128.png", byUuid)

        // Username
        val byUser = service.resolveAvatarUrl(null, null, "NyxKrishna")
        assertEquals("https://minotar.net/helm/NyxKrishna/128.png", byUser)

        // Fallback Steve
        val fallback = service.resolveAvatarUrl(null, null, "")
        assertEquals("https://minotar.net/helm/Steve/128.png", fallback)
    }

    @Test
    fun testVersionPrefixHandling() {
        val payload = service.buildActivityPayload(
            playerUsername = "Player",
            minecraftVersion = "Minecraft 1.21.1",
            startedAtMs = 1000000L
        )
        val json = Json.parseToJsonElement(payload).jsonObject
        val state = json["args"]?.jsonObject?.get("activity")?.jsonObject?.get("state")?.jsonPrimitive?.content
        assertEquals("Minecraft 1.21.1", state)
    }
}
