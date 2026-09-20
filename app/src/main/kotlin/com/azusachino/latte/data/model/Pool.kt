package com.azusachino.latte.data.model

data class PoolSummary(
    val id: Long,
    val name: String,
    val postCount: Int,
    val isPublic: Boolean,
    val description: String,
) {
    val displayName: String get() = name.replace('_', ' ')
}
