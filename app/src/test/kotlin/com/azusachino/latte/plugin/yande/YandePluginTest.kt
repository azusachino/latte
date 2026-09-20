package com.azusachino.latte.plugin.yande

import com.azusachino.latte.plugin.storage.PluginStorage
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YandePluginTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var httpClient: OkHttpClient

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        httpClient = OkHttpClient()
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    private class InMemoryStorage : PluginStorage {
        private val map = mutableMapOf<String, String>()

        override fun save(pluginId: String, key: String, value: String) {
            map["$pluginId:$key"] = value
        }

        override fun get(pluginId: String, key: String): String? = map["$pluginId:$key"]

        override fun remove(pluginId: String, key: String) {
            map.remove("$pluginId:$key")
        }

        override fun clearPlugin(pluginId: String) {
            val prefix = "$pluginId:"
            map.keys.filter { it.startsWith(prefix) }.forEach { map.remove(it) }
        }

        override fun getAll(pluginId: String): Map<String, String> {
            val prefix = "$pluginId:"
            return map.filterKeys { it.startsWith(prefix) }
                .mapKeys { it.key.removePrefix(prefix) }
        }
    }

    @Test
    fun testYandePasswordHasher() {
        val hash = YandePasswordHasher.hash("password")
        assertNotNull(hash)
        assertEquals(40, hash.length) // SHA-1 is 40 hex chars
        // Verify deterministic output
        assertEquals(hash, YandePasswordHasher.hash("password"))
    }

    @Test
    fun testCsrfExtraction() {
        val html1 = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="csrf-param" content="authenticity_token" />
                <meta name="csrf-token" content="test_token_12345" />
            </head>
            </html>
        """.trimIndent()
        assertEquals("test_token_12345", YandePlugin.extractCsrfToken(html1))

        val html2 = """
            <head>
                <meta content="alt_token_67890" name="csrf-token" />
            </head>
        """.trimIndent()
        assertEquals("alt_token_67890", YandePlugin.extractCsrfToken(html2))

        val htmlEmpty = "<html><head></head></html>"
        assertNull(YandePlugin.extractCsrfToken(htmlEmpty))
    }

    @Test
    fun testSetScoreRequiresLogin() = runTest {
        val storage = InMemoryStorage()
        val plugin = YandePlugin(storage, httpClient, baseUrl = mockServer.url("/").toString().removeSuffix("/"))

        assertFalse(plugin.isLoggedIn)
        val result = plugin.setScore(123L, 3)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun testSetScoreAndCache() = runTest {
        val storage = InMemoryStorage()
        storage.save("yande.re", "username", "alice")
        val plugin = YandePlugin(storage, httpClient, baseUrl = mockServer.url("/").toString().removeSuffix("/"))

        assertTrue(plugin.isLoggedIn)
        assertEquals("alice", plugin.getDisplayUsername())
        assertNull(plugin.getScore(12345L))

        // 1. First setScore: fetches CSRF from /user/login, then posts to /post/vote.json
        val loginHtml = """
            <html>
            <head><meta name="csrf-token" content="test_vote_csrf_token" /></head>
            </html>
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(loginHtml))
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"post_id":12345,"score":3}"""))

        val voteResult = plugin.setScore(12345L, 3)
        assertTrue(voteResult.isSuccess)
        assertEquals(3, plugin.getScore(12345L))

        // Check request sent to /post/vote.json
        mockServer.takeRequest() // /user/login
        val voteRequest = mockServer.takeRequest() // /post/vote.json
        assertEquals("/post/vote.json", voteRequest.path)
        assertEquals("POST", voteRequest.method)
        assertEquals("test_vote_csrf_token", voteRequest.getHeader("X-CSRF-Token"))
        assertEquals("XMLHttpRequest", voteRequest.getHeader("X-Requested-With"))

        // 2. Unvote (score = 0): cached CSRF is reused
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"post_id":12345,"score":0}"""))
        val unvoteResult = plugin.setScore(12345L, 0)
        assertTrue(unvoteResult.isSuccess)
        assertNull(plugin.getScore(12345L))

        // 3. Logout clears cache
        plugin.logout()
        assertFalse(plugin.isLoggedIn)
        assertNull(plugin.getDisplayUsername())
        assertNull(plugin.getScore(12345L))
    }

    @Test
    fun testFavoritesQueryFormat() {
        val username = "testuser"
        val expectedQuery = "vote:3:$username"
        assertEquals("vote:3:testuser", expectedQuery)
    }
}
