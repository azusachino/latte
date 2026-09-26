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
import java.time.LocalDate

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

    @Test
    fun safeModeFiltersMoebooruPagesAtTheSource() = runTest {
        val source = MoebooruFeedSource(
            api = MoebooruApi(server.url("/").toString().removeSuffix("/"), PlatformId.YANDE),
            username = { "test_artist" },
        )
        listOf(
            PluginFeedKind.POPULAR to "order:score date:2026-09-26 rating:safe",
            PluginFeedKind.NEWEST to "rating:safe",
            PluginFeedKind.SEARCH to "landscape rating:safe",
            PluginFeedKind.FAVORITES to "vote:3:test_artist rating:safe",
        ).forEach { (kind, tags) ->
            server.enqueue(MockResponse().setBody("[]"))
            source.load(
                PluginFeedRequest(
                    kind = kind,
                    query = "landscape",
                    date = LocalDate.of(2026, 9, 26),
                    safeMode = true,
                ),
            )
            assertEquals(tags, server.takeRequest().requestUrl?.queryParameter("tags"))
        }
    }
}
