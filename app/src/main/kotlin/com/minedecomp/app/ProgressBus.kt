package com.minedecomp.app

import com.minedecomp.core.PipelineEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ProgressBus {
    private val _events = MutableSharedFlow<PipelineEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PipelineEvent> = _events.asSharedFlow()

    fun emit(event: PipelineEvent) {
        _events.tryEmit(event)
    }
}
