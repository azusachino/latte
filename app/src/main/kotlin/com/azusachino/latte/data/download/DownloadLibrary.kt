package com.azusachino.latte.data.download

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.plugin.PlatformId

/**
 * Per-platform keyset of saved downloads (`id` or `id:pN`).
 *
 * Built once by scanning MediaStore file names, then persisted. Each finished
 * download adds its key ([remember]), so the set grows without rescans. Files
 * changed outside the app are not tracked until Settings → Rebuild Download
 * Index runs a fresh scan.
 */
object DownloadLibrary {
    private const val PREFS = "download_library"
    private const val BUILT_FLAG = "built"

    private val keys = mutableMapOf<PlatformId, MutableSet<String>>()
    private var loaded = false
    // True only when the keyset came from a scan with photo access, or from a
    // persisted set that was built that way.
    private var built = false

    /** Scans storage, replaces every platform keyset, and persists it. Returns total keys. */
    fun refresh(context: Context): Int = synchronized(keys) {
        val resolver = context.contentResolver
        keys.clear()
        for (platform in PlatformId.entries) {
            keys[platform] = scan(resolver, platform, DownloadIdentity.albumPath(platform)).toMutableSet()
        }
        loaded = true
        // Without photo access the scan cannot see saved files, so stay unbuilt
        // and let a later load with permission scan again.
        built = hasPhotoAccess(context)
        persist(context)
        keys.values.sumOf { it.size }
    }

    /** Loads the persisted keyset, or builds it with one scan if none exists yet. */
    fun ensureLoaded(context: Context) {
        synchronized(keys) {
            if (loaded) return@synchronized
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(BUILT_FLAG, false)) {
                refresh(context)
                return@synchronized
            }
            for (platform in PlatformId.entries) {
                keys[platform] = prefs.getStringSet(platform.externalId, emptySet()).orEmpty().toMutableSet()
            }
            loaded = true
            built = true
        }
    }

    fun isSaved(post: Post): Boolean = synchronized(keys) {
        keys[post.platform]?.contains(DownloadIdentity.key(post)) ?: false
    }

    /**
     * Records a file this process just saved, and persists the keyset so the
     * next launch starts with it. Keys come from the file name.
     */
    fun remember(context: Context, displayName: String) = synchronized(keys) {
        // Load first: persisting a partial in-memory set would drop the stored library.
        ensureLoaded(context)
        addKeys(displayName)
        persist(context)
    }

    /** In-memory part of [remember]. Returns the platforms whose keyset changed. */
    internal fun addKeys(displayName: String): Set<PlatformId> = synchronized(keys) {
        val changed = mutableSetOf<PlatformId>()
        for (platform in PlatformId.entries) {
            val key = DownloadIdentity.parseKey(platform, displayName) ?: continue
            keys.getOrPut(platform) { mutableSetOf() }.add(key)
            changed.add(platform)
        }
        changed
    }

    fun keyCount(platform: PlatformId): Int = synchronized(keys) {
        keys[platform]?.size ?: 0
    }

    private fun hasPhotoAccess(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED

    /** Caller holds the `keys` lock. */
    private fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            for (platform in PlatformId.entries) {
                putStringSet(platform.externalId, keys[platform].orEmpty().toSet())
            }
            putBoolean(BUILT_FLAG, built)
            apply()
        }
    }

    private fun scan(
        resolver: android.content.ContentResolver,
        platform: PlatformId,
        folder: String,
    ): Set<String> {
        val parsed = mutableSetOf<String>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
            "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
            arrayOf("$folder/"),
            null,
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                DownloadIdentity.parseKey(platform, cursor.getString(nameColumn))?.let { parsed.add(it) }
            }
        }
        return parsed
    }
}
