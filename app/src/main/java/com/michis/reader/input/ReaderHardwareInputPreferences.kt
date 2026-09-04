package com.michis.reader.input

import android.content.SharedPreferences

enum class ReaderHardwareControl(
    val displayName: String,
    internal val preferenceKey: String,
    internal val defaultAction: ReaderHardwareAction
) {
    BUTTON_CLICK("Presionar una vez", "hardware_button_click", ReaderHardwareAction.NEXT_PAGE),
    BUTTON_DOUBLE_CLICK("Presionar dos veces", "hardware_button_double_click", ReaderHardwareAction.PREVIOUS_PAGE),
    SWIPE_UP("Gesto hacia arriba", "hardware_swipe_up", ReaderHardwareAction.INCREASE_TEXT_SIZE),
    SWIPE_DOWN("Gesto hacia abajo", "hardware_swipe_down", ReaderHardwareAction.DECREASE_TEXT_SIZE),
    SWIPE_LEFT("Gesto hacia la izquierda", "hardware_swipe_left", ReaderHardwareAction.PREVIOUS_PAGE),
    SWIPE_RIGHT("Gesto hacia la derecha", "hardware_swipe_right", ReaderHardwareAction.NEXT_PAGE),
    CIRCLE_COUNTERCLOCKWISE("Círculo hacia la izquierda", "hardware_circle_counterclockwise", ReaderHardwareAction.TOGGLE_READING_THEME),
    CIRCLE_CLOCKWISE("Círculo hacia la derecha", "hardware_circle_clockwise", ReaderHardwareAction.TOGGLE_BOOKMARK)
}

class ReaderHardwareInputPreferences(private val preferences: SharedPreferences) {
    fun actionFor(control: ReaderHardwareControl): ReaderHardwareAction {
        val storedAction = preferences.getString(control.preferenceKey, null)
        return ReaderHardwareAction.entries.firstOrNull { it.name == storedAction } ?: control.defaultAction
    }

    fun setAction(control: ReaderHardwareControl, action: ReaderHardwareAction) {
        preferences.edit().putString(control.preferenceKey, action.name).apply()
    }

    fun restoreDefaults() {
        preferences.edit().apply {
            ReaderHardwareControl.entries.forEach { remove(it.preferenceKey) }
        }.apply()
    }

    companion object {
        val actionOptions = listOf(
            ReaderHardwareAction.NEXT_PAGE to "Página siguiente",
            ReaderHardwareAction.PREVIOUS_PAGE to "Página anterior",
            ReaderHardwareAction.INCREASE_TEXT_SIZE to "Aumentar texto",
            ReaderHardwareAction.DECREASE_TEXT_SIZE to "Reducir texto",
            ReaderHardwareAction.TOGGLE_READING_THEME to "Cambiar tema",
            ReaderHardwareAction.TOGGLE_BOOKMARK to "Agregar o quitar marcador",
            ReaderHardwareAction.NONE to "Sin acción"
        )
    }
}
