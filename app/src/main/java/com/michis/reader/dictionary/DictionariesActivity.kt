package com.michis.reader.dictionary

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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** Punto de entrada global a los diccionarios, agrupados por libro. */
class DictionariesActivity : ComponentActivity() {
    private lateinit var database: ReaderDatabase
    private var dictionaryBooks by mutableStateOf(emptyList<DictionaryBookItem>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ReaderDatabase.getInstance(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MichisReaderComposeTheme {
                DictionariesScreen(
                    books = dictionaryBooks,
                    navigateBack = ::finish,
                    openDictionary = ::openDictionary
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) {
            dictionaryBooks = database.documentsWithDictionaries().map { document ->
                DictionaryBookItem(document, database.dictionaryCategories(document.identifier).size)
            }
        }
    }

    private fun openDictionary(document: LibraryDocument) {
        startActivity(
            Intent(this, DictionaryActivity::class.java)
                .putExtra(DictionaryActivity.EXTRA_DOCUMENT_IDENTIFIER, document.identifier)
        )
    }
}

private data class DictionaryBookItem(
    val document: LibraryDocument,
    val categoryCount: Int
)

@Composable
private fun DictionariesScreen(
    books: List<DictionaryBookItem>,
    navigateBack: () -> Unit,
    openDictionary: (LibraryDocument) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { MichisReaderScreenHeader("Diccionarios", navigateBack) }
    ) { contentPadding ->
        if (books.isEmpty()) {
            Text(
                text = "Los libros que tengan diccionario aparecerán aquí.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 24.dp, vertical = 48.dp)
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
                items(books, key = { it.document.identifier }) { book ->
                    MichisReaderCard(
                        modifier = Modifier.clickable { openDictionary(book.document) }
                    ) {
                        Text(book.document.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${book.categoryCount} subcategoría${if (book.categoryCount == 1) "" else "s"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
