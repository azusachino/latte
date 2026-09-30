package com.azusachino.latte.plugin.moebooru

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class MoebooruAuthResult(
    val externalId: String,
    val username: String,
    val passHash: String,
    val userId: String?,
)

object MoebooruAuthCallbackBus {
    private val _events = MutableSharedFlow<MoebooruAuthResult>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun publish(result: MoebooruAuthResult) {
        _events.tryEmit(result)
    }
}
