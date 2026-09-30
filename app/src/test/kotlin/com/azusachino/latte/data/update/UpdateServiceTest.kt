package com.azusachino.latte.data.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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
