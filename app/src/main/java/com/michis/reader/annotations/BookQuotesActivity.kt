package com.michis.reader.annotations

import com.michis.reader.R
import com.michis.reader.data.*
import com.michis.reader.reader.ReadiumEpubActivity
import com.michis.reader.theme.*
import com.michis.reader.ui.ScreenHeader

import android.content.Intent
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class BookQuotesActivity : ComponentActivity() {
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
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(18)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; AppThemePalette.markBackground(this)
            addView(fixedHeader())
            addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
            applyInsets(this)
        }
        setContentView(root); render(); AppThemePalette.apply(this)
    }

    private fun fixedHeader(): View {
        val header = ScreenHeader.create(
            this,
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
        header.actionContainer.addView(selectionButton)
        header.actionContainer.addView(deleteSelectionButton)
        return header.root
    }

    private fun render() {
        content.removeAllViews()
        val quotes = database.annotations(documentIdentifier).filter { it.kind == "cita" }
        if (quotes.isEmpty()) content.addView(TextView(this).apply { text = "Este libro todavía no tiene citas."; gravity = Gravity.CENTER; setPadding(0, dp(60), 0, 0) })
        quotes.forEach { quote -> content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(14), dp(15), dp(12)); setBackgroundResource(R.drawable.rounded_panel)
            if (selectionMode) addView(CheckBox(context).apply {
                text = "Seleccionar cita"; isChecked = quote.identifier in selectedQuoteIdentifiers
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedQuoteIdentifiers += quote.identifier else selectedQuoteIdentifiers -= quote.identifier
                    updateSelectionControls()
                }
            })
            addView(TextView(context).apply { text = quote.selectedText; textSize = 17f; setBackgroundColor(quote.color) })
            addView(TextView(context).apply { text = "Página ${quote.pageNumber.coerceAtLeast(1)}"; setTextColor(Color.DKGRAY) })
            if (!selectionMode) addView(LinearLayout(context).apply {
                addView(Button(context).apply { text = "Abrir"; isAllCaps = false; setOnClickListener { openQuote(quote) } })
                addView(Button(context).apply { text = "Color"; isAllCaps = false; setOnClickListener { editColor(quote) } })
                addView(Button(context).apply { text = "Eliminar"; isAllCaps = false; setOnClickListener { database.deleteAnnotation(quote.identifier); render() } })
            })
            setOnClickListener {
                if (selectionMode) {
                    if (!selectedQuoteIdentifiers.add(quote.identifier)) selectedQuoteIdentifiers.remove(quote.identifier)
                    updateSelectionControls(); render()
                } else openQuote(quote)
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }) }
        updateSelectionControls()
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
