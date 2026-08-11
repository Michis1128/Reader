package com.michis.reader.integration

import com.michis.reader.app.ReaderResumeState
import com.michis.reader.data.ReaderDatabase
import com.michis.reader.library.LibraryImportCoordinator
import com.michis.reader.sync.LibrarySyncSnapshotBuilder

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
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
class LibraryFlowsTest {
    private lateinit var context: Context
    private lateinit var database: ReaderDatabase

    @Before
    fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        context.getSharedPreferences("reader_resume_state", Context.MODE_PRIVATE).edit().clear().commit()
        database = ReaderDatabase(context)
    }

    @After
    fun cleanUp() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun snapshotContainsReadingAndDictionaryStateUsingMetadataFallback() {
        val documentIdentifier = database.saveDocument(
            "content://missing.provider/library/book.epub", "book.epub"
        )
        database.updateProgress(documentIdentifier, 25, 0.5f)
        val locatorJson = """{"href":"chapter.xhtml","type":"application/xhtml+xml"}"""
        database.addAnnotation(documentIdentifier, "cita", "Fragmento", "Nota", 123, 25, 4, locatorJson)
        val category = database.createDictionaryCategory(documentIdentifier, "Lugares")
        database.saveDictionaryEntry(documentIdentifier, category, "Ávalon", "Isla", "")

        val snapshot = LibrarySyncSnapshotBuilder(context, database).build()
        val root = JSONObject(snapshot.bytes.toString(Charsets.UTF_8))
        val document = root.getJSONArray("documents").getJSONObject(0)

        assertEquals(1, snapshot.documentCount)
        assertEquals(3, snapshot.itemCount)
        assertEquals("sha256-metadata-fallback", document.getString("documentKeyType"))
        assertEquals(25, document.getInt("readerLocation"))
        assertEquals(1, document.getJSONArray("annotations").length())
        assertEquals(locatorJson, document.getJSONArray("annotations").getJSONObject(0).getString("locatorJson"))
        assertEquals(1, document.getJSONArray("dictionaryCategories").length())
    }

    @Test
    fun resetProducesTombstonesIncludedInNextSnapshot() {
        val documentIdentifier = database.saveDocument("content://missing.provider/reset.epub", "reset.epub")
        database.addAnnotation(documentIdentifier, "marcador", "", "", 0, 8, 2)
        val category = database.createDictionaryCategory(documentIdentifier, "Personajes")
        database.saveDictionaryEntry(documentIdentifier, category, "Alicia", "Protagonista", "")

        database.resetBook(documentIdentifier)
        val root = JSONObject(LibrarySyncSnapshotBuilder(context, database).build().bytes.toString(Charsets.UTF_8))

        assertTrue(root.getJSONArray("tombstones").length() >= 3)
        assertEquals(0.0, root.getJSONArray("documents").getJSONObject(0).getDouble("progress"), 0.0)
    }

    @Test
    fun resumeStateOnlyReopensReaderWhileItWasActive() {
        assertFalse(ReaderResumeState.shouldResumeReader(context))
        ReaderResumeState.markReaderActive(context, 42)
        assertTrue(ReaderResumeState.shouldResumeReader(context))
        assertEquals(42, ReaderResumeState.lastDocumentIdentifier(context))
        ReaderResumeState.markReaderExited(context)
        assertFalse(ReaderResumeState.shouldResumeReader(context))
        assertEquals(42, ReaderResumeState.lastDocumentIdentifier(context))
    }

    @Test
    fun incomingSafDocumentIsSavedRefreshedAndOpened() {
        var refreshCount = 0
        var openedIdentifier = -1L
        val coordinator = LibraryImportCoordinator(
            context.contentResolver,
            database,
            { refreshCount++ },
            { openedIdentifier = it },
            {}
        )

        coordinator.importIncoming(Intent(Intent.ACTION_VIEW, Uri.parse("content://missing.provider/import.epub")))

        assertEquals(1, refreshCount)
        assertTrue(openedIdentifier > 0)
        assertEquals("import", database.findDocument(openedIdentifier)?.title)
    }

    @Test
    fun incomingUnsupportedDocumentIsRejected() {
        var refreshCount = 0
        var openedIdentifier = -1L
        var shownMessage = ""
        val coordinator = LibraryImportCoordinator(
            context.contentResolver,
            database,
            { refreshCount++ },
            { openedIdentifier = it },
            { shownMessage = it }
        )

        coordinator.importIncoming(
            Intent(Intent.ACTION_VIEW, Uri.parse("content://missing.provider/import.unsupported"))
        )

        assertEquals(1, refreshCount)
        assertEquals(-1L, openedIdentifier)
        assertTrue(database.findDocuments().isEmpty())
        assertTrue(shownMessage.contains("EPUB"))
    }

    private companion object {
        const val DATABASE_NAME = "reader_library.db"
    }
}
