package com.azusachino.latte.plugin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import okhttp3.Request

class SitePluginManager(val plugins: List<SitePlugin>) {

    fun get(id: String): SitePlugin? = plugins.find { it.id == id }

    fun loggedInPlugins(): List<SitePlugin> = plugins.filter { it.isLoggedIn }

    fun loggedInPluginsFlow(): Flow<List<SitePlugin>> {
        val flows = plugins.map { it.isLoggedInFlow }
        return combine(flows) { states ->
            plugins.filterIndexed { index, _ -> states[index] }
        }
    }

    fun applyHeaders(builder: Request.Builder, url: String) {
        for (plugin in plugins) {
            plugin.applyHeaders(builder, url)
        }
    }
}
