@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import com.michis.reader.data.*
import com.michis.reader.settings.ReaderSettingsRepository

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search

/** Mantiene resaltados de diccionario y citas separados del ciclo de navegacion EPUB. */
class EpubDecorationController(
    context: Context,
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
    private val dictionaryLocatorCache = DictionaryLocatorCache(context.applicationContext)
    private val dictionaryRefreshMutex = Mutex()
    private val dictionaryLocatorsByTerm = mutableMapOf<String, List<Locator>>()
    private var dictionaryCacheLoaded = false

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
        if (this.publication !== publication) {
            dictionaryLocatorsByTerm.clear()
            dictionaryCacheLoaded = false
        }
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

    suspend fun showSearchResults(locators: List<Locator>, selectedIndex: Int) {
        val targetNavigator = navigator ?: return
        val decorations = locators.mapIndexed { index, locator ->
            Decoration(
                id = "search-$index",
                locator = locator,
                style = Decoration.Style.Highlight(
                    if (index == selectedIndex) 0xCCFFB300.toInt() else 0x66FFEB3B,
                    false
                )
            )
        }
        targetNavigator.applyDecorations(decorations, SEARCH_GROUP)
    }

    suspend fun clearSearchResults() {
        navigator?.applyDecorations(emptyList(), SEARCH_GROUP)
    }

    private suspend fun refreshDictionaryHighlights() {
        dictionaryRefreshMutex.lock()
        try {
            val opened = publication ?: return
            val targetNavigator = navigator ?: return
            val document = database.findDocument(documentIdentifier) ?: return
            if (!dictionaryCacheLoaded) {
                dictionaryLocatorsByTerm.putAll(dictionaryLocatorCache.load(document))
                dictionaryCacheLoaded = true
            }
            val entries = database.effectiveDictionaryEntries(documentIdentifier)
            val plan = DictionaryDecorationSearchPlan.create(
                entries.map { it.identifier to it.term },
                dictionaryLocatorsByTerm.keys
            )
            val activeKeys = plan.activeTerms.mapTo(mutableSetOf(), DictionarySearchTerm::cacheKey)
            var cacheChanged = dictionaryLocatorsByTerm.keys.retainAll(activeKeys)
            plan.activeTerms.filter { it.cacheKey in plan.missingCacheKeys }.forEach { searchTerm ->
                currentCoroutineContext().ensureActive()
                val locators = mutableListOf<Locator>()
                val iterator = opened.search(
                    searchTerm.term,
                    SearchService.Options(caseSensitive = false, wholeWord = true, exact = true)
                )
                if (iterator != null) {
                    try {
                        iterator.forEach { result -> locators += result.locators }
                    } finally {
                        iterator.close()
                    }
                }
                dictionaryLocatorsByTerm[searchTerm.cacheKey] = locators
                cacheChanged = true
                yield()
            }
            if (cacheChanged) dictionaryLocatorCache.save(document, dictionaryLocatorsByTerm)

            val colorText = settings.preferences.getString(
                ReaderSettingsRepository.KEY_DICTIONARY_HIGHLIGHT_COLOR,
                ReaderSettingsRepository.DEFAULT_DICTIONARY_HIGHLIGHT_COLOR
            )
            val tint = runCatching { Color.parseColor(colorText) }.getOrDefault(0x665A7D9A)
            val decorations = buildList {
                plan.activeTerms.forEach { searchTerm ->
                    dictionaryLocatorsByTerm[searchTerm.cacheKey].orEmpty().forEachIndexed { index, locator ->
                        add(Decoration(
                            id = "dictionary-${searchTerm.entryIdentifier}-$index",
                            locator = locator,
                            style = Decoration.Style.Highlight(tint, true),
                            extras = mapOf(ENTRY_IDENTIFIER_EXTRA to searchTerm.entryIdentifier)
                        ))
                    }
                }
            }
            targetNavigator.applyDecorations(decorations, DICTIONARY_GROUP)
        } finally {
            dictionaryRefreshMutex.unlock()
        }
    }

    private suspend fun refreshQuoteHighlights() {
        val targetNavigator = navigator ?: return
        val decorations = mutableListOf<Decoration>()
        database.annotations(documentIdentifier)
            .filter { it.kind == "cita" && it.selectedText.isNotBlank() }
            .forEach { quote ->
                savedQuoteLocators(quote.locatorJson).forEachIndexed { fragmentIndex, locator ->
                    decorations += quoteDecoration(quote, locator, fragmentIndex)
                }
            }
        targetNavigator.applyDecorations(decorations, QUOTE_GROUP)
    }

    private fun quoteDecoration(quote: SavedAnnotation, locator: Locator, fragmentIndex: Int? = null) = Decoration(
        id = "quote-${quote.identifier}${fragmentIndex?.let { "-$it" }.orEmpty()}",
        locator = locator,
        style = Decoration.Style.Highlight(quote.color, false),
        extras = mapOf(QUOTE_IDENTIFIER_EXTRA to quote.identifier)
    )

    private fun savedQuoteLocators(json: String): List<Locator> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            if (json.trimStart().startsWith("[")) Locator.fromJSONArray(JSONArray(json))
            else listOfNotNull(Locator.fromJSON(JSONObject(json)))
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val DICTIONARY_GROUP = "book_dictionary"
        const val QUOTE_GROUP = "book_quotes"
        const val SEARCH_GROUP = "book_search"
        const val ENTRY_IDENTIFIER_EXTRA = "dictionary_entry_identifier"
        const val QUOTE_IDENTIFIER_EXTRA = "quote_identifier"
    }
}
