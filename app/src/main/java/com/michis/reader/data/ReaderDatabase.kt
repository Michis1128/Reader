package com.michis.reader.data

import com.michis.reader.annotations.AnnotationRepository
import com.michis.reader.dictionary.DictionaryRepository
import com.michis.reader.dictionary.DictionarySyncMerger
import com.michis.reader.library.LibraryDocumentRepository
import com.michis.reader.sync.SyncStateRepository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

data class LibraryDocument(
    val identifier: Long, val uri: String, val fileName: String, val title: String,
    val author: String, val format: String, val progress: Float, val lastOpenedAt: Long
)
data class LibraryFolder(val remoteIdentifier: String, val parentRemoteIdentifier: String?, val name: String)

data class SavedAnnotation(
    val identifier: Long, val documentIdentifier: Long, val kind: String,
    val selectedText: String, val note: String, val color: Int,
    val location: Int, val pageNumber: Int, val createdAt: Long, val orderPosition: Int,
    val locatorJson: String
)

data class DictionaryCategory(val identifier: Long, val documentIdentifier: Long, val name: String)
data class DictionaryEntry(
    val identifier: Long, val categoryIdentifier: Long, val documentIdentifier: Long,
    val term: String, val description: String, val context: String
)
data class SyncRecordMetadata(val syncIdentifier: String, val updatedAt: Long)
data class SyncDictionaryLink(
    val ownerDocumentIdentifier: Long,
    val linkedDocumentIdentifier: Long,
    val syncIdentifier: String,
    val updatedAt: Long
)
data class SyncTombstone(val entityType: String, val syncIdentifier: String, val documentSyncIdentifier: String, val deletedAt: Long)
data class ReadingMergeResult(val progressUpdates: Int, val insertedAnnotations: Int, val updatedAnnotations: Int, val ignoredLocalNewer: Int)
data class DictionaryMergeResult(
    val insertedCategories: Int,
    val insertedEntries: Int,
    val updatedEntries: Int,
    val mergedDuplicateTerms: Int,
    val insertedLinks: Int,
    val ignoredLocalNewer: Int
)
data class PendingSyncDeletion(val entityType: String, val syncIdentifier: String, val deletedAt: Long, val label: String)
data class DeletionMergeResult(val appliedDeletions: Int, val ignoredLocalNewer: Int, val alreadyAbsent: Int)
data class BookResetResult(val annotationsDeleted: Int, val dictionaryEntriesDeleted: Int, val dictionaryCategoriesDeleted: Int)

