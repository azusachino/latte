package com.azusachino.latte.data.network.moebooru

import com.azusachino.latte.data.model.PoolSummary

internal fun MoebooruPoolDto.toDomain(): PoolSummary = PoolSummary(
    id = id,
    name = name,
    postCount = postCount,
    isPublic = isPublic,
    description = description,
)
