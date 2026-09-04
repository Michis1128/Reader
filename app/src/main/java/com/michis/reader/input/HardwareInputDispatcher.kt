package com.michis.reader.input

enum class ReaderHardwareAction {
    NEXT_PAGE,
    PREVIOUS_PAGE,
    INCREASE_TEXT_SIZE,
    DECREASE_TEXT_SIZE,
    TOGGLE_READING_THEME,
    TOGGLE_BOOKMARK,
    NONE
}

class HardwareInputDispatcher(
    private val actionReceiver: (ReaderHardwareAction) -> Unit
) {
    fun dispatch(action: ReaderHardwareAction) {
        actionReceiver(action)
    }
}