class ReaderDatabase(context: Context) : SQLiteOpenHelper(context, "reader_library.db", null, 10) {
    private val libraryDocuments by lazy { LibraryDocumentRepository(this) }
    private val annotationsRepository by lazy { AnnotationRepository(this) }
    private val dictionaries by lazy { DictionaryRepository(this) }
    private val synchronization by lazy { SyncStateRepository(this) }
    private val dictionarySynchronization by lazy { DictionarySyncMerger(this) }
    override fun onConfigure(database: SQLiteDatabase) {
        super.onConfigure(database)
        database.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL("""CREATE TABLE documents (
            identifier INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT NOT NULL UNIQUE,
            file_name TEXT NOT NULL, title TEXT NOT NULL, author TEXT NOT NULL DEFAULT '',
            format TEXT NOT NULL, progress REAL NOT NULL DEFAULT 0,
            reader_location INTEGER NOT NULL DEFAULT 0, last_opened_at INTEGER NOT NULL DEFAULT 0,
            added_at INTEGER NOT NULL, sync_id TEXT NOT NULL UNIQUE, updated_at INTEGER NOT NULL,
            library_folder_remote_id TEXT)""")
        createLibraryFoldersTable(database)
        database.execSQL("""CREATE TABLE annotations (
            identifier INTEGER PRIMARY KEY AUTOINCREMENT, document_identifier INTEGER NOT NULL,
            kind TEXT NOT NULL, selected_text TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '',
            color INTEGER NOT NULL DEFAULT 0, location INTEGER NOT NULL DEFAULT 0,
            page_number INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL, order_position INTEGER NOT NULL DEFAULT 0,
            locator_json TEXT NOT NULL DEFAULT '',
            sync_id TEXT NOT NULL UNIQUE, updated_at INTEGER NOT NULL,
            FOREIGN KEY(document_identifier) REFERENCES documents(identifier) ON DELETE CASCADE)""")
        database.execSQL("CREATE INDEX annotations_document_index ON annotations(document_identifier)")
        createDictionaryTables(database)
        createDictionaryLinksTable(database)
        createSyncTombstonesTable(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE annotations ADD COLUMN order_position INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE vocabulary ADD COLUMN order_position INTEGER NOT NULL DEFAULT 0")
            database.execSQL("UPDATE annotations SET order_position = identifier")
            database.execSQL("UPDATE vocabulary SET order_position = created_at")
        }
        if (oldVersion < 3) createDictionaryTables(database)
        if (oldVersion < 4) {
            database.execSQL("""DELETE FROM dictionary_entries WHERE identifier NOT IN (
                SELECT MIN(identifier) FROM dictionary_entries GROUP BY document_identifier, term COLLATE NOCASE)""")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS dictionary_entry_book_term_unique ON dictionary_entries(document_identifier, term COLLATE NOCASE)")
        }
        if (oldVersion < 5) database.execSQL("ALTER TABLE annotations ADD COLUMN page_number INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 6) createDictionaryLinksTable(database)
        if (oldVersion < 7) migrateToSyncMetadata(database)
        if (oldVersion < 8) {
            if (!hasColumn(database, "documents", "library_folder_remote_id"))
                database.execSQL("ALTER TABLE documents ADD COLUMN library_folder_remote_id TEXT")
            createLibraryFoldersTable(database)
        }
        if (oldVersion < 9) database.execSQL("DROP TABLE IF EXISTS vocabulary")
        if (oldVersion < 10 && hasTable(database, "annotations") &&
            !hasColumn(database, "annotations", "locator_json")
        ) {
            database.execSQL("ALTER TABLE annotations ADD COLUMN locator_json TEXT NOT NULL DEFAULT ''")
        }
    }

    private fun createLibraryFoldersTable(database: SQLiteDatabase) {
        database.execSQL("""CREATE TABLE IF NOT EXISTS library_folders (
            remote_id TEXT PRIMARY KEY, parent_remote_id TEXT, name TEXT NOT NULL,
            updated_at INTEGER NOT NULL)""")
        database.execSQL("CREATE INDEX IF NOT EXISTS library_folders_parent_index ON library_folders(parent_remote_id)")
    }

    private fun createDictionaryTables(database: SQLiteDatabase) {
        database.execSQL("""CREATE TABLE IF NOT EXISTS dictionary_categories (
            identifier INTEGER PRIMARY KEY AUTOINCREMENT, document_identifier INTEGER NOT NULL,
            name TEXT NOT NULL, order_position INTEGER NOT NULL DEFAULT 0,
            sync_id TEXT NOT NULL UNIQUE, updated_at INTEGER NOT NULL,
            UNIQUE(document_identifier, name),
            FOREIGN KEY(document_identifier) REFERENCES documents(identifier) ON DELETE CASCADE)""")
        database.execSQL("""CREATE TABLE IF NOT EXISTS dictionary_entries (
            identifier INTEGER PRIMARY KEY AUTOINCREMENT, category_identifier INTEGER NOT NULL,
            document_identifier INTEGER NOT NULL, term TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
            context TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, order_position INTEGER NOT NULL DEFAULT 0,
            sync_id TEXT NOT NULL UNIQUE, updated_at INTEGER NOT NULL,
            UNIQUE(category_identifier, term),
            FOREIGN KEY(category_identifier) REFERENCES dictionary_categories(identifier) ON DELETE CASCADE,
            FOREIGN KEY(document_identifier) REFERENCES documents(identifier) ON DELETE CASCADE)""")
        database.execSQL("CREATE INDEX IF NOT EXISTS dictionary_categories_document_index ON dictionary_categories(document_identifier)")
        database.execSQL("CREATE INDEX IF NOT EXISTS dictionary_entries_document_index ON dictionary_entries(document_identifier)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS dictionary_entry_book_term_unique ON dictionary_entries(document_identifier, term COLLATE NOCASE)")
    }

    private fun createDictionaryLinksTable(database: SQLiteDatabase) {
        database.execSQL("""CREATE TABLE IF NOT EXISTS dictionary_document_links (
            owner_document_identifier INTEGER NOT NULL, linked_document_identifier INTEGER NOT NULL,
            sync_id TEXT NOT NULL UNIQUE, updated_at INTEGER NOT NULL,
            PRIMARY KEY(owner_document_identifier, linked_document_identifier),
            FOREIGN KEY(owner_document_identifier) REFERENCES documents(identifier) ON DELETE CASCADE,
            FOREIGN KEY(linked_document_identifier) REFERENCES documents(identifier) ON DELETE CASCADE)""")
    }

    private fun createSyncTombstonesTable(database: SQLiteDatabase) {
        database.execSQL("""CREATE TABLE IF NOT EXISTS sync_tombstones (
            entity_type TEXT NOT NULL, sync_id TEXT NOT NULL, document_sync_id TEXT NOT NULL DEFAULT '',
            deleted_at INTEGER NOT NULL, PRIMARY KEY(entity_type, sync_id))""")
    }

    private fun migrateToSyncMetadata(database: SQLiteDatabase) {
        listOf("documents", "annotations", "dictionary_categories", "dictionary_entries", "dictionary_document_links").forEach { table ->
            if (!hasColumn(database, table, "sync_id")) database.execSQL("ALTER TABLE $table ADD COLUMN sync_id TEXT")
            if (!hasColumn(database, table, "updated_at")) database.execSQL("ALTER TABLE $table ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            database.execSQL("UPDATE $table SET sync_id = lower(hex(randomblob(16))) WHERE sync_id IS NULL OR sync_id = ''")
            database.execSQL("UPDATE $table SET updated_at = strftime('%s','now') * 1000 WHERE updated_at = 0")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS ${table}_sync_id_unique ON $table(sync_id)")
        }
        database.execSQL("UPDATE documents SET updated_at = MAX(added_at, last_opened_at)")
        database.execSQL("UPDATE annotations SET updated_at = created_at")
        createSyncTombstonesTable(database)
    }

    private fun hasColumn(database: SQLiteDatabase, table: String, column: String): Boolean =
        database.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            var found = false
            while (cursor.moveToNext()) if (cursor.getString(nameIndex) == column) { found = true; break }
            found
        }

    private fun hasTable(database: SQLiteDatabase, table: String): Boolean = database.rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
        arrayOf(table)
    ).use { cursor -> cursor.moveToFirst() }

