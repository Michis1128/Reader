package com.michis.reader.reader

import com.michis.reader.data.*
import com.michis.reader.settings.ReaderSettingsRepository

import android.content.Context
import android.net.Uri
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

object EpubPageEstimator {
    private val executor = Executors.newFixedThreadPool(2)
    private val cachedPages = mutableMapOf<String, Int>()

    fun estimate(context: Context, document: LibraryDocument, result: (Int) -> Unit) {
        synchronized(cachedPages) { cachedPages[document.uri] }?.let { result(it); return }
        val fontSize = ReaderSettingsRepository.get(context).fontSizeDp
        executor.execute {
            val pages = runCatching {
                reflowablePages(epubCharacterCount(context, Uri.parse(document.uri)), fontSize)
            }.getOrDefault(1).coerceAtLeast(1)
            synchronized(cachedPages) { cachedPages[document.uri] = pages }
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

    private fun reflowablePages(characters: Long, fontSize: Float): Int {
        val charactersPerPage = (1800f * 19f / fontSize.coerceAtLeast(8f)).coerceAtLeast(300f)
        return kotlin.math.ceil(characters / charactersPerPage).toInt()
    }
}
