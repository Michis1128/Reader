package com.michis.reader.theme

import com.michis.reader.settings.ReaderSettingsRepository

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import java.util.WeakHashMap

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

object AppThemePalette {
    private enum class SurfaceRole { BACKGROUND, SURFACE, CARD, INHERITED }
    private val managedSurfaces = WeakHashMap<View, SurfaceRole>()

    fun markBackground(view: View) {
        managedSurfaces[view] = SurfaceRole.BACKGROUND
    }

    fun markSurface(view: View) {
        managedSurfaces[view] = SurfaceRole.SURFACE
    }

    fun markCard(view: View) {
        managedSurfaces[view] = SurfaceRole.CARD
    }

    fun cardBackground(activity: Activity, radiusDp: Float = 18f): GradientDrawable {
        val colors = current(activity)
        return rounded(colors.card, activity.resources.displayMetrics.density, radiusDp)
    }

    fun current(activity: Activity): AppPalette {
        val settings = ReaderSettingsRepository.get(activity)
        val preferences = settings.preferences
        val theme = settings.theme
        return when (val mode = settings.menuColorMode) {
            "light" -> named("Día")
            "dark" -> named("Noche")
            "custom" -> custom(runCatching {
                Color.parseColor(preferences.getString(ReaderSettingsRepository.KEY_MENU_CUSTOM_COLOR, ReaderSettingsRepository.DEFAULT_MENU_CUSTOM_COLOR))
            }.getOrDefault(0xFFFFF4E0.toInt()))
            else -> named(if (mode.startsWith("theme:")) mode.removePrefix("theme:") else theme)
        }
    }

    fun textFor(background: Int): Int = contrast(background)

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

    private fun palette(background: Long, surface: Long, card: Long, accent: Long, text: Long, secondary: Long): AppPalette {
        val backgroundColor = background.toInt()
        val surfaceColor = surface.toInt()
        val accentColor = accent.toInt()
        val primaryText = readable(text.toInt(), surfaceColor)
        val secondaryText = readable(secondary.toInt(), backgroundColor)
        return AppPalette(backgroundColor, surfaceColor, card.toInt(), accentColor, primaryText, secondaryText, contrast(accentColor), ColorUtils.setAlphaComponent(primaryText, 70))
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
        return AppPalette(background, surface, card, accent, surfaceText, secondaryText, contrast(accent), ColorUtils.setAlphaComponent(contrast(card), 65))
    }

    @Suppress("DEPRECATION")
    fun apply(activity: Activity) {
        val content = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val colors = current(activity)
        content.setBackgroundColor(colors.background)
        repeat(content.childCount) { style(content.getChildAt(it), colors, colors.background, colors.primaryText, 0) }
        activity.window.statusBarColor = colors.surface
        activity.window.navigationBarColor = colors.background
    }

