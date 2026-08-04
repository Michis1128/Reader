package com.michis.reader.spen

import android.view.KeyEvent
import com.michis.reader.settings.ReaderSettingsRepository

/** Traduce eventos nativos y preferencias del S Pen a acciones concretas del lector. */
internal class SpenReaderActionController(
    private val settings: ReaderSettingsRepository,
    private val actions: Actions
) {
    data class Actions(
        val nextPage: () -> Unit,
        val previousPage: () -> Unit,
        val toggleControls: () -> Unit,
        val toggleBookmark: () -> Unit,
        val increaseText: () -> Unit,
        val decreaseText: () -> Unit,
        val toggleQuickTheme: () -> Unit,
        val addSelectionToDictionary: () -> Unit,
        val addSelectionAsQuote: () -> Unit,
        val interactionCompleted: () -> Unit
    )

    fun gestureForKeyCode(keyCode: Int): SpenControlPreferences.Gesture? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> SpenControlPreferences.click
        KeyEvent.KEYCODE_B -> SpenControlPreferences.doubleClick
        KeyEvent.KEYCODE_PAGE_DOWN -> SpenControlPreferences.swipeLeft
        KeyEvent.KEYCODE_PAGE_UP -> SpenControlPreferences.swipeRight
        KeyEvent.KEYCODE_DPAD_UP -> SpenControlPreferences.swipeUp
        KeyEvent.KEYCODE_DPAD_DOWN -> SpenControlPreferences.swipeDown
        KeyEvent.KEYCODE_PLUS -> SpenControlPreferences.circleClockwise
        KeyEvent.KEYCODE_MINUS -> SpenControlPreferences.circleCounterclockwise
        else -> null
    }

    fun execute(gesture: SpenControlPreferences.Gesture) {
        when (settings.preferences.getString(gesture.preferenceKey, gesture.defaultAction)) {
            SpenControlPreferences.NONE -> Unit
            SpenControlPreferences.NEXT -> actions.nextPage()
            SpenControlPreferences.PREVIOUS -> actions.previousPage()
            SpenControlPreferences.TOGGLE_CONTROLS -> actions.toggleControls()
            SpenControlPreferences.BOOKMARK -> actions.toggleBookmark()
            SpenControlPreferences.LARGER_TEXT -> actions.increaseText()
            SpenControlPreferences.SMALLER_TEXT -> actions.decreaseText()
            SpenControlPreferences.QUICK_THEME -> actions.toggleQuickTheme()
            SpenControlPreferences.ADD_DICTIONARY -> actions.addSelectionToDictionary()
            SpenControlPreferences.ADD_QUOTE -> actions.addSelectionAsQuote()
        }
        actions.interactionCompleted()
    }
}
