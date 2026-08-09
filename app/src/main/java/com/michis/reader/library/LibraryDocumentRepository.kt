package com.michis.reader.library

import com.michis.reader.data.*

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/** Acceso local a documentos, carpetas de biblioteca y progreso de lectura. */
internal class LibraryDocumentRepository(private val database: SQLiteOpenHelper) {
    fun saveDocument(uri: String, fileName: String, libraryFolderRemoteIdentifier: String?): Long {
        if (!fileName.endsWith(".epub", ignoreCase = true)) return -1
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("uri", uri)
            put("file_name", fileName)
            put("title", fileName.substringBeforeLast('.'))
            put("format", "EPUB")
            put("added_at", now)
            put("sync_id", UUID.randomUUID().toString())
            put("updated_at", now)
            put("library_folder_remote_id", libraryFolderRemoteIdentifier)
        }
        database.writableDatabase.insertWithOnConflict("documents", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        if (libraryFolderRemoteIdentifier != null) {
            database.writableDatabase.update(
                "documents",
                ContentValues().apply { put("library_folder_remote_id", libraryFolderRemoteIdentifier) },
                "uri = ?",
                arrayOf(uri)
            )
        }
        return database.readableDatabase.rawQuery(
            "SELECT identifier FROM documents WHERE uri = ?",
            arrayOf(uri)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1 }
    }

    fun findDocuments(query: String): List<LibraryDocument> {
        val search = "%${query.trim()}%"
        return database.readableDatabase.rawQuery(
            "SELECT identifier, uri, file_name, title, author, format, progress, last_opened_at FROM documents " +
                "WHERE format = 'EPUB' AND (title LIKE ? OR author LIKE ? OR format LIKE ? OR file_name LIKE ?) " +
                "ORDER BY last_opened_at DESC, title COLLATE NOCASE",
            arrayOf(search, search, search, search)
        ).use(::readDocuments)
    }

    fun findCurrentlyReadingDocuments(query: String): List<LibraryDocument> {
        val search = "%${query.trim()}%"
        return database.readableDatabase.rawQuery(
            "SELECT identifier, uri, file_name, title, author, format, progress, last_opened_at FROM documents " +
                "WHERE format = 'EPUB' AND last_opened_at > 0 " +
                "AND (title LIKE ? OR author LIKE ? OR file_name LIKE ?) " +
                "ORDER BY last_opened_at DESC, title COLLATE NOCASE",
            arrayOf(search, search, search)
        ).use(::readDocuments)
    }

    fun findDocumentsInFolder(folderRemoteIdentifier: String?, query: String): List<LibraryDocument> {
        if (query.isNotBlank()) return findDocuments(query)
        val condition = if (folderRemoteIdentifier == null) {
            "library_folder_remote_id IS NULL"
        } else {
            "library_folder_remote_id = ?"
        }
        return database.readableDatabase.rawQuery(
            "SELECT identifier, uri, file_name, title, author, format, progress, last_opened_at " +
                "FROM documents WHERE format = 'EPUB' AND $condition ORDER BY last_opened_at DESC, title COLLATE NOCASE",
            folderRemoteIdentifier?.let { arrayOf(it) }
        ).use(::readDocuments)
    }

    fun findDocument(identifier: Long): LibraryDocument? = database.readableDatabase.rawQuery(
        "SELECT identifier, uri, file_name, title, author, format, progress, last_opened_at " +
            "FROM documents WHERE identifier = ? AND format = 'EPUB'",
        arrayOf(identifier.toString())
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toLibraryDocument() else null }

    fun saveLibraryFolder(remoteIdentifier: String, parentRemoteIdentifier: String?, name: String) {
        database.writableDatabase.insertWithOnConflict(
            "library_folders",
            null,
            ContentValues().apply {
                put("remote_id", remoteIdentifier)
                put("parent_remote_id", parentRemoteIdentifier)
                put("name", name)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun libraryFolders(parentRemoteIdentifier: String?): List<LibraryFolder> {
        val condition = if (parentRemoteIdentifier == null) "parent_remote_id IS NULL" else "parent_remote_id = ?"
        return database.readableDatabase.rawQuery(
            "SELECT remote_id, parent_remote_id, name FROM library_folders " +
                "WHERE $condition ORDER BY name COLLATE NOCASE",
            parentRemoteIdentifier?.let { arrayOf(it) }
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    LibraryFolder(
                        cursor.getString(0),
                        if (cursor.isNull(1)) null else cursor.getString(1),
                        cursor.getString(2)
                    )
                )
            }
        }
    }

    fun documentFolderRemoteIdentifier(identifier: Long): String? = database.readableDatabase.rawQuery(
        "SELECT library_folder_remote_id FROM documents WHERE identifier = ?",
        arrayOf(identifier.toString())
    ).use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null }

    fun libraryFolderPath(remoteIdentifier: String?): List<LibraryFolder> {
        if (remoteIdentifier.isNullOrBlank()) return emptyList()
        val reversedPath = mutableListOf<LibraryFolder>()
        val visited = mutableSetOf<String>()
        var currentIdentifier: String? = remoteIdentifier
        while (!currentIdentifier.isNullOrBlank() && visited.add(currentIdentifier)) {
            val folder = database.readableDatabase.rawQuery(
                "SELECT remote_id, parent_remote_id, name FROM library_folders WHERE remote_id = ?",
                arrayOf(currentIdentifier)
            ).use { cursor ->
                if (!cursor.moveToFirst()) null else LibraryFolder(
                    cursor.getString(0),
                    if (cursor.isNull(1)) null else cursor.getString(1),
                    cursor.getString(2)
                )
            } ?: break
            reversedPath += folder
            currentIdentifier = folder.parentRemoteIdentifier
        }
        return reversedPath.asReversed()
    }

    fun updateDocumentMetadata(identifier: Long, title: String, author: String) {
        if (title.isBlank()) return
        database.writableDatabase.update(
            "documents",
            ContentValues().apply {
                put("title", title.trim())
                put("author", author.trim())
                put("updated_at", System.currentTimeMillis())
            },
            "identifier = ?",
            arrayOf(identifier.toString())
        )
    }

    fun readerLocation(identifier: Long): Int = database.readableDatabase.rawQuery(
        "SELECT reader_location FROM documents WHERE identifier = ?",
        arrayOf(identifier.toString())
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    fun updateProgress(identifier: Long, location: Int, progress: Float) {
        val now = System.currentTimeMillis()
        database.writableDatabase.update(
            "documents",
            ContentValues().apply {
                put("reader_location", location)
                put("progress", progress.coerceIn(0f, 1f))
                put("last_opened_at", now)
                put("updated_at", now)
            },
            "identifier = ?",
            arrayOf(identifier.toString())
        )
    }

    fun markDocumentOpened(identifier: Long, openedAt: Long = System.currentTimeMillis()) {
        database.writableDatabase.update(
            "documents",
            ContentValues().apply {
                put("last_opened_at", openedAt)
                put("updated_at", openedAt)
            },
            "identifier = ?",
            arrayOf(identifier.toString())
        )
    }

    private fun readDocuments(cursor: android.database.Cursor): List<LibraryDocument> = buildList {
        while (cursor.moveToNext()) add(cursor.toLibraryDocument())
    }

    private fun android.database.Cursor.toLibraryDocument() = LibraryDocument(
        getLong(0), getString(1), getString(2), getString(3), getString(4), getString(5), getFloat(6), getLong(7)
    )
}
