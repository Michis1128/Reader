package com.michis.reader.sync.drive

import com.michis.reader.sync.AutomaticDriveSyncScheduler
import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderButton
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DriveLibraryPickerActivity : ComponentActivity() {
    private val repository by lazy { GoogleDriveBookLibraryRepository(this) }
    private lateinit var accountIdentifier: String
    private lateinit var accessToken: String
    private val navigationStack = mutableListOf(DriveLibrarySource("root", "Mi unidad", true))
    private var screenState by mutableStateOf(DrivePickerState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountIdentifier = intent.getStringExtra(EXTRA_ACCOUNT_IDENTIFIER).orEmpty()
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()
        if (accountIdentifier.isBlank() || accessToken.isBlank()) {
            finish()
            return
        }
        val selected = repository.selectedSources(accountIdentifier)
        screenState = screenState.copy(
            selectedIdentifiers = selected.mapTo(linkedSetOf(), DriveLibrarySource::identifier),
            selectedSources = selected.associateByTo(linkedMapOf(), DriveLibrarySource::identifier)
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navigationStack.size > 1) navigateBack() else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MichisReaderComposeTheme {
                DrivePickerScreen(
                    state = screenState,
                    navigateActivityBack = ::finish,
                    updateQuery = { screenState = screenState.copy(query = it) },
                    navigateBack = ::navigateBack,
                    enterFolder = ::enterFolder,
                    toggleSelection = ::toggleSelection,
                    applySelection = ::applySelection
                )
            }
        }
        loadSources()
    }

    private fun loadSources() {
        screenState = screenState.copy(loading = true, error = null, path = navigationStack.joinToString("  ›  ") { it.name })
        lifecycleScope.launch {
            val parentIdentifier = navigationStack.last().identifier
            runCatching { withContext(Dispatchers.IO) { repository.listChildren(accessToken, parentIdentifier) } }
                .onSuccess { screenState = screenState.copy(sources = it, loading = false) }
                .onFailure { screenState = screenState.copy(loading = false, error = "No se pudo cargar Drive: ${it.message.orEmpty()}") }
        }
    }

    private fun enterFolder(folder: DriveLibrarySource) {
        if (!folder.isFolder) return
        navigationStack += folder
        screenState = screenState.copy(query = "")
        loadSources()
    }

    private fun navigateBack() {
        if (navigationStack.size <= 1) return
        navigationStack.removeAt(navigationStack.lastIndex)
        screenState = screenState.copy(query = "")
        loadSources()
    }

    private fun toggleSelection(source: DriveLibrarySource) {
        val identifiers = screenState.selectedIdentifiers.toMutableSet()
        val sources = screenState.selectedSources.toMutableMap()
        if (identifiers.add(source.identifier)) sources[source.identifier] = source
        else sources.remove(source.identifier)
        screenState = screenState.copy(selectedIdentifiers = identifiers, selectedSources = sources)
    }

    private fun applySelection() {
        repository.saveSelectedSources(accountIdentifier, screenState.selectedSources.values)
        AutomaticDriveSyncScheduler(this).enqueueImmediateSync()
        setResult(RESULT_OK)
        Toast.makeText(this, "Biblioteca de Drive actualizada", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_ACCOUNT_IDENTIFIER = "account_identifier"
        const val EXTRA_ACCESS_TOKEN = "access_token"
    }
}

private data class DrivePickerState(
    val path: String = "Mi unidad",
    val query: String = "",
    val sources: List<DriveLibrarySource> = emptyList(),
    val selectedIdentifiers: Set<String> = emptySet(),
    val selectedSources: Map<String, DriveLibrarySource> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null
) {
    val visibleSources: List<DriveLibrarySource>
        get() = sources.filter { it.name.contains(query.trim(), ignoreCase = true) }
}

@Composable
private fun DrivePickerScreen(
    state: DrivePickerState,
    navigateActivityBack: () -> Unit,
    updateQuery: (String) -> Unit,
    navigateBack: () -> Unit,
    enterFolder: (DriveLibrarySource) -> Unit,
    toggleSelection: (DriveLibrarySource) -> Unit,
    applySelection: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { MichisReaderScreenHeader("Biblioteca de Google Drive", navigateActivityBack) }
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Selecciona carpetas completas, libros EPUB individuales o una combinación de ambos.")
            Text(
                state.path,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clickable(onClick = navigateBack).padding(vertical = 6.dp)
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = updateQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar carpetas o libros EPUB") },
                singleLine = true,
                shape = MichisReaderInputShape
            )
            Text("${state.selectedIdentifiers.size} seleccionados · ${state.visibleSources.size} resultados", style = MaterialTheme.typography.bodySmall)
            when {
                state.loading -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(state.error)
                }
                state.visibleSources.isEmpty() -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No hay resultados")
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.visibleSources, key = DriveLibrarySource::identifier) { source ->
                        DriveSourceCard(source, source.identifier in state.selectedIdentifiers, toggleSelection, enterFolder)
                    }
                }
            }
            MichisReaderButton("Aplicar selección", applySelection, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DriveSourceCard(
    source: DriveLibrarySource,
    selected: Boolean,
    toggleSelection: (DriveLibrarySource) -> Unit,
    enterFolder: (DriveLibrarySource) -> Unit
) {
    MichisReaderCard(modifier = Modifier.clickable {
        if (source.isFolder) enterFolder(source) else toggleSelection(source)
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Checkbox(selected, { toggleSelection(source) })
            Text(if (source.isFolder) "📁" else "📖")
            Text(source.name, modifier = Modifier.weight(1f))
            if (source.isFolder) Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}