    private fun style(view: View, colors: AppPalette, inheritedBackground: Int, inheritedText: Int, depth: Int) {
        var childBackground = inheritedBackground
        var childText = inheritedText
        if (view is ViewGroup) {
            val drawableBackground = view.background
            val solid = (drawableBackground as? ColorDrawable)?.color
            val hasPanelShape = drawableBackground != null && solid == null
            val previousRole = managedSurfaces[view]
            val role = previousRole ?: when {
                depth == 0 -> SurfaceRole.BACKGROUND
                hasPanelShape -> SurfaceRole.CARD
                view.elevation >= 4f * view.resources.displayMetrics.density -> SurfaceRole.SURFACE
                view.elevation > 0f -> SurfaceRole.CARD
                solid != null && isNeutral(solid) -> SurfaceRole.INHERITED
                else -> null
            }
            val replacement = when (role) {
                SurfaceRole.BACKGROUND -> colors.background
                SurfaceRole.SURFACE -> colors.surface
                SurfaceRole.CARD -> colors.card
                SurfaceRole.INHERITED -> inheritedBackground
                null -> null
            }
            if (replacement != null) {
                managedSurfaces[view] = role
                childBackground = replacement
                childText = contrast(replacement)
                val savedLeft = view.paddingLeft
                val savedTop = view.paddingTop
                val savedRight = view.paddingRight
                val savedBottom = view.paddingBottom
                view.background = if (role == SurfaceRole.CARD) rounded(replacement, view.resources.displayMetrics.density) else ColorDrawable(replacement)
                view.setPadding(savedLeft, savedTop, savedRight, savedBottom)
            }
        } else if (view is CompoundButton && view.background != null && view.background !is ColorDrawable) {
            // Las filas seleccionables, como los libros de "Reiniciar libros", son
            // CompoundButton con una tarjeta propia y no heredan visualmente el fondo padre.
            childBackground = colors.card
            childText = contrast(colors.card)
            view.setRoundedBackground(colors.card, radiusDp = 18f)
        }
        when (view) {
            is Switch -> {
                view.setTextColor(childText)
                view.backgroundTintList = null
                view.thumbTintList = switchThumbColors(colors, childBackground)
                view.trackTintList = switchTrackColors(colors, childBackground)
            }
            is CompoundButton -> {
                view.setTextColor(childText)
                view.backgroundTintList = null
                view.buttonTintList = selectionColors(colors, childBackground)
            }
            is Button -> {
                view.backgroundTintList = null
                view.setRoundedBackground(colors.accent, radiusDp = 14f)
                view.setTextColor(colors.onAccent)
                view.minimumHeight = (44 * view.resources.displayMetrics.density).toInt()
                view.ensureOuterMargins(horizontalDp = 4, verticalDp = 4)
            }
            is EditText -> {
                view.setTextColor(childText); view.setHintTextColor(ColorUtils.setAlphaComponent(childText, 145))
                view.backgroundTintList = ColorStateList.valueOf(colors.accent)
                view.ensureOuterMargins(horizontalDp = 4, verticalDp = 5)
            }
            is Spinner -> {
                val horizontal = (12 * view.resources.displayMetrics.density).toInt()
                val vertical = (8 * view.resources.displayMetrics.density).toInt()
                val outsideHorizontal = (10 * view.resources.displayMetrics.density).toInt()
                val outsideVertical = (5 * view.resources.displayMetrics.density).toInt()
                view.setPadding(horizontal, vertical, horizontal, vertical)
                view.minimumHeight = (48 * view.resources.displayMetrics.density).toInt()
                view.backgroundTintList = null
                view.setRoundedBackground(colors.card, radiusDp = 12f)
                view.isLongClickable = true
                if (view !is com.michis.reader.ui.LimitedHeightSpinner) view.setOnLongClickListener { true }
                (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { parameters ->
                    parameters.marginStart = maxOf(parameters.marginStart, outsideHorizontal)
                    parameters.marginEnd = maxOf(parameters.marginEnd, outsideHorizontal)
                    parameters.topMargin = maxOf(parameters.topMargin, outsideVertical)
                    parameters.bottomMargin = maxOf(parameters.bottomMargin, outsideVertical)
                    view.layoutParams = parameters
                }
            }
            is TextView -> view.setTextColor(childText)
        }
        if (view is ViewGroup) repeat(view.childCount) { style(view.getChildAt(it), colors, childBackground, childText, depth + 1) }
    }

    private fun rounded(color: Int, density: Float, radiusDp: Float = 18f) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
        setStroke(maxOf(1, density.toInt()), ColorUtils.setAlphaComponent(contrast(color), 42))
    }

    private fun View.setRoundedBackground(color: Int, radiusDp: Float) {
        val left = paddingLeft
        val top = paddingTop
        val right = paddingRight
        val bottom = paddingBottom
        background = rounded(color, resources.displayMetrics.density, radiusDp)
        setPadding(left, top, right, bottom)
    }

    private fun View.ensureOuterMargins(horizontalDp: Int, verticalDp: Int) {
        val parameters = layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val horizontal = (horizontalDp * resources.displayMetrics.density).toInt()
        val vertical = (verticalDp * resources.displayMetrics.density).toInt()
        parameters.marginStart = maxOf(parameters.marginStart, horizontal)
        parameters.marginEnd = maxOf(parameters.marginEnd, horizontal)
        parameters.topMargin = maxOf(parameters.topMargin, vertical)
        parameters.bottomMargin = maxOf(parameters.bottomMargin, vertical)
        layoutParams = parameters
    }

    private fun selectionColors(colors: AppPalette, background: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(colors.accent, ColorUtils.blendARGB(contrast(background), background, .42f))
    )

    private fun switchThumbColors(colors: AppPalette, background: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(colors.onAccent, ColorUtils.blendARGB(contrast(background), background, .28f))
    )

    private fun switchTrackColors(colors: AppPalette, background: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(colors.accent, ColorUtils.blendARGB(contrast(background), background, .65f))
    )
    private fun isNeutral(color: Int): Boolean = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }[1] < .15f
    private fun contrast(color: Int): Int {
        val dark = 0xFF151619.toInt()
        return if (ColorUtils.calculateContrast(Color.WHITE, color) >= ColorUtils.calculateContrast(dark, color)) Color.WHITE else dark
    }
    private fun readable(preferred: Int, background: Int): Int =
        if (ColorUtils.calculateContrast(preferred, background) >= 4.5) preferred else contrast(background)
}
