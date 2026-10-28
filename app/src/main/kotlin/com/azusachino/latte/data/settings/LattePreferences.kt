package com.azusachino.latte.data.settings

import android.content.Context
import com.azusachino.latte.data.model.FavoriteTag
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.plugin.PlatformId
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

// One DataStore per file, held by the Context so every LattePreferences
// instance (Explore, Settings) shares the same single-writer pipeline.
private val Context.latteDataStore by preferencesDataStore(name = "latte_settings")

/**
 * Pure encode/decode for preference values that need a shape DataStore key
 * types cannot express, kept side-effect free so unit tests cover the logic
 * without Android.
 */
object LattePreferenceCodecs {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeRecentSearches(values: List<String>): String =
        json.encodeToString(listSerializer, values)

    fun decodeRecentSearches(value: String): List<String> =
        runCatching { json.decodeFromString(listSerializer, value) }.getOrDefault(emptyList())

    fun encodeFavoriteTags(values: List<FavoriteTag>): String =
        json.encodeToString(favoriteTagsSerializer, values)

    fun decodeFavoriteTags(value: String): List<FavoriteTag> =
        runCatching { json.decodeFromString(favoriteTagsSerializer, value) }.getOrDefault(emptyList())

    /**
     * Records [query] as the most recent entry: dedupe, trim to [limit],
     * most-recent first.
     */
    fun recordRecentSearch(existing: List<String>, query: String, limit: Int = 20): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return existing
        return (listOf(trimmed) + existing.filter { it != trimmed }).take(limit)
    }

    private val listSerializer = ListSerializer(String.serializer())

    private val favoriteTagsSerializer = ListSerializer(FavoriteTag.serializer())

    private val postsSerializer = ListSerializer(Post.serializer())

    fun encodeViewHistory(values: List<Post>): String =
        json.encodeToString(postsSerializer, values)

    fun decodeViewHistory(value: String): List<Post> =
        runCatching { json.decodeFromString(postsSerializer, value) }.getOrDefault(emptyList())

    /** Puts [post] first, dropping any earlier view of the same work, and keeps the newest [limit]. */
    fun recordView(existing: List<Post>, post: Post, limit: Int = 200): List<Post> =
        (listOf(post) + existing.filterNot { it.siteId == post.siteId && it.id == post.id }).take(limit)
}

