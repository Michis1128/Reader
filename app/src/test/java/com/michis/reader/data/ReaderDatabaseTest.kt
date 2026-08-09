package com.michis.reader.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: ReaderDatabase

    @Before
    fun prepareEmptyDatabase() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        database = ReaderDatabase(context)
    }

    @After
    fun closeDatabase() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun resetBookClearsReadingDataButPreservesDocument() {
        val documentIdentifier = database.saveDocument("content://books/reset.epub", "reset.epub")
        val linkedDocumentIdentifier = database.saveDocument("content://books/linked.epub", "linked.epub")
        database.updateProgress(documentIdentifier, location = 420, progress = 0.65f)
        database.addAnnotation(documentIdentifier, "cita", "Fragmento", "Nota", 0xFFFFCC00.toInt(), 420, 12)
        database.addAnnotation(documentIdentifier, "marcador", "", "", 0xFF795548.toInt(), 420, 12)
        val categoryIdentifier = database.createDictionaryCategory(documentIdentifier, "Personajes")
        database.saveDictionaryEntry(documentIdentifier, categoryIdentifier, "Alicia", "Protagonista", "")
        database.setDictionaryLinked(documentIdentifier, linkedDocumentIdentifier, true)

        val result = database.resetBook(documentIdentifier)

        assertEquals(2, result.annotationsDeleted)
        assertEquals(1, result.dictionaryEntriesDeleted)
        assertEquals(1, result.dictionaryCategoriesDeleted)
        assertNotNull(database.findDocument(documentIdentifier))
        assertEquals(0, database.readerLocation(documentIdentifier))
        val restoredDocument = requireNotNull(database.findDocument(documentIdentifier))
        assertEquals(0f, restoredDocument.progress, 0f)
        assertEquals(-1L, restoredDocument.lastOpenedAt)
        assertTrue(database.annotations(documentIdentifier).isEmpty())
        assertTrue(database.dictionaryCategories(documentIdentifier).isEmpty())
        assertTrue(database.dictionaryEntriesForDocument(documentIdentifier).isEmpty())
        assertTrue(database.linkedDocuments(documentIdentifier).isEmpty())
        assertTrue(database.syncTombstones().isNotEmpty())
    }

    @Test
    fun dictionaryRejectsCaseInsensitiveDuplicatesAcrossCategories() {
        val documentIdentifier = database.saveDocument("content://books/dictionary.epub", "dictionary.epub")
        val peopleCategory = database.createDictionaryCategory(documentIdentifier, "Personajes")
        val placesCategory = database.createDictionaryCategory(documentIdentifier, "Lugares")

        val firstEntry = database.saveDictionaryEntry(
            documentIdentifier, peopleCategory, "CeSsRt", "Personaje", ""
        )
        val duplicateEntry = database.saveDictionaryEntry(
            documentIdentifier, placesCategory, "CESSRT", "Lugar", ""
        )

        assertTrue(firstEntry > 0)
        assertEquals(ReaderDatabase.DUPLICATE_DICTIONARY_ENTRY, duplicateEntry)
        assertEquals(1, database.dictionaryEntriesForDocument(documentIdentifier).size)
    }

    @Test
    fun deletingDictionaryCategoryAlsoDeletesItsEntries() {
        val documentIdentifier = database.saveDocument("content://books/delete-dictionary.epub", "delete-dictionary.epub")
        val categoryIdentifier = database.createDictionaryCategory(documentIdentifier, "Personajes")
        database.saveDictionaryEntry(documentIdentifier, categoryIdentifier, "Alicia", "Protagonista", "")

        database.deleteDictionaryCategory(categoryIdentifier)

        assertTrue(database.dictionaryCategories(documentIdentifier).isEmpty())
        assertTrue(database.dictionaryEntriesForDocument(documentIdentifier).isEmpty())
        assertTrue(database.syncTombstones().count { it.entityType in setOf("dictionary_category", "dictionary_entry") } >= 2)
    }

    @Test
    fun remoteDeletionDoesNotOverwriteNewerLocalAnnotation() {
        val documentIdentifier = database.saveDocument("content://books/sync.epub", "sync.epub")
        database.addAnnotation(documentIdentifier, "cita", "Texto local", "", 0, 10, 1)
        val annotation = database.annotations(documentIdentifier).single()
        val metadata = database.annotationSyncMetadata(annotation.identifier)
        val olderRemoteDeletion = SyncTombstone(
            entityType = "annotation",
            syncIdentifier = metadata.syncIdentifier,
            documentSyncIdentifier = database.documentSyncMetadata(documentIdentifier).syncIdentifier,
            deletedAt = metadata.updatedAt - 1
        )

        assertTrue(database.previewSyncDeletions(listOf(olderRemoteDeletion)).isEmpty())
        val result = database.applySyncDeletions(listOf(olderRemoteDeletion))

        assertEquals(0, result.appliedDeletions)
        assertEquals(1, result.ignoredLocalNewer)
        assertEquals(1, database.annotations(documentIdentifier).size)
    }

    @Test
    fun restoredInstallationAcceptsRemoteProgressEvenWhenImportedBookHasNewerMetadata() {
        val documentIdentifier = database.saveDocument("content://books/restored.epub", "restored.epub")
        val localMetadata = database.documentSyncMetadata(documentIdentifier)
        val remoteState = JSONObject().apply {
            put("updatedAt", localMetadata.updatedAt - 10_000L)
            put("progress", 0.62)
            put("readerLocation", 318)
            put("lastOpenedAt", localMetadata.updatedAt - 20_000L)
            put("annotations", JSONArray())
        }

        val result = database.mergeReadingState(listOf(documentIdentifier to remoteState))

        assertEquals(1, result.progressUpdates)
        assertEquals(318, database.readerLocation(documentIdentifier))
        val restoredDocument = requireNotNull(database.findDocument(documentIdentifier))
        assertEquals(0.62f, restoredDocument.progress, 0.001f)
        assertEquals(localMetadata.updatedAt - 20_000L, restoredDocument.lastOpenedAt)
    }

    @Test
    fun currentlyReadingOnlyContainsOpenedBooksOrderedByMostRecent() {
        val unopenedIdentifier = database.saveDocument("content://books/unopened.epub", "unopened.epub")
        val olderIdentifier = database.saveDocument("content://books/older.epub", "older.epub")
        val newestIdentifier = database.saveDocument("content://books/newest.epub", "newest.epub")
        database.markDocumentOpened(olderIdentifier, 1_000L)
        database.markDocumentOpened(newestIdentifier, 2_000L)

        val currentlyReading = database.findCurrentlyReadingDocuments()

        assertEquals(listOf(newestIdentifier, olderIdentifier), currentlyReading.map { it.identifier })
        assertTrue(currentlyReading.none { it.identifier == unopenedIdentifier })
    }

    @Test
    fun restoredInstallationAcceptsRemoteOpeningWithoutProgress() {
        val documentIdentifier = database.saveDocument("content://books/opened-only.epub", "opened-only.epub")
        val localMetadata = database.documentSyncMetadata(documentIdentifier)
        val remoteLastOpenedAt = localMetadata.updatedAt - 20_000L
        val remoteState = JSONObject().apply {
            put("updatedAt", localMetadata.updatedAt - 10_000L)
            put("progress", 0.0)
            put("readerLocation", 0)
            put("lastOpenedAt", remoteLastOpenedAt)
            put("annotations", JSONArray())
        }

        database.mergeReadingState(listOf(documentIdentifier to remoteState))

        assertEquals(remoteLastOpenedAt, requireNotNull(database.findDocument(documentIdentifier)).lastOpenedAt)
        assertEquals(listOf(documentIdentifier), database.findCurrentlyReadingDocuments().map { it.identifier })
    }

    @Test
    fun remoteDictionaryMergeCreatesEntriesAndSharedLinks() {
        val ownerIdentifier = database.saveDocument("content://books/owner.epub", "owner.epub")
        val linkedIdentifier = database.saveDocument("content://books/linked-dictionary.epub", "linked-dictionary.epub")
        val remoteDocument = JSONObject().apply {
            put("dictionaryCategories", JSONArray().put(JSONObject().apply {
                put("syncId", "remote-category")
                put("name", "Lugares")
                put("updatedAt", 10L)
                put("entries", JSONArray().put(JSONObject().apply {
                    put("syncId", "remote-entry")
                    put("term", "Ávalon")
                    put("description", "Isla legendaria")
                    put("updatedAt", 10L)
                }))
            }))
            put("dictionaryLinks", JSONArray().put(JSONObject().apply {
                put("syncId", "remote-link")
                put("linkedDocumentKey", "linked-book")
                put("updatedAt", 10L)
            }))
        }

        val result = database.mergeDictionaryState(
            listOf(ownerIdentifier to remoteDocument),
            mapOf("linked-book" to linkedIdentifier)
        )

        assertEquals(1, result.insertedCategories)
        assertEquals(1, result.insertedEntries)
        assertEquals(1, result.insertedLinks)
        assertEquals(setOf(linkedIdentifier), database.linkedDocuments(ownerIdentifier))
        assertEquals("Ávalon", database.effectiveDictionaryEntries(linkedIdentifier).single().term)
    }

    private companion object {
        const val DATABASE_NAME = "reader_library.db"
    }
}
