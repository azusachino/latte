package com.azusachino.latte.data.network.moebooru

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MoebooruPoolDto(
    val id: Long,
    val name: String = "",
    @SerialName("post_count") val postCount: Int = 0,
    @SerialName("is_public") val isPublic: Boolean = true,
    val description: String = "",
)
