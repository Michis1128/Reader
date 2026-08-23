package com.michis.reader.settings

import com.michis.reader.data.LibraryDocument
import com.michis.reader.data.ReaderDatabase
import com.michis.reader.sync.AutomaticDriveSyncScheduler
import com.michis.reader.sync.drive.GoogleDriveAuthorizationManager
import com.michis.reader.sync.drive.GoogleDriveFolderRepository
import com.michis.reader.sync.drive.OptionalGoogleAccountManager
import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderButton
import com.michis.reader.ui.compose.MichisReaderButtonRow
import com.michis.reader.ui.compose.MichisReaderCard
import com.michis.reader.ui.compose.MichisReaderInputShape
import com.michis.reader.ui.compose.MichisReaderScreenHeader

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

class ResetBooksActivity : ComponentActivity() {
    private lateinit var database: ReaderDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ReaderDatabase.getInstance(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MichisReaderComposeTheme {
                ResetBooksScreen(
                    documents = remember { database.findDocuments() },
                    navigateBack = ::finish,
                    resetDocuments = ::resetDocuments
                )
            }
        }
    }

    private fun resetDocuments(documents: List<LibraryDocument>) {
        var completed = 0
        documents.forEach { document ->
            if (runCatching { database.resetBook(document.identifier) }.isSuccess) completed++
        }
        enqueueDriveSyncIfAvailable()
        Toast.makeText(this, "$completed libros reiniciados", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun enqueueDriveSyncIfAvailable() {
        val session = OptionalGoogleAccountManager(this).currentSession() ?: return
        if (!GoogleDriveAuthorizationManager(this).isAuthorized()) return
        if (GoogleDriveFolderRepository(this).savedFolder(session.accountIdentifier) == null) return
        AutomaticDriveSyncScheduler(this).enqueueImmediateSync()
    }
}

@Composable
private fun ResetBooksScreen(
    documents: List<LibraryDocument>,
    navigateBack: () -> Unit,
    resetDocuments: (List<LibraryDocument>) -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var sortMode by rememberSaveable { mutableStateOf(ResetBooksSortMode.TITLE) }
    var selectedIdentifiers by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    val visibleDocuments = remember(documents, query.text, sortMode) {
        filterAndSortResetDocuments(documents, query.text, sortMode)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { MichisReaderScreenHeader("Reiniciar libros", navigateBack) }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "El libro se conservará, pero se borrarán su progreso, citas, notas, marcadores y diccionario.",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            MichisReaderCard {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar por título, autor o archivo EPUB") },
                    singleLine = true,
                    shape = MichisReaderInputShape,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Ordenar por", fontWeight = FontWeight.Bold)
                ResetBooksSortSelector(sortMode) { sortMode = it }
            }
            Text(
                "${selectedIdentifiers.size} seleccionados · ${visibleDocuments.size} resultados",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
            )
            MichisReaderButton(
                text = "Seleccionar todos los libros",
                onClick = { selectedIdentifiers = documents.mapTo(mutableSetOf(), LibraryDocument::identifier) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            MichisReaderButtonRow {
                MichisReaderButton(
                    text = "Seleccionar visibles",
                    onClick = {
                        selectedIdentifiers = selectedIdentifiers + visibleDocuments.map(LibraryDocument::identifier)
                    },
                    modifier = Modifier.weight(1f)
                )
                MichisReaderButton(
                    text = "Limpiar",
                    onClick = { selectedIdentifiers = emptySet() },
                    modifier = Modifier.weight(1f)
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (visibleDocuments.isEmpty()) {
                    item {
                        Text(
                            "No se encontraron libros",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                items(visibleDocuments, key = LibraryDocument::identifier) { document ->
                    ResetBookRow(
                        document = document,
                        selected = document.identifier in selectedIdentifiers,
                        selectionChanged = { selected ->
                            selectedIdentifiers = if (selected) {
                                selectedIdentifiers + document.identifier
                            } else {
                                selectedIdentifiers - document.identifier
                            }
                        }
                    )
                }
            }
            MichisReaderButton(
                text = "Reiniciar seleccionados",
                onClick = {
                    if (selectedIdentifiers.isEmpty()) {
                        Toast.makeText(context, "Selecciona al menos un libro", Toast.LENGTH_SHORT).show()
                    } else showResetConfirmation = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
    }
    if (showResetConfirmation) {
        val selectedDocuments = documents.filter { it.identifier in selectedIdentifiers }
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reiniciar ${selectedDocuments.size} libros") },
            text = {
                Text(
                    "Esta operación borrará los datos de lectura asociados y se sincronizará con Drive. " +
                        "Los archivos EPUB no se eliminarán."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirmation = false
                    resetDocuments(selectedDocuments)
                }) { Text("Reiniciar") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text("Cancelar") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun ResetBooksSortSelector(selected: ResetBooksSortMode, select: (ResetBooksSortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(selected.label, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Mostrar opciones")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ResetBooksSortMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        select(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ResetBookRow(
    document: LibraryDocument,
    selected: Boolean,
    selectionChanged: (Boolean) -> Unit
) {
    MichisReaderCard(modifier = Modifier.clickable { selectionChanged(!selected) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = selectionChanged)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(document.title, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(document.format)
                        if (document.author.isNotBlank()) append(" · ${document.author}")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal enum class ResetBooksSortMode(val label: String) {
    TITLE("Título"), AUTHOR("Autor"), FORMAT("Formato"), RECENTLY_OPENED("Abiertos recientemente")
}

internal fun filterAndSortResetDocuments(
    documents: List<LibraryDocument>,
    query: String,
    sortMode: ResetBooksSortMode
): List<LibraryDocument> {
    val normalizedQuery = query.trim()
    val filtered = documents.filter { document ->
        normalizedQuery.isBlank() || listOf(
            document.title, document.author, document.format, document.fileName
        ).any { value -> value.contains(normalizedQuery, ignoreCase = true) }
    }
    return when (sortMode) {
        ResetBooksSortMode.AUTHOR -> filtered.sortedWith(
            compareBy<LibraryDocument> { it.author.ifBlank { "￿" }.lowercase() }
                .thenBy { it.title.lowercase() }
        )
        ResetBooksSortMode.FORMAT -> filtered.sortedWith(
            compareBy<LibraryDocument> { it.format.lowercase() }.thenBy { it.title.lowercase() }
        )
        ResetBooksSortMode.RECENTLY_OPENED -> filtered.sortedByDescending(LibraryDocument::lastOpenedAt)
        ResetBooksSortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
    }
}
