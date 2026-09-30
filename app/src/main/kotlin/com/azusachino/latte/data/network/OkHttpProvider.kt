package com.azusachino.latte.data.network

import android.content.Context
import com.azusachino.latte.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DohResolver {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, List<InetAddress>>()
    private val dohEndpoints = listOf(
        "https://1.1.1.1/dns-query",
        "https://8.8.8.8/resolve",
    )

    private val bootstrapClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @Serializable
    private data class DnsResponse(
        @SerialName("Answer") val answer: List<DnsAnswer> = emptyList(),
    )

    @Serializable
    private data class DnsAnswer(
        val type: Int = 1,
        val data: String = "",
    )

    fun resolve(hostname: String): List<InetAddress>? {
        cache[hostname]?.let { return it }

        for (endpoint in dohEndpoints) {
            try {
                val url = "$endpoint?name=$hostname&type=A"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()

                bootstrapClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val parsed = json.decodeFromString<DnsResponse>(body)
                        val addresses = parsed.answer
                            .filter { it.type == 1 }
                            .mapNotNull {
                                runCatching { InetAddress.getByName(it.data.trim()) }.getOrNull()
                            }
                        if (addresses.isNotEmpty()) {
                            cache[hostname] = addresses
                            return addresses
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next DoH endpoint
            }
        }
        return null
    }
}

object OkHttpProvider {
    private const val CACHE_SIZE = 256L * 1024 * 1024 // 256 MB
    private const val PIXIV_IMAGE_HOST = "i.pximg.net"
    private const val PIXIV_STATIC_HOST = "s.pximg.net"
    private const val PIXIV_IMAGE_REFERER = "https://app-api.pixiv.net/"

    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                val systemResult = Dns.SYSTEM.lookup(hostname)
                if (systemResult.isNotEmpty()) {
                    systemResult
                } else {
                    DohResolver.resolve(hostname) ?: throw UnknownHostException(hostname)
                }
            } catch (e: Exception) {
                DohResolver.resolve(hostname) ?: throw e
            }
        }
    }

    private var appContext: Context? = null
    private var _client: OkHttpClient? = null
    private var _cookieJar: PersistentCookieJar? = null
    private var activeProxy: Proxy? = null

    val cookieJar: PersistentCookieJar?
        get() = _cookieJar

    val client: OkHttpClient
        get() = _client ?: synchronized(this) {
            _client ?: buildClient(appContext, activeProxy).also { _client = it }
        }

    fun init(context: Context) {
        synchronized(this) {
            appContext = context.applicationContext
            if (_cookieJar == null) {
                _cookieJar = PersistentCookieJar(context)
            }
            if (_client == null) {
                _client = buildClient(appContext, activeProxy)
            }
        }
    }

    fun setProxy(host: String?, port: Int?) {
        val newProxy = if (!host.isNullOrBlank() && port != null && port > 0) {
            Proxy(Proxy.Type.HTTP, InetSocketAddress(host.trim(), port))
        } else {
            null
        }
        synchronized(this) {
            if (activeProxy != newProxy) {
                activeProxy = newProxy
                _client = buildClient(appContext, activeProxy)
            }
        }
    }

    private fun buildClient(context: Context?, proxy: Proxy?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            .dns(customDns)

        if (context != null) {
            val cacheDir = File(context.cacheDir, "http_cache")
            builder.cache(Cache(cacheDir, CACHE_SIZE))
        }

        _cookieJar?.let { builder.cookieJar(it) }
        proxy?.let { builder.proxy(it) }

        builder.addInterceptor { chain ->
            chain.proceed(withDefaultHeaders(chain.request()))
        }

        return builder.build()
    }

    private fun withDefaultHeaders(request: Request): Request {
        val builder = request.newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; Latte/${BuildConfig.VERSION_NAME})")
        if (request.url.host == PIXIV_IMAGE_HOST || request.url.host == PIXIV_STATIC_HOST) {
            builder.header("Referer", PIXIV_IMAGE_REFERER)
        }
        return builder.build()
    }
}
