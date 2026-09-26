package com.azusachino.latte.plugin.konachan

import com.azusachino.latte.data.network.SessionCookieStore
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.plugin.storage.PluginStorage
import com.azusachino.latte.plugin.yande.YandePlugin
import okhttp3.OkHttpClient

/**
 * Konachan runs the same Moebooru engine as yande.re, so it subclasses the
 * Yande plugin and only rebases the site identity. This is the probe for the
 * plugin abstraction: how little code a third platform actually needs.
 */
class KonachanPlugin(
    storage: PluginStorage,
    httpClient: OkHttpClient,
    cookieJar: SessionCookieStore? = null,
) : YandePlugin(
    storage = storage,
    httpClient = httpClient,
    cookieJar = cookieJar,
    baseUrl = PlatformId.KONACHAN.apiUrl,
    platform = PlatformId.KONACHAN,
    name = "Konachan",
)
