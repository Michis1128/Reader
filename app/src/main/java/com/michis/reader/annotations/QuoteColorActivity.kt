package com.michis.reader.annotations

import com.michis.reader.data.*
import com.michis.reader.databinding.ActivityQuoteColorBinding
import com.michis.reader.settings.ReaderSettingsRepository
import com.michis.reader.theme.*
import com.michis.reader.ui.ScreenHeader
import com.michis.reader.ui.SystemBarInsets

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity

class QuoteColorActivity : ComponentActivity() {
    private var selectedColor = Color.rgb(255, 213, 79)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = ReaderDatabase.getInstance(this)
        val quoteIdentifier = intent.getLongExtra(EXTRA_QUOTE_IDENTIFIER, -1)
        val existingQuote = database.annotations().firstOrNull {
            it.identifier == quoteIdentifier && it.kind == "cita"
        }
        selectedColor = existingQuote?.color ?: runCatching {
            val settings = ReaderSettingsRepository.get(this)
            Color.parseColor(settings.preferences.getString(
                ReaderSettingsRepository.KEY_QUOTE_DEFAULT_COLOR,
                ReaderSettingsRepository.DEFAULT_QUOTE_COLOR
            ))
        }.getOrDefault(Color.rgb(255, 213, 79))
        val selectedText = existingQuote?.selectedText ?: intent.getStringExtra(EXTRA_TEXT).orEmpty().trim()
        if (selectedText.isBlank()) { finish(); return }
        val binding = ActivityQuoteColorBinding.inflate(layoutInflater)
        AppThemePalette.markBackground(binding.rootContainer)
        ScreenHeader.configure(this, binding.screenHeader, if (existingQuote == null) "Nueva cita" else "Editar cita") { finish() }
        binding.selectedText.text = selectedText
        binding.noteInput.setText(existingQuote?.note.orEmpty())
        binding.saveQuoteButton.text = if (existingQuote == null) "Aplicar color y guardar cita" else "Guardar cambios"
        binding.deleteQuoteButton.apply {
            visibility = if (existingQuote == null) View.GONE else View.VISIBLE
            setOnClickListener {
                val quote = existingQuote ?: return@setOnClickListener
                AlertDialog.Builder(this@QuoteColorActivity)
                    .setTitle("Eliminar cita")
                    .setMessage("La cita se eliminará de este libro y el cambio se sincronizará con Drive.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Eliminar") { _, _ ->
                        database.deleteAnnotation(quote.identifier)
                        Toast.makeText(this@QuoteColorActivity, "Cita eliminada", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .show()
            }
        }
        updateColorPreview(binding)
        binding.colorPreview.setOnClickListener { showColorPicker(binding) }
        binding.saveQuoteButton.setOnClickListener {
            if (existingQuote == null) {
                database.addAnnotation(
                    intent.getLongExtra(EXTRA_DOCUMENT_IDENTIFIER, -1),
                    "cita",
                    selectedText,
                    binding.noteInput.text.toString(),
                    selectedColor,
                    intent.getIntExtra(EXTRA_LOCATION, 0),
                    intent.getIntExtra(EXTRA_PAGE_NUMBER, 0)
                )
            } else {
                database.updateQuote(existingQuote.identifier, binding.noteInput.text.toString(), selectedColor)
            }
            Toast.makeText(this, if (existingQuote == null) "Cita guardada" else "Cita actualizada", Toast.LENGTH_SHORT).show()
            finish()
        }
        SystemBarInsets.apply(binding.rootContainer)
        setContentView(binding.root)
        AppThemePalette.apply(this)
        if (existingQuote == null) showColorPicker(binding)
    }

    private fun showColorPicker(binding: ActivityQuoteColorBinding) {
        KvColorPickerOverlay.show(this, selectedColor) { color ->
            selectedColor = color
            updateColorPreview(binding)
        }
    }

    private fun updateColorPreview(binding: ActivityQuoteColorBinding) {
        binding.colorPreview.setBackgroundColor(selectedColor)
        binding.colorPreview.setTextColor(if (Color.luminance(selectedColor) < .45f) Color.WHITE else Color.BLACK)
    }

    companion object {
        const val EXTRA_DOCUMENT_IDENTIFIER = "document_identifier"
        const val EXTRA_TEXT = "selected_text"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_PAGE_NUMBER = "page_number"
        const val EXTRA_QUOTE_IDENTIFIER = "quote_identifier"
    }
}
