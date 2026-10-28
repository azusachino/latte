package com.azusachino.latte.data.settings

import com.azusachino.latte.data.model.FavoriteTag
import com.azusachino.latte.data.model.Post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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
    // Mihon-style name, e.g. latte_2026-10-28_09-30.lattebak, so exports sort and never collide.
    fun fileName(now: LocalDateTime): String =
        "latte_${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm", Locale.ENGLISH))}.lattebak"

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

    /** The .lattebak file: the JSON gzip-compressed. */
    fun encodeCompressed(backup: LatteBackup): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(encode(backup).toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    /** Accepts a gzip-compressed backup, or plain JSON for files written before compression. */
    fun decodeBytes(bytes: ByteArray): Result<LatteBackup> = runCatching {
        val isGzip = bytes.size >= 2 && bytes[0] == GZIP_MAGIC_1 && bytes[1] == GZIP_MAGIC_2
        val json = if (isGzip) GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes
        json.toString(Charsets.UTF_8)
    }.mapCatching { text -> decode(text).getOrThrow() }

    private const val GZIP_MAGIC_1: Byte = 0x1f.toByte()
    private const val GZIP_MAGIC_2: Byte = 0x8b.toByte()
}
