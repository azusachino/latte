package com.azusachino.latte.data.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

interface SessionCookieStore : CookieJar {
    fun getCookieValue(host: String, name: String): String?
    fun clear(host: String)
}

class PersistentCookieJar(context: Context) : SessionCookieStore {
    private val appContext = context.applicationContext
    private var prefs: SharedPreferences = createEncryptedPrefs(appContext)
    private val memoryStore = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    init {
        try {
            loadPersistedCookies()
        } catch (_: Exception) {
            // Keystore keys can be invalidated by restore, profile migration, or
            // a device-side security reset. Drop only the cookie cache and
            // recreate it so a stale session cannot prevent app startup.
            appContext.deleteSharedPreferences(PREFS_FILE_NAME)
            prefs = createEncryptedPrefs(appContext)
            memoryStore.clear()
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val hostCookies = memoryStore.getOrPut(host) { mutableMapOf() }
        val now = System.currentTimeMillis()

        for (cookie in cookies) {
            if (cookie.isExpired(now)) {
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
                val validCookies = cookies.values.filter { !it.isExpired(now) }
                result.addAll(validCookies)
            }
        }
        return result
    }

    override fun getCookieValue(host: String, name: String): String? {
        val now = System.currentTimeMillis()
        return memoryStore[host]?.get(name)?.takeIf { !it.isExpired(now) }?.value
    }

    @Synchronized
    override fun clear(host: String) {
        val hosts = (memoryStore.keys + prefs.all.keys)
            .filter { it == host || it.endsWith(".$host") }
        val editor = prefs.edit()
        hosts.forEach {
            memoryStore.remove(it)
            editor.remove(it)
        }
        editor.apply()
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

                            if (expiresAt == Long.MIN_VALUE || expiresAt > now) {
                                val builder = Cookie.Builder()
                                    .name(name)
                                    .value(value)
                                    .domain(domain)
                                    .path(path)
                                if (expiresAt != Long.MIN_VALUE) {
                                    builder.expiresAt(expiresAt)
                                }
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

    private fun Cookie.isExpired(now: Long): Boolean =
        expiresAt != Long.MIN_VALUE && expiresAt <= now

    companion object {
        private const val PREFS_FILE_NAME = "latte_cookie_jar"

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            fun create(): SharedPreferences {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                return EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }

            return try {
                create()
            } catch (firstFailure: Exception) {
                // Keystore hardware desync recovery: wipe file & corrupted MasterKey entry, then retry
                try {
                    context.deleteSharedPreferences(PREFS_FILE_NAME)
                    runCatching {
                        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                        keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    }
                    create()
                } catch (secondFailure: Exception) {
                    // Safe degradation on unrecoverable hardware Keystore corruption
                    context.getSharedPreferences("${PREFS_FILE_NAME}_fallback", Context.MODE_PRIVATE)
                }
            }
        }
    }
}
