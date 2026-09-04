package com.michis.reader.input

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class ReaderHardwareAction {
    NEXT_PAGE,
    PREVIOUS_PAGE
}

class HardwareInputDispatcher {
    private val mutableActions = MutableSharedFlow<ReaderHardwareAction>(extraBufferCapacity = 8)

    val actions = mutableActions.asSharedFlow()

    fun dispatch(action: ReaderHardwareAction): Boolean = mutableActions.tryEmit(action)
}
