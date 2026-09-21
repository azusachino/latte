package com.azusachino.latte.data.network

import android.content.Context
import com.azusachino.latte.BuildConfig
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object OkHttpProvider {
    private const val CACHE_SIZE = 256L * 1024 * 1024 // 256 MB
    private const val MOE_HOST = "yande.re"
    private const val PIXIV_IMAGE_HOST = "i.pximg.net"
    private const val PIXIV_STATIC_HOST = "s.pximg.net"
    private const val PIXIV_IMAGE_REFERER = "https://app-api.pixiv.net/"
    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                if (hostname.endsWith(MOE_HOST)) {
                    listOf(InetAddress.getByName("198.251.89.183"))
                } else {
                    throw e
                }
            }
        }
    }

    private var _client: OkHttpClient? = null
    private var _cookieJar: PersistentCookieJar? = null

    val cookieJar: PersistentCookieJar?
        get() = _cookieJar

    val client: OkHttpClient
        get() = _client ?: synchronized(this) {
            _client ?: OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
                .dns(customDns)
                .apply {
                    _cookieJar?.let { cookieJar(it) }
                }
                .addInterceptor { chain ->
                    chain.proceed(withDefaultHeaders(chain.request()))
                }
                .build()
                .also { _client = it }
        }

    fun init(context: Context) {
        if (_client == null) {
            synchronized(this) {
                if (_client == null) {
                    val cacheDir = File(context.cacheDir, "http_cache")
                    val jar = PersistentCookieJar(context)
                    _cookieJar = jar
                    _client = OkHttpClient.Builder()
                        .cache(Cache(cacheDir, CACHE_SIZE))
                        .cookieJar(jar)
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
                        .dns(customDns)
                        .addInterceptor { chain ->
                            chain.proceed(withDefaultHeaders(chain.request()))
                        }
                        .build()
                }
            }
        }
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
