package com.azusachino.latte.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class LattePreferences(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("latte_prefs", Context.MODE_PRIVATE)

    private val _columnCount = MutableStateFlow(prefs.getInt(KEY_COLUMN_COUNT, 2))
    val columnCount: StateFlow<Int> = _columnCount.asStateFlow()

    private val _themeMode = MutableStateFlow(
        runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _safeMode = MutableStateFlow(prefs.getBoolean(KEY_SAFE_MODE, true))
    val safeMode: StateFlow<Boolean> = _safeMode.asStateFlow()

    fun setColumnCount(count: Int) {
        val safeCount = count.coerceIn(1, 3)
        prefs.edit().putInt(KEY_COLUMN_COUNT, safeCount).apply()
        _columnCount.value = safeCount
    }

    fun cycleColumnCount(): Int {
        val next = if (_columnCount.value >= 3) 1 else _columnCount.value + 1
        setColumnCount(next)
        return next
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setSafeMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAFE_MODE, enabled).apply()
        _safeMode.value = enabled
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
        private const val KEY_COLUMN_COUNT = "column_count"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SAFE_MODE = "safe_mode"
    }
}
