package com.michis.reader.theme

import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppThemePaletteTest {
    @Test
    fun everyReadingThemeKeepsImportantTextReadable() {
        ReadingThemePalette.names.forEach { themeName ->
            val palette = AppThemePalette.named(themeName)

            assertReadable(themeName, "texto principal", palette.primaryText, palette.surface)
            assertReadable(themeName, "texto secundario", palette.secondaryText, palette.background)
            assertReadable(themeName, "botones", palette.onAccent, palette.accent)
        }
    }

    private fun assertReadable(theme: String, element: String, foreground: Int, background: Int) {
        val contrast = ColorUtils.calculateContrast(foreground, background)
        assertTrue("$element de $theme tiene contraste $contrast", contrast >= 4.5)
    }
}
