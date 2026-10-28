package com.azusachino.latte.data.settings

import com.azusachino.latte.data.model.FavoriteTag
import com.azusachino.latte.data.model.Post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A portable copy of the local preferences, favorite tags, recent searches and
 * view history. Account credentials are deliberately absent: they live in
 * encrypted storage and never leave the device.
 */
@Serializable
data class LatteBackup(
    val version: Int = CURRENT_VERSION,
    val columnCount: Int = 2,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val safeMode: Boolean = true,
    val favoriteTags: List<FavoriteTag> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val viewHistory: List<Post> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

object LatteBackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        // Write defaults too, so the file always states its format version.
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun encode(backup: LatteBackup): String =
        json.encodeToString(LatteBackup.serializer(), backup)

    /** Fails on malformed JSON or a backup written by a newer format version. */
    fun decode(text: String): Result<LatteBackup> = runCatching {
        json.decodeFromString(LatteBackup.serializer(), text)
    }.mapCatching { backup ->
        require(backup.version == LatteBackup.CURRENT_VERSION) {
            "Unsupported backup version ${backup.version}"
        }
        backup
    }
}
