@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import com.michis.reader.data.*
import com.michis.reader.settings.ReaderSettingsRepository

import android.graphics.Color
import android.os.SystemClock
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search

/** Mantiene resaltados de diccionario y citas separados del ciclo de navegacion EPUB. */
class EpubDecorationController(
    private val database: ReaderDatabase,
    private val documentIdentifier: Long,
    private val settings: ReaderSettingsRepository,
    private val openDictionaryEntry: (Long) -> Unit,
    private val editQuote: (Long) -> Unit
) {
    var lastDecorationActivationAt: Long = 0L
        private set

    private var publication: Publication? = null
    private var navigator: EpubNavigatorFragment? = null

    private val dictionaryListener = object : DecorableNavigator.Listener {
        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
            lastDecorationActivationAt = SystemClock.uptimeMillis()
            val identifier = event.decoration.extras[ENTRY_IDENTIFIER_EXTRA]?.toString()?.toLongOrNull() ?: return false
            val entry = database.findDictionaryEntry(identifier)
                ?.takeIf { it.documentIdentifier in database.effectiveDictionaryOwnerIdentifiers(documentIdentifier) }
                ?: return false
            openDictionaryEntry(entry.identifier)
            return true
        }
    }

    private val quoteListener = object : DecorableNavigator.Listener {
        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
            lastDecorationActivationAt = SystemClock.uptimeMillis()
            val identifier = event.decoration.extras[QUOTE_IDENTIFIER_EXTRA]?.toString()?.toLongOrNull() ?: return false
            val quoteExists = database.annotations(documentIdentifier)
                .any { it.identifier == identifier && it.kind == "cita" }
            if (!quoteExists) return false
            editQuote(identifier)
            return true
        }
    }

    fun attach(publication: Publication, navigator: EpubNavigatorFragment) {
        this.publication = publication
        this.navigator = navigator
        navigator.addDecorationListener(DICTIONARY_GROUP, dictionaryListener)
        navigator.addDecorationListener(QUOTE_GROUP, quoteListener)
    }

    fun dictionaryButtonLabel(): String = if (
        database.effectiveDictionaryEntries(documentIdentifier).isEmpty() &&
        database.dictionaryCategories(documentIdentifier).isEmpty()
    ) "Crear diccionario" else "Diccionario"

    suspend fun refreshAll() {
        refreshDictionaryHighlights()
        refreshQuoteHighlights()
    }

    private suspend fun refreshDictionaryHighlights() {
        val opened = publication ?: return
        val targetNavigator = navigator ?: return
        val colorText = settings.preferences.getString(
            ReaderSettingsRepository.KEY_DICTIONARY_HIGHLIGHT_COLOR,
            ReaderSettingsRepository.DEFAULT_DICTIONARY_HIGHLIGHT_COLOR
        )
        val tint = runCatching { Color.parseColor(colorText) }.getOrDefault(0x665A7D9A)
        val decorations = mutableListOf<Decoration>()
        database.effectiveDictionaryEntries(documentIdentifier).forEach { entry ->
            val iterator = opened.search(
                entry.term,
                SearchService.Options(caseSensitive = false, wholeWord = true, exact = true)
            ) ?: return@forEach
            iterator.forEach { result ->
                result.locators.forEach { locator ->
                    decorations += Decoration(
                        id = "dictionary-${entry.identifier}-${decorations.size}",
                        locator = locator,
                        style = Decoration.Style.Highlight(tint, true),
                        extras = mapOf(ENTRY_IDENTIFIER_EXTRA to entry.identifier)
                    )
                }
            }
            iterator.close()
        }
        targetNavigator.applyDecorations(decorations, DICTIONARY_GROUP)
    }

    private suspend fun refreshQuoteHighlights() {
        val opened = publication ?: return
        val targetNavigator = navigator ?: return
        val decorations = mutableListOf<Decoration>()
        database.annotations(documentIdentifier)
            .filter { it.kind == "cita" && it.selectedText.isNotBlank() }
            .forEach { quote ->
                val iterator = opened.search(
                    quote.selectedText,
                    SearchService.Options(caseSensitive = true, wholeWord = false, exact = true)
                ) ?: return@forEach
                val locators = mutableListOf<Locator>()
                iterator.forEach { result -> locators += result.locators }
                iterator.close()
                val locator = locators.firstOrNull { it.locations.position == quote.location } ?: locators.firstOrNull()
                if (locator != null) decorations += Decoration(
                    id = "quote-${quote.identifier}",
                    locator = locator,
                    style = Decoration.Style.Highlight(quote.color, false),
                    extras = mapOf(QUOTE_IDENTIFIER_EXTRA to quote.identifier)
                )
            }
        targetNavigator.applyDecorations(decorations, QUOTE_GROUP)
    }

    private companion object {
        const val DICTIONARY_GROUP = "book_dictionary"
        const val QUOTE_GROUP = "book_quotes"
        const val ENTRY_IDENTIFIER_EXTRA = "dictionary_entry_identifier"
        const val QUOTE_IDENTIFIER_EXTRA = "quote_identifier"
    }
}
