package com.azusachino.latte.data.download

import android.content.Context
import android.provider.MediaStore
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.plugin.PlatformId

/**
 * Per-platform keyset of saved downloads, rebuilt from the files themselves.
 *
 * The keyset holds one key per stored work (`id` or `id:pN`), parsed from the
 * MediaStore file names in the platform's subfolder. Nothing here is cached
 * across processes: reloading rescans storage, so reinstalls and externally
 * modified galleries are handled by the same path. Downloads auto-load once
 * per process; Settings can force a reload.
 */
object DownloadLibrary {
    private val keys = mutableMapOf<PlatformId, Set<String>>()
    private var loaded = false

    /** Scans storage and rebuilds every platform keyset. Returns total keys. */
    fun refresh(context: Context): Int {
        val resolver = context.contentResolver
        val rebuilt = mutableMapOf<PlatformId, Set<String>>()
        for (platform in PlatformId.entries) {
            rebuilt[platform] = scan(resolver, platform, DownloadIdentity.albumPath(platform))
        }
        synchronized(keys) {
            keys.clear()
            keys.putAll(rebuilt.mapValues { it.value.toSet() })
            loaded = true
        }
        return rebuilt.values.sumOf { it.size }
    }

    /** Loads the keyset once per process, then answers from memory. */
    fun ensureLoaded(context: Context) {
        if (!loaded) refresh(context)
    }

    fun isSaved(post: Post): Boolean = synchronized(keys) {
        keys[post.platform]?.contains(DownloadIdentity.key(post)) ?: false
    }

    /**
     * Records a file this process just saved, so repeat downloads of the same
     * work dedup without waiting for a rescan. Keys come from the file name,
     * the same way [refresh] parses them.
     */
    fun remember(displayName: String) = synchronized(keys) {
        for (platform in PlatformId.entries) {
            val key = DownloadIdentity.parseKey(platform, displayName) ?: continue
            keys[platform] = keys[platform].orEmpty() + key
        }
    }

    fun keyCount(platform: PlatformId): Int = synchronized(keys) {
        keys[platform]?.size ?: 0
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
