package com.azusachino.latte

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.azusachino.latte.data.network.OkHttpProvider
import com.azusachino.latte.data.settings.LattePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

import okio.Path.Companion.toOkioPath

class LatteApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        OkHttpProvider.init(this)
        val prefs = LattePreferences(this)
        CoroutineScope(Dispatchers.Main).launch {
            combine(prefs.proxyHost, prefs.proxyPort) { host, port ->
                host to port
            }.collect { (host, port) ->
                OkHttpProvider.setProxy(host, port)
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpProvider.client }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "image_cache").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024) // 512 MB
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
