package com.azusachino.latte.data.network.moebooru

import com.azusachino.latte.plugin.PlatformId
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MoebooruTagSuggestionTest {
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
    fun suggestionsQueryByCountAndMapNames() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[
                    {"name": "genshin_impact", "count": 90000},
                    {"name": "genshin", "count": 100}
                ]""",
            ),
        )
        val api = MoebooruApi(baseUrl = server.url("/").toString().trimEnd('/'), platform = PlatformId.YANDE)

        val suggestions = api.getTagSuggestions("genshin")

        assertEquals(listOf("genshin_impact", "genshin"), suggestions)
        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/tag.json?"))
        assertTrue(request.path!!.contains("name=genshin*"))
        assertTrue(request.path!!.contains("order=count"))
    }

    @Test
    fun shortPrefixesSkipTheNetwork() = runBlocking {
        val api = MoebooruApi(baseUrl = server.url("/").toString().trimEnd('/'), platform = PlatformId.YANDE)

        assertEquals(emptyList<String>(), api.getTagSuggestions("g"))
        assertEquals(0, server.requestCount)
    }
}