class LattePreferences(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore = context.latteDataStore

    // Read once only to migrate legacy values into the DataStore.
    private val legacyPrefs: android.content.SharedPreferences =
        context.getSharedPreferences("latte_prefs", Context.MODE_PRIVATE)

    private val _columnCount = MutableStateFlow(2)
    val columnCount: StateFlow<Int> = _columnCount.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _safeMode = MutableStateFlow(true)
    val safeMode: StateFlow<Boolean> = _safeMode.asStateFlow()

    private val _favoriteTags = MutableStateFlow<List<FavoriteTag>>(emptyList())
    val favoriteTags: StateFlow<List<FavoriteTag>> = _favoriteTags.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _proxyHost = MutableStateFlow<String?>(null)
    val proxyHost: StateFlow<String?> = _proxyHost.asStateFlow()

    private val _proxyPort = MutableStateFlow<Int?>(null)
    val proxyPort: StateFlow<Int?> = _proxyPort.asStateFlow()

    private val _viewHistory = MutableStateFlow<List<Post>>(emptyList())
    val viewHistory: StateFlow<List<Post>> = _viewHistory.asStateFlow()

    init {
        scope.launch {
            migrateLegacyPrefsOnce()
            dataStore.data.collect { values ->
                _columnCount.value = values[KEY_COLUMN_COUNT] ?: 2
                _themeMode.value = values[KEY_THEME_MODE]
                    ?.let { mode -> runCatching { ThemeMode.valueOf(mode) }.getOrNull() }
                    ?: ThemeMode.SYSTEM
                _safeMode.value = values[KEY_SAFE_MODE] ?: true
                // Crash-safe read: a value written with an incompatible shape
                // (dev-build leftovers) is dropped rather than cast-crashing.
                val rawFavorites = runCatching { values[KEY_FAVORITE_TAGS] }.getOrNull()
                if (rawFavorites is String) {
                    _favoriteTags.value = LattePreferenceCodecs.decodeFavoriteTags(rawFavorites)
                } else {
                    if (rawFavorites != null) {
                        scope.launch { dataStore.edit { it.remove(KEY_FAVORITE_TAGS) } }
                    }
                    _favoriteTags.value = emptyList()
                }
                _recentSearches.value = runCatching { values[KEY_RECENT_SEARCHES] }.getOrNull()
                    ?.let(LattePreferenceCodecs::decodeRecentSearches)
                    ?: emptyList()
                _viewHistory.value = runCatching { values[KEY_VIEW_HISTORY] }.getOrNull()
                    ?.let(LattePreferenceCodecs::decodeViewHistory)
                    ?: emptyList()
                _proxyHost.value = values[KEY_HTTP_PROXY_HOST]
                _proxyPort.value = values[KEY_HTTP_PROXY_PORT]
            }
        }
    }

    /**
     * One-time copy of the pre-DataStore SharedPreferences values into the
     * DataStore. Runs only when the DataStore is still empty, so it is a
     * no-op on every later start. The legacy file is left in place (small and
     * inert) rather than deleted, keeping the migration reversible.
     */
    private suspend fun migrateLegacyPrefsOnce() {
        val current = dataStore.data.first()
        if (current.asMap().isNotEmpty()) return
        val legacy = legacyPrefs
        val legacyColumns = legacy.getInt(KEY_COLUMN_COUNT.name, 2)
        val legacyTheme = legacy.getString(KEY_THEME_MODE.name, null)
        val legacySafe = legacy.getBoolean(KEY_SAFE_MODE.name, true)
        dataStore.edit { values ->
            values[KEY_COLUMN_COUNT] = legacyColumns
            if (legacyTheme != null) values[KEY_THEME_MODE] = legacyTheme
            values[KEY_SAFE_MODE] = legacySafe
        }
    }

    fun setColumnCount(count: Int) {
        val safeCount = count.coerceIn(1, 3)
        scope.launch { dataStore.edit { it[KEY_COLUMN_COUNT] = safeCount } }
    }

    fun cycleColumnCount(): Int {
        val next = if (_columnCount.value >= 3) 1 else _columnCount.value + 1
        setColumnCount(next)
        return next
    }

    fun setThemeMode(mode: ThemeMode) {
        scope.launch { dataStore.edit { it[KEY_THEME_MODE] = mode.name } }
    }

    fun setSafeMode(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SAFE_MODE] = enabled } }
    }

    fun isFavoriteTag(tag: String, platform: PlatformId): Boolean =
        _favoriteTags.value.any { it.tag == tag.trim() && it.platform == platform }

    fun setTagFavorite(tag: String, platform: PlatformId, favorite: Boolean) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        val updated = _favoriteTags.value
            .filterNot { it.tag == trimmed && it.platform == platform }
            .let { existing -> if (favorite) existing + FavoriteTag(trimmed, platform) else existing }
        _favoriteTags.value = updated
        scope.launch {
            dataStore.edit { it[KEY_FAVORITE_TAGS] = LattePreferenceCodecs.encodeFavoriteTags(updated) }
        }
    }

    fun recordRecentSearch(query: String) {
        // Compute inside the edit so concurrent records serialize against the
        // stored value instead of racing on a stale StateFlow snapshot.
        scope.launch {
            dataStore.edit { values ->
                val existing = values[KEY_RECENT_SEARCHES]
                    ?.let(LattePreferenceCodecs::decodeRecentSearches)
                    ?: emptyList()
                val updated = LattePreferenceCodecs.recordRecentSearch(existing, query)
                values[KEY_RECENT_SEARCHES] = LattePreferenceCodecs.encodeRecentSearches(updated)
            }
        }
    }

    fun recordView(post: Post) {
        scope.launch {
            dataStore.edit { values ->
                val existing = values[KEY_VIEW_HISTORY]
                    ?.let(LattePreferenceCodecs::decodeViewHistory)
                    ?: emptyList()
                values[KEY_VIEW_HISTORY] = LattePreferenceCodecs.encodeViewHistory(
                    LattePreferenceCodecs.recordView(existing, post),
                )
            }
        }
    }

    fun clearViewHistory() {
        scope.launch {
            dataStore.edit { values -> values.remove(KEY_VIEW_HISTORY) }
        }
    }

    fun exportBackup(): String = LatteBackupCodec.encode(
        LatteBackup(
            columnCount = _columnCount.value,
            themeMode = _themeMode.value,
            safeMode = _safeMode.value,
            favoriteTags = _favoriteTags.value,
            recentSearches = _recentSearches.value,
            proxyHost = _proxyHost.value,
            proxyPort = _proxyPort.value,
            viewHistory = _viewHistory.value,
        ),
    )

    /** Replaces the local settings and history with [text]'s contents. Credentials are untouched. */
    suspend fun importBackup(text: String): Result<Unit> {
        val backup = LatteBackupCodec.decode(text).getOrElse { return Result.failure(it) }
        dataStore.edit { values ->
            values[KEY_COLUMN_COUNT] = backup.columnCount
            values[KEY_THEME_MODE] = backup.themeMode.name
            values[KEY_SAFE_MODE] = backup.safeMode
            values[KEY_FAVORITE_TAGS] = LattePreferenceCodecs.encodeFavoriteTags(backup.favoriteTags)
            values[KEY_RECENT_SEARCHES] = LattePreferenceCodecs.encodeRecentSearches(backup.recentSearches)
            values[KEY_VIEW_HISTORY] = LattePreferenceCodecs.encodeViewHistory(backup.viewHistory)
            val host = backup.proxyHost
            val port = backup.proxyPort
            if (host.isNullOrBlank() || port == null || port <= 0) {
                values.remove(KEY_HTTP_PROXY_HOST)
                values.remove(KEY_HTTP_PROXY_PORT)
            } else {
                values[KEY_HTTP_PROXY_HOST] = host
                values[KEY_HTTP_PROXY_PORT] = port
            }
        }
        return Result.success(Unit)
    }

    fun clearRecentSearches() {
        scope.launch {
            dataStore.edit { values ->
                values.remove(KEY_RECENT_SEARCHES)
            }
        }
    }

    fun clearCache() {
        context.cacheDir?.deleteRecursively()
        context.cacheDir?.mkdirs()
    }

    fun setHttpProxy(host: String?, port: Int?) {
        scope.launch {
            dataStore.edit { values ->
                if (host.isNullOrBlank() || port == null || port <= 0) {
                    values.remove(KEY_HTTP_PROXY_HOST)
                    values.remove(KEY_HTTP_PROXY_PORT)
                } else {
                    values[KEY_HTTP_PROXY_HOST] = host.trim()
                    values[KEY_HTTP_PROXY_PORT] = port
                }
            }
        }
    }

    companion object {
        private val KEY_COLUMN_COUNT = intPreferencesKey("column_count")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_SAFE_MODE = booleanPreferencesKey("safe_mode")
        private val KEY_FAVORITE_TAGS = stringPreferencesKey("favorite_tags")
        private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        private val KEY_VIEW_HISTORY = stringPreferencesKey("view_history")
        private val KEY_HTTP_PROXY_HOST = stringPreferencesKey("http_proxy_host")
        private val KEY_HTTP_PROXY_PORT = intPreferencesKey("http_proxy_port")
    }
}
