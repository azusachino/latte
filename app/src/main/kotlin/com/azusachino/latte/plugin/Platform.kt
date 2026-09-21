package com.azusachino.latte.plugin

enum class PlatformId(
    val externalId: String,
    val displayName: String,
    val webUrl: String,
    val apiUrl: String,
    val capabilities: Set<PlatformCapability>,
) {
    YANDE(
        externalId = "yande.re",
        displayName = "yande.re",
        webUrl = "https://yande.re",
        apiUrl = "https://yande.re",
        capabilities = setOf(PlatformCapability.SCORING, PlatformCapability.FAVORITES),
    ),
    PIXIV(
        externalId = "pixiv",
        displayName = "Pixiv",
        webUrl = "https://www.pixiv.net",
        apiUrl = "https://app-api.pixiv.net",
        capabilities = setOf(PlatformCapability.FAVORITES, PlatformCapability.USER_FEED),
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
}
