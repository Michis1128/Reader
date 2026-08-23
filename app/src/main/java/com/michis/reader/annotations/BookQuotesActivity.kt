package com.michis.reader.annotations

import com.michis.reader.data.LibraryDocument
import com.michis.reader.data.ReaderDatabase
import com.michis.reader.data.SavedAnnotation
import com.michis.reader.reader.ReadiumEpubActivity
import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderButton
import com.michis.reader.ui.compose.MichisReaderButtonRow
import com.michis.reader.ui.compose.MichisReaderCard
import com.michis.reader.ui.compose.MichisReaderScreenHeader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

class BookQuotesActivity : ComponentActivity() {
    private lateinit var database: ReaderDatabase
    private var documentIdentifier = -1L
    private var quotes by mutableStateOf(emptyList<SavedAnnotation>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ReaderDatabase.getInstance(this)
        documentIdentifier = intent.getLongExtra(EXTRA_DOCUMENT_IDENTIFIER, -1)
        val documentTitle = database.findDocument(documentIdentifier)?.title.orEmpty()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MichisReaderComposeTheme {
                BookQuotesScreen(
                    title = "Citas · $documentTitle",
                    quotes = quotes,
                    navigateBack = ::finish,
                    editQuote = ::editQuote,
                    openQuote = ::openQuote
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) {
            quotes = database.annotations(documentIdentifier).filter { it.kind == "cita" }
        }
    }

    private fun editQuote(quote: SavedAnnotation) {
        startActivity(Intent(this, QuoteColorActivity::class.java)
            .putExtra(QuoteColorActivity.EXTRA_QUOTE_IDENTIFIER, quote.identifier))
    }

    private fun openQuote(quote: SavedAnnotation) {
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_READER, false)) {
            setResult(RESULT_OK, Intent()
                .putExtra(EXTRA_QUOTE_LOCATION, quote.location)
                .putExtra(EXTRA_QUOTE_PAGE, quote.pageNumber))
            finish()
            return
        }
        val document: LibraryDocument = database.findDocument(documentIdentifier) ?: return
        startActivity(Intent(this, ReadiumEpubActivity::class.java)
            .putExtra("document_identifier", document.identifier)
            .putExtra(EXTRA_QUOTE_LOCATION, quote.location)
            .putExtra(EXTRA_QUOTE_PAGE, quote.pageNumber))
    }

    companion object {
        const val EXTRA_DOCUMENT_IDENTIFIER = "document_identifier"
        const val EXTRA_QUOTE_LOCATION = "quote_location"
        const val EXTRA_QUOTE_PAGE = "quote_page"
        const val EXTRA_RETURN_TO_READER = "return_to_reader"
    }
}

@Composable
private fun BookQuotesScreen(
    title: String,
    quotes: List<SavedAnnotation>,
    navigateBack: () -> Unit,
    editQuote: (SavedAnnotation) -> Unit,
    openQuote: (SavedAnnotation) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { MichisReaderScreenHeader(title, navigateBack) }
    ) { contentPadding ->
        if (quotes.isEmpty()) {
            Text(
                "Este libro todavía no tiene citas.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(quotes, key = SavedAnnotation::identifier) { quote ->
                    QuoteCard(quote, editQuote, openQuote)
                }
            }
        }
    }
}

@Composable
private fun QuoteCard(
    quote: SavedAnnotation,
    editQuote: (SavedAnnotation) -> Unit,
    openQuote: (SavedAnnotation) -> Unit
) {
    val highlightColor = Color(quote.color).compositeOver(MaterialTheme.colorScheme.surfaceVariant)
    val highlightTextColor = if (highlightColor.luminance() < 0.45f) Color.White else Color(0xFF151619)
    MichisReaderCard {
        androidx.compose.material3.Surface(
            color = highlightColor,
            contentColor = highlightTextColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(quote.selectedText, modifier = Modifier.padding(12.dp))
        }
        Text("Página ${quote.pageNumber.coerceAtLeast(1)}")
        if (quote.note.isNotBlank()) {
            Text(quote.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MichisReaderButtonRow {
            MichisReaderButton("Abrir", { openQuote(quote) }, Modifier.weight(1f))
            MichisReaderButton("Editar", { editQuote(quote) }, Modifier.weight(1f))
        }
    }
}
