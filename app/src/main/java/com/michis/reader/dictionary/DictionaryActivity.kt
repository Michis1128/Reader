package com.michis.reader.dictionary

import com.michis.reader.data.DictionaryCategory
import com.michis.reader.data.DictionaryEntry
import com.michis.reader.data.LibraryDocument
import com.michis.reader.data.ReaderDatabase
import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderButton
import com.michis.reader.ui.compose.MichisReaderButtonRow
import com.michis.reader.ui.compose.MichisReaderCard
import com.michis.reader.ui.compose.MichisReaderInputShape
import com.michis.reader.ui.compose.MichisReaderScreenHeader

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

class DictionaryActivity : ComponentActivity() {
    private lateinit var database: ReaderDatabase
    private lateinit var document: LibraryDocument
    private val pendingTerm by lazy { intent.getStringExtra(EXTRA_SELECTED_TEXT).orEmpty().trim() }
    private val pendingContext by lazy { intent.getStringExtra(EXTRA_SELECTED_CONTEXT).orEmpty().trim() }
    private var screen by mutableStateOf<DictionaryScreen>(DictionaryScreen.Categories)
    private var deletion by mutableStateOf<DictionaryDeletion?>(null)
    private var refreshVersion by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ReaderDatabase.getInstance(this)
        document = database.findDocument(intent.getLongExtra(EXTRA_DOCUMENT_IDENTIFIER, -1)) ?: run {
            finish()
            return
        }
        screen = requestedEntryScreen() ?: DictionaryScreen.Categories
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MichisReaderComposeTheme {
                DictionaryScreenContent(
                    document = document,
                    screen = screen,
                    pendingTerm = pendingTerm,
                    categories = database.dictionaryCategories(document.identifier),
                    entries = { database.dictionaryEntries(it.identifier) },
                    documents = database.findDocuments(),
                    linkedDocuments = database.linkedDocuments(document.identifier),
                    refreshVersion = refreshVersion,
                    navigateBack = ::navigateBack,
                    navigate = { screen = it },
                    createCategory = ::createCategory,
                    saveEntry = ::saveEntry,
                    updateEntry = ::updateEntry,
                    requestDeletion = { deletion = it },
                    toggleDictionaryLink = ::toggleDictionaryLink
                )
                deletion?.let { request ->
                    DictionaryDeletionDialog(
                        request = request,
                        dismiss = { deletion = null },
                        confirm = ::confirmDeletion
                    )
                }
            }
        }
    }

    private fun requestedEntryScreen(): DictionaryScreen.Editor? {
        val entry = database.findDictionaryEntry(intent.getLongExtra(EXTRA_ENTRY_IDENTIFIER, -1))
            ?.takeIf { it.documentIdentifier in database.effectiveDictionaryOwnerIdentifiers(document.identifier) }
            ?: return null
        val category = database.dictionaryCategories(entry.documentIdentifier)
            .firstOrNull { it.identifier == entry.categoryIdentifier }
            ?: return null
        return DictionaryScreen.Editor(category, entry)
    }

    private fun createCategory(name: String) {
        val identifier = database.createDictionaryCategory(document.identifier, name)
        if (identifier < 0) {
            Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show()
            return
        }
        val category = database.dictionaryCategories(document.identifier).firstOrNull { it.identifier == identifier }
            ?: return
        screen = if (pendingTerm.isBlank()) DictionaryScreen.Entries(category) else DictionaryScreen.Editor(category, null)
        refresh()
    }

    private fun saveEntry(category: DictionaryCategory, term: String, description: String, describeLater: Boolean) {
        val result = database.saveDictionaryEntry(
            document.identifier,
            category.identifier,
            term,
            if (describeLater) "" else description,
            pendingContext
        )
        when (result) {
            ReaderDatabase.DUPLICATE_DICTIONARY_ENTRY -> Toast.makeText(
                this,
                "Esta palabra o frase ya existe en el diccionario de este libro",
                Toast.LENGTH_LONG
            ).show()
            -1L -> Toast.makeText(this, "Escribe una palabra o frase", Toast.LENGTH_SHORT).show()
            else -> entrySaved(category)
        }
    }

    private fun updateEntry(category: DictionaryCategory, entry: DictionaryEntry, description: String) {
        database.updateDictionaryDescription(entry.identifier, description)
        entrySaved(category)
    }

    private fun entrySaved(category: DictionaryCategory) {
        Toast.makeText(this, "Elemento guardado", Toast.LENGTH_SHORT).show()
        if (pendingTerm.isNotBlank()) finish() else {
            screen = DictionaryScreen.Entries(category)
            refresh()
        }
    }

    private fun toggleDictionaryLink(documentIdentifier: Long, linked: Boolean) {
        database.setDictionaryLinked(document.identifier, documentIdentifier, linked)
        refresh()
    }

    private fun confirmDeletion(request: DictionaryDeletion) {
        when (request) {
            is DictionaryDeletion.Category -> {
                database.deleteDictionaryCategory(request.category.identifier)
                screen = DictionaryScreen.Categories
            }
            is DictionaryDeletion.Categories -> {
                request.identifiers.forEach(database::deleteDictionaryCategory)
                screen = DictionaryScreen.Categories
            }
            is DictionaryDeletion.Entry -> {
                database.deleteDictionaryEntry(request.entry.identifier)
                screen = DictionaryScreen.Entries(request.category)
            }
            is DictionaryDeletion.Entries -> {
                request.identifiers.forEach(database::deleteDictionaryEntry)
                screen = DictionaryScreen.Entries(request.category)
            }
        }
        deletion = null
        refresh()
    }

    private fun navigateBack() {
        screen = when (val current = screen) {
            DictionaryScreen.Categories -> {
                finish()
                return
            }
            is DictionaryScreen.Entries -> DictionaryScreen.Categories
            is DictionaryScreen.Editor -> if (pendingTerm.isNotBlank()) DictionaryScreen.Categories else DictionaryScreen.Entries(current.category)
            is DictionaryScreen.SelectCategories -> DictionaryScreen.Categories
            is DictionaryScreen.SelectEntries -> DictionaryScreen.Entries(current.category)
            DictionaryScreen.Sharing -> DictionaryScreen.Categories
        }
    }

    private fun refresh() {
        refreshVersion += 1
    }

    companion object {
        const val EXTRA_DOCUMENT_IDENTIFIER = "document_identifier"
        const val EXTRA_SELECTED_TEXT = "selected_text"
        const val EXTRA_SELECTED_CONTEXT = "selected_context"
        const val EXTRA_ENTRY_IDENTIFIER = "dictionary_entry_identifier"
    }
}

