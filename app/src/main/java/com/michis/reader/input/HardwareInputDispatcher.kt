package com.michis.reader.input

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class ReaderHardwareAction {
    NEXT_PAGE,
    PREVIOUS_PAGE,
    INCREASE_TEXT_SIZE,
    DECREASE_TEXT_SIZE,
    TOGGLE_READING_THEME,
    TOGGLE_BOOKMARK,
    NONE
}

class HardwareInputDispatcher {
    private val mutableActions = MutableSharedFlow<ReaderHardwareAction>(extraBufferCapacity = 8)

    val actions = mutableActions.asSharedFlow()

    fun dispatch(action: ReaderHardwareAction): Boolean = mutableActions.tryEmit(action)
}
