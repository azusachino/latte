package com.azusachino.latte.plugin.moebooru

import com.azusachino.latte.data.network.moebooru.MoebooruApi
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.plugin.PluginFeedKind
import com.azusachino.latte.plugin.PluginFeedRequest
import com.azusachino.latte.plugin.PluginFeedResult
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MoebooruFeedSourceTest {
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
    fun konachanPlatformUsesNetHostAndFavoriteQueryUsesSignedInUsername() = runTest {
        assertEquals("konachan.net", com.azusachino.latte.plugin.PlatformId.KONACHAN.apiUrl.toHttpUrl().host)
        server.enqueue(MockResponse().setBody("[{\"id\":408739,\"rating\":\"s\"}]"))
        val source = MoebooruFeedSource(
            api = MoebooruApi(
                server.url("/").toString().removeSuffix("/"),
                platform = PlatformId.KONACHAN,
            ),
            username = { "test_artist" },
        )

        val result = source.load(PluginFeedRequest(kind = PluginFeedKind.FAVORITES))
        val request = server.takeRequest()

        assertTrue(result is PluginFeedResult.Success)
        val post = (result as PluginFeedResult.Success).page.items.single()
        assertEquals(PlatformId.KONACHAN, post.platform)
        assertEquals("/post.json?page=1&limit=100&tags=vote%3A3%3Atest_artist", request.path)
    }
}
