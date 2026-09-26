package com.azusachino.latte.plugin.yande

import com.azusachino.latte.data.network.SessionCookieStore
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.plugin.moebooru.MoebooruPlugin
import com.azusachino.latte.plugin.storage.PluginStorage
import okhttp3.OkHttpClient

/** The yande.re site binding for the shared Moebooru implementation. */
class YandePlugin(
    storage: PluginStorage,
    httpClient: OkHttpClient,
    cookieJar: SessionCookieStore? = null,
) : MoebooruPlugin(
    storage = storage,
    httpClient = httpClient,
    baseUrl = PlatformId.YANDE.apiUrl,
    platform = PlatformId.YANDE,
    name = PlatformId.YANDE.displayName,
    cookieJar = cookieJar,
)
