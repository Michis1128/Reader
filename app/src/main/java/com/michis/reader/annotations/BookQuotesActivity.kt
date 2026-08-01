package com.michis.reader.annotations

import com.michis.reader.data.*
import com.michis.reader.databinding.ActivityBookQuotesBinding
import com.michis.reader.databinding.ItemQuoteBinding
import com.michis.reader.databinding.ViewEmptyStateBinding
import com.michis.reader.reader.ReadiumEpubActivity
import com.michis.reader.theme.*
import com.michis.reader.ui.ScreenHeader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import androidx.activity.ComponentActivity

class BookQuotesActivity : ComponentActivity() {
    private lateinit var binding: ActivityBookQuotesBinding
    private lateinit var database: ReaderDatabase
    private var documentIdentifier = -1L
    private lateinit var content: LinearLayout

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
    }

    private fun render() {
        content.removeAllViews()
        val quotes = database.annotations(documentIdentifier).filter { it.kind == "cita" }
        if (quotes.isEmpty()) {
            val emptyBinding = ViewEmptyStateBinding.inflate(layoutInflater, content, false)
            emptyBinding.root.text = "Este libro todavía no tiene citas."
            content.addView(emptyBinding.root)
        }
        quotes.forEach { quote ->
            val binding = ItemQuoteBinding.inflate(layoutInflater, content, false)
            binding.selectionCheckbox.visibility = View.GONE
            binding.quoteText.apply { text = quote.selectedText; setBackgroundColor(quote.color) }
            binding.pageText.text = "Página ${quote.pageNumber.coerceAtLeast(1)}"
            binding.actionContainer.visibility = View.VISIBLE
            binding.openButton.setOnClickListener { openQuote(quote) }
            binding.colorButton.setOnClickListener { editQuote(quote) }
            binding.root.setOnClickListener { editQuote(quote) }
            AppThemePalette.markCard(binding.root)
            content.addView(binding.root)
        }
        content.post { AppThemePalette.apply(this) }
    }

    private fun editQuote(quote: SavedAnnotation) {
        startActivity(Intent(this, QuoteColorActivity::class.java)
            .putExtra(QuoteColorActivity.EXTRA_QUOTE_IDENTIFIER, quote.identifier))
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) render()
    }

    private fun openQuote(quote: SavedAnnotation) {
        database.findDocument(documentIdentifier) ?: return
        startActivity(Intent(this, ReadiumEpubActivity::class.java).putExtra("document_identifier", documentIdentifier)
            .putExtra(EXTRA_QUOTE_LOCATION, quote.location).putExtra(EXTRA_QUOTE_PAGE, quote.pageNumber))
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
        const val EXTRA_QUOTE_LOCATION = "quote_location"
        const val EXTRA_QUOTE_PAGE = "quote_page"
    }
}
