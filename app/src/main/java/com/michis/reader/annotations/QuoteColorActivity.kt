package com.michis.reader.annotations

import com.michis.reader.data.*
import com.michis.reader.databinding.ActivityQuoteColorBinding
import com.michis.reader.settings.ReaderSettingsRepository
import com.michis.reader.theme.*
import com.michis.reader.ui.ScreenHeader

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
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
        applyInsets(binding.rootContainer)
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

    private fun applyInsets(view: View) {
        val left = view.paddingLeft; val top = view.paddingTop; val right = view.paddingRight; val bottom = view.paddingBottom
        view.setOnApplyWindowInsetsListener { target, insets ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                target.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            }; insets
        }
    }

    companion object {
        const val EXTRA_DOCUMENT_IDENTIFIER = "document_identifier"
        const val EXTRA_TEXT = "selected_text"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_PAGE_NUMBER = "page_number"
        const val EXTRA_QUOTE_IDENTIFIER = "quote_identifier"
    }
}
