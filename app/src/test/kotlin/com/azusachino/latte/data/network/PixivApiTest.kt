package com.azusachino.latte.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PixivApiTest {
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
    fun rankingMapsIllustrationsAndSendsAndroidRequestShape() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(illustrationListJson()))
        val api = api()

        val result = api.load(
            PixivFeedRequest(
                kind = PixivFeedKind.POPULAR,
                refresh = true,
            ),
        )

        val request = server.takeRequest()
        assertEquals("/v1/illust/ranking?filter=for_android&mode=day", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
        assertTrue(result is PixivFeedResult.Success)
        assertEquals(75034219L, (result as PixivFeedResult.Success).page.items.single().id)
    }

    @Test
    fun authAndRateLimitRemainTypedFailures() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(api().load(PixivFeedRequest(PixivFeedKind.FOLLOWED_UPDATES)) is PixivFeedResult.AuthRequired)

        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "17"),
        )
        val result = api().load(PixivFeedRequest(PixivFeedKind.FOLLOWED_UPDATES))
        assertEquals(PixivFeedResult.RateLimited(17), result)

        server.enqueue(MockResponse().setResponseCode(503))
        val serverError = api().load(PixivFeedRequest(PixivFeedKind.FOLLOWED_UPDATES))
        assertTrue(serverError is PixivFeedResult.TransportFailure)
    }

    @Test
    fun bookmarkMutationUsesFormBodyAndDoesNotPretendSuccessOnFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        assertEquals(PixivBookmarkResult.Success, api().bookmark(75034219, bookmarked = true))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v2/illust/bookmark/add", request.path)
        assertEquals("illust_id=75034219&restrict=public", request.body.readUtf8())
    }

    @Test
    fun searchSupportAndMalformedResponsesStayScopedToTheirOperations() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"tags\":[{\"name\":\"blue hair\"}]}"))
        assertEquals(
            PixivSupportResult.Success(listOf("blue hair")),
            api().autocomplete("blue"),
        )
        assertEquals("/v2/search/autocomplete?merge_plain_keyword_results=true&word=blue", server.takeRequest().path)

        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"trend_tags\":[{\"tag\":\"original\"}]}"))
        assertEquals(PixivSupportResult.Success(listOf("original")), api().trendingTags())
        assertEquals("/v1/trending-tags/illust?filter=for_android", server.takeRequest().path)

        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
        assertEquals(
            PixivFeedResult.UpstreamDrift("popular feed"),
            api().load(PixivFeedRequest(PixivFeedKind.POPULAR)),
        )
    }

    @Test
    fun searchAndFavoritesUseTheirAccountScopedEndpoints() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(illustrationListJson()))
        assertTrue(
            api().load(PixivFeedRequest(PixivFeedKind.SEARCH, query = "blue hair")) is PixivFeedResult.Success,
        )
        assertEquals(
            "/v1/search/illust?filter=for_android&merge_plain_keyword_results=true&word=blue%20hair",
            server.takeRequest().path,
        )

        server.enqueue(MockResponse().setResponseCode(200).setBody(illustrationListJson()))
        assertTrue(api().load(PixivFeedRequest(PixivFeedKind.FAVORITES)) is PixivFeedResult.Success)
        assertEquals(
            "/v1/user/bookmarks/illust?user_id=42&restrict=public",
            server.takeRequest().path,
        )

        server.enqueue(MockResponse().setResponseCode(200).setBody(illustrationListJson()))
        assertTrue(
            api().load(PixivFeedRequest(PixivFeedKind.USER_WORKS, userId = 99)) is PixivFeedResult.Success,
        )
        assertEquals(
            "/v1/user/illusts?filter=for_android&user_id=99&type=illust",
            server.takeRequest().path,
        )
    }

    @Test
    fun userWorksRequiresAuthentication() = runBlocking {
        val anonymousApi = PixivApi(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
        )

        assertTrue(
            anonymousApi.load(PixivFeedRequest(PixivFeedKind.USER_WORKS, userId = 99)) is PixivFeedResult.AuthRequired,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun detailMapsPagesAndContinuationRemainsOpaque() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(PixivFixtures.read("detail.json")))
        val detail = api().detail(75034219)
        assertTrue(detail is PixivDetailResult.Success)
        assertEquals(2, (detail as PixivDetailResult.Success).post.pages.size)
        assertEquals("https://www.pixiv.net/artworks/75034219", detail.post.canonicalUrl)
        assertEquals("/v1/illust/detail?filter=for_android&illust_id=75034219", server.takeRequest().path)

        val cursor = server.url("/v2/illust/follow?cursor=opaque-token").toString()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                PixivFixtures.read("ranking.json").replace("\"next_url\": null", "\"next_url\": \"$cursor\""),
            ),
        )
        val first = api().load(PixivFeedRequest(PixivFeedKind.POPULAR))
        assertEquals(cursor, (first as PixivFeedResult.Success).page.nextCursor)
        server.takeRequest()

        server.enqueue(MockResponse().setResponseCode(200).setBody(PixivFixtures.read("ranking.json")))
        api().load(PixivFeedRequest(PixivFeedKind.POPULAR, cursor = cursor))
        assertEquals("/v2/illust/follow?cursor=opaque-token", server.takeRequest().path)
    }

    private fun api(): PixivApi = PixivApi(
        httpClient = OkHttpClient(),
        baseUrl = server.url("/").toString().trimEnd('/'),
        sessionProvider = { PixivSession(accessToken = "access-token", userId = 42) },
    )

    private fun illustrationListJson(): String = PixivFixtures.read("ranking.json")
}
