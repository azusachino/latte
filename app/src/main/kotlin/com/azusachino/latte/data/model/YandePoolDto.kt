package com.azusachino.latte.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YandePoolDto(
    val id: Long,
    val name: String = "",
    @SerialName("post_count") val postCount: Int = 0,
    @SerialName("is_public") val isPublic: Boolean = true,
    val description: String = "",
)

fun YandePoolDto.toDomain(): PoolSummary = PoolSummary(
    id = id,
    name = name,
    postCount = postCount,
    isPublic = isPublic,
    description = description,
)
