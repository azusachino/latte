package com.azusachino.latte.data.model

import com.azusachino.latte.plugin.PlatformId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A locally saved tag, bound to the platform whose search syntax it belongs
 * to. Both yande.re and Pixiv support tag search, so a favorite records
 * where it should be opened.
 */
@Serializable
data class FavoriteTag(
    @SerialName("tag") val tag: String,
    @SerialName("platform") val platform: PlatformId,
)
