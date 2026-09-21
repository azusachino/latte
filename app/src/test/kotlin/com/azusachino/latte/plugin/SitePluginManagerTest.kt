package com.azusachino.latte.plugin

import com.azusachino.latte.plugin.pixiv.PixivPlugin
import com.azusachino.latte.plugin.storage.PluginStorage
import com.azusachino.latte.plugin.yande.YandePlugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePluginManagerTest {

    private class FakePlugin(
        override val id: String,
        override val name: String,
        initialLoggedIn: Boolean = false,
        override val capabilities: Set<PluginCapability> = emptySet(),
    ) : SitePlugin {
        override val iconRes: Int? = null
        override val authType: AuthType = AuthType.CREDENTIALS
        private val _isLoggedIn = MutableStateFlow(initialLoggedIn)
        override val isLoggedIn: Boolean get() = _isLoggedIn.value
        override val isLoggedInFlow: Flow<Boolean> get() = _isLoggedIn
        override fun getDisplayUsername(): String? = if (isLoggedIn) "user_$id" else null

        override suspend fun login(credentials: Map<String, String>): Result<Unit> {
            _isLoggedIn.value = true
            return Result.success(Unit)
        }

        override fun logout() {
            _isLoggedIn.value = false
        }

        override fun applyHeaders(builder: Request.Builder, url: String) {
            if (url.contains(id)) {
                builder.header("X-Plugin", id)
            }
        }
    }

    @Test
    fun testPluginLookup() {
        val yande = FakePlugin("yande.re", "yande.re")
        val pixiv = FakePlugin("pixiv", "Pixiv")
        val manager = SitePluginManager(listOf(yande, pixiv))

        assertEquals(yande, manager.get("yande.re"))
        assertEquals(pixiv, manager.get("pixiv"))
        assertNull(manager.get("unknown"))
    }

    @Test
    fun testLoggedInPlugins() = runTest {
        val yande = FakePlugin("yande.re", "yande.re", initialLoggedIn = false)
        val pixiv = FakePlugin("pixiv", "Pixiv", initialLoggedIn = true)
        val manager = SitePluginManager(listOf(yande, pixiv))

        assertEquals(listOf(pixiv), manager.loggedInPlugins())
        assertEquals(listOf(pixiv), manager.loggedInPluginsFlow().first())

        yande.login(emptyMap())
        assertEquals(listOf(yande, pixiv), manager.loggedInPlugins())
        assertEquals(listOf(yande, pixiv), manager.loggedInPluginsFlow().first())

        pixiv.logout()
        assertEquals(listOf(yande), manager.loggedInPlugins())
        assertEquals(listOf(yande), manager.loggedInPluginsFlow().first())
    }

    @Test
    fun testApplyHeaders() {
        val yande = FakePlugin("yande.re", "yande.re")
        val pixiv = FakePlugin("pixiv", "Pixiv")
        val manager = SitePluginManager(listOf(yande, pixiv))

        val reqBuilder = Request.Builder().url("https://yande.re/post")
        manager.applyHeaders(reqBuilder, "https://yande.re/post")
        val req = reqBuilder.build()

        assertEquals("yande.re", req.header("X-Plugin"))
    }

    @Test
    fun platformCardsExposeCapabilitiesAndConnectionStatus() {
        val storage = InMemoryStorage()
        val yande = YandePlugin(storage, OkHttpClient())
        val pixiv = PixivPlugin(storage, OkHttpClient())
        val manager = SitePluginManager(listOf(yande, pixiv))

        assertEquals(
            setOf(PluginCapability.SCORING, PluginCapability.FAVORITES),
            manager.get("yande.re")?.capabilities,
        )
        assertEquals(
            setOf(PluginCapability.FAVORITES, PluginCapability.USER_FEED),
            manager.get("pixiv")?.capabilities,
        )
        assertTrue(manager.get("yande.re")?.supportedAuthFlows == setOf(AuthFlow.CREDENTIALS))
        assertTrue(AuthFlow.BROWSER in (manager.get("pixiv")?.supportedAuthFlows ?: emptySet()))
        assertTrue(AuthFlow.TOKEN_IMPORT in (manager.get("pixiv")?.supportedAuthFlows ?: emptySet()))
        assertTrue(manager.loggedInPlugins().isEmpty())
    }

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
