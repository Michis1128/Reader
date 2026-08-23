package com.michis.reader.theme

import com.michis.reader.settings.ReaderSettingsRepository

import android.app.Activity
import android.graphics.Color
import androidx.core.graphics.ColorUtils

data class AppPalette(
    val background: Int,
    val surface: Int,
    val card: Int,
    val accent: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val onAccent: Int,
    val outline: Int
)

/** Calcula las paletas compartidas por Compose y los controles del sistema. */
object AppThemePalette {
    fun current(activity: Activity): AppPalette {
        val settings = ReaderSettingsRepository.get(activity)
        val preferences = settings.preferences
        val theme = settings.theme
        return when (val mode = settings.menuColorMode) {
            "light" -> named("Día")
            "dark" -> named("Noche")
            "custom" -> custom(runCatching {
                Color.parseColor(preferences.getString(
                    ReaderSettingsRepository.KEY_MENU_CUSTOM_COLOR,
                    ReaderSettingsRepository.DEFAULT_MENU_CUSTOM_COLOR
                ))
            }.getOrDefault(0xFFFFF4E0.toInt()))
            else -> named(if (mode.startsWith("theme:")) mode.removePrefix("theme:") else theme)
        }
    }

    fun forReader(activity: Activity, readingTheme: String): AppPalette {
        val mode = ReaderSettingsRepository.get(activity).menuColorMode
        return if (mode == "theme" || mode.startsWith("theme:")) named(readingTheme) else current(activity)
    }

    fun named(name: String): AppPalette = when (name) {
        "Noche" -> palette(0xFF111318, 0xFF1A1D24, 0xFF242832, 0xFF8FA9FF, 0xFFE8EAF0, 0xFFB8BDC9)
        "Sepia" -> palette(0xFFF4ECD8, 0xFFE7D9BC, 0xFFFFF8E8, 0xFF79552D, 0xFF3E3124, 0xFF6C5B49)
        "Crepúsculo" -> palette(0xFF2F2638, 0xFF3B3046, 0xFF493A54, 0xFFE3A88F, 0xFFF1E2DC, 0xFFCAB7C6)
        "Consola" -> palette(0xFF071A0D, 0xFF0C2514, 0xFF12331C, 0xFF78F58B, 0xFFD6F7DC, 0xFF9FD7A9)
        "Papel" -> palette(0xFFFFFCF2, 0xFFF4F0E5, 0xFFFFFFFF, 0xFF665C49, 0xFF302D28, 0xFF68635B)
        "Arena" -> palette(0xFFEAD9B8, 0xFFDDC69F, 0xFFF4E5C8, 0xFF76552E, 0xFF3E3328, 0xFF685A49)
        "Lavanda" -> palette(0xFFEDE7F6, 0xFFDED3EC, 0xFFF7F2FC, 0xFF675080, 0xFF332B45, 0xFF655D70)
        "Bosque" -> palette(0xFF183229, 0xFF214239, 0xFF2B5045, 0xFF9BC7A0, 0xFFE1EEE4, 0xFFB2C9B8)
        "Océano" -> palette(0xFF102C3A, 0xFF173B4B, 0xFF205064, 0xFF8ECBE0, 0xFFE0F0F5, 0xFFA9C8D3)
        "Grafito" -> palette(0xFF292B2F, 0xFF35383E, 0xFF42464D, 0xFFB9C1CC, 0xFFF0F1F2, 0xFFBEC1C5)
        "Medianoche" -> palette(0xFF0B1020, 0xFF121A30, 0xFF1C2742, 0xFF89A7FF, 0xFFE4EAFF, 0xFFADB9DA)
        "Rosa suave" -> palette(0xFFFFEEF2, 0xFFF8DDE5, 0xFFFFF7F9, 0xFF8B5263, 0xFF4A3038, 0xFF765B64)
        "Menta" -> palette(0xFFE7F5EE, 0xFFD5EADF, 0xFFF3FBF7, 0xFF3E735C, 0xFF203B30, 0xFF526C61)
        else -> palette(0xFFF7F7F9, 0xFFFFFFFF, 0xFFEEEFF3, 0xFF53699F, 0xFF191B21, 0xFF5D616B)
    }

    private fun palette(
        background: Long,
        surface: Long,
        card: Long,
        accent: Long,
        text: Long,
        secondary: Long
    ): AppPalette {
        val backgroundColor = background.toInt()
        val surfaceColor = surface.toInt()
        val accentColor = accent.toInt()
        val primaryText = readable(text.toInt(), surfaceColor)
        val secondaryText = readable(secondary.toInt(), backgroundColor)
        return AppPalette(
            backgroundColor,
            surfaceColor,
            card.toInt(),
            accentColor,
            primaryText,
            secondaryText,
            contrast(accentColor),
            ColorUtils.setAlphaComponent(primaryText, 70)
        )
    }

    private fun custom(color: Int): AppPalette {
        val base = Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
        val dark = ColorUtils.calculateLuminance(base) < .42
        val direction = if (dark) Color.WHITE else Color.BLACK
        val background = ColorUtils.blendARGB(base, direction, .08f)
        val surface = base
        val card = ColorUtils.blendARGB(base, direction, .14f)
        val accent = ColorUtils.blendARGB(base, if (dark) Color.WHITE else Color.BLACK, .22f)
        val surfaceText = contrast(surface)
        val secondaryText = readable(ColorUtils.blendARGB(surfaceText, background, .16f), background)
        return AppPalette(
            background,
            surface,
            card,
            accent,
            surfaceText,
            secondaryText,
            contrast(accent),
            ColorUtils.setAlphaComponent(contrast(card), 65)
        )
    }

    private fun contrast(color: Int): Int {
        val dark = 0xFF151619.toInt()
        return if (ColorUtils.calculateContrast(Color.WHITE, color) >= ColorUtils.calculateContrast(dark, color)) {
            Color.WHITE
        } else {
            dark
        }
    }

    private fun readable(preferred: Int, background: Int): Int =
        if (ColorUtils.calculateContrast(preferred, background) >= 4.5) preferred else contrast(background)
}
