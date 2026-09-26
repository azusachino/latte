package com.azusachino.latte.data.network.moebooru

import kotlinx.serialization.Serializable

@Serializable
data class MoebooruTagDto(
    val name: String = "",
    val count: Int = 0,
)