    fun saveDocument(uri: String, fileName: String, libraryFolderRemoteIdentifier: String? = null) =
        libraryDocuments.saveDocument(uri, fileName, libraryFolderRemoteIdentifier)

    fun findDocuments(query: String = "") = libraryDocuments.findDocuments(query)

    fun findCurrentlyReadingDocuments(query: String = "") =
        libraryDocuments.findCurrentlyReadingDocuments(query)

    fun findDocumentsInFolder(folderRemoteIdentifier: String?, query: String = "") =
        libraryDocuments.findDocumentsInFolder(folderRemoteIdentifier, query)

    fun saveLibraryFolder(remoteIdentifier: String, parentRemoteIdentifier: String?, name: String) =
        libraryDocuments.saveLibraryFolder(remoteIdentifier, parentRemoteIdentifier, name)

    fun libraryFolders(parentRemoteIdentifier: String?) = libraryDocuments.libraryFolders(parentRemoteIdentifier)

    fun findDocument(identifier: Long) = libraryDocuments.findDocument(identifier)

    fun documentFolderRemoteIdentifier(identifier: Long) = libraryDocuments.documentFolderRemoteIdentifier(identifier)

    fun libraryFolderPath(remoteIdentifier: String?) = libraryDocuments.libraryFolderPath(remoteIdentifier)

