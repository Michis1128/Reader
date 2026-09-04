package com.michis.reader.input

import android.view.KeyEvent

object ReaderHardwareKeyMapper {
    private val supportedKeyCodes = setOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_MINUS,
        KeyEvent.KEYCODE_PLUS,
        // Samsung Air Command can retain trigger keys from a previously
        // registered RemoteActions definition after the app is updated.
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_B
    )

    fun supports(keyCode: Int): Boolean = keyCode in supportedKeyCodes

    fun controlFor(keyCode: Int, event: KeyEvent): ReaderHardwareControl? {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return null

        return when (keyCode) {
            KeyEvent.KEYCODE_PAGE_DOWN -> ReaderHardwareControl.BUTTON_CLICK
            KeyEvent.KEYCODE_PAGE_UP -> ReaderHardwareControl.BUTTON_DOUBLE_CLICK
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> ReaderHardwareControl.BUTTON_CLICK
            KeyEvent.KEYCODE_B -> ReaderHardwareControl.BUTTON_DOUBLE_CLICK
            KeyEvent.KEYCODE_DPAD_UP -> ReaderHardwareControl.SWIPE_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> ReaderHardwareControl.SWIPE_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> ReaderHardwareControl.SWIPE_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> ReaderHardwareControl.SWIPE_RIGHT
            KeyEvent.KEYCODE_MINUS -> ReaderHardwareControl.CIRCLE_COUNTERCLOCKWISE
            KeyEvent.KEYCODE_PLUS -> ReaderHardwareControl.CIRCLE_CLOCKWISE
            else -> null
        }
    }
}
