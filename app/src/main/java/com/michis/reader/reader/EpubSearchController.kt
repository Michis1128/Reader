@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderButton
import com.michis.reader.ui.compose.MichisReaderButtonRow
import com.michis.reader.ui.compose.MichisReaderCardShape
import com.michis.reader.ui.compose.MichisReaderInputShape

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search

/** Administra consulta, resultados, decoraciones y saltos de la búsqueda dentro del EPUB. */
internal class EpubSearchController(
    private val activity: FragmentActivity,
    private val scope: CoroutineScope,
    private val decorations: EpubDecorationController,
    private val navigationHistory: ReaderNavigationHistory,
    private val currentPageIndex: () -> Int,
    private val animationsEnabled: () -> Boolean,
    private val navigate: (Locator, Boolean) -> Unit,
    private val scheduleDelayed: (() -> Unit, Long) -> Unit,
    private val closePanel: () -> Unit
) {
    private var publication: Publication? = null
    private var results = emptyList<Locator>()
    private var selectedResultIndex = -1
    private var requestToken = 0
    private var query by mutableStateOf("")
    private var resultLabel by mutableStateOf(INITIAL_MESSAGE)
    private var previousEnabled by mutableStateOf(false)
    private var nextEnabled by mutableStateOf(false)
    private var focusRequest by mutableIntStateOf(0)
    private var themeRevision by mutableIntStateOf(0)

    fun create(): View = ComposeView(activity).apply {
        tag = ReaderMenuTags.CARD
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            themeRevision
            MichisReaderComposeTheme {
                EpubSearchPanel(
                    query = query,
                    resultLabel = resultLabel,
                    previousEnabled = previousEnabled,
                    nextEnabled = nextEnabled,
                    focusRequest = focusRequest,
                    updateQuery = { query = it },
                    search = ::performSearch,
                    previous = { move(-1) },
                    next = { move(1) },
                    close = closePanel
                )
            }
        }
    }

    fun attachPublication(publication: Publication) {
        this.publication = publication
    }

    fun requestInputFocus() {
        focusRequest++
    }

    fun refreshTheme() {
        themeRevision++
    }

    fun performSearch() {
        val normalizedQuery = query.trim()
        val opened = publication
        if (normalizedQuery.isBlank() || opened == null) {
            resultLabel = INITIAL_MESSAGE
            return
        }
        val initialPage = currentPageIndex()
        val currentRequest = ++requestToken
        resultLabel = "Buscando…"
        previousEnabled = false
        nextEnabled = false
        scope.launch {
            val matches = mutableListOf<Locator>()
            val iterator = opened.search(
                normalizedQuery,
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
        resultLabel = if (count == 0) "Sin coincidencias" else
            "Coincidencia ${selectedResultIndex + 1} de $count"
        previousEnabled = selectedResultIndex > 0
        nextEnabled = selectedResultIndex in 0 until count - 1
    }

    private companion object {
        const val PENDING_JUMP_TIMEOUT_MILLISECONDS = 2_000L
        const val INITIAL_MESSAGE = "Escribe un texto para buscar"
    }
}

@Composable
private fun EpubSearchPanel(
    query: String,
    resultLabel: String,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    focusRequest: Int,
    updateQuery: (String) -> Unit,
    search: () -> Unit,
    previous: () -> Unit,
    next: () -> Unit,
    close: () -> Unit
) {
    val inputFocusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) inputFocusRequester.requestFocus()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MichisReaderCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Buscar en el libro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = updateQuery,
                modifier = Modifier.fillMaxWidth().focusRequester(inputFocusRequester),
                label = { Text("Palabra o frase") },
                singleLine = true,
                shape = MichisReaderInputShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() })
            )
            MichisReaderButton("Buscar", search, Modifier.fillMaxWidth())
            Text(resultLabel, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
            MichisReaderButtonRow {
                MichisReaderButton("Anterior", previous, Modifier.weight(1f), previousEnabled)
                MichisReaderButton("Siguiente", next, Modifier.weight(1f), nextEnabled)
            }
            MichisReaderButton("Cerrar", close, Modifier.fillMaxWidth())
        }
    }
}
