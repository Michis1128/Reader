package com.michis.reader.dictionary

import com.michis.reader.data.*

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/** Consultas y cambios locales de diccionarios asociados o compartidos entre libros. */
internal class DictionaryRepository(private val database: SQLiteOpenHelper) {
    fun categories(documentIdentifier: Long): List<DictionaryCategory> = database.readableDatabase.rawQuery(
        "SELECT identifier, document_identifier, name FROM dictionary_categories " +
            "WHERE document_identifier = ? ORDER BY order_position, name COLLATE NOCASE",
        arrayOf(documentIdentifier.toString())
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                DictionaryCategory(cursor.getLong(0), cursor.getLong(1), cursor.getString(2))
            )
        }
    }

    fun createCategory(documentIdentifier: Long, name: String): Long {
        if (name.isBlank()) return -1
        val normalizedName = name.trim()
        val now = System.currentTimeMillis()
        database.writableDatabase.insertWithOnConflict(
            "dictionary_categories",
            null,
            ContentValues().apply {
                put("document_identifier", documentIdentifier)
                put("name", normalizedName)
                put("order_position", nextPosition("dictionary_categories"))
                put("sync_id", UUID.randomUUID().toString())
                put("updated_at", now)
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
        return database.readableDatabase.rawQuery(
            "SELECT identifier FROM dictionary_categories " +
                "WHERE document_identifier = ? AND name = ? COLLATE NOCASE",
            arrayOf(documentIdentifier.toString(), normalizedName)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1 }
    }

    fun saveEntry(
        documentIdentifier: Long,
        categoryIdentifier: Long,
        term: String,
        description: String,
        context: String,
        duplicateResult: Long
    ): Long {
        if (term.isBlank() || categoryIdentifier < 0) return -1
        val normalizedTerm = term.trim()
        val existingIdentifier = database.readableDatabase.rawQuery(
            "SELECT identifier FROM dictionary_entries " +
                "WHERE document_identifier = ? AND term = ? COLLATE NOCASE",
            arrayOf(documentIdentifier.toString(), normalizedTerm)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1 }
        if (existingIdentifier >= 0) return duplicateResult

        val now = System.currentTimeMillis()
        database.writableDatabase.insertWithOnConflict(
            "dictionary_entries",
            null,
            ContentValues().apply {
                put("document_identifier", documentIdentifier)
                put("category_identifier", categoryIdentifier)
                put("term", normalizedTerm)
                put("description", description.trim())
                put("context", context.trim())
                put("created_at", now)
                put("order_position", nextPosition("dictionary_entries"))
                put("sync_id", UUID.randomUUID().toString())
                put("updated_at", now)
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
        return database.readableDatabase.rawQuery(
            "SELECT identifier FROM dictionary_entries " +
                "WHERE document_identifier = ? AND term = ? COLLATE NOCASE",
            arrayOf(documentIdentifier.toString(), normalizedTerm)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1 }
    }

    fun entries(categoryIdentifier: Long): List<DictionaryEntry> =
        entriesQuery("WHERE category_identifier = ?", arrayOf(categoryIdentifier.toString()))

    fun entriesForDocument(documentIdentifier: Long): List<DictionaryEntry> =
        entriesQuery("WHERE document_identifier = ?", arrayOf(documentIdentifier.toString()))

    fun effectiveOwnerIdentifiers(documentIdentifier: Long): List<Long> = buildList {
        add(documentIdentifier)
        database.readableDatabase.rawQuery(
            "SELECT owner_document_identifier FROM dictionary_document_links " +
                "WHERE linked_document_identifier = ?",
            arrayOf(documentIdentifier.toString())
        ).use { cursor -> while (cursor.moveToNext()) add(cursor.getLong(0)) }
    }.distinct()

    fun effectiveEntries(documentIdentifier: Long): List<DictionaryEntry> =
        effectiveOwnerIdentifiers(documentIdentifier)
            .flatMap(::entriesForDocument)
            .distinctBy { it.term.lowercase() }

    fun hasEffectiveEntries(documentIdentifier: Long): Boolean = database.readableDatabase.rawQuery(
        """SELECT EXISTS(
               SELECT 1 FROM dictionary_entries entry
               WHERE entry.document_identifier = ?
                  OR entry.document_identifier IN (
                      SELECT owner_document_identifier FROM dictionary_document_links
                      WHERE linked_document_identifier = ?
                  )
           )""",
        arrayOf(documentIdentifier.toString(), documentIdentifier.toString())
    ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) != 0 }

    fun linkedDocuments(ownerDocumentIdentifier: Long): Set<Long> = database.readableDatabase.rawQuery(
        "SELECT linked_document_identifier FROM dictionary_document_links " +
            "WHERE owner_document_identifier = ?",
        arrayOf(ownerDocumentIdentifier.toString())
    ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) } }

    fun insertLink(ownerDocumentIdentifier: Long, linkedDocumentIdentifier: Long) {
        if (ownerDocumentIdentifier == linkedDocumentIdentifier) return
        database.writableDatabase.insertWithOnConflict(
            "dictionary_document_links",
            null,
            ContentValues().apply {
                put("owner_document_identifier", ownerDocumentIdentifier)
                put("linked_document_identifier", linkedDocumentIdentifier)
                put("sync_id", UUID.randomUUID().toString())
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun findEntry(identifier: Long): DictionaryEntry? =
        entriesQuery("WHERE identifier = ?", arrayOf(identifier.toString())).firstOrNull()

    fun updateDescription(identifier: Long, description: String): Int = database.writableDatabase.update(
        "dictionary_entries",
        ContentValues().apply {
            put("description", description.trim())
            put("updated_at", System.currentTimeMillis())
        },
        "identifier = ?",
        arrayOf(identifier.toString())
    )

    fun documentsWithDictionaries(): List<LibraryDocument> = database.readableDatabase.rawQuery(
        """SELECT DISTINCT d.identifier, d.uri, d.file_name, d.title, d.author, d.format, d.progress, d.last_opened_at
           FROM documents d INNER JOIN dictionary_categories c ON c.document_identifier = d.identifier
           ORDER BY d.title COLLATE NOCASE""",
        null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                LibraryDocument(
                    cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                    cursor.getString(4), cursor.getString(5), cursor.getFloat(6), cursor.getLong(7)
                )
            )
            }
        }

    private fun entriesQuery(condition: String, arguments: Array<String>): List<DictionaryEntry> =
        database.readableDatabase.rawQuery(
            "SELECT identifier, category_identifier, document_identifier, term, description, context " +
                "FROM dictionary_entries $condition ORDER BY order_position, term COLLATE NOCASE",
            arguments
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    DictionaryEntry(
                        cursor.getLong(0), cursor.getLong(1), cursor.getLong(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5)
                    )
                )
            }
        }

    private fun nextPosition(table: String): Int = database.readableDatabase.rawQuery(
        "SELECT COALESCE(MAX(order_position), 0) + 1 FROM $table",
        null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
}
