package com.azusachino.latte.plugin.pixiv

import com.azusachino.latte.plugin.PluginFeedKind
import com.azusachino.latte.plugin.PluginFeedRequest
import com.azusachino.latte.plugin.PluginFeedResult
import com.azusachino.latte.plugin.storage.PluginStorage
import com.azusachino.latte.data.network.PixivApi
import com.azusachino.latte.data.network.PixivSession
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for the owner-facing follow loop (issue #5): reading an
 * author's follow state, toggling it, and the neutral feed mapping the
 * Explore layer consumes.
 */
class PixivFollowRegressionTest {
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
    fun followToggleRoundTripsThroughThePluginSeam() = runTest {
        val plugin = plugin()
        plugin.login(mapOf("access_token" to "access-token", "user_id" to "42"))

        // Current state: followed author.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"user":{"id":9,"is_followed":true}}"""),
        )
        assertEquals(true, plugin.isAuthorFollowed(9))
        assertEquals("/v1/user/detail?filter=for_android&user_id=9", server.takeRequest().path)

        // Unfollow sends the delete form and reports success.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        assertEquals(Result.success(Unit), plugin.setAuthorFollowed(9, followed = false))
        val delete = server.takeRequest()
        assertEquals("/v1/user/follow/delete", delete.path)
        assertEquals("user_id=9", delete.body.readUtf8())

        // Follow sends the add form with public restrict.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        assertEquals(Result.success(Unit), plugin.setAuthorFollowed(9, followed = true))
        val add = server.takeRequest()
        assertEquals("/v1/user/follow/add", add.path)
        assertEquals("user_id=9&restrict=public", add.body.readUtf8())
    }

    @Test
    fun failedFollowStateReadsReportUnknownNotUnfollowed() = runTest {
        val plugin = plugin()
        plugin.login(mapOf("access_token" to "access-token", "user_id" to "42"))

        server.enqueue(MockResponse().setResponseCode(401))
        assertNull(plugin.isAuthorFollowed(9))

        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertNull(plugin.isAuthorFollowed(9))
    }

    @Test
    fun signedOutPluginCannotFollow() = runTest {
        val plugin = plugin()

        // State read fails as unknown (the unauthenticated request is rejected).
        server.enqueue(MockResponse().setResponseCode(401))
        assertNull(plugin.isAuthorFollowed(9))

        // The mutation never reaches the network without a session.
        val result = plugin.setAuthorFollowed(9, followed = true)
        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun feedSourceMapsNeutralKindsOntoPixivOperations() = runTest {
        val api = PixivApi(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
            sessionProvider = { PixivSession(accessToken = "access-token") },
        )
        val source = PixivFeedSource(api)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"illusts":[],"next_url":null}"""))

        val result = source.load(
            PluginFeedRequest(kind = PluginFeedKind.FOLLOWED, refresh = true),
        )

        assertTrue(result is PluginFeedResult.Empty)
        assertEquals("/v2/illust/follow?restrict=public", server.takeRequest().path)
    }

    private fun plugin(): PixivPlugin = PixivPlugin(
        storage = InMemoryStorage(),
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
            values.keys.filter { it.startsWith("$pluginId:") }.forEach(values::remove)
        }
    }
}
