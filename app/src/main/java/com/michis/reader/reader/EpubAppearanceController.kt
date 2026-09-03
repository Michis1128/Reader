@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
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
    private val publicationView: View,
    private val navigator: () -> EpubNavigatorFragment?,
    private val applyMenuColors: (Pair<Int, Int>) -> Unit,
    private val presentationChanged: () -> Unit
) {
    private var currentPreferences = EpubPreferences(publisherStyles = false)
    private var presentationUpdateJob: Job? = null

    fun initialPreferences(): EpubPreferences {
        val themeIndex = ReadingThemePalette.names.indexOf(settings.theme).coerceAtLeast(0)
        val colors = ReadingThemePalette.colors(themeIndex)
        val twoPages = settings.twoPagesLandscape(
            activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        )
        val fontFamily = EpubFontCatalog.family(settings.fontFamilyIndex)
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
        presentationUpdateJob?.cancel()
        publicationView.alpha = 0f
        targetNavigator.submitPreferences(currentPreferences)
        presentationUpdateJob = scope.launch {
            // Readium updates every loaded resource from this single preference state. Waiting for
            // the next render cycles prevents exposing the previous pagination while its WebViews
            // receive the new CSS variables.
            awaitRenderCycle()
            awaitRenderCycle()
            delay(PRESENTATION_SETTLE_DELAY_MILLIS)
            applyDocumentLayoutNow(targetNavigator)
            awaitRenderCycle()
            presentationChanged()
            publicationView.alpha = 1f
        }
    }

    fun prepareLoadedPage(applied: () -> Unit = {}) {
        presentationUpdateJob?.cancel()
        publicationView.alpha = 0f
        presentationUpdateJob = scope.launch {
            val targetNavigator = awaitNavigator()
            applyDocumentLayoutNow(targetNavigator)
            awaitRenderCycle()
            presentationChanged()
            publicationView.alpha = 1f
            applied()
        }
    }

    private suspend fun awaitNavigator(): EpubNavigatorFragment {
        val targetNavigator = navigator()
        if (targetNavigator != null) return targetNavigator
        while (true) {
            delay(NAVIGATOR_RETRY_DELAY_MILLIS)
            navigator()?.let { return it }
        }
    }

    private suspend fun applyDocumentLayoutNow(targetNavigator: EpubNavigatorFragment) {
        val marginMode = settings.pageMarginMode
        val top = settings.customPageMarginTopDp
        val right = settings.customPageMarginRightDp
        val bottom = settings.customPageMarginBottomDp
        val left = settings.customPageMarginLeftDp
        targetNavigator.evaluateJavascript(documentLayoutScript(marginMode, top, right, bottom, left))
    }

    private suspend fun awaitRenderCycle() {
        suspendCancellableCoroutine { continuation ->
            publicationView.postOnAnimation {
                if (continuation.isActive) continuation.resume(Unit) { _, _, _ -> }
            }
        }
    }

    fun showFontSelection() {
        AlertDialog.Builder(activity).setTitle("Tipo de fuente").setItems(EpubFontCatalog.names) { _, index ->
            settings.fontFamilyIndex = index
            submit(EpubPreferences(fontFamily = EpubFontCatalog.family(index)))
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
    ): String = EpubContentStyles.installationScript(marginMode, top, right, bottom, left)

    private companion object {
        const val NAVIGATOR_RETRY_DELAY_MILLIS = 16L
        const val PRESENTATION_SETTLE_DELAY_MILLIS = 48L
        val TEXT_ALIGNMENTS = arrayOf(TextAlign.JUSTIFY, TextAlign.START, TextAlign.CENTER, TextAlign.END)
    }
}