    fun updateDocumentMetadata(identifier: Long, title: String, author: String) =
        libraryDocuments.updateDocumentMetadata(identifier, title, author)

    fun deleteDocument(identifier: Long): Int = synchronization.deleteEntity("documents", "document", identifier)

    fun resetBook(identifier: Long): BookResetResult {
        require(findDocument(identifier) != null) { "El libro ya no existe en la biblioteca" }
        val annotations = annotations(identifier)
        val entries = dictionaryEntriesForDocument(identifier)
        val categories = dictionaryCategories(identifier)
        val links = syncDictionaryLinks().filter {
            it.ownerDocumentIdentifier == identifier || it.linkedDocumentIdentifier == identifier
        }
        annotations.forEach { synchronization.deleteEntity("annotations", "annotation", it.identifier) }
        entries.forEach { synchronization.deleteEntity("dictionary_entries", "dictionary_entry", it.identifier) }
        categories.forEach { synchronization.deleteEntity("dictionary_categories", "dictionary_category", it.identifier) }
        links.forEach { synchronization.deleteDictionaryLink(it.ownerDocumentIdentifier, it.linkedDocumentIdentifier) }
        val now = System.currentTimeMillis()
        writableDatabase.update("documents", ContentValues().apply {
            put("progress", 0f)
            put("reader_location", 0)
            // Distingue un reinicio intencional de un EPUB recién importado.
            put("last_opened_at", RESET_LAST_OPENED_SENTINEL)
            put("updated_at", now)
        }, "identifier = ?", arrayOf(identifier.toString()))
        return BookResetResult(annotations.size, entries.size, categories.size)
    }

    fun readerLocation(identifier: Long) = libraryDocuments.readerLocation(identifier)

    fun updateProgress(identifier: Long, location: Int, progress: Float) =
        libraryDocuments.updateProgress(identifier, location, progress)

    fun markDocumentOpened(identifier: Long, openedAt: Long = System.currentTimeMillis()) =
        libraryDocuments.markDocumentOpened(identifier, openedAt)

    fun addAnnotation(
        documentIdentifier: Long,
        kind: String,
        text: String,
        note: String,
        color: Int,
        location: Int,
        pageNumber: Int = 0,
        locatorJson: String = ""
    ) = annotationsRepository.addAnnotation(
        documentIdentifier, kind, text, note, color, location, pageNumber, locatorJson
    )

    fun annotations(documentIdentifier: Long? = null) = annotationsRepository.annotations(documentIdentifier)

    fun annotationCount(documentIdentifier: Long, kind: String) = annotationsRepository.count(documentIdentifier, kind)

    fun deleteAnnotation(identifier: Long) = synchronization.deleteEntity("annotations", "annotation", identifier)
    fun updateQuote(identifier: Long, note: String, color: Int) = annotationsRepository.updateQuote(identifier, note, color)

    fun bookmarkAt(documentIdentifier: Long, location: Int) = annotationsRepository.bookmarkAt(documentIdentifier, location)

    fun moveAnnotation(identifier: Long, direction: Int) = annotationsRepository.moveAnnotation(identifier, direction)

    fun dictionaryCategories(documentIdentifier: Long) = dictionaries.categories(documentIdentifier)

    fun createDictionaryCategory(documentIdentifier: Long, name: String) =
        dictionaries.createCategory(documentIdentifier, name)

    fun saveDictionaryEntry(documentIdentifier: Long, categoryIdentifier: Long, term: String, description: String, context: String) =
        dictionaries.saveEntry(documentIdentifier, categoryIdentifier, term, description, context, DUPLICATE_DICTIONARY_ENTRY)

