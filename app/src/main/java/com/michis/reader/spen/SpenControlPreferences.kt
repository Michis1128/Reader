package com.michis.reader.spen

object SpenControlPreferences {
    const val NONE = "none"
    const val NEXT = "next"
    const val PREVIOUS = "previous"
    const val TOGGLE_CONTROLS = "toggle_controls"
    const val BOOKMARK = "bookmark"
    const val LARGER_TEXT = "larger_text"
    const val SMALLER_TEXT = "smaller_text"
    const val QUICK_THEME = "quick_theme"
    const val ADD_DICTIONARY = "add_dictionary"
    const val ADD_QUOTE = "add_quote"

    val actionLabels = arrayOf(
        "Sin acción", "Página o sección siguiente", "Página o sección anterior", "Mostrar u ocultar controles",
        "Agregar marcador", "Aumentar texto", "Reducir texto", "Cambiar tema rápido", "Agregar al diccionario", "Agregar cita"
    )
    val actionValues = arrayOf(
        NONE, NEXT, PREVIOUS, TOGGLE_CONTROLS, BOOKMARK, LARGER_TEXT,
        SMALLER_TEXT, QUICK_THEME, ADD_DICTIONARY, ADD_QUOTE
    )

    data class Gesture(val label: String, val preferenceKey: String, val defaultAction: String)

    val click = Gesture("Clic del botón", "spen_click_action", NEXT)
    val doubleClick = Gesture("Doble clic", "spen_double_click_action", PREVIOUS)
    val swipeLeft = Gesture("Deslizar a la izquierda", "spen_swipe_left_action", BOOKMARK)
    val swipeRight = Gesture("Deslizar a la derecha", "spen_swipe_right_action", QUICK_THEME)
    val swipeUp = Gesture("Deslizar hacia arriba", "spen_swipe_up_action", LARGER_TEXT)
    val swipeDown = Gesture("Deslizar hacia abajo", "spen_swipe_down_action", SMALLER_TEXT)
    val circleClockwise = Gesture("Círculo horario", "spen_circle_clockwise_action", ADD_DICTIONARY)
    val circleCounterclockwise = Gesture("Círculo antihorario", "spen_circle_counterclockwise_action", ADD_QUOTE)

    val gestures = listOf(
        click, doubleClick, swipeLeft, swipeRight, swipeUp, swipeDown, circleClockwise, circleCounterclockwise
    )
}
