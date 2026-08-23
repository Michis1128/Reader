package com.michis.reader.annotations

import com.michis.reader.data.ReaderDatabase
import com.michis.reader.data.SavedAnnotation
import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderButton
import com.michis.reader.ui.compose.MichisReaderButtonRow
import com.michis.reader.ui.compose.MichisReaderCard
import com.michis.reader.ui.compose.MichisReaderScreenHeader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** Lista global de marcadores. La persistencia continúa delegada en [ReaderDatabase]. */
class BookmarksActivity : ComponentActivity() {
    private lateinit var database: ReaderDatabase
    private var screenState by mutableStateOf(BookmarksScreenState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ReaderDatabase.getInstance(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MichisReaderComposeTheme {
                BookmarksScreen(
                    state = screenState,
                    navigateBack = ::finish,
                    startSelection = { screenState = screenState.copy(selecting = true) },
                    cancelSelection = { screenState = screenState.copy(selecting = false, selectedIdentifiers = emptySet()) },
                    toggleSelection = ::toggleSelection,
                    requestDeleteSelection = { screenState = screenState.copy(confirmSelectionDeletion = true) },
                    dismissSelectionDeletion = { screenState = screenState.copy(confirmSelectionDeletion = false) },
                    deleteSelection = ::deleteSelection,
                    showActions = { screenState = screenState.copy(actionBookmark = it) },
                    dismissActions = { screenState = screenState.copy(actionBookmark = null) },
                    move = ::moveBookmark,
                    delete = ::deleteBookmark
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) reloadBookmarks()
    }

    private fun reloadBookmarks() {
        val bookmarks = database.annotations().filter { it.kind == BOOKMARK_KIND }
        screenState = screenState.copy(
            bookmarks = bookmarks,
            bookTitles = bookmarks.map(SavedAnnotation::documentIdentifier).distinct().associateWith {
                database.findDocument(it)?.title ?: "Libro eliminado"
            },
            selectedIdentifiers = screenState.selectedIdentifiers.intersect(bookmarks.mapTo(hashSetOf(), SavedAnnotation::identifier))
        )
    }

    private fun toggleSelection(identifier: Long) {
        val selection = screenState.selectedIdentifiers.toMutableSet()
        if (!selection.add(identifier)) selection.remove(identifier)
        screenState = screenState.copy(selectedIdentifiers = selection)
    }

    private fun deleteSelection() {
        screenState.selectedIdentifiers.forEach(database::deleteAnnotation)
        screenState = screenState.copy(selecting = false, selectedIdentifiers = emptySet(), confirmSelectionDeletion = false)
        reloadBookmarks()
    }

    private fun moveBookmark(bookmark: SavedAnnotation, direction: Int) {
        database.moveAnnotation(bookmark.identifier, direction)
        screenState = screenState.copy(actionBookmark = null)
        reloadBookmarks()
    }

    private fun deleteBookmark(bookmark: SavedAnnotation) {
        database.deleteAnnotation(bookmark.identifier)
        screenState = screenState.copy(actionBookmark = null)
        reloadBookmarks()
    }

    private companion object {
        const val BOOKMARK_KIND = "marcador"
    }
}

private data class BookmarksScreenState(
    val bookmarks: List<SavedAnnotation> = emptyList(),
    val bookTitles: Map<Long, String> = emptyMap(),
    val selecting: Boolean = false,
    val selectedIdentifiers: Set<Long> = emptySet(),
    val confirmSelectionDeletion: Boolean = false,
    val actionBookmark: SavedAnnotation? = null
)

@Composable
private fun BookmarksScreen(
    state: BookmarksScreenState,
    navigateBack: () -> Unit,
    startSelection: () -> Unit,
    cancelSelection: () -> Unit,
    toggleSelection: (Long) -> Unit,
    requestDeleteSelection: () -> Unit,
    dismissSelectionDeletion: () -> Unit,
    deleteSelection: () -> Unit,
    showActions: (SavedAnnotation) -> Unit,
    dismissActions: () -> Unit,
    move: (SavedAnnotation, Int) -> Unit,
    delete: (SavedAnnotation) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { MichisReaderScreenHeader("Marcadores", navigateBack) }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.bookmarks.isNotEmpty()) {
                if (state.selecting) {
                    Text("Selecciona uno o varios marcadores.")
                    MichisReaderButtonRow {
                        MichisReaderButton(
                            text = "Eliminar (${state.selectedIdentifiers.size})",
                            onClick = requestDeleteSelection,
                            modifier = Modifier.weight(1f),
                            enabled = state.selectedIdentifiers.isNotEmpty()
                        )
                        MichisReaderButton("Cancelar", cancelSelection, Modifier.weight(1f))
                    }
                } else {
                    MichisReaderButton(
                        text = "Seleccionar marcadores para eliminar",
                        onClick = startSelection,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (state.bookmarks.isEmpty()) {
                Text(
                    "Los marcadores de todos tus libros aparecerán aquí.",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 32.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.bookmarks, key = SavedAnnotation::identifier) { bookmark ->
                        BookmarkCard(
                            bookmark = bookmark,
                            bookTitle = state.bookTitles[bookmark.documentIdentifier].orEmpty(),
                            selecting = state.selecting,
                            selected = bookmark.identifier in state.selectedIdentifiers,
                            toggleSelection = { toggleSelection(bookmark.identifier) },
                            showActions = { showActions(bookmark) }
                        )
                    }
                }
            }
        }
    }
    if (state.confirmSelectionDeletion) {
        AlertDialog(
            onDismissRequest = dismissSelectionDeletion,
            title = { Text("Eliminar selección") },
            text = { Text("Se eliminarán ${state.selectedIdentifiers.size} marcadores y el cambio se sincronizará con Drive.") },
            confirmButton = { TextButton(onClick = deleteSelection) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = dismissSelectionDeletion) { Text("Cancelar") } }
        )
    }
    state.actionBookmark?.let { bookmark ->
        BookmarkActionsDialog(bookmark, dismissActions, move, delete)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkCard(
    bookmark: SavedAnnotation,
    bookTitle: String,
    selecting: Boolean,
    selected: Boolean,
    toggleSelection: () -> Unit,
    showActions: () -> Unit
) {
    MichisReaderCard(
        modifier = Modifier.combinedClickable(
            onClick = { if (selecting) toggleSelection() },
            onLongClick = { if (!selecting) showActions() }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selecting) Checkbox(checked = selected, onCheckedChange = { toggleSelection() })
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(bookmark.selectedText.ifBlank { "Página marcada" }, style = MaterialTheme.typography.titleMedium)
                Text("De: $bookTitle", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (bookmark.pageNumber > 0) Text("Página ${bookmark.pageNumber}")
                if (bookmark.note.isNotBlank()) Text(bookmark.note)
            }
        }
    }
}

@Composable
private fun BookmarkActionsDialog(
    bookmark: SavedAnnotation,
    dismiss: () -> Unit,
    move: (SavedAnnotation, Int) -> Unit,
    delete: (SavedAnnotation) -> Unit
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Organizar marcador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { move(bookmark, -1) }, modifier = Modifier.fillMaxWidth()) { Text("Mover arriba") }
                TextButton(onClick = { move(bookmark, 1) }, modifier = Modifier.fillMaxWidth()) { Text("Mover abajo") }
                TextButton(onClick = { delete(bookmark) }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar") }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Cerrar") } }
    )
}
