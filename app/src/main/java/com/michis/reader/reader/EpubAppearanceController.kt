@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import com.michis.reader.settings.PageMarginMode
import com.michis.reader.settings.ReaderSettingsRepository
import com.michis.reader.theme.ReadingThemePalette

/** Fuente única para preferencias Readium, temas, tipografía y ajustes CSS de la página. */
internal class EpubAppearanceController(
    private val activity: FragmentActivity,
    private val settings: ReaderSettingsRepository,
    private val scope: CoroutineScope,
    private val readerRoot: View,
    private val navigator: () -> EpubNavigatorFragment?,
    private val applyMenuColors: (Pair<Int, Int>) -> Unit
) {
    private var currentPreferences = EpubPreferences(publisherStyles = false)

    fun initialPreferences(): EpubPreferences {
        val themeIndex = ReadingThemePalette.names.indexOf(settings.theme).coerceAtLeast(0)
        val colors = ReadingThemePalette.colors(themeIndex)
        val twoPages = settings.twoPagesLandscape(
            activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        )
        val fontFamily = FONT_FAMILIES[settings.fontFamilyIndex.coerceIn(FONT_FAMILIES.indices)]
        activity.requestedOrientation = when (settings.readerOrientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        applyMenuColors(colors)
        currentPreferences = EpubPreferences(
            fontSize = settings.fontSizeDp / 16.0,
            publisherStyles = false,
            pageMargins = settings.pageMarginMode.horizontalFactor,
            scroll = settings.continuousScroll,
            lineHeight = settings.lineHeight.toDouble(),
            fontWeight = settings.fontWeight.toDouble(),
            fontFamily = fontFamily,
            textAlign = TEXT_ALIGNMENTS[settings.textAlignment],
            columnCount = if (twoPages) ColumnCount.TWO else ColumnCount.ONE,
            spread = if (twoPages) Spread.ALWAYS else Spread.NEVER,
            theme = readiumTheme(themeIndex),
            backgroundColor = ReadiumColor(colors.first),
            textColor = ReadiumColor(colors.second)
        )
        return currentPreferences
    }

    fun applyInitialTheme() {
        val colors = ReadingThemePalette.colors(settings.theme)
        readerRoot.setBackgroundColor(colors.first)
        applyMenuColors(colors)
    }

    fun submit(changes: EpubPreferences) {
        currentPreferences += changes
        val targetNavigator = navigator() ?: return
        targetNavigator.submitPreferences(currentPreferences)
        readerRoot.postDelayed(::applyDocumentLayout, 80)
        readerRoot.postDelayed(::applyDocumentLayout, 240)
    }

    fun applyDocumentLayout() {
        val targetNavigator = navigator()
        if (targetNavigator == null) {
            readerRoot.postDelayed(::applyDocumentLayout, 120)
            return
        }
        val marginMode = settings.pageMarginMode
        val top = settings.customPageMarginTopDp
        val right = settings.customPageMarginRightDp
        val bottom = settings.customPageMarginBottomDp
        val left = settings.customPageMarginLeftDp
        scope.launch {
            targetNavigator.evaluateJavascript(documentLayoutScript(marginMode, top, right, bottom, left))
        }
    }

    fun showFontSelection() {
        AlertDialog.Builder(activity).setTitle("Tipo de fuente").setItems(FONT_NAMES) { _, index ->
            settings.fontFamilyIndex = index
            submit(EpubPreferences(fontFamily = FONT_FAMILIES[index]))
        }.show()
    }

    fun applyQuickMode(index: Int) {
        val themeName = settings.quickMode(index)
        applyReadingTheme(ReadingThemePalette.names.indexOf(themeName).coerceAtLeast(0))
        navigator()?.clearSelection()
        Toast.makeText(activity, themeName, Toast.LENGTH_SHORT).show()
    }

    fun applyReadingTheme(index: Int) {
        val safeIndex = index.coerceIn(ReadingThemePalette.names.indices)
        val colors = ReadingThemePalette.colors(safeIndex)
        submit(EpubPreferences(
            theme = readiumTheme(safeIndex),
            backgroundColor = ReadiumColor(colors.first),
            textColor = ReadiumColor(colors.second)
        ))
        settings.theme = ReadingThemePalette.names[safeIndex]
        readerRoot.setBackgroundColor(colors.first)
        applyMenuColors(colors)
    }

    private fun readiumTheme(index: Int) = when (index) {
        1 -> Theme.DARK
        2 -> Theme.SEPIA
        else -> Theme.LIGHT
    }

    private fun documentLayoutScript(
        marginMode: PageMarginMode,
        top: Float,
        right: Float,
        bottom: Float,
        left: Float
    ): String =
        EpubContentStyles.installationScript + "\n" + """
        (() => {
          const elements = [document.documentElement, document.body].filter(Boolean);
          elements.forEach(element => {
            element.style.setProperty('justify-content', 'flex-start', 'important');
            element.style.setProperty('align-content', 'start', 'important');
          });
          const root = document.documentElement;
          const body = document.body;
          if (root && body) {
            const readiumPageGutter = getComputedStyle(root).getPropertyValue('--RS__pageGutter').trim();
            if (readiumPageGutter) {
              root.style.setProperty('--RS__maxLineLength', 'var(--RS__viewportWidth, 100vw)', 'important');
              body.style.setProperty('width', '100%', 'important');
              body.style.setProperty('max-width', '100%', 'important');
              body.style.setProperty('box-sizing', 'border-box', 'important');
              if ('${marginMode.preferenceValue}' === 'custom') {
                body.style.setProperty('padding', '${top}px ${right}px ${bottom}px ${left}px', 'important');
              } else {
                body.style.removeProperty('padding');
              }
            }
          }
        })();
        """.trimIndent()

    private companion object {
        val FONT_NAMES = arrayOf(
            "Sans Serif", "Serif", "Cursiva", "Monoespaciada", "OpenDyslexic", "Accessible DfA", "iA Writer Duospace"
        )
        val FONT_FAMILIES = arrayOf(
            FontFamily.SANS_SERIF, FontFamily.SERIF, FontFamily.CURSIVE, FontFamily.MONOSPACE,
            FontFamily.OPEN_DYSLEXIC, FontFamily.ACCESSIBLE_DFA, FontFamily.IA_WRITER_DUOSPACE
        )
        val TEXT_ALIGNMENTS = arrayOf(TextAlign.JUSTIFY, TextAlign.START, TextAlign.CENTER, TextAlign.END)
    }
}
