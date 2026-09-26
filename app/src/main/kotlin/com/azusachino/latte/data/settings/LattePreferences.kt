package com.azusachino.latte.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

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

    private val _favoriteTags = MutableStateFlow<Set<String>>(emptySet())
    val favoriteTags: StateFlow<Set<String>> = _favoriteTags.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    init {
        scope.launch {
            migrateLegacyPrefsOnce()
            dataStore.data.collect { values ->
                _columnCount.value = values[KEY_COLUMN_COUNT] ?: 2
                _themeMode.value = values[KEY_THEME_MODE]
                    ?.let { mode -> runCatching { ThemeMode.valueOf(mode) }.getOrNull() }
                    ?: ThemeMode.SYSTEM
                _safeMode.value = values[KEY_SAFE_MODE] ?: true
                _favoriteTags.value = values[KEY_FAVORITE_TAGS] ?: emptySet()
                _recentSearches.value = values[KEY_RECENT_SEARCHES]
                    ?.let(LattePreferenceCodecs::decodeRecentSearches)
                    ?: emptyList()
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

    fun isFavoriteTag(tag: String): Boolean = _favoriteTags.value.contains(tag.trim())

    fun setTagFavorite(tag: String, favorite: Boolean) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        scope.launch {
            dataStore.edit { values ->
                val current = values[KEY_FAVORITE_TAGS] ?: emptySet()
                values[KEY_FAVORITE_TAGS] =
                    if (favorite) current + trimmed else current - trimmed
            }
        }
    }

    fun recordRecentSearch(query: String) {
        val updated = LattePreferenceCodecs.recordRecentSearch(_recentSearches.value, query)
        scope.launch {
            dataStore.edit { it[KEY_RECENT_SEARCHES] = LattePreferenceCodecs.encodeRecentSearches(updated) }
        }
    }

    fun getCacheSizeBytes(): Long {
        val cacheDir = context.cacheDir ?: return 0L
        return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearCache() {
        context.cacheDir?.deleteRecursively()
        context.cacheDir?.mkdirs()
    }

    companion object {
        private val KEY_COLUMN_COUNT = intPreferencesKey("column_count")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_SAFE_MODE = booleanPreferencesKey("safe_mode")
        private val KEY_FAVORITE_TAGS = stringSetPreferencesKey("favorite_tags")
        private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    }
}
