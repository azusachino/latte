package com.azusachino.latte.plugin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DanbooruPluginFixtureTest {

    private class DanbooruPlugin : SitePlugin {
        override val platform: PlatformId = PlatformId.YANDE
        override val name: String = "Danbooru"
        override val iconRes: Int? = null
        override val authType: AuthType = AuthType.API_KEY
        override val isLoggedIn: Boolean = false
        override val isLoggedInFlow: Flow<Boolean> = flowOf(false)
        override fun getDisplayUsername(): String? = null
        override suspend fun login(credentials: Map<String, String>): Result<Unit> = Result.success(Unit)
        override fun logout() {}

        override val feedTabs: List<PluginFeedTab> = listOf(
            PluginFeedTab("Posts", PluginFeedKind.POPULAR),
            PluginFeedTab("Curated", PluginFeedKind.NEWEST),
        )
    }

    @Test
    fun newPlatformCompilesAndExposesTabsWithoutExploreLayerEdits() {
        val plugin = DanbooruPlugin()
        val manager = SitePluginManager(listOf(plugin))
        assertNotNull(manager.get(PlatformId.YANDE))
        assertEquals(2, plugin.feedTabs.size)
        assertEquals("Posts", plugin.feedTabs[0].title)
        assertEquals("Curated", plugin.feedTabs[1].title)
    }
}
