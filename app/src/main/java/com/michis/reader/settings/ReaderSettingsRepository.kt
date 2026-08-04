package com.michis.reader.settings

import com.michis.reader.theme.ReadingThemePalette

import android.content.Context
import android.content.SharedPreferences

enum class PageMarginMode(val preferenceValue: String, val displayName: String, val horizontalFactor: Double) {
    LARGE("large", "Grandes", 1.5),
    NORMAL("normal", "Normales", 1.0),
    REDUCED("reduced", "Reducidos", 0.5),
    CUSTOM("custom", "Personalizados", 0.0)
}

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

    var fontWeight: Float
        get() = preferences.getFloat(KEY_FONT_WEIGHT, DEFAULT_FONT_WEIGHT).coerceIn(0.5f, 2f)
        set(value) = preferences.edit().putFloat(KEY_FONT_WEIGHT, value.coerceIn(0.5f, 2f)).apply()

    var continuousScroll: Boolean
        get() = preferences.getBoolean(KEY_CONTINUOUS_SCROLL, false)
        set(value) = preferences.edit().putBoolean(KEY_CONTINUOUS_SCROLL, value).apply()

    var pageTurnAnimations: Boolean
        get() = preferences.getBoolean(KEY_PAGE_TURN_ANIMATIONS, true)
        set(value) = preferences.edit().putBoolean(KEY_PAGE_TURN_ANIMATIONS, value).apply()

    fun twoPagesLandscape(defaultValue: Boolean): Boolean =
        preferences.getBoolean(KEY_TWO_PAGES_LANDSCAPE, defaultValue)

    fun setTwoPagesLandscape(value: Boolean) {
        preferences.edit().putBoolean(KEY_TWO_PAGES_LANDSCAPE, value).apply()
    }

    var fontFamilyIndex: Int
        get() = preferences.getInt(KEY_FONT_FAMILY, 0).coerceAtLeast(0)
        set(value) = preferences.edit().putInt(KEY_FONT_FAMILY, value.coerceAtLeast(0)).apply()

    var readerOrientation: Int
        get() = preferences.getInt(KEY_READER_ORIENTATION, 0).coerceIn(0, 2)
        set(value) = preferences.edit().putInt(KEY_READER_ORIENTATION, value.coerceIn(0, 2)).apply()

    var cornerBookmarkEnabled: Boolean
        get() = preferences.getBoolean(KEY_CORNER_BOOKMARK_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_CORNER_BOOKMARK_ENABLED, value).apply()

    fun quickMode(index: Int): String {
        require(index in 0..1)
        val defaultValue = if (index == 0) "Día" else "Noche"
        return preferences.getString(if (index == 0) KEY_QUICK_MODE_ONE else KEY_QUICK_MODE_TWO, defaultValue)
            ?: defaultValue
    }

    fun setQuickMode(index: Int, themeName: String) {
        require(index in 0..1)
        preferences.edit().putString(if (index == 0) KEY_QUICK_MODE_ONE else KEY_QUICK_MODE_TWO, themeName).apply()
    }

    var pageMarginMode: PageMarginMode
        get() {
            val storedValue = preferences.getString(KEY_PAGE_MARGIN_MODE, null)
            return PageMarginMode.entries.firstOrNull { it.preferenceValue == storedValue }
                ?: if (preferences.contains(LEGACY_KEY_PAGE_MARGINS)) {
                    if (preferences.getBoolean(LEGACY_KEY_PAGE_MARGINS, true)) PageMarginMode.NORMAL
                    else PageMarginMode.CUSTOM
                } else PageMarginMode.REDUCED
        }
        set(value) = preferences.edit().putString(KEY_PAGE_MARGIN_MODE, value.preferenceValue).apply()

    var customPageMarginTopDp: Float
        get() = customPageMargin(KEY_CUSTOM_PAGE_MARGIN_TOP_DP)
        set(value) = saveCustomPageMargin(KEY_CUSTOM_PAGE_MARGIN_TOP_DP, value)

    var customPageMarginBottomDp: Float
        get() = customPageMargin(KEY_CUSTOM_PAGE_MARGIN_BOTTOM_DP)
        set(value) = saveCustomPageMargin(KEY_CUSTOM_PAGE_MARGIN_BOTTOM_DP, value)

    var customPageMarginLeftDp: Float
        get() = customPageMargin(KEY_CUSTOM_PAGE_MARGIN_LEFT_DP)
        set(value) = saveCustomPageMargin(KEY_CUSTOM_PAGE_MARGIN_LEFT_DP, value)

    var customPageMarginRightDp: Float
        get() = customPageMargin(KEY_CUSTOM_PAGE_MARGIN_RIGHT_DP)
        set(value) = saveCustomPageMargin(KEY_CUSTOM_PAGE_MARGIN_RIGHT_DP, value)

    private fun customPageMargin(key: String): Float {
        val legacyDisabled = preferences.contains(LEGACY_KEY_PAGE_MARGINS) &&
            !preferences.getBoolean(LEGACY_KEY_PAGE_MARGINS, true)
        val defaultValue = if (legacyDisabled) 0f else DEFAULT_CUSTOM_PAGE_MARGIN_DP
        return preferences.getFloat(key, defaultValue).coerceIn(MINIMUM_CUSTOM_PAGE_MARGIN_DP, MAXIMUM_CUSTOM_PAGE_MARGIN_DP)
    }

    private fun saveCustomPageMargin(key: String, value: Float) {
        preferences.edit().putFloat(
            key,
            value.coerceIn(MINIMUM_CUSTOM_PAGE_MARGIN_DP, MAXIMUM_CUSTOM_PAGE_MARGIN_DP)
        ).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "reading_preferences"
        const val KEY_THEME = "theme"
        const val KEY_FONT_SIZE_DP = "font_size_dp"
        const val KEY_LINE_HEIGHT = "line_height"
        const val KEY_TEXT_ALIGNMENT = "text_alignment"
        const val KEY_SCREEN_TIMEOUT_MINUTES = "reader_screen_timeout_minutes"
        const val KEY_MENU_COLOR_MODE = "menu_color_mode"
        const val KEY_MENU_CUSTOM_COLOR = "menu_custom_color"
        const val KEY_FONT_WEIGHT = "font_weight"
        const val KEY_CONTINUOUS_SCROLL = "continuous_scroll"
        const val KEY_PAGE_TURN_ANIMATIONS = "page_turn_animations"
        const val KEY_TWO_PAGES_LANDSCAPE = "two_pages_landscape"
        const val KEY_FONT_FAMILY = "font_family"
        const val KEY_READER_ORIENTATION = "reader_orientation"
        const val KEY_CORNER_BOOKMARK_ENABLED = "corner_bookmark_enabled"
        const val KEY_QUICK_MODE_ONE = "quick_mode_1"
        const val KEY_QUICK_MODE_TWO = "quick_mode_2"
        const val KEY_QUOTE_DEFAULT_COLOR = "quote_default_color"
        const val KEY_DICTIONARY_HIGHLIGHT_COLOR = "dictionary_highlight_color"
        const val KEY_BOOKMARK_COLOR = "bookmark_color"
        const val KEY_PAGE_MARGIN_MODE = "page_margin_mode"
        const val KEY_CUSTOM_PAGE_MARGIN_TOP_DP = "custom_page_margin_top_dp"
        const val KEY_CUSTOM_PAGE_MARGIN_BOTTOM_DP = "custom_page_margin_bottom_dp"
        const val KEY_CUSTOM_PAGE_MARGIN_LEFT_DP = "custom_page_margin_left_dp"
        const val KEY_CUSTOM_PAGE_MARGIN_RIGHT_DP = "custom_page_margin_right_dp"

        const val DEFAULT_THEME = "Sepia"
        const val DEFAULT_FONT_SIZE_DP = 19f
        const val DEFAULT_LINE_HEIGHT = 1.35f
        const val DEFAULT_FONT_WEIGHT = 1f
        const val DEFAULT_TEXT_ALIGNMENT = 0
        const val DEFAULT_SCREEN_TIMEOUT_MINUTES = 5
        const val DEFAULT_MENU_COLOR_MODE = "theme"
        const val DEFAULT_MENU_CUSTOM_COLOR = "#FFF4E0"
        const val DEFAULT_QUOTE_COLOR = "#66FFD54F"
        const val DEFAULT_DICTIONARY_HIGHLIGHT_COLOR = "#665A7D9A"
        const val DEFAULT_BOOKMARK_COLOR = "#FF8D6E63"
        const val MINIMUM_FONT_SIZE_DP = 8f
        const val MAXIMUM_FONT_SIZE_DP = 72f
        const val DEFAULT_CUSTOM_PAGE_MARGIN_DP = 16f
        const val MINIMUM_CUSTOM_PAGE_MARGIN_DP = 0f
        const val MAXIMUM_CUSTOM_PAGE_MARGIN_DP = 96f
        val SCREEN_TIMEOUT_OPTIONS = setOf(2, 3, 5, 10, 15)

        private const val LEGACY_KEY_FONT_SIZE = "font_size"
        private const val LEGACY_KEY_LINE_SPACING = "line_spacing"
        private const val LEGACY_KEY_PAGE_MARGINS = "page_margins"

        @Volatile
        private var sharedInstance: ReaderSettingsRepository? = null

        fun get(context: Context): ReaderSettingsRepository = sharedInstance ?: synchronized(this) {
            sharedInstance ?: ReaderSettingsRepository(context).also { sharedInstance = it }
        }
    }
}