private sealed interface DictionaryScreen {
    data object Categories : DictionaryScreen
    data class Entries(val category: DictionaryCategory) : DictionaryScreen
    data class Editor(val category: DictionaryCategory, val entry: DictionaryEntry?) : DictionaryScreen
    data class SelectCategories(val categories: List<DictionaryCategory>) : DictionaryScreen
    data class SelectEntries(val category: DictionaryCategory, val entries: List<DictionaryEntry>) : DictionaryScreen
    data object Sharing : DictionaryScreen
}

private sealed interface DictionaryDeletion {
    val title: String
    val message: String

    data class Category(val category: DictionaryCategory) : DictionaryDeletion {
        override val title = "Eliminar subcategoría"
        override val message = "Se eliminará ${category.name} y todos sus elementos. El diccionario puede quedar sin subcategorías."
    }
    data class Categories(val identifiers: Set<Long>) : DictionaryDeletion {
        override val title = "Eliminar subcategorías"
        override val message = "Se eliminarán ${identifiers.size} subcategorías y todos sus elementos."
    }
    data class Entry(val category: DictionaryCategory, val entry: DictionaryEntry) : DictionaryDeletion {
        override val title = "Eliminar elemento"
        override val message = "Se eliminará ${entry.term} del diccionario."
    }
    data class Entries(val category: DictionaryCategory, val identifiers: Set<Long>) : DictionaryDeletion {
        override val title = "Eliminar elementos"
        override val message = "Se eliminarán ${identifiers.size} elementos del diccionario."
    }
}

@Composable
private fun DictionaryScreenContent(
    document: LibraryDocument,
    screen: DictionaryScreen,
    pendingTerm: String,
    categories: List<DictionaryCategory>,
    entries: (DictionaryCategory) -> List<DictionaryEntry>,
    documents: List<LibraryDocument>,
    linkedDocuments: Set<Long>,
    refreshVersion: Int,
    navigateBack: () -> Unit,
    navigate: (DictionaryScreen) -> Unit,
    createCategory: (String) -> Unit,
    saveEntry: (DictionaryCategory, String, String, Boolean) -> Unit,
    updateEntry: (DictionaryCategory, DictionaryEntry, String) -> Unit,
    requestDeletion: (DictionaryDeletion) -> Unit,
    toggleDictionaryLink: (Long, Boolean) -> Unit
) {
    val title = when (screen) {
        DictionaryScreen.Categories -> "Diccionario · ${document.title}"
        is DictionaryScreen.Entries -> screen.category.name
        is DictionaryScreen.Editor -> screen.category.name
        is DictionaryScreen.SelectCategories -> "Eliminar subcategorías"
        is DictionaryScreen.SelectEntries -> "Eliminar de ${screen.category.name}"
        DictionaryScreen.Sharing -> "Compartir diccionario"
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { MichisReaderScreenHeader(title, navigateBack) }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            when (screen) {
                DictionaryScreen.Categories -> CategoriesContent(
                    categories, pendingTerm, createCategory, navigate
                )
                is DictionaryScreen.Entries -> EntriesContent(screen.category, entries(screen.category), navigate, requestDeletion)
                is DictionaryScreen.Editor -> EntryEditorContent(screen.category, screen.entry, pendingTerm, refreshVersion, saveEntry, updateEntry, requestDeletion)
                is DictionaryScreen.SelectCategories -> CategorySelectionContent(screen.categories, navigate, requestDeletion)
                is DictionaryScreen.SelectEntries -> EntrySelectionContent(screen.category, screen.entries, navigate, requestDeletion)
                DictionaryScreen.Sharing -> SharingContent(document, documents, linkedDocuments, toggleDictionaryLink, navigate)
            }
        }
    }
}

