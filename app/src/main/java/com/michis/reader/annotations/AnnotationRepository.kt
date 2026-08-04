package com.michis.reader.annotations

import com.michis.reader.data.*

import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/** Persistencia de marcadores y citas. */
internal class AnnotationRepository(private val database: SQLiteOpenHelper) {
    fun addAnnotation(
        documentIdentifier: Long,
        kind: String,
        text: String,
        note: String,
        color: Int,
        location: Int,
        pageNumber: Int
    ) {
        val now = System.currentTimeMillis()
        database.writableDatabase.insert("annotations", null, ContentValues().apply {
            put("document_identifier", documentIdentifier)
            put("kind", kind)
            put("selected_text", text)
            put("note", note)
            put("color", color)
            put("location", location)
            put("page_number", pageNumber)
            put("created_at", now)
            put("order_position", nextPosition("annotations"))
            put("sync_id", UUID.randomUUID().toString())
            put("updated_at", now)
        })
    }

    fun annotations(documentIdentifier: Long?): List<SavedAnnotation> {
        val condition = if (documentIdentifier == null) "" else "WHERE document_identifier = ?"
        return database.readableDatabase.rawQuery(
            "SELECT identifier, document_identifier, kind, selected_text, note, color, location, " +
                "page_number, created_at, order_position FROM annotations $condition " +
                "ORDER BY order_position, created_at",
            documentIdentifier?.let { arrayOf(it.toString()) }
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    SavedAnnotation(
                        cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3),
                        cursor.getString(4), cursor.getInt(5), cursor.getInt(6), cursor.getInt(7),
                        cursor.getLong(8), cursor.getInt(9)
                    )
                )
            }
        }
    }

    fun count(documentIdentifier: Long, kind: String): Int = database.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM annotations WHERE document_identifier = ? AND kind = ?",
        arrayOf(documentIdentifier.toString(), kind)
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    fun updateQuote(identifier: Long, note: String, color: Int): Int = database.writableDatabase.update(
        "annotations",
        ContentValues().apply {
            put("note", note)
            put("color", color)
            put("updated_at", System.currentTimeMillis())
        },
        "identifier = ? AND kind = 'cita'",
        arrayOf(identifier.toString())
    )

    fun bookmarkAt(documentIdentifier: Long, location: Int): SavedAnnotation? =
        annotations(documentIdentifier).firstOrNull { it.kind == "marcador" && it.location == location }

    fun moveAnnotation(identifier: Long, direction: Int) {
        val ordered = annotations(null)
        val currentIndex = ordered.indexOfFirst { it.identifier == identifier }
        if (currentIndex < 0) return
        val targetIndex = (currentIndex + direction).coerceIn(0, ordered.lastIndex)
        if (currentIndex == targetIndex) return
        swapPositions(
            "annotations", "identifier", identifier.toString(), ordered[targetIndex].identifier.toString(),
            ordered[currentIndex].orderPosition, ordered[targetIndex].orderPosition, updateSyncTimestamp = true
        )
    }

    private fun nextPosition(table: String): Int = database.readableDatabase.rawQuery(
        "SELECT COALESCE(MAX(order_position), 0) + 1 FROM $table",
        null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun swapPositions(
        table: String,
        key: String,
        firstKey: String,
        secondKey: String,
        firstPosition: Int,
        secondPosition: Int,
        updateSyncTimestamp: Boolean
    ) {
        val writableDatabase = database.writableDatabase
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                table, ContentValues().apply { put("order_position", secondPosition) },
                "$key = ?", arrayOf(firstKey)
            )
            writableDatabase.update(
                table, ContentValues().apply { put("order_position", firstPosition) },
                "$key = ?", arrayOf(secondKey)
            )
            if (updateSyncTimestamp) {
                writableDatabase.update(
                    table,
                    ContentValues().apply { put("updated_at", System.currentTimeMillis()) },
                    "$key IN (?, ?)",
                    arrayOf(firstKey, secondKey)
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }
}
