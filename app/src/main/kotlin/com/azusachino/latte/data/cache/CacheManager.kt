package com.azusachino.latte.data.cache

import android.content.Context
import coil3.SingletonImageLoader
import com.azusachino.latte.data.network.OkHttpProvider
import com.azusachino.latte.data.settings.LattePreferences
import java.io.File

enum class CacheType(val displayName: String, val description: String) {
    IMAGES("Image Cache", "Cached thumbnails, previews, and illustrations"),
    NETWORK("Network API Cache", "Cached API responses and network headers"),
    UPDATES("Downloaded Updates", "Downloaded APK files and installation packages"),
    SEARCH("Search History", "Recent search suggestions and search queries"),
}

class CacheManager internal constructor(
    private val context: Context?,
    private val preferences: LattePreferences?,
    private val cacheDirProvider: () -> File?,
    private val clearSearchAction: () -> Unit,
) {
    constructor(context: Context, preferences: LattePreferences) : this(
        context = context,
        preferences = preferences,
        cacheDirProvider = { context.cacheDir },
        clearSearchAction = { preferences.clearRecentSearches() },
    )

    private val baseCacheDir: File?
        get() = cacheDirProvider()

    private val imageCacheDir: File
        get() = File(baseCacheDir, "image_cache")

    private val httpCacheDir: File
        get() = File(baseCacheDir, "http_cache")

    private val updatesDir: File
        get() = File(baseCacheDir, "updates")

    fun getCacheSizeBytes(type: CacheType): Long = when (type) {
        CacheType.IMAGES -> calculateDirSize(imageCacheDir)
        CacheType.NETWORK -> calculateDirSize(httpCacheDir)
        CacheType.UPDATES -> calculateUpdateFilesSize()
        CacheType.SEARCH -> {
            preferences?.recentSearches?.value?.sumOf { it.toByteArray().size.toLong() } ?: 0L
        }
    }

    fun getTotalCacheSizeBytes(): Long {
        val cacheDir = baseCacheDir ?: return 0L
        return calculateDirSize(cacheDir)
    }

    fun clearCache(types: Set<CacheType>, onCleared: () -> Unit = {}) {
        if (CacheType.IMAGES in types) {
            context?.let { ctx ->
                runCatching {
                    val imageLoader = SingletonImageLoader.get(ctx)
                    imageLoader.diskCache?.clear()
                    imageLoader.memoryCache?.clear()
                }
            }
            deleteDirectoryContents(imageCacheDir)
        }

        if (CacheType.NETWORK in types) {
            runCatching {
                OkHttpProvider.client.cache?.evictAll()
            }
            deleteDirectoryContents(httpCacheDir)
        }

        if (CacheType.UPDATES in types) {
            deleteUpdateFiles()
        }

        if (CacheType.SEARCH in types) {
            clearSearchAction()
        }

        onCleared()
    }

    fun clearAll(onCleared: () -> Unit = {}) {
        clearCache(CacheType.entries.toSet(), onCleared)
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun calculateUpdateFilesSize(): Long {
        var size = calculateDirSize(updatesDir)
        val cacheDir = baseCacheDir ?: return size
        val rootApks = cacheDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".apk", ignoreCase = true) || file.name.startsWith("update", ignoreCase = true))
        } ?: emptyArray()
        size += rootApks.sumOf { it.length() }
        return size
    }

    private fun deleteDirectoryContents(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            file.deleteRecursively()
        }
    }

    private fun deleteUpdateFiles() {
        deleteDirectoryContents(updatesDir)
        runCatching { updatesDir.delete() }
        val cacheDir = baseCacheDir ?: return
        cacheDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".apk", ignoreCase = true) || file.name.startsWith("update", ignoreCase = true))
        }?.forEach { it.delete() }
    }
}
