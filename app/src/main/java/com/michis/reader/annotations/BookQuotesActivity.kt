package com.michis.reader.annotations

import com.michis.reader.R
import com.michis.reader.data.*
import com.michis.reader.reader.ReadiumEpubActivity
import com.michis.reader.theme.*

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

    private fun fixedHeader() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(5), dp(18), dp(5)); elevation = dp(5).toFloat()
        AppThemePalette.markSurface(this)
        addView(Button(context).apply { text = "‹"; contentDescription = "Regresar"; setOnClickListener { finish() } })
        addView(TextView(context).apply {
            text = "Citas · ${database.findDocument(documentIdentifier)?.title.orEmpty()}"; textSize = 23f; maxLines = 2
        }, LinearLayout.LayoutParams(0, dp(62), 1f))
        selectionButton = Button(context).apply {
            text = "Seleccionar"; isAllCaps = false; setOnClickListener {
                selectionMode = !selectionMode
                if (!selectionMode) selectedQuoteIdentifiers.clear()
                updateSelectionControls(); render()
            }
        }
        addView(selectionButton)
        deleteSelectionButton = Button(context).apply {
            text = "Eliminar"; isAllCaps = false; visibility = View.GONE; setOnClickListener { confirmDeleteSelection() }
        }
        addView(deleteSelectionButton)
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