    fun dictionaryEntries(categoryIdentifier: Long) = dictionaries.entries(categoryIdentifier)

    fun dictionaryEntriesForDocument(documentIdentifier: Long) = dictionaries.entriesForDocument(documentIdentifier)

    fun effectiveDictionaryOwnerIdentifiers(documentIdentifier: Long) =
        dictionaries.effectiveOwnerIdentifiers(documentIdentifier)

    fun effectiveDictionaryEntries(documentIdentifier: Long) = dictionaries.effectiveEntries(documentIdentifier)

    fun hasEffectiveDictionaryEntries(documentIdentifier: Long) = dictionaries.hasEffectiveEntries(documentIdentifier)

    fun linkedDocuments(ownerDocumentIdentifier: Long) = dictionaries.linkedDocuments(ownerDocumentIdentifier)

    fun setDictionaryLinked(ownerDocumentIdentifier: Long, linkedDocumentIdentifier: Long, linked: Boolean) {
        if (ownerDocumentIdentifier == linkedDocumentIdentifier) return
        if (linked) dictionaries.insertLink(ownerDocumentIdentifier, linkedDocumentIdentifier)
        else synchronization.deleteDictionaryLink(ownerDocumentIdentifier, linkedDocumentIdentifier)
    }

    fun findDictionaryEntry(identifier: Long) = dictionaries.findEntry(identifier)

    fun updateDictionaryDescription(identifier: Long, description: String) =
        dictionaries.updateDescription(identifier, description)

    fun deleteDictionaryEntry(identifier: Long) = synchronization.deleteEntity("dictionary_entries", "dictionary_entry", identifier)

    fun deleteDictionaryCategory(identifier: Long) {
        dictionaries.entries(identifier).forEach { entry ->
            synchronization.deleteEntity("dictionary_entries", "dictionary_entry", entry.identifier)
        }
        synchronization.deleteEntity("dictionary_categories", "dictionary_category", identifier)
    }

    fun documentsWithDictionaries() = dictionaries.documentsWithDictionaries()

    fun documentSyncMetadata(identifier: Long) = synchronization.metadata("documents", identifier)
    fun annotationSyncMetadata(identifier: Long) = synchronization.metadata("annotations", identifier)
    fun dictionaryCategorySyncMetadata(identifier: Long) = synchronization.metadata("dictionary_categories", identifier)
    fun dictionaryEntrySyncMetadata(identifier: Long) = synchronization.metadata("dictionary_entries", identifier)

    fun documentIdentifierBySyncIdentifier(syncIdentifier: String) = synchronization.documentIdentifier(syncIdentifier)

    fun mergeReadingState(documents: List<Pair<Long, JSONObject>>) = synchronization.mergeReadingState(documents)

    fun mergeDictionaryState(
        documents: List<Pair<Long, JSONObject>>,
        localDocumentIdentifiersByKey: Map<String, Long>
    ) = dictionarySynchronization.mergeDictionaryState(documents, localDocumentIdentifiersByKey)
    fun syncDictionaryLinks() = synchronization.dictionaryLinks()

    fun syncTombstones() = synchronization.tombstones()

    fun previewSyncDeletions(remoteTombstones: List<SyncTombstone>) =
        synchronization.previewDeletions(remoteTombstones)

    fun applySyncDeletions(remoteTombstones: List<SyncTombstone>) =
        synchronization.applyDeletions(remoteTombstones)

    companion object {
        const val DUPLICATE_DICTIONARY_ENTRY = -2L
        const val RESET_LAST_OPENED_SENTINEL = -1L

        @Volatile
        private var sharedInstance: ReaderDatabase? = null

        fun getInstance(context: Context): ReaderDatabase = sharedInstance ?: synchronized(this) {
            sharedInstance ?: ReaderDatabase(context.applicationContext).also { sharedInstance = it }
        }
    }
}
