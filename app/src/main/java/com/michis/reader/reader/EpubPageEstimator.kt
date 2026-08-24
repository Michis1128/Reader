package com.michis.reader.reader

import com.michis.reader.data.*
import com.michis.reader.settings.ReaderSettingsRepository

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

object EpubPageEstimator {
    private val executor = Executors.newFixedThreadPool(2)
    private val cachedPages = mutableMapOf<String, Int>()

    fun estimate(context: Context, document: LibraryDocument, result: (Int) -> Unit) {
        val settings = ReaderSettingsRepository.get(context)
        val presentation = Presentation.from(context, settings)
        val cacheKey = "${document.uri}|${presentation.cacheKey}"
        synchronized(cachedPages) { cachedPages[cacheKey] }?.let { result(it); return }
        executor.execute {
            val pages = runCatching {
                reflowablePages(epubCharacterCount(context, Uri.parse(document.uri)), presentation)
            }.getOrDefault(1).coerceAtLeast(1)
            synchronized(cachedPages) { cachedPages[cacheKey] = pages }
            android.os.Handler(android.os.Looper.getMainLooper()).post { result(pages) }
        }
    }

    private fun epubCharacterCount(context: Context, uri: Uri): Long {
        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { stream -> ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val extension = entry.name.substringAfterLast('.', "").lowercase()
                if (!entry.isDirectory && extension in setOf("xhtml", "html", "htm", "xml")) {
                    val markup = zip.bufferedReader().readText()
                    total += markup.replace(Regex("<[^>]+>"), " ").length
                }
            }
        } }
        return total
    }

    private fun reflowablePages(characters: Long, presentation: Presentation): Int {
        val fontFactor = (19f / presentation.fontSize.coerceAtLeast(8f)).let { it * it }
        val lineFactor = 1.35f / presentation.lineHeight.coerceAtLeast(0.5f)
        val areaFactor = (presentation.pageWidthDp * presentation.pageHeightDp) / (400f * 760f)
        val charactersPerPage = (1800f * fontFactor * lineFactor * areaFactor).coerceAtLeast(180f)
        return kotlin.math.ceil(characters / charactersPerPage).toInt()
    }

    private data class Presentation(
        val fontSize: Float,
        val lineHeight: Float,
        val pageWidthDp: Float,
        val pageHeightDp: Float,
        val cacheKey: String
    ) {
        companion object {
            fun from(context: Context, settings: ReaderSettingsRepository): Presentation {
                val metrics = context.resources.displayMetrics
                val widthDp = metrics.widthPixels / metrics.density
                val heightDp = metrics.heightPixels / metrics.density
                val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val columns = if (landscape && settings.twoPagesLandscape(true)) 2 else 1
                val horizontalMargin = when (settings.pageMarginMode) {
                    com.michis.reader.settings.PageMarginMode.LARGE -> 48f
                    com.michis.reader.settings.PageMarginMode.NORMAL -> 32f
                    com.michis.reader.settings.PageMarginMode.REDUCED -> 16f
                    com.michis.reader.settings.PageMarginMode.CUSTOM ->
                        settings.customPageMarginLeftDp + settings.customPageMarginRightDp
                }
                val verticalMargin = if (settings.pageMarginMode == com.michis.reader.settings.PageMarginMode.CUSTOM) {
                    settings.customPageMarginTopDp + settings.customPageMarginBottomDp
                } else 24f
                val pageWidth = ((widthDp - horizontalMargin) / columns).coerceAtLeast(120f)
                val pageHeight = (heightDp - verticalMargin).coerceAtLeast(200f)
                val key = listOf(
                    settings.fontSizeDp, settings.lineHeight, settings.pageMarginMode.preferenceValue,
                    settings.customPageMarginTopDp, settings.customPageMarginRightDp,
                    settings.customPageMarginBottomDp, settings.customPageMarginLeftDp,
                    widthDp.toInt(), heightDp.toInt(), columns
                ).joinToString(":")
                return Presentation(settings.fontSizeDp, settings.lineHeight, pageWidth, pageHeight, key)
            }
        }
    }
}
