package com.azusachino.latte.data.model

import kotlinx.serialization.Serializable

@Serializable
data class YandeTagDto(
    val name: String = "",
    val count: Int = 0,
)
