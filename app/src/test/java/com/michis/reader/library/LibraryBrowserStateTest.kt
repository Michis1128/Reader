package com.michis.reader.library

import com.michis.reader.data.ReaderDatabase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryBrowserStateTest {
    private lateinit var context: Context
    private lateinit var database: ReaderDatabase

    @Before
    fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("reader_library.db")
        context.getSharedPreferences("library_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        database = ReaderDatabase(context)
    }

    @After
    fun cleanUp() {
        database.close()
        context.deleteDatabase("reader_library.db")
    }

    @Test
    fun restoresFolderPathAndNavigatesBack() {
        database.saveLibraryFolder("authors", null, "Autores")
        database.saveLibraryFolder("author-1", "authors", "Autora")
        val document = database.saveDocument("content://books/book.epub", "book.epub", "author-1")
        val state = LibraryBrowserState(context, database)

        state.restoreLastDocumentFolder(document)

        assertEquals("author-1", state.currentFolderIdentifier)
        assertEquals("Mi biblioteca  ›  Autores  ›  Autora", state.pathLabel)
        assertTrue(state.navigateToParent())
        assertEquals("authors", state.currentFolderIdentifier)
        state.openRoot()
        assertFalse(state.canNavigateBack)
    }

    @Test
    fun displayModeCyclesAndPersists() {
        val state = LibraryBrowserState(context, database)
        assertEquals(1, state.cycleDisplayMode())
        assertEquals(1, LibraryBrowserState(context, database).displayMode)
    }
}
