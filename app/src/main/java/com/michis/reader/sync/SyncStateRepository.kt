package com.michis.reader.sync

import com.michis.reader.data.*

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/** Metadatos, fusiones de lectura y eliminaciones sincronizables. */
internal class SyncStateRepository(private val database: SQLiteOpenHelper) {
    fun metadata(table: String, identifier: Long): SyncRecordMetadata = database.readableDatabase.rawQuery(
        "SELECT sync_id, updated_at FROM $table WHERE identifier = ?",
        arrayOf(identifier.toString())
    ).use { cursor ->
        check(cursor.moveToFirst()) { "No existe el registro $identifier en $table" }
        SyncRecordMetadata(cursor.getString(0), cursor.getLong(1))
    }

    fun documentIdentifier(syncIdentifier: String): Long? = database.readableDatabase.rawQuery(
        "SELECT identifier FROM documents WHERE sync_id = ?",
        arrayOf(syncIdentifier)
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    fun mergeReadingState(documents: List<Pair<Long, JSONObject>>): ReadingMergeResult {
        var progressUpdates = 0
        var insertedAnnotations = 0
        var updatedAnnotations = 0
        var ignoredLocalNewer = 0
        val writableDatabase = database.writableDatabase
        writableDatabase.beginTransaction()
        try {
            documents.forEach { (localDocumentIdentifier, remoteDocument) ->
                val localUpdatedAt = metadata("documents", localDocumentIdentifier).updatedAt
                val remoteUpdatedAt = remoteDocument.optLong("updatedAt", 0)
                val localReadingState = database.readableDatabase.rawQuery(
                    "SELECT progress, reader_location, last_opened_at FROM documents WHERE identifier = ?",
                    arrayOf(localDocumentIdentifier.toString())
                ).use { cursor ->
                    if (cursor.moveToFirst()) Triple(cursor.getDouble(0), cursor.getInt(1), cursor.getLong(2))
                    else Triple(0.0, 0, 0L)
                }
                val localIsUntouched = localReadingState.first <= 0.0 &&
                    localReadingState.second <= 0 && localReadingState.third == 0L
                val remoteHasReadingActivity = remoteDocument.optDouble("progress", 0.0) > 0.0 ||
                    remoteDocument.optInt("readerLocation", 0) > 0 || remoteDocument.optLong("lastOpenedAt", 0) > 0
                if (remoteUpdatedAt > localUpdatedAt || (localIsUntouched && remoteHasReadingActivity)) {
                    writableDatabase.update("documents", ContentValues().apply {
                        put("progress", remoteDocument.optDouble("progress", 0.0).coerceIn(0.0, 1.0))
                        put("reader_location", remoteDocument.optInt("readerLocation", 0).coerceAtLeast(0))
                        put("last_opened_at", remoteDocument.optLong("lastOpenedAt", 0))
                        put("updated_at", remoteUpdatedAt)
                    }, "identifier = ?", arrayOf(localDocumentIdentifier.toString()))
                    progressUpdates++
                } else if (remoteUpdatedAt < localUpdatedAt) ignoredLocalNewer++

                val remoteAnnotations = remoteDocument.optJSONArray("annotations") ?: return@forEach
                for (index in 0 until remoteAnnotations.length()) {
                    val remote = remoteAnnotations.optJSONObject(index) ?: continue
                    val kind = remote.optString("kind")
                    val syncIdentifier = remote.optString("syncId")
                    if (kind !in setOf("cita", "marcador") || syncIdentifier.isBlank()) continue
                    val annotationUpdatedAt = remote.optLong("updatedAt", 0)
                    if (hasNewerOrEqualTombstone("annotation", syncIdentifier, annotationUpdatedAt)) continue
                    val local = database.readableDatabase.rawQuery(
                        "SELECT identifier, updated_at FROM annotations WHERE sync_id = ?",
                        arrayOf(syncIdentifier)
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getLong(1) else null
                    }
                    val values = ContentValues().apply {
                        put("document_identifier", localDocumentIdentifier)
                        put("kind", kind)
                        put("selected_text", remote.optString("selectedText"))
                        put("note", remote.optString("note"))
                        put("color", remote.optInt("color", 0))
                        put("location", remote.optInt("location", 0))
                        put("page_number", remote.optInt("pageNumber", 0))
                        put("created_at", remote.optLong("createdAt", annotationUpdatedAt))
                        put("order_position", remote.optInt("orderPosition", 0))
                        put("sync_id", syncIdentifier)
                        put("updated_at", annotationUpdatedAt)
                    }
                    if (local == null) {
                        writableDatabase.insertOrThrow("annotations", null, values)
                        insertedAnnotations++
                    } else if (annotationUpdatedAt > local.second) {
                        writableDatabase.update("annotations", values, "identifier = ?", arrayOf(local.first.toString()))
                        updatedAnnotations++
                    } else if (annotationUpdatedAt < local.second) ignoredLocalNewer++
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return ReadingMergeResult(progressUpdates, insertedAnnotations, updatedAnnotations, ignoredLocalNewer)
    }

    private fun hasNewerOrEqualTombstone(entityType: String, syncIdentifier: String, updatedAt: Long): Boolean =
        database.readableDatabase.rawQuery(
            "SELECT deleted_at FROM sync_tombstones WHERE entity_type = ? AND sync_id = ?",
            arrayOf(entityType, syncIdentifier)
        ).use { cursor -> cursor.moveToFirst() && cursor.getLong(0) >= updatedAt }

    fun dictionaryLinks(): List<SyncDictionaryLink> = database.readableDatabase.rawQuery(
        "SELECT owner_document_identifier, linked_document_identifier, sync_id, updated_at " +
            "FROM dictionary_document_links",
        null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                SyncDictionaryLink(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getLong(3))
            )
        }
    }

    fun tombstones(): List<SyncTombstone> = database.readableDatabase.rawQuery(
        "SELECT entity_type, sync_id, document_sync_id, deleted_at FROM sync_tombstones ORDER BY deleted_at",
        null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                SyncTombstone(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3))
            )
        }
    }

    fun previewDeletions(remoteTombstones: List<SyncTombstone>): List<PendingSyncDeletion> = buildList {
        remoteTombstones.forEach { tombstone ->
            val target = deletionTarget(tombstone) ?: return@forEach
            if (tombstone.deletedAt >= target.updatedAt) add(
                PendingSyncDeletion(tombstone.entityType, tombstone.syncIdentifier, tombstone.deletedAt, target.label)
            )
        }
    }

    fun applyDeletions(remoteTombstones: List<SyncTombstone>): DeletionMergeResult {
        var applied = 0
        var ignored = 0
        var absent = 0
        val writableDatabase = database.writableDatabase
        writableDatabase.beginTransaction()
        try {
            remoteTombstones.forEach { tombstone ->
                val target = deletionTarget(tombstone)
                if (target == null) {
                    absent++
                    saveRemoteTombstone(tombstone)
                } else if (tombstone.deletedAt >= target.updatedAt) {
                    saveRemoteTombstone(tombstone)
                    writableDatabase.delete(target.table, "sync_id = ?", arrayOf(tombstone.syncIdentifier))
                    applied++
                } else ignored++
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return DeletionMergeResult(applied, ignored, absent)
    }

    fun deleteEntity(table: String, entityType: String, identifier: Long): Int {
        val documentColumn = if (table == "documents") "identifier" else "document_identifier"
        val row = database.readableDatabase.rawQuery(
            """SELECT item.sync_id, COALESCE(document.sync_id, '')
               FROM $table item LEFT JOIN documents document ON document.identifier = item.$documentColumn
               WHERE item.identifier = ?""",
            arrayOf(identifier.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1) else null
        } ?: return 0
        val writableDatabase = database.writableDatabase
        writableDatabase.beginTransaction()
        return try {
            writableDatabase.insertWithOnConflict("sync_tombstones", null, ContentValues().apply {
                put("entity_type", entityType)
                put("sync_id", row.first)
                put("document_sync_id", row.second)
                put("deleted_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE)
            val deleted = writableDatabase.delete(table, "identifier = ?", arrayOf(identifier.toString()))
            writableDatabase.setTransactionSuccessful()
            deleted
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun deleteDictionaryLink(ownerDocumentIdentifier: Long, linkedDocumentIdentifier: Long) {
        val row = database.readableDatabase.rawQuery(
            """SELECT link.sync_id, document.sync_id FROM dictionary_document_links link
               INNER JOIN documents document ON document.identifier = link.owner_document_identifier
               WHERE link.owner_document_identifier = ? AND link.linked_document_identifier = ?""",
            arrayOf(ownerDocumentIdentifier.toString(), linkedDocumentIdentifier.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1) else null
        } ?: return
        val writableDatabase = database.writableDatabase
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertWithOnConflict("sync_tombstones", null, ContentValues().apply {
                put("entity_type", "dictionary_link")
                put("sync_id", row.first)
                put("document_sync_id", row.second)
                put("deleted_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE)
            writableDatabase.delete(
                "dictionary_document_links",
                "owner_document_identifier = ? AND linked_document_identifier = ?",
                arrayOf(ownerDocumentIdentifier.toString(), linkedDocumentIdentifier.toString())
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun deletionTarget(tombstone: SyncTombstone): SyncDeletionTarget? {
        val specification = when (tombstone.entityType) {
            "annotation" -> Triple("annotations", "updated_at", "kind || ': ' || CASE WHEN selected_text = '' THEN 'página ' || page_number ELSE selected_text END")
            "dictionary_category" -> Triple("dictionary_categories", "updated_at", "'Subcategoría: ' || name")
            "dictionary_entry" -> Triple("dictionary_entries", "updated_at", "'Diccionario: ' || term")
            "dictionary_link" -> Triple("dictionary_document_links", "updated_at", "'Vínculo de diccionario compartido'")
            else -> return null
        }
        return database.readableDatabase.rawQuery(
            "SELECT ${specification.second}, ${specification.third} FROM ${specification.first} WHERE sync_id = ?",
            arrayOf(tombstone.syncIdentifier)
        ).use { cursor ->
            if (cursor.moveToFirst()) SyncDeletionTarget(specification.first, cursor.getLong(0), cursor.getString(1)) else null
        }
    }

    private fun saveRemoteTombstone(tombstone: SyncTombstone) {
        val existingDeletedAt = database.readableDatabase.rawQuery(
            "SELECT deleted_at FROM sync_tombstones WHERE entity_type = ? AND sync_id = ?",
            arrayOf(tombstone.entityType, tombstone.syncIdentifier)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
        if (tombstone.deletedAt < existingDeletedAt) return
        database.writableDatabase.insertWithOnConflict("sync_tombstones", null, ContentValues().apply {
            put("entity_type", tombstone.entityType)
            put("sync_id", tombstone.syncIdentifier)
            put("document_sync_id", tombstone.documentSyncIdentifier)
            put("deleted_at", tombstone.deletedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private data class SyncDeletionTarget(val table: String, val updatedAt: Long, val label: String)
}
