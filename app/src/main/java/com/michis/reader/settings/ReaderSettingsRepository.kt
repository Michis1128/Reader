package com.michis.reader.settings

import com.michis.reader.theme.ReadingThemePalette

import android.content.Context
import android.content.SharedPreferences

/** Punto unico de acceso a las preferencias globales de lectura y apariencia. */
class ReaderSettingsRepository private constructor(context: Context) {
    val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    var theme: String
        get() = preferences.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        set(value) = preferences.edit().putString(KEY_THEME, value).apply()

    var fontSizeDp: Float
        get() = preferences.getFloat(KEY_FONT_SIZE_DP, preferences.getFloat(LEGACY_KEY_FONT_SIZE, DEFAULT_FONT_SIZE_DP))
            .coerceIn(MINIMUM_FONT_SIZE_DP, MAXIMUM_FONT_SIZE_DP)
        set(value) = preferences.edit().putFloat(KEY_FONT_SIZE_DP, value.coerceIn(MINIMUM_FONT_SIZE_DP, MAXIMUM_FONT_SIZE_DP)).apply()

    var lineHeight: Float
        get() = preferences.getFloat(KEY_LINE_HEIGHT, preferences.getFloat(LEGACY_KEY_LINE_SPACING, DEFAULT_LINE_HEIGHT))
        set(value) = preferences.edit().putFloat(KEY_LINE_HEIGHT, value.coerceIn(0.5f, 3f)).apply()

    var textAlignment: Int
        get() = preferences.getInt(KEY_TEXT_ALIGNMENT, DEFAULT_TEXT_ALIGNMENT).coerceIn(0, 3)
        set(value) = preferences.edit().putInt(KEY_TEXT_ALIGNMENT, value.coerceIn(0, 3)).apply()

    var screenTimeoutMinutes: Int
        get() = preferences.getInt(KEY_SCREEN_TIMEOUT_MINUTES, DEFAULT_SCREEN_TIMEOUT_MINUTES)
            .takeIf { it in SCREEN_TIMEOUT_OPTIONS } ?: DEFAULT_SCREEN_TIMEOUT_MINUTES
        set(value) = preferences.edit().putInt(
            KEY_SCREEN_TIMEOUT_MINUTES,
            value.takeIf { it in SCREEN_TIMEOUT_OPTIONS } ?: DEFAULT_SCREEN_TIMEOUT_MINUTES
        ).apply()

    var menuColorMode: String
        get() = preferences.getString(KEY_MENU_COLOR_MODE, DEFAULT_MENU_COLOR_MODE) ?: DEFAULT_MENU_COLOR_MODE
        set(value) = preferences.edit().putString(KEY_MENU_COLOR_MODE, value).apply()

    companion object {
        const val PREFERENCES_NAME = "reading_preferences"
        const val KEY_THEME = "theme"
        const val KEY_FONT_SIZE_DP = "font_size_dp"
        const val KEY_LINE_HEIGHT = "line_height"
        const val KEY_TEXT_ALIGNMENT = "text_alignment"
        const val KEY_SCREEN_TIMEOUT_MINUTES = "reader_screen_timeout_minutes"
        const val KEY_MENU_COLOR_MODE = "menu_color_mode"
        const val KEY_MENU_CUSTOM_COLOR = "menu_custom_color"
        const val KEY_QUOTE_DEFAULT_COLOR = "quote_default_color"
        const val KEY_DICTIONARY_HIGHLIGHT_COLOR = "dictionary_highlight_color"
        const val KEY_BOOKMARK_COLOR = "bookmark_color"

        const val DEFAULT_THEME = "Sepia"
        const val DEFAULT_FONT_SIZE_DP = 19f
        const val DEFAULT_LINE_HEIGHT = 1.35f
        const val DEFAULT_TEXT_ALIGNMENT = 0
        const val DEFAULT_SCREEN_TIMEOUT_MINUTES = 5
        const val DEFAULT_MENU_COLOR_MODE = "theme"
        const val DEFAULT_MENU_CUSTOM_COLOR = "#FFF4E0"
        const val DEFAULT_QUOTE_COLOR = "#66FFD54F"
        const val DEFAULT_DICTIONARY_HIGHLIGHT_COLOR = "#665A7D9A"
        const val DEFAULT_BOOKMARK_COLOR = "#FF8D6E63"
        const val MINIMUM_FONT_SIZE_DP = 8f
        const val MAXIMUM_FONT_SIZE_DP = 72f
        val SCREEN_TIMEOUT_OPTIONS = setOf(2, 3, 5, 10, 15)

        private const val LEGACY_KEY_FONT_SIZE = "font_size"
        private const val LEGACY_KEY_LINE_SPACING = "line_spacing"

        @Volatile
        private var sharedInstance: ReaderSettingsRepository? = null

        fun get(context: Context): ReaderSettingsRepository = sharedInstance ?: synchronized(this) {
            sharedInstance ?: ReaderSettingsRepository(context).also { sharedInstance = it }
        }
    }
}
