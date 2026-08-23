package com.michis.reader.app

import com.michis.reader.ui.compose.MichisReaderButton
import com.michis.reader.ui.compose.MichisReaderButtonRow
import com.michis.reader.ui.compose.MichisReaderInputShape

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class MainControlsState(
    val query: String = "",
    val syncStatus: String = "",
    val syncActionsEnabled: Boolean = true,
    val filterLabel: String = "",
    val displayIcon: String = ""
)

@Composable
internal fun MainLibraryControls(
    state: MainControlsState,
    updateQuery: (String) -> Unit,
    openSettings: () -> Unit,
    upload: () -> Unit,
    download: () -> Unit,
    importBooks: () -> Unit,
    openLibrary: () -> Unit,
    openCurrentlyReading: () -> Unit,
    openQuotes: () -> Unit,
    openBookmarks: () -> Unit,
    openDictionaries: () -> Unit,
    openFilters: () -> Unit,
    changeDisplay: () -> Unit,
    modifier: Modifier = Modifier
) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mi biblioteca", style = MaterialTheme.typography.headlineMedium)
                        Text("Tus lecturas, disponibles sin conexión", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = openSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Configuración")
                    }
                }
                if (state.syncStatus.isNotBlank()) {
                    Text(state.syncStatus, style = MaterialTheme.typography.bodySmall)
                }
                MichisReaderButtonRow {
                    MichisReaderButton("↑ Subir cambios", upload, Modifier.weight(1f), state.syncActionsEnabled)
                    MichisReaderButton("↓ Descargar", download, Modifier.weight(1f), state.syncActionsEnabled)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = updateQuery,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Buscar título, autor o EPUB") },
                        shape = MichisReaderInputShape
                    )
                    IconButton(onClick = importBooks) {
                        Icon(Icons.Rounded.Add, contentDescription = "Importar libros")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MichisReaderButton("Biblioteca", openLibrary)
                    MichisReaderButton("Leyendo actualmente", openCurrentlyReading)
                    MichisReaderButton("Citas", openQuotes)
                    MichisReaderButton("Marcadores", openBookmarks)
                    MichisReaderButton("Diccionarios", openDictionaries)
                    MichisReaderButton("Filtro: ${state.filterLabel}", openFilters)
                    MichisReaderButton(state.displayIcon, changeDisplay)
                }
            }
    }
}
