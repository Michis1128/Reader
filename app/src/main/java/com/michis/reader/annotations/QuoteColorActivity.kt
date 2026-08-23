package com.michis.reader.annotations

import com.michis.reader.data.ReaderDatabase
import com.michis.reader.data.SavedAnnotation
import com.michis.reader.settings.ReaderSettingsRepository
import com.michis.reader.theme.KvColorPickerOverlay
import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderButton
import com.michis.reader.ui.compose.MichisReaderCard
import com.michis.reader.ui.compose.MichisReaderInputShape
import com.michis.reader.ui.compose.MichisReaderScreenHeader

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

class QuoteColorActivity : ComponentActivity() {
    private lateinit var database: ReaderDatabase
    private var existingQuote: SavedAnnotation? = null
    private var selectedColor by mutableIntStateOf(AndroidColor.rgb(255, 213, 79))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ReaderDatabase.getInstance(this)
        val quoteIdentifier = intent.getLongExtra(EXTRA_QUOTE_IDENTIFIER, -1)
        existingQuote = database.annotations().firstOrNull {
            it.identifier == quoteIdentifier && it.kind == "cita"
        }
        selectedColor = existingQuote?.color ?: defaultQuoteColor()
        val selectedText = existingQuote?.selectedText ?: intent.getStringExtra(EXTRA_TEXT).orEmpty().trim()
        if (selectedText.isBlank()) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MichisReaderComposeTheme {
                QuoteEditorScreen(
                    selectedText = selectedText,
                    initialNote = existingQuote?.note.orEmpty(),
                    selectedColor = selectedColor,
                    editing = existingQuote != null,
                    navigateBack = ::finish,
                    chooseColor = ::showColorPicker,
                    save = ::saveQuote,
                    delete = ::deleteQuote
                )
            }
        }
        if (existingQuote == null) window.decorView.post(::showColorPicker)
    }

    private fun defaultQuoteColor(): Int = runCatching {
        val settings = ReaderSettingsRepository.get(this)
        AndroidColor.parseColor(settings.preferences.getString(
            ReaderSettingsRepository.KEY_QUOTE_DEFAULT_COLOR,
            ReaderSettingsRepository.DEFAULT_QUOTE_COLOR
        ))
    }.getOrDefault(AndroidColor.rgb(255, 213, 79))

    private fun showColorPicker() {
        KvColorPickerOverlay.show(this, selectedColor) { color -> selectedColor = color }
    }

    private fun saveQuote(note: String) {
        val quote = existingQuote
        if (quote == null) {
            database.addAnnotation(
                intent.getLongExtra(EXTRA_DOCUMENT_IDENTIFIER, -1),
                "cita",
                intent.getStringExtra(EXTRA_TEXT).orEmpty().trim(),
                note,
                selectedColor,
                intent.getIntExtra(EXTRA_LOCATION, 0),
                intent.getIntExtra(EXTRA_PAGE_NUMBER, 0),
                intent.getStringExtra(EXTRA_LOCATOR_JSON).orEmpty()
            )
        } else {
            database.updateQuote(quote.identifier, note, selectedColor)
        }
        Toast.makeText(this, if (quote == null) "Cita guardada" else "Cita actualizada", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun deleteQuote() {
        val quote = existingQuote ?: return
        database.deleteAnnotation(quote.identifier)
        Toast.makeText(this, "Cita eliminada", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_DOCUMENT_IDENTIFIER = "document_identifier"
        const val EXTRA_TEXT = "selected_text"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_PAGE_NUMBER = "page_number"
        const val EXTRA_LOCATOR_JSON = "locator_json"
        const val EXTRA_QUOTE_IDENTIFIER = "quote_identifier"
    }
}

@Composable
private fun QuoteEditorScreen(
    selectedText: String,
    initialNote: String,
    selectedColor: Int,
    editing: Boolean,
    navigateBack: () -> Unit,
    chooseColor: () -> Unit,
    save: (String) -> Unit,
    delete: () -> Unit
) {
    var note by rememberSaveable { mutableStateOf(initialNote) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MichisReaderScreenHeader(if (editing) "Editar cita" else "Nueva cita", navigateBack)
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Fragmento", style = MaterialTheme.typography.titleMedium)
            MichisReaderCard { Text(selectedText) }
            Text("Apariencia y nota", style = MaterialTheme.typography.titleMedium)
            MichisReaderCard {
                ColorPreview(selectedColor, chooseColor)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota opcional") },
                    minLines = 2,
                    shape = MichisReaderInputShape,
                    modifier = Modifier.fillMaxWidth()
                )
                MichisReaderButton(
                    text = if (editing) "Guardar cambios" else "Aplicar color y guardar cita",
                    onClick = { save(note) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (editing) {
                    Button(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("Eliminar cita") }
                }
            }
        }
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Eliminar cita") },
            text = { Text("La cita se eliminará de este libro y el cambio se sincronizará con Drive.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    delete()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ColorPreview(selectedColor: Int, chooseColor: () -> Unit) {
    val previewColor = Color(selectedColor).compositeOver(MaterialTheme.colorScheme.surfaceVariant)
    val textColor = if (previewColor.luminance() < 0.45f) Color.White else Color(0xFF151619)
    Surface(
        color = previewColor,
        contentColor = textColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = chooseColor)
    ) {
        Text("Color del resaltado", modifier = Modifier.padding(16.dp))
    }
}
