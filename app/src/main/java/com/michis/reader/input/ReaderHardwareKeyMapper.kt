package com.michis.reader.input

import android.view.KeyEvent

object ReaderHardwareKeyMapper {
    private val supportedKeyCodes = setOf(
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_DEL,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_MINUS,
        KeyEvent.KEYCODE_PLUS
    )

    fun supports(keyCode: Int): Boolean = keyCode in supportedKeyCodes

    fun controlFor(keyCode: Int, event: KeyEvent): ReaderHardwareControl? {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return null

        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> ReaderHardwareControl.BUTTON_CLICK
            KeyEvent.KEYCODE_DEL -> ReaderHardwareControl.BUTTON_DOUBLE_CLICK
            KeyEvent.KEYCODE_DPAD_UP -> ReaderHardwareControl.SWIPE_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> ReaderHardwareControl.SWIPE_DOWN
            KeyEvent.KEYCODE_PAGE_UP -> ReaderHardwareControl.SWIPE_LEFT
            KeyEvent.KEYCODE_PAGE_DOWN -> ReaderHardwareControl.SWIPE_RIGHT
            KeyEvent.KEYCODE_MINUS -> ReaderHardwareControl.CIRCLE_COUNTERCLOCKWISE
            KeyEvent.KEYCODE_PLUS -> ReaderHardwareControl.CIRCLE_CLOCKWISE
            else -> null
        }
    }
}
