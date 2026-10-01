package com.azusachino.latte.plugin.pixiv

import com.azusachino.latte.plugin.AuthFlow
import com.azusachino.latte.plugin.storage.PluginStorage
import com.azusachino.latte.data.network.PixivFeedKind
import com.azusachino.latte.data.network.PixivFeedRequest
import com.azusachino.latte.data.network.PixivFeedResult
import com.azusachino.latte.data.network.PixivOAuthClient
import com.azusachino.latte.data.network.PixivOAuthConfiguration
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

    @Test
    fun expiredAccessTokenRefreshesAndRetriesWithTheNewSession() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("{\"error\":{\"message\":\"OAuth token expired\"}}"),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"response":{"access_token":"new-access","refresh_token":"new-refresh","user":{"id":42,"name":"artist"}}}
                """.trimIndent(),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"illusts\":[],\"next_url\":null}"))

        val storage = InMemoryStorage()
        val plugin = plugin(
            storage,
            PixivOAuthClient(
                OkHttpClient(),
                PixivOAuthConfiguration(
                    clientId = "fixture-client",
                    clientSecret = "fixture-secret",
                    tokenEndpoint = server.url("/auth/token").toString(),
                ),
            ),
        )
        plugin.login(
            mapOf(
                "access_token" to "old-access",
                "refresh_token" to "old-refresh",
                "user_id" to "42",
            ),
        )

        assertEquals(
            PixivFeedResult.Empty,
            plugin.api.load(PixivFeedRequest(PixivFeedKind.FOLLOWED_UPDATES)),
        )
        assertEquals("/v2/illust/follow?restrict=public", server.takeRequest().path)
        assertEquals("/auth/token", server.takeRequest().path)
        val retryRequest = server.takeRequest()
        assertEquals("/v2/illust/follow?restrict=public", retryRequest.path)
        assertEquals("Bearer new-access", retryRequest.getHeader("Authorization"))
        assertEquals("new-access", storage.get("pixiv", "access_token"))
        assertEquals("new-refresh", storage.get("pixiv", "refresh_token"))
    }

    @Test
    fun failedRefreshInvalidatesTheAccountAndReturnsAuthRequired() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("Error occurred at the OAuth process"),
        )
        server.enqueue(MockResponse().setResponseCode(400).setBody("{\"error\":{\"message\":\"invalid_grant\"}}"))

        val storage = InMemoryStorage()
        val plugin = plugin(
            storage,
            PixivOAuthClient(
                OkHttpClient(),
                PixivOAuthConfiguration(
                    clientId = "fixture-client",
                    clientSecret = "fixture-secret",
                    tokenEndpoint = server.url("/auth/token").toString(),
                ),
            ),
        )
        plugin.login(mapOf("access_token" to "old-access", "refresh_token" to "old-refresh", "user_id" to "42"))

        assertEquals(
            PixivFeedResult.AuthRequired,
            plugin.api.load(PixivFeedRequest(PixivFeedKind.FOLLOWED_UPDATES)),
        )
        assertFalse(plugin.isLoggedIn)
        assertTrue(storage.getAll("pixiv").isEmpty())
    }

    @Test
    fun browserAuthorizationExchangesCodeAndPersistsSession() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"response":{"access_token":"browser-access","refresh_token":"browser-refresh","user":{"id":42,"name":"artist"}}}
                """.trimIndent(),
            ),
        )

        val storage = InMemoryStorage()
        val plugin = plugin(
            storage,
            PixivOAuthClient(
                OkHttpClient(),
                PixivOAuthConfiguration(
                    clientId = "fixture-client",
                    clientSecret = "fixture-secret",
                    tokenEndpoint = server.url("/auth/token").toString(),
                ),
            ),
        )

        val authorizationUrl = plugin.beginBrowserLogin().getOrThrow()
        assertTrue(authorizationUrl.contains("code_challenge="))
        assertTrue(authorizationUrl.contains("client=pixiv-android"))

        assertTrue(plugin.completeBrowserLogin("fixture-code").isSuccess)
        assertTrue(plugin.isLoggedIn)
        assertEquals("browser-access", storage.get("pixiv", "access_token"))
        assertEquals("browser-refresh", storage.get("pixiv", "refresh_token"))
        assertEquals("artist", plugin.getDisplayUsername())

        val request = server.takeRequest()
        assertEquals("/auth/token", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("grant_type=authorization_code"))
        assertTrue(body.contains("code=fixture-code"))
        assertTrue(body.contains("code_verifier="))
        assertTrue(body.contains("redirect_uri="))
    }

    @Test
    fun browserAuthorizationVerifierSurvivesPluginRecreation() = runTest {
        val storage = InMemoryStorage()
        val oauthClient = PixivOAuthClient(
            OkHttpClient(),
            PixivOAuthConfiguration(
                clientId = "fixture-client",
                clientSecret = "fixture-secret",
                tokenEndpoint = server.url("/auth/token").toString(),
            ),
        )
        plugin(storage, oauthClient).beginBrowserLogin().getOrThrow()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"response":{"access_token":"recreated-access","user":{"id":42,"name":"artist"}}}
                """.trimIndent(),
            ),
        )

        val recreatedPlugin = plugin(storage, oauthClient)
        assertTrue(recreatedPlugin.completeBrowserLogin("fixture-code").isSuccess)
        assertEquals("recreated-access", storage.get("pixiv", "access_token"))
        assertEquals(null, storage.get("pixiv", "pending_code_verifier"))
    }

    private fun plugin(
        storage: PluginStorage,
        oauthClient: PixivOAuthClient? = null,
    ): PixivPlugin = PixivPlugin(
        storage = storage,
        httpClient = OkHttpClient(),
        apiBaseUrl = server.url("/").toString().trimEnd('/'),
        oauthClient = oauthClient,
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
