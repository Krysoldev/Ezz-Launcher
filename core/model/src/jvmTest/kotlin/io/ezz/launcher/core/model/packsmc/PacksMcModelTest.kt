package io.ezz.launcher.core.model.packsmc

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PacksMcModelTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun testDeserializeListResponse() {
        val rawJson = """
        {
          "data": [
            {
              "id": "f1d8c1e2-3b4a-5c6d-7e8f-9a0b1c2d3e4f",
              "slug": "blood-default-1-9",
              "name": "Blood Default 1.9",
              "resolution": "16x",
              "gamemodes": ["PVP_UHC"],
              "mc_versions": ["1.8.9", "1.21.4"],
              "thumbnail_url": "https://pub-38d24c371f5a443584563fcc0c95e875.r2.dev/thumb.webp",
              "gallery": ["https://pub-38d24c371f5a443584563fcc0c95e875.r2.dev/1.webp"],
              "downloads": 8421,
              "likes": 312,
              "views": 40233,
              "file_size_bytes": 2118041,
              "license": {
                "code": "CREDIT_REQUIRED",
                "label": "Credit Required",
                "requires_credit": true,
                "allows_redistribution": true
              },
              "author": {
                "username": "Steve",
                "display_name": "Steve The Crafter",
                "verified": true
              },
              "download_url": "https://packsmc.com/pack/blood-default-1-9",
              "is_exclusive": false
            }
          ],
          "next_cursor": "2026-05-04T12:01:00.000Z"
        }
        """.trimIndent()

        val response = json.decodeFromString<PacksMcListResponse>(rawJson)
        assertEquals(1, response.data.size)
        assertEquals("2026-05-04T12:01:00.000Z", response.nextCursor)

        val pack = response.data[0]
        assertEquals("f1d8c1e2-3b4a-5c6d-7e8f-9a0b1c2d3e4f", pack.id)
        assertEquals("blood-default-1-9", pack.slug)
        assertEquals("Blood Default 1.9", pack.name)
        assertEquals("16x", pack.resolution)
        assertEquals(listOf("PVP_UHC"), pack.gamemodes)
        assertEquals(listOf("1.8.9", "1.21.4"), pack.mcVersions)
        assertEquals(8421L, pack.downloads)
        assertEquals(312L, pack.likes)
        assertEquals(40233L, pack.views)
        assertFalse(pack.isExclusive)
        assertEquals("CREDIT_REQUIRED", pack.license?.code)
        assertTrue(pack.license?.requiresCredit == true)
        assertEquals("Steve", pack.author?.username)
        assertTrue(pack.author?.verified == true)
        assertEquals("https://packsmc.com/pack/blood-default-1-9", pack.downloadUrl)
    }

    @Test
    fun testDeserializePackDetails() {
        val rawJson = """
        {
          "id": "f1d8c1e2-3b4a-5c6d-7e8f-9a0b1c2d3e4f",
          "slug": "blood-default-1-9",
          "name": "Blood Default 1.9",
          "description": "Clean red PvP edit with short swords and low fire.",
          "resolution": "16x",
          "gamemodes": ["PVP_UHC"],
          "gallery": ["https://pub-38d24c371f5a443584563fcc0c95e875.r2.dev/1.webp"],
          "tags": ["red", "clean", "uhc"],
          "credits": [
            {
              "username": "Alex",
              "role": "collaborator",
              "verified": false
            }
          ],
          "license": {
            "code": "CREDIT_REQUIRED",
            "requires_credit": true,
            "allows_redistribution": true
          },
          "downloads": 8421,
          "likes": 312,
          "views": 40233,
          "is_exclusive": false,
          "versions": [
            {
              "mc_version": "1.21.4",
              "verified_safe": true,
              "created_at": "2026-04-01T00:00:00.000Z"
            }
          ],
          "features": ["optifine sky", "custom GUI"],
          "author": {
            "username": "Steve",
            "verified": true
          },
          "download_url": "https://packsmc.com/pack/blood-default-1-9"
        }
        """.trimIndent()

        val pack = json.decodeFromString<PacksMcPack>(rawJson)
        assertEquals("Clean red PvP edit with short swords and low fire.", pack.description)
        assertEquals(listOf("red", "clean", "uhc"), pack.tags)
        assertEquals(1, pack.credits.size)
        assertEquals("Alex", pack.credits[0].username)
        assertEquals("collaborator", pack.credits[0].role)
        assertEquals(1, pack.versions.size)
        assertEquals("1.21.4", pack.versions[0].mcVersion)
        assertTrue(pack.versions[0].verifiedSafe)
        assertEquals(listOf("optifine sky", "custom GUI"), pack.features)
    }

    @Test
    fun testDeserializeIdentityAndError() {
        val identityJson = """
        {
          "id": "5a1f0000-0000-0000-0000-000000000000",
          "username": "Steve",
          "display_name": "Steve",
          "verified": true,
          "packs_plus": false,
          "tier": "free",
          "limits": {
            "daily_quota": 1000,
            "per_minute": 60,
            "per_ip_per_minute": 120
          },
          "counts": {
            "total_packs": 12,
            "total_downloads": 8421
          },
          "can": {
            "list_packs": true,
            "view_pack_metadata": true,
            "get_pack_web_urls": true,
            "submit_conversions": false
          }
        }
        """.trimIndent()

        val identity = json.decodeFromString<PacksMcIdentityResponse>(identityJson)
        assertEquals("Steve", identity.username)
        assertEquals(1000, identity.limits?.dailyQuota)
        assertTrue(identity.can?.listPacks == true)
        assertFalse(identity.can?.submitConversions == true)

        val errorJson = """
        {
          "error": {
            "code": "rate_limited",
            "message": "Rate limit exceeded (60 req / 60s)"
          }
        }
        """.trimIndent()

        val error = json.decodeFromString<PacksMcErrorResponse>(errorJson)
        assertNotNull(error.error)
        assertEquals("rate_limited", error.error?.code)
    }
}
