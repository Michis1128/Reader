package com.michis.reader.theme.compose

import com.michis.reader.theme.AppPalette
import com.michis.reader.theme.AppThemePalette

import android.graphics.Color as AndroidColor
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

/** Expone la paleta configurable de la app como un tema Material 3. */
@Composable
fun MichisReaderComposeTheme(content: @Composable () -> Unit) {
    val activity = LocalActivity.current
    val palette = activity?.let(AppThemePalette::current) ?: fallbackPalette(isSystemInDarkTheme())
    val dark = AndroidColor.luminance(palette.background) < 0.45f
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = Color(palette.accent),
            onPrimary = Color(palette.onAccent),
            background = Color(palette.background),
            onBackground = Color(palette.primaryText),
            surface = Color(palette.surface),
            onSurface = Color(palette.primaryText),
            surfaceVariant = Color(palette.card),
            onSurfaceVariant = Color(palette.secondaryText),
            outline = Color(palette.outline),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005)
        )
    } else {
        lightColorScheme(
            primary = Color(palette.accent),
            onPrimary = Color(palette.onAccent),
            background = Color(palette.background),
            onBackground = Color(palette.primaryText),
            surface = Color(palette.surface),
            onSurface = Color(palette.primaryText),
            surfaceVariant = Color(palette.card),
            onSurfaceVariant = Color(palette.secondaryText),
            outline = Color(palette.outline),
            error = Color(0xFFBA1A1A),
            onError = Color.White
        )
    }
    if (activity != null) SideEffect {
        activity.window.statusBarColor = colorScheme.surface.toArgb()
        activity.window.navigationBarColor = colorScheme.background.toArgb()
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

private fun fallbackPalette(dark: Boolean) = if (dark) {
    AppPalette(
        background = 0xFF111318.toInt(), surface = 0xFF1A1D24.toInt(), card = 0xFF242832.toInt(),
        accent = 0xFF8FA9FF.toInt(), primaryText = 0xFFE8EAF0.toInt(), secondaryText = 0xFFB8BDC9.toInt(),
        onAccent = 0xFF101B3D.toInt(), outline = 0x66E8EAF0.toInt()
    )
} else {
    AppPalette(
        background = 0xFFF7F7F9.toInt(), surface = 0xFFFFFFFF.toInt(), card = 0xFFEEEFF3.toInt(),
        accent = 0xFF53699F.toInt(), primaryText = 0xFF191B21.toInt(), secondaryText = 0xFF5D616B.toInt(),
        onAccent = 0xFFFFFFFF.toInt(), outline = 0x46191B21
    )
}