@Composable
private fun CategoriesContent(
    categories: List<DictionaryCategory>,
    pendingTerm: String,
    createCategory: (String) -> Unit,
    navigate: (DictionaryScreen) -> Unit
) {
    var categoryName by rememberSaveable { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            MichisReaderCard {
                Text("Organiza palabras, personajes o conceptos en subcategorías. Primero crea una subcategoría y después agrega dentro las palabras o frases.")
            }
        }
        if (pendingTerm.isNotBlank()) item {
            MichisReaderCard { Text("Vas a guardar “$pendingTerm”. Toca la subcategoría donde debe aparecer.") }
        }
        item { Text("Subcategorías", style = MaterialTheme.typography.titleLarge) }
        item {
            MichisReaderCard {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Nombre de la nueva subcategoría") },
                    singleLine = true,
                    shape = MichisReaderInputShape,
                    modifier = Modifier.fillMaxWidth()
                )
                MichisReaderButton("Crear subcategoría", { createCategory(categoryName) }, Modifier.fillMaxWidth())
            }
        }
        item { MichisReaderButton("Compartir este diccionario con otros libros", { navigate(DictionaryScreen.Sharing) }, Modifier.fillMaxWidth()) }
        if (categories.isNotEmpty() && pendingTerm.isBlank()) item {
            MichisReaderButton("Seleccionar subcategorías para eliminar", { navigate(DictionaryScreen.SelectCategories(categories)) }, Modifier.fillMaxWidth())
        }
        items(categories, key = DictionaryCategory::identifier) { category ->
            MichisReaderCard(modifier = Modifier.clickable {
                navigate(if (pendingTerm.isBlank()) DictionaryScreen.Entries(category) else DictionaryScreen.Editor(category, null))
            }) {
                Text(category.name, style = MaterialTheme.typography.titleMedium)
                Text("Toca para ver sus elementos", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (categories.isEmpty()) item { Text("Este libro todavía no tiene subcategorías. Crea la primera, por ejemplo: Personajes, Palabras o Lugares.") }
    }
}

@Composable
private fun EntriesContent(
    category: DictionaryCategory,
    entries: List<DictionaryEntry>,
    navigate: (DictionaryScreen) -> Unit,
    requestDeletion: (DictionaryDeletion) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { MichisReaderButton("Agregar palabra o frase", { navigate(DictionaryScreen.Editor(category, null)) }, Modifier.fillMaxWidth()) }
        item { MichisReaderButton("Eliminar esta subcategoría", { requestDeletion(DictionaryDeletion.Category(category)) }, Modifier.fillMaxWidth()) }
        if (entries.isNotEmpty()) item {
            MichisReaderButton("Seleccionar elementos para eliminar", { navigate(DictionaryScreen.SelectEntries(category, entries)) }, Modifier.fillMaxWidth())
        }
        items(entries, key = DictionaryEntry::identifier) { entry ->
            MichisReaderCard(modifier = Modifier.clickable { navigate(DictionaryScreen.Editor(category, entry)) }) {
                Text(entry.term, style = MaterialTheme.typography.titleMedium)
                Text(entry.description.ifBlank { "Sin descripción" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (entries.isEmpty()) item { Text("Esta subcategoría aún no tiene elementos.") }
    }
}

@Composable
private fun EntryEditorContent(
    category: DictionaryCategory,
    existing: DictionaryEntry?,
    pendingTerm: String,
    refreshVersion: Int,
    saveEntry: (DictionaryCategory, String, String, Boolean) -> Unit,
    updateEntry: (DictionaryCategory, DictionaryEntry, String) -> Unit,
    requestDeletion: (DictionaryDeletion) -> Unit
) {
    var term by rememberSaveable(existing?.identifier, refreshVersion) { mutableStateOf(existing?.term ?: pendingTerm) }
    var description by rememberSaveable(existing?.identifier, refreshVersion) { mutableStateOf(existing?.description.orEmpty()) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { Text(if (existing == null) "Nuevo elemento" else "Editar elemento", style = MaterialTheme.typography.titleLarge) }
        item {
            MichisReaderCard {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    enabled = existing == null,
                    label = { Text("Palabra o frase") },
                    shape = MichisReaderInputShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    minLines = 3,
                    shape = MichisReaderInputShape,
                    modifier = Modifier.fillMaxWidth()
                )
                MichisReaderButton(
                    if (existing == null) "Guardar elemento" else "Guardar cambios",
                    { if (existing == null) saveEntry(category, term, description, false) else updateEntry(category, existing, description) },
                    Modifier.fillMaxWidth()
                )
                if (existing == null) {
                    MichisReaderButton("Guardar y describir después", { saveEntry(category, term, description, true) }, Modifier.fillMaxWidth())
                } else {
                    MichisReaderButton("Eliminar elemento", { requestDeletion(DictionaryDeletion.Entry(category, existing)) }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CategorySelectionContent(
    categories: List<DictionaryCategory>,
    navigate: (DictionaryScreen) -> Unit,
    requestDeletion: (DictionaryDeletion) -> Unit
) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    SelectionContent(
        message = "Selecciona una o varias subcategorías. También se eliminarán todas las entradas que contienen.",
        items = categories.map { it.identifier to it.name },
        selected = selected,
        toggle = { selected = selected.toggle(it) },
        deleteLabel = "Eliminar seleccionadas",
        delete = { if (selected.isNotEmpty()) requestDeletion(DictionaryDeletion.Categories(selected)) },
        cancel = { navigate(DictionaryScreen.Categories) }
    )
}

@Composable
private fun EntrySelectionContent(
    category: DictionaryCategory,
    entries: List<DictionaryEntry>,
    navigate: (DictionaryScreen) -> Unit,
    requestDeletion: (DictionaryDeletion) -> Unit
) {
    var selected by remember(category.identifier) { mutableStateOf(emptySet<Long>()) }
    SelectionContent(
        message = "Selecciona todas las palabras o frases que deseas eliminar.",
        items = entries.map { it.identifier to it.term },
        selected = selected,
        toggle = { selected = selected.toggle(it) },
        deleteLabel = "Eliminar seleccionados",
        delete = { if (selected.isNotEmpty()) requestDeletion(DictionaryDeletion.Entries(category, selected)) },
        cancel = { navigate(DictionaryScreen.Entries(category)) }
    )
}

@Composable
private fun SelectionContent(
    message: String,
    items: List<Pair<Long, String>>,
    selected: Set<Long>,
    toggle: (Long) -> Unit,
    deleteLabel: String,
    delete: () -> Unit,
    cancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        Text(message)
        MichisReaderButtonRow {
            MichisReaderButton(deleteLabel, delete, Modifier.weight(1f), selected.isNotEmpty())
            MichisReaderButton("Cancelar", cancel, Modifier.weight(1f))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.first }) { item ->
                MichisReaderCard(modifier = Modifier.clickable { toggle(item.first) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.first in selected, { toggle(item.first) })
                        Text(item.second, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SharingContent(
    owner: LibraryDocument,
    documents: List<LibraryDocument>,
    linkedDocuments: Set<Long>,
    toggleDictionaryLink: (Long, Boolean) -> Unit,
    navigate: (DictionaryScreen) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { Text("Selecciona los libros que también podrán usar las categorías y entradas de ${owner.title}.") }
        items(documents.filter { it.identifier != owner.identifier }, key = LibraryDocument::identifier) { target ->
            MichisReaderCard(modifier = Modifier.clickable {
                toggleDictionaryLink(target.identifier, target.identifier !in linkedDocuments)
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = target.identifier in linkedDocuments,
                        onCheckedChange = { toggleDictionaryLink(target.identifier, it) }
                    )
                    Text(target.title, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        item { MichisReaderButton("Listo", { navigate(DictionaryScreen.Categories) }, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun DictionaryDeletionDialog(
    request: DictionaryDeletion,
    dismiss: () -> Unit,
    confirm: (DictionaryDeletion) -> Unit
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(request.title) },
        text = { Text(request.message) },
        confirmButton = { TextButton(onClick = { confirm(request) }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } }
    )
}

private fun Set<Long>.toggle(identifier: Long): Set<Long> = toMutableSet().apply {
    if (!add(identifier)) remove(identifier)
}
