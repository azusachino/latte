package com.azusachino.latte.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PlatformId(
    val externalId: String,
    val displayName: String,
    val webUrl: String,
    val apiUrl: String,
    val capabilities: Set<PlatformCapability>,
    val accentColor: Long = 0xFF0080FFL,
) {
    @SerialName("YANDE")
    YANDE(
        externalId = "yande.re",
        displayName = "Yande",
        webUrl = "https://yande.re",
        apiUrl = "https://yande.re",
        capabilities = setOf(PlatformCapability.SCORING, PlatformCapability.FAVORITES),
        accentColor = 0xFF3F6F8FL,
    ),
    @SerialName("KONACHAN")
    KONACHAN(
        externalId = "konachan.net",
        displayName = "Konachan",
        webUrl = "https://konachan.net",
        apiUrl = "https://konachan.net",
        capabilities = setOf(PlatformCapability.SCORING, PlatformCapability.FAVORITES),
        accentColor = 0xFF8D6E2FL,
    ),
    @SerialName("PIXIV")
    PIXIV(
        externalId = "pixiv",
        displayName = "Pixiv",
        webUrl = "https://www.pixiv.net",
        apiUrl = "https://app-api.pixiv.net",
        capabilities = setOf(PlatformCapability.FAVORITES, PlatformCapability.USER_FEED, PlatformCapability.FOLLOW_AUTHORS),
        accentColor = 0xFF0096FAL,
    ),
    ;

    companion object {
        fun fromExternalId(value: String): PlatformId? =
            entries.firstOrNull { it.externalId == value }
    }
}

enum class PlatformCapability {
    SCORING,
    FAVORITES,
    REFERER_INJECT,
    USER_FEED,
    FOLLOW_AUTHORS,
}
