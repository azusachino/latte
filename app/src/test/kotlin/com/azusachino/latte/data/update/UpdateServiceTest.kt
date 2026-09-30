package com.azusachino.latte.data.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testUpdateAvailable() = runBlocking {
        val mockJson = """
        {
            "tag_name": "v0.2.0",
            "name": "Latte v0.2.0",
            "body": "Bug fixes and improvements",
            "published_at": "2026-10-01T00:00:00Z",
            "assets": [
                {
                    "name": "latte-v0.2.0.apk",
                    "size": 12345678,
                    "browser_download_url": "${server.url("/download/latte-v0.2.0.apk")}"
                }
            ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val service = UpdateService(
            client = client,
            baseUrl = server.url("/").toString(),
        )

        val result = service.checkForUpdate(currentVersion = "0.1.2")
        assertTrue(result is UpdateCheckResult.Available)
        val available = result as UpdateCheckResult.Available
        assertEquals("0.2.0", available.updateInfo.versionName)
        assertEquals("latte-v0.2.0.apk", available.updateInfo.fileName)
        assertEquals(12345678L, available.updateInfo.fileSize)
        assertTrue(available.updateInfo.isDeviceSupported)
        assertFalse(available.updateInfo.isMandatory)
    }

    @Test
    fun testMinSdkAndMandatoryMetadata() = runBlocking {
        val releaseBody = """
        <!-- latte-meta: {"min_sdk": 31, "min_supported_version": "0.2.0"} -->
        ## What's Changed
        - Major breaking protocol update
        """.trimIndent()

        val mockJson = """
        {
            "tag_name": "v0.3.0",
            "name": "Latte v0.3.0",
            "body": ${kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer(), releaseBody)},
            "assets": [
                {
                    "name": "latte-v0.3.0.apk",
                    "size": 5000,
                    "browser_download_url": "${server.url("/download/latte-v0.3.0.apk")}"
                }
            ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val service = UpdateService(
            client = client,
            baseUrl = server.url("/").toString(),
        )

        // Device on Android 10 (API 29), app on 0.1.2 -> unsupported SDK, mandatory
        val resultApi29 = service.checkForUpdate(currentVersion = "0.1.2", deviceSdk = 29)
        assertTrue(resultApi29 is UpdateCheckResult.Available)
        val info29 = (resultApi29 as UpdateCheckResult.Available).updateInfo
        assertEquals(31, info29.minSdk)
        assertEquals("0.2.0", info29.minSupportedVersion)
        assertFalse("API 29 should not be supported when minSdk is 31", info29.isDeviceSupported)
        assertTrue("Current version 0.1.2 < minSupported 0.2.0 should be mandatory", info29.isMandatory)
        assertFalse(info29.releaseNotes.contains("latte-meta"))
        assertTrue(info29.releaseNotes.contains("Major breaking protocol update"))

        // Device on Android 12 (API 31), app on 0.2.0 -> supported SDK, optional
        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))
        val resultApi31 = service.checkForUpdate(currentVersion = "0.2.0", deviceSdk = 31)
        assertTrue(resultApi31 is UpdateCheckResult.Available)
        val info31 = (resultApi31 as UpdateCheckResult.Available).updateInfo
        assertTrue("API 31 should be supported", info31.isDeviceSupported)
        assertFalse("Current version 0.2.0 >= minSupported 0.2.0 should be optional", info31.isMandatory)
    }

    @Test
    fun testUpToDate() = runBlocking {
        val mockJson = """
        {
            "tag_name": "v0.1.2",
            "name": "Latte v0.1.2",
            "body": "Current release",
            "assets": [
                {
                    "name": "latte-v0.1.2.apk",
                    "size": 1000,
                    "browser_download_url": "https://example.com/latte.apk"
                }
            ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val service = UpdateService(
            client = client,
            baseUrl = server.url("/").toString(),
        )

        val result = service.checkForUpdate(currentVersion = "0.1.2")
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun testNoApkAsset() = runBlocking {
        val mockJson = """
        {
            "tag_name": "v0.3.0",
            "assets": [
                {
                    "name": "source.zip",
                    "size": 1000,
                    "browser_download_url": "https://example.com/source.zip"
                }
            ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val service = UpdateService(
            client = client,
            baseUrl = server.url("/").toString(),
        )

        val result = service.checkForUpdate(currentVersion = "0.1.2")
        assertTrue(result is UpdateCheckResult.Error)
    }
}
