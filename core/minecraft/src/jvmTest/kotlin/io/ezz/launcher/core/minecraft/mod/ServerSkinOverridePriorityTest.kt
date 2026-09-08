package io.ezz.launcher.core.minecraft.mod

import java.io.File
import java.net.URLClassLoader
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerSkinOverridePriorityTest {

    // Simple test mock simulating a GameProfile with properties
    class MockGameProfile(
        val id: UUID,
        val name: String
    ) {
        val properties = MockPropertyMap()
        fun getProfile(): Any = this
    }

    class MockPropertyMap {
        private val map = mutableMapOf<String, MutableList<MockProperty>>()

        fun put(key: String, prop: MockProperty) {
            map.getOrPut(key) { mutableListOf() }.add(prop)
        }

        fun removeAll(key: String) {
            map.remove(key)
        }

        fun get(key: String): Collection<MockProperty> = map[key] ?: emptyList()
    }

    class MockProperty(
        val name: String,
        val value: String,
        val signature: String? = null
    )

    @Test
    fun testServerSkinOverrideLifecycle_AcrossAllScenarios() {
        val tempDir = File.createTempFile("test_server_override_lifecycle", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        val localUuid = UUID.randomUUID()
        val localUsername = "EzzLocalPlayer"
        val remoteUuid = UUID.randomUUID()
        val remoteUsername = "RemoteGamer"

        // Set up test config in temp directory
        val runDir = tempDir.resolve("instance_test").apply { mkdirs() }
        val configDir = runDir.resolve("config").apply { mkdirs() }
        val ezzSkinDir = configDir.resolve("ezz-skin").apply { mkdirs() }
        val skinFile = ezzSkinDir.resolve("skin.png")
        skinFile.writeBytes("VAULT_SKIN_A_BYTES".toByteArray())

        val configFile = configDir.resolve("ezz-skin-config.json")
        configFile.writeText(
            """
            {
                "enabled": true,
                "username": "$localUsername",
                "uuid": "$localUuid",
                "accountId": "acc-qa-1",
                "skinId": "skin-vault-a",
                "skinHash": "vaultAHash",
                "model": "STEVE",
                "skinFile": "config/ezz-skin/skin.png"
            }
            """.trimIndent()
        )

        // Load EzzSkinMod classes from the built 1.21 JAR
        val entry = FabricSkinModManager.resolveModEntry("1.21.4")
        assertNotNull(entry, "Mod entry for 1.21.4 must exist")
        val jarBytes = FabricSkinModManager.getModJarBytes(entry)
        val jarFile = tempDir.resolve("ezz-skin-mod-1.21.jar")
        jarFile.writeBytes(jarBytes)

        val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), javaClass.classLoader)
        try {
            val commonClass = classLoader.loadClass("io.ezz.skinmod.common.EzzSkinModCommon")
            val providerClass = classLoader.loadClass("io.ezz.skinmod.common.EzzSkinTextureProvider")

            // Initialize EzzSkinModCommon
            commonClass.getMethod("init", File::class.java).invoke(null, runDir)
            providerClass.getMethod("initLocalPlayerIdentity").invoke(null)

            val isLocalPlayerMethod = providerClass.getMethod("isLocalPlayer", Any::class.java)
            val hasServerSkinOverrideMethod = providerClass.getMethod("hasServerSkinOverride")
            val resetServerOverrideMethod = providerClass.getMethod("resetServerOverride")
            val onGameJoinMethod = providerClass.getMethod("onGameJoin")
            val updateServerSkinStateMethod = providerClass.getMethod("updateServerSkinState", Any::class.java)
            val extractSkinTextureValueMethod = providerClass.getMethod("extractSkinTextureValue", Any::class.java)
            val getCustomSkinTextureMethod = providerClass.getMethod("getCustomSkinTexture", Any::class.java)
            val getCustomModelMethod = providerClass.getMethod("getCustomModel", Any::class.java)
            val getDiagnosticReportLinesMethod = providerClass.getMethod("getDiagnosticReportLines")

            val shouldApplyVaultSkinMethod = providerClass.getMethod("shouldApplyVaultSkin", Any::class.java)

            // 1. Initial State: Local Player is identified
            assertTrue(isLocalPlayerMethod.invoke(null, localUuid) as Boolean)
            assertTrue(isLocalPlayerMethod.invoke(null, localUsername) as Boolean)
            assertFalse(isLocalPlayerMethod.invoke(null, remoteUuid) as Boolean)
            assertFalse(isLocalPlayerMethod.invoke(null, remoteUsername) as Boolean)

            // Initial server override must be FALSE (Vault skin active)
            assertFalse(hasServerSkinOverrideMethod.invoke(null) as Boolean)

            // 2. Connect to server (simulate onGameJoin)
            onGameJoinMethod.invoke(null)
            assertFalse(hasServerSkinOverrideMethod.invoke(null) as Boolean)

            // Create Local Player GameProfile with initial join state (no custom skin yet)
            val localProfile = MockGameProfile(localUuid, localUsername)
            updateServerSkinStateMethod.invoke(null, localProfile)

            // Still Vault fallback because no server-side skin update has arrived
            assertFalse(hasServerSkinOverrideMethod.invoke(null) as Boolean)
            assertTrue(shouldApplyVaultSkinMethod.invoke(null, localProfile) as Boolean, "Vault skin must apply when server provides no skin")

            // 3. Server changes local player skin to Skin B (via SkinsRestorer /skin)
            val skinBBase64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvc2tpbkJ1cmwifX19"
            localProfile.properties.put("textures", MockProperty("textures", skinBBase64, "sigB"))

            // Client receives server skin update packet
            updateServerSkinStateMethod.invoke(null, localProfile)

            // Extracted texture value must match Skin B
            val extractedValB = extractSkinTextureValueMethod.invoke(null, localProfile) as? String
            assertEquals(skinBBase64, extractedValB)

            // Server override must now be ACTIVE!
            assertTrue(
                hasServerSkinOverrideMethod.invoke(null) as Boolean,
                "Server skin override must be active after server sends Skin B"
            )
            assertFalse(
                shouldApplyVaultSkinMethod.invoke(null, localProfile) as Boolean,
                "Vault skin must yield (return false) when server skin override is active"
            )

            // Local Vault getter must return null, allowing the server skin to render!
            assertNull(
                getCustomSkinTextureMethod.invoke(null, localProfile),
                "Local player custom skin must yield to server-provided skin B"
            )
            assertNull(
                getCustomModelMethod.invoke(null, localProfile),
                "Local player model must yield to server model"
            )

            // Diagnostics must reflect SERVER_OVERRIDE status
            val diagReport = getDiagnosticReportLinesMethod.invoke(null) as Array<*>
            val diagText = diagReport.filterIsInstance<String>().joinToString("\n")
            assertTrue(diagText.contains("SERVER_OVERRIDE"), "Diagnostics must report SERVER_OVERRIDE as active source")
            assertTrue(diagText.contains("SkinRestorer / Server Skin"), "Diagnostics must mention active server skin")

            // 4. Remote Player Safety Check: Remote player must NEVER be overridden!
            val remoteProfile = MockGameProfile(remoteUuid, remoteUsername)
            assertFalse(isLocalPlayerMethod.invoke(null, remoteProfile) as Boolean)
            assertNull(getCustomSkinTextureMethod.invoke(null, remoteProfile))
            assertNull(getCustomModelMethod.invoke(null, remoteProfile))

            // 5. Live Skin Switch: Server changes from Skin B to Skin C while already connected!
            val skinCBase64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvc2tpbkN1cmwifX19"
            localProfile.properties.removeAll("textures")
            localProfile.properties.put("textures", MockProperty("textures", skinCBase64, "sigC"))

            updateServerSkinStateMethod.invoke(null, localProfile)
            assertTrue(
                hasServerSkinOverrideMethod.invoke(null) as Boolean,
                "Server skin override must remain active for Skin C live change"
            )
            assertEquals(skinCBase64, extractSkinTextureValueMethod.invoke(null, localProfile) as? String)

            // 6. Live Skin Switch: Server changes from Skin C to Skin D!
            val skinDBase64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvc2tpbkR1cmwifX19"
            localProfile.properties.removeAll("textures")
            localProfile.properties.put("textures", MockProperty("textures", skinDBase64, "sigD"))

            updateServerSkinStateMethod.invoke(null, localProfile)
            assertTrue(
                hasServerSkinOverrideMethod.invoke(null) as Boolean,
                "Server skin override must remain active for Skin D live change"
            )
            assertEquals(skinDBase64, extractSkinTextureValueMethod.invoke(null, localProfile) as? String)

            // 7. Disconnect / Leave Server: Temporary server override state MUST be cleared!
            resetServerOverrideMethod.invoke(null)
            assertFalse(
                hasServerSkinOverrideMethod.invoke(null) as Boolean,
                "Server override must be cleared immediately upon disconnect"
            )

            // Vault skin must be active again!
            val diagAfterDisconnect = (getDiagnosticReportLinesMethod.invoke(null) as Array<*>).joinToString("\n")
            assertTrue(diagAfterDisconnect.contains("EZZ_VAULT"), "Diagnostics must show EZZ_VAULT source restored after disconnect")

            // 8. Reconnect: State starts clean on new server session
            onGameJoinMethod.invoke(null)
            assertFalse(
                hasServerSkinOverrideMethod.invoke(null) as Boolean,
                "Server override must start clean on reconnect"
            )

        } finally {
            classLoader.close()
        }
    }

    @Test
    fun testAllVersionJars_HaveServerSkinOverrideSupport() {
        for (entry in FabricSkinModManager.registry.entries) {
            val jarBytes = FabricSkinModManager.getModJarBytes(entry)
            assertTrue(jarBytes.isNotEmpty(), "JAR bytes for ${entry.jarName} must not be empty")

            val tempJar = File.createTempFile("test_override_${entry.versionFamily}", ".jar").apply {
                deleteOnExit()
                writeBytes(jarBytes)
            }

            val classLoader = URLClassLoader(arrayOf(tempJar.toURI().toURL()), javaClass.classLoader)
            try {
                val providerClass = classLoader.loadClass("io.ezz.skinmod.common.EzzSkinTextureProvider")
                assertNotNull(providerClass.getMethod("hasServerSkinOverride"), "${entry.jarName} must have hasServerSkinOverride")
                assertNotNull(providerClass.getMethod("resetServerOverride"), "${entry.jarName} must have resetServerOverride")
                assertNotNull(providerClass.getMethod("onGameJoin"), "${entry.jarName} must have onGameJoin")
                assertNotNull(providerClass.getMethod("updateServerSkinState", Any::class.java), "${entry.jarName} must have updateServerSkinState")
                assertNotNull(providerClass.getMethod("extractSkinTextureValue", Any::class.java), "${entry.jarName} must have extractSkinTextureValue")
                assertNotNull(providerClass.getMethod("extractGameProfile", Any::class.java), "${entry.jarName} must have extractGameProfile")
                assertNotNull(providerClass.getMethod("shouldApplyVaultSkin", Any::class.java), "${entry.jarName} must have shouldApplyVaultSkin")

                // Verify ClientPlayNetworkHandlerMixin contains no references to method_2874
                val mixinEntry = "io/ezz/skinmod/mixin/ClientPlayNetworkHandlerMixin.class"
                val zipFile = java.util.zip.ZipFile(tempJar)
                try {
                    val entryZip = zipFile.getEntry(mixinEntry)
                    if (entryZip != null) {
                        val classBytes = zipFile.getInputStream(entryZip).readBytes()
                        val classContent = String(classBytes, Charsets.ISO_8859_1)
                        assertFalse(
                            classContent.contains("method_2874"),
                            "${entry.jarName} ClientPlayNetworkHandlerMixin must NEVER reference method_2874 (which returns PlayerListEntry)"
                        )
                        assertTrue(
                            classContent.contains("method_54134") || classContent.contains("method_47658") || classContent.contains("method_2868"),
                            "${entry.jarName} ClientPlayNetworkHandlerMixin must reference correct clearWorld void method"
                        )
                    }
                } finally {
                    zipFile.close()
                }
            } finally {
                classLoader.close()
            }
        }
    }
}
