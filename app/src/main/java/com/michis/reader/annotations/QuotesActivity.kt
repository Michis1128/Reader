package com.michis.reader.annotations

import com.michis.reader.data.LibraryDocument
import com.michis.reader.data.ReaderDatabase
import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderCard
import com.michis.reader.ui.compose.MichisReaderScreenHeader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

class QuotesActivity : ComponentActivity() {
    private lateinit var database: ReaderDatabase
    private var books by mutableStateOf(emptyList<QuotesBookItem>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ReaderDatabase.getInstance(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MichisReaderComposeTheme {
                QuotesScreen(books, ::finish, ::openQuotes)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) {
            books = database.annotations().filter { it.kind == "cita" }
                .groupBy { it.documentIdentifier }
                .mapNotNull { (identifier, quotes) ->
                    database.findDocument(identifier)?.let { QuotesBookItem(it, quotes.size) }
                }
        }
    }

    private fun openQuotes(document: LibraryDocument) {
        startActivity(Intent(this, BookQuotesActivity::class.java)
            .putExtra(BookQuotesActivity.EXTRA_DOCUMENT_IDENTIFIER, document.identifier))
    }
}

private data class QuotesBookItem(val document: LibraryDocument, val count: Int)

@Composable
private fun QuotesScreen(books: List<QuotesBookItem>, navigateBack: () -> Unit, open: (LibraryDocument) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { MichisReaderScreenHeader("Citas", navigateBack) }
    ) { padding ->
        if (books.isEmpty()) {
            Text("Las citas y notas de todos tus libros aparecerán aquí.", Modifier.fillMaxSize().padding(padding).padding(24.dp))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(books, key = { it.document.identifier }) { book ->
                    MichisReaderCard(Modifier.clickable { open(book.document) }) {
                        Text(book.document.title, style = MaterialTheme.typography.titleMedium)
                        Text("${book.count} cita${if (book.count == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
