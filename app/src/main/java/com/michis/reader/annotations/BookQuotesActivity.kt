package com.michis.reader.annotations

import com.michis.reader.data.*
import com.michis.reader.databinding.ActivityBookQuotesBinding
import com.michis.reader.databinding.ItemQuoteBinding
import com.michis.reader.reader.ReadiumEpubActivity
import com.michis.reader.theme.*
import com.michis.reader.ui.ScreenHeader

import android.content.Intent
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class BookQuotesActivity : ComponentActivity() {
    private lateinit var binding: ActivityBookQuotesBinding
    private lateinit var database: ReaderDatabase
    private var documentIdentifier = -1L
    private lateinit var content: LinearLayout
    private lateinit var selectionButton: Button
    private lateinit var deleteSelectionButton: Button
    private var selectionMode = false
    private val selectedQuoteIdentifiers = linkedSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); database = ReaderDatabase.getInstance(this)
        documentIdentifier = intent.getLongExtra(EXTRA_DOCUMENT_IDENTIFIER, -1)
        binding = ActivityBookQuotesBinding.inflate(layoutInflater)
        content = binding.contentContainer
        AppThemePalette.markBackground(binding.rootContainer)
        configureHeader()
        applyInsets(binding.rootContainer)
        setContentView(binding.root)
        render()
        AppThemePalette.apply(this)
    }

    private fun configureHeader() {
        ScreenHeader.configure(
            this,
            binding.screenHeader,
            "Citas · ${database.findDocument(documentIdentifier)?.title.orEmpty()}"
        ) { finish() }
        selectionButton = Button(this).apply {
            text = "Seleccionar"; isAllCaps = false; setOnClickListener {
                selectionMode = !selectionMode
                if (!selectionMode) selectedQuoteIdentifiers.clear()
                updateSelectionControls(); render()
            }
        }
        deleteSelectionButton = Button(this).apply {
            text = "Eliminar"; isAllCaps = false; visibility = View.GONE; setOnClickListener { confirmDeleteSelection() }
        }
        binding.screenHeader.actionContainer.addView(selectionButton)
        binding.screenHeader.actionContainer.addView(deleteSelectionButton)
    }

    private fun render() {
        content.removeAllViews()
        val quotes = database.annotations(documentIdentifier).filter { it.kind == "cita" }
        if (quotes.isEmpty()) content.addView(TextView(this).apply { text = "Este libro todavía no tiene citas."; gravity = Gravity.CENTER; setPadding(0, dp(60), 0, 0) })
        quotes.forEach { quote ->
            val binding = ItemQuoteBinding.inflate(layoutInflater, content, false)
            binding.selectionCheckbox.apply {
                visibility = if (selectionMode) View.VISIBLE else View.GONE
                isChecked = quote.identifier in selectedQuoteIdentifiers
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedQuoteIdentifiers += quote.identifier else selectedQuoteIdentifiers -= quote.identifier
                    updateSelectionControls()
                }
            }
            binding.quoteText.apply { text = quote.selectedText; setBackgroundColor(quote.color) }
            binding.pageText.text = "Página ${quote.pageNumber.coerceAtLeast(1)}"
            binding.actionContainer.visibility = if (selectionMode) View.GONE else View.VISIBLE
            binding.openButton.setOnClickListener { openQuote(quote) }
            binding.colorButton.setOnClickListener { editColor(quote) }
            binding.deleteButton.setOnClickListener { database.deleteAnnotation(quote.identifier); render() }
            binding.root.setOnClickListener {
                if (selectionMode) {
                    if (!selectedQuoteIdentifiers.add(quote.identifier)) selectedQuoteIdentifiers.remove(quote.identifier)
                    updateSelectionControls(); render()
                } else openQuote(quote)
            }
            AppThemePalette.markCard(binding.root)
            content.addView(binding.root)
        }
        updateSelectionControls()
        content.post { AppThemePalette.apply(this) }
    }

    private fun updateSelectionControls() {
        if (!::selectionButton.isInitialized) return
        selectionButton.text = if (selectionMode) "Cancelar" else "Seleccionar"
        deleteSelectionButton.visibility = if (selectionMode) View.VISIBLE else View.GONE
        deleteSelectionButton.text = "Eliminar (${selectedQuoteIdentifiers.size})"
        deleteSelectionButton.isEnabled = selectedQuoteIdentifiers.isNotEmpty()
    }

    private fun confirmDeleteSelection() {
        val count = selectedQuoteIdentifiers.size
        if (count == 0) return
        AlertDialog.Builder(this).setTitle("Eliminar citas")
            .setMessage("Se eliminarán $count cita${if (count == 1) "" else "s"}. Esta acción se sincronizará con Drive.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                selectedQuoteIdentifiers.forEach(database::deleteAnnotation)
                selectedQuoteIdentifiers.clear(); selectionMode = false; render()
            }.show()
    }

    private fun editColor(quote: SavedAnnotation) {
        KvColorPickerOverlay.show(this, quote.color) { selectedColor ->
            database.updateAnnotationColor(quote.identifier, selectedColor)
            render()
        }
    }

    private fun openQuote(quote: SavedAnnotation) {
        database.findDocument(documentIdentifier) ?: return
        startActivity(Intent(this, ReadiumEpubActivity::class.java).putExtra("document_identifier", documentIdentifier)
            .putExtra(EXTRA_QUOTE_LOCATION, quote.location).putExtra(EXTRA_QUOTE_PAGE, quote.pageNumber))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
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
        const val EXTRA_QUOTE_LOCATION = "quote_location"
        const val EXTRA_QUOTE_PAGE = "quote_page"
    }
}
