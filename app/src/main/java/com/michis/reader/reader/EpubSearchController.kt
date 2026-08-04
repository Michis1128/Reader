@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import com.michis.reader.databinding.ViewEpubSearchPanelBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search

/** Administra consulta, resultados, decoraciones y saltos de la búsqueda dentro del EPUB. */
internal class EpubSearchController(
    private val binding: ViewEpubSearchPanelBinding,
    private val scope: CoroutineScope,
    private val decorations: EpubDecorationController,
    private val navigationHistory: ReaderNavigationHistory,
    private val currentPageIndex: () -> Int,
    private val animationsEnabled: () -> Boolean,
    private val navigate: (Locator, Boolean) -> Unit,
    private val scheduleDelayed: (() -> Unit, Long) -> Unit
) {
    private var publication: Publication? = null
    private var results = emptyList<Locator>()
    private var selectedResultIndex = -1
    private var requestToken = 0

    fun attachPublication(publication: Publication) {
        this.publication = publication
    }

    fun performSearch() {
        val query = binding.searchInput.text.toString().trim()
        val opened = publication
        if (query.isBlank() || opened == null) {
            binding.searchResultLabel.text = "Escribe un texto para buscar"
            return
        }
        val initialPage = currentPageIndex()
        val currentRequest = ++requestToken
        binding.searchResultLabel.text = "Buscando…"
        binding.previousResultButton.isEnabled = false
        binding.nextResultButton.isEnabled = false
        scope.launch {
            val matches = mutableListOf<Locator>()
            val iterator = opened.search(
                query,
                SearchService.Options(caseSensitive = false, wholeWord = false, exact = true)
            )
            if (iterator != null) {
                try {
                    iterator.forEach { result -> matches += result.locators }
                } finally {
                    iterator.close()
                }
            }
            if (currentRequest != requestToken) return@launch
            results = matches
            selectedResultIndex = if (matches.isEmpty()) -1 else 0
            decorations.showSearchResults(matches, selectedResultIndex)
            updateControls()
            if (matches.isNotEmpty()) navigateToResult(0, initialPage)
        }
    }

    fun move(direction: Int) {
        if (results.isEmpty()) return
        val target = (selectedResultIndex + direction).coerceIn(results.indices)
        if (target == selectedResultIndex) return
        selectedResultIndex = target
        scope.launch { decorations.showSearchResults(results, target) }
        updateControls()
        navigateToResult(target)
    }

    private fun navigateToResult(index: Int, originPage: Int = currentPageIndex()) {
        val locator = results.getOrNull(index) ?: return
        val jumpToken = navigationHistory.beginPending(PendingJumpSource.SEARCH, originPage)
        navigate(locator, animationsEnabled())
        scheduleDelayed(
            { navigationHistory.cancelPending(PendingJumpSource.SEARCH, jumpToken) },
            PENDING_JUMP_TIMEOUT_MILLISECONDS
        )
    }

    private fun updateControls() {
        val count = results.size
        binding.searchResultLabel.text = if (count == 0) "Sin coincidencias" else
            "Coincidencia ${selectedResultIndex + 1} de $count"
        binding.previousResultButton.isEnabled = selectedResultIndex > 0
        binding.nextResultButton.isEnabled = selectedResultIndex in 0 until count - 1
    }

    private companion object {
        const val PENDING_JUMP_TIMEOUT_MILLISECONDS = 2_000L
    }
}
