package com.azusachino.latte.plugin.pixiv

import com.azusachino.latte.plugin.AuthFlow
import com.azusachino.latte.plugin.storage.PluginStorage
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PixivPluginTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun tokenImportPersistsSessionWithoutAcceptingPassword() = runTest {
        val storage = InMemoryStorage()
        val plugin = plugin(storage)

        assertFalse(plugin.isLoggedIn)
        assertTrue(AuthFlow.TOKEN_IMPORT in plugin.supportedAuthFlows)
        assertTrue(AuthFlow.CREDENTIALS !in plugin.supportedAuthFlows)

        val result = plugin.login(
            mapOf(
                "access_token" to "access-token",
                "refresh_token" to "refresh-token",
                "user_id" to "42",
                "username" to "artist",
                "password" to "must-not-be-used",
            ),
        )

        assertTrue(result.isSuccess)
        assertTrue(plugin.isLoggedIn)
        assertEquals("artist", plugin.getDisplayUsername())
        assertEquals("access-token", storage.get("pixiv", "access_token"))
        assertEquals("refresh-token", storage.get("pixiv", "refresh_token"))
        assertEquals("42", storage.get("pixiv", "user_id"))
    }

    @Test
    fun logoutPurgesAllPixivSessionFields() = runTest {
        val storage = InMemoryStorage()
        val plugin = plugin(storage)
        plugin.login(mapOf("access_token" to "access-token", "user_id" to "42"))

        plugin.logout()

        assertFalse(plugin.isLoggedIn)
        assertTrue(storage.getAll("pixiv").isEmpty())
    }

    @Test
    fun bookmarkMutationIsDelegatedToPixivApi() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val plugin = plugin(InMemoryStorage())
        plugin.login(mapOf("access_token" to "access-token", "user_id" to "42"))

        assertTrue(plugin.setBookmark(75034219, bookmarked = true).isSuccess)
        assertEquals("/v2/illust/bookmark/add", server.takeRequest().path)
    }

    private fun plugin(storage: PluginStorage): PixivPlugin = PixivPlugin(
        storage = storage,
        httpClient = OkHttpClient(),
        apiBaseUrl = server.url("/").toString().trimEnd('/'),
    )

    private class InMemoryStorage : PluginStorage {
        private val values = mutableMapOf<String, String>()

        override fun save(pluginId: String, key: String, value: String) {
            values["$pluginId:$key"] = value
        }

        override fun get(pluginId: String, key: String): String? = values["$pluginId:$key"]

        override fun remove(pluginId: String, key: String) {
            values.remove("$pluginId:$key")
        }

        override fun clearPlugin(pluginId: String) {
            values.keys.filter { it.startsWith("$pluginId:") }.toList().forEach(values::remove)
        }

        override fun getAll(pluginId: String): Map<String, String> = values
            .filterKeys { it.startsWith("$pluginId:") }
            .mapKeys { it.key.removePrefix("$pluginId:") }
    }
}
