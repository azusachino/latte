package com.azusachino.latte.data.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("latte_cookie_jar", Context.MODE_PRIVATE)
    private val memoryStore = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    init {
        loadPersistedCookies()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val hostCookies = memoryStore.getOrPut(host) { mutableMapOf() }
        val now = System.currentTimeMillis()

        for (cookie in cookies) {
            if (cookie.expiresAt <= now) {
                hostCookies.remove(cookie.name)
            } else {
                hostCookies[cookie.name] = cookie
            }
        }
        persistHostCookies(host, hostCookies.values.toList())
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()

        for ((storedHost, cookies) in memoryStore) {
            if (hostMatches(storedHost, host)) {
                val validCookies = cookies.values.filter { it.expiresAt > now }
                result.addAll(validCookies)
            }
        }
        return result
    }

    fun getCookieValue(host: String, name: String): String? {
        val now = System.currentTimeMillis()
        return memoryStore[host]?.get(name)?.takeIf { it.expiresAt > now }?.value
    }

    @Synchronized
    fun clear() {
        memoryStore.clear()
        prefs.edit().clear().apply()
    }

    private fun hostMatches(storedHost: String, requestHost: String): Boolean {
        return requestHost == storedHost || requestHost.endsWith(".$storedHost")
    }

    private fun persistHostCookies(host: String, cookies: List<Cookie>) {
        val serialized = cookies.joinToString(";") { "${it.name}=${it.value}|${it.domain}|${it.path}|${it.expiresAt}|${it.secure}|${it.httpOnly}" }
        prefs.edit().putString(host, serialized).apply()
    }

    private fun loadPersistedCookies() {
        val now = System.currentTimeMillis()
        for ((host, serialized) in prefs.all) {
            if (serialized is String && serialized.isNotBlank()) {
                val hostCookies = mutableMapOf<String, Cookie>()
                val tokens = serialized.split(";")
                for (token in tokens) {
                    val parts = token.split("|")
                    if (parts.size == 6) {
                        val nameValue = parts[0].split("=", limit = 2)
                        if (nameValue.size == 2) {
                            val name = nameValue[0]
                            val value = nameValue[1]
                            val domain = parts[1]
                            val path = parts[2]
                            val expiresAt = parts[3].toLongOrNull() ?: 0L
                            val secure = parts[4].toBoolean()
                            val httpOnly = parts[5].toBoolean()

                            if (expiresAt > now) {
                                val builder = Cookie.Builder()
                                    .name(name)
                                    .value(value)
                                    .domain(domain)
                                    .path(path)
                                    .expiresAt(expiresAt)
                                if (secure) builder.secure()
                                if (httpOnly) builder.httpOnly()
                                val cookie = builder.build()
                                hostCookies[name] = cookie
                            }
                        }
                    }
                }
                if (hostCookies.isNotEmpty()) {
                    memoryStore[host] = hostCookies
                }
            }
        }
    }
}
