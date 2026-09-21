package com.azusachino.latte.plugin

enum class PlatformId(
    val externalId: String,
    val displayName: String,
) {
    YANDE(externalId = "yande.re", displayName = "yande.re"),
    PIXIV(externalId = "pixiv", displayName = "Pixiv"),
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
