package com.azusachino.latte.plugin.konachan

import com.azusachino.latte.data.network.SessionCookieStore
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.plugin.storage.PluginStorage
import com.azusachino.latte.plugin.moebooru.MoebooruPlugin
import okhttp3.OkHttpClient

/** Site-specific Konachan binding over the shared Moebooru implementation. */
class KonachanPlugin(
    storage: PluginStorage,
    httpClient: OkHttpClient,
    cookieJar: SessionCookieStore? = null,
) : MoebooruPlugin(
    storage = storage,
    httpClient = httpClient,
    cookieJar = cookieJar,
    baseUrl = PlatformId.KONACHAN.apiUrl,
    platform = PlatformId.KONACHAN,
    name = "Konachan",
)
