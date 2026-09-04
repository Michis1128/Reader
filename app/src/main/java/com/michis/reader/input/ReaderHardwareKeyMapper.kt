package com.michis.reader.input

import android.view.KeyEvent

object ReaderHardwareKeyMapper {
    fun actionFor(keyCode: Int, event: KeyEvent): ReaderHardwareAction? {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return null

        return when (keyCode) {
            KeyEvent.KEYCODE_PAGE_DOWN -> ReaderHardwareAction.NEXT_PAGE
            KeyEvent.KEYCODE_PAGE_UP -> ReaderHardwareAction.PREVIOUS_PAGE
            else -> null
        }
    }
}
