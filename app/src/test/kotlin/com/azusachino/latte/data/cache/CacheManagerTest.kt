package com.azusachino.latte.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootCacheDir: File
    private lateinit var imageCacheDir: File
    private lateinit var httpCacheDir: File
    private lateinit var updatesDir: File
    private var searchClearCount = 0

    private lateinit var cacheManager: CacheManager

    @Before
    fun setUp() {
        rootCacheDir = tempFolder.newFolder("cache")
        imageCacheDir = File(rootCacheDir, "image_cache").apply { mkdirs() }
        httpCacheDir = File(rootCacheDir, "http_cache").apply { mkdirs() }
        updatesDir = File(rootCacheDir, "updates").apply { mkdirs() }
        searchClearCount = 0

        cacheManager = CacheManager(
            context = null,
            preferences = null,
            cacheDirProvider = { rootCacheDir },
            clearSearchAction = { searchClearCount++ },
        )
    }

    @Test
    fun getCacheSizeBytesCalculatesCategorySizesAccurately() {
        val imageFile = File(imageCacheDir, "thumb.jpg").apply { writeBytes(ByteArray(1024)) }
        val httpFile = File(httpCacheDir, "journal").apply { writeBytes(ByteArray(2048)) }
        val updateFile = File(updatesDir, "latte-v0.3.1.apk").apply { writeBytes(ByteArray(4096)) }

        assertEquals(1024L, cacheManager.getCacheSizeBytes(CacheType.IMAGES))
        assertEquals(2048L, cacheManager.getCacheSizeBytes(CacheType.NETWORK))
        assertEquals(4096L, cacheManager.getCacheSizeBytes(CacheType.UPDATES))
        assertEquals(1024L + 2048L + 4096L, cacheManager.getTotalCacheSizeBytes())
    }

    @Test
    fun clearingOnlyUpdatesLeavesOtherCachesIntact() {
        val imageFile = File(imageCacheDir, "thumb.jpg").apply { writeBytes(ByteArray(500)) }
        val httpFile = File(httpCacheDir, "response.json").apply { writeBytes(ByteArray(300)) }
        val updateFile = File(updatesDir, "update.apk").apply { writeBytes(ByteArray(1000)) }
        val rootApk = File(rootCacheDir, "latte.apk").apply { writeBytes(ByteArray(800)) }

        cacheManager.clearCache(setOf(CacheType.UPDATES))

        assertFalse(updateFile.exists())
        assertFalse(rootApk.exists())
        assertTrue(imageFile.exists())
        assertTrue(httpFile.exists())
        assertEquals(0, searchClearCount)
    }

    @Test
    fun clearingOnlyImagesLeavesNetworkAndUpdatesIntact() {
        val imageFile = File(imageCacheDir, "thumb.jpg").apply { writeBytes(ByteArray(500)) }
        val httpFile = File(httpCacheDir, "response.json").apply { writeBytes(ByteArray(300)) }
        val updateFile = File(updatesDir, "update.apk").apply { writeBytes(ByteArray(1000)) }

        cacheManager.clearCache(setOf(CacheType.IMAGES))

        assertFalse(imageFile.exists())
        assertTrue(httpFile.exists())
        assertTrue(updateFile.exists())
        assertEquals(0, searchClearCount)
    }

    @Test
    fun clearingSearchInvokesClearAction() {
        val imageFile = File(imageCacheDir, "thumb.jpg").apply { writeBytes(ByteArray(500)) }

        cacheManager.clearCache(setOf(CacheType.SEARCH))

        assertEquals(1, searchClearCount)
        assertTrue(imageFile.exists())
    }

    @Test
    fun clearAllRemovesAllFilesAndTriggersSearchClear() {
        val imageFile = File(imageCacheDir, "thumb.jpg").apply { writeBytes(ByteArray(500)) }
        val httpFile = File(httpCacheDir, "response.json").apply { writeBytes(ByteArray(300)) }
        val updateFile = File(updatesDir, "update.apk").apply { writeBytes(ByteArray(1000)) }

        var clearedCallbackCalled = false
        cacheManager.clearAll {
            clearedCallbackCalled = true
        }

        assertFalse(imageFile.exists())
        assertFalse(httpFile.exists())
        assertFalse(updateFile.exists())
        assertEquals(1, searchClearCount)
        assertTrue(clearedCallbackCalled)
        assertEquals(0L, cacheManager.getTotalCacheSizeBytes())
    }
}
