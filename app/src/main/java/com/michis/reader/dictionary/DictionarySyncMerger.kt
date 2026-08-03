package com.michis.reader.dictionary

import com.michis.reader.data.*

import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID
import org.json.JSONObject

/** Fusiona categorías, entradas y vínculos de diccionario recibidos desde Drive. */
internal class DictionarySyncMerger(private val database: SQLiteOpenHelper) {
    private val readableDatabase get() = database.readableDatabase
    private val writableDatabase get() = database.writableDatabase

    fun mergeDictionaryState(
        documents: List<Pair<Long, JSONObject>>,
        localDocumentIdentifiersByKey: Map<String, Long>
    ): DictionaryMergeResult {
        var insertedCategories = 0
        var insertedEntries = 0
        var updatedEntries = 0
        var mergedDuplicateTerms = 0
        var insertedLinks = 0
        var ignoredLocalNewer = 0
        writableDatabase.beginTransaction()
        try {
            documents.forEach { (localDocumentIdentifier, remoteDocument) ->
                val remoteCategories = remoteDocument.optJSONArray("dictionaryCategories")
                if (remoteCategories != null) for (categoryIndex in 0 until remoteCategories.length()) {
                    val remoteCategory = remoteCategories.optJSONObject(categoryIndex) ?: continue
                    val remoteCategorySyncId = remoteCategory.optString("syncId")
                    val categoryName = remoteCategory.optString("name").trim()
                    if (remoteCategorySyncId.isBlank() || categoryName.isBlank()) continue
                    val remoteCategoryUpdatedAt = remoteCategory.optLong("updatedAt", 0)
                    if (hasNewerOrEqualTombstone("dictionary_category", remoteCategorySyncId, remoteCategoryUpdatedAt)) continue
                    val categoryBySync = categoryRowBySyncId(remoteCategorySyncId)
                    val categoryByName = categoryRowByName(localDocumentIdentifier, categoryName)
                    val localCategoryIdentifier = when {
                        categoryBySync != null -> categoryBySync.first
                        categoryByName != null -> categoryByName.first
                        else -> {
                            val inserted = writableDatabase.insertOrThrow("dictionary_categories", null, ContentValues().apply {
                                put("document_identifier", localDocumentIdentifier); put("name", categoryName)
                                put("order_position", remoteCategory.optInt("orderPosition", categoryIndex))
                                put("sync_id", remoteCategorySyncId); put("updated_at", remoteCategory.optLong("updatedAt", 0))
                            })
                            insertedCategories++
                            inserted
                        }
                    }
                    if (categoryBySync != null && remoteCategoryUpdatedAt > categoryBySync.second &&
                        (categoryByName == null || categoryByName.first == categoryBySync.first)) {
                        writableDatabase.update("dictionary_categories", ContentValues().apply {
                            put("name", categoryName); put("order_position", remoteCategory.optInt("orderPosition", categoryIndex))
                            put("updated_at", remoteCategoryUpdatedAt)
                        }, "identifier = ?", arrayOf(localCategoryIdentifier.toString()))
                    }
                    val remoteEntries = remoteCategory.optJSONArray("entries") ?: continue
                    for (entryIndex in 0 until remoteEntries.length()) {
                        val remoteEntry = remoteEntries.optJSONObject(entryIndex) ?: continue
                        val remoteSyncId = remoteEntry.optString("syncId")
                        val term = remoteEntry.optString("term").trim()
                        if (remoteSyncId.isBlank() || term.isBlank()) continue
                        val remoteUpdatedAt = remoteEntry.optLong("updatedAt", 0)
                        if (hasNewerOrEqualTombstone("dictionary_entry", remoteSyncId, remoteUpdatedAt)) continue
                        val bySync = dictionaryEntryRowBySyncId(remoteSyncId)
                        val byTerm = dictionaryEntryRowByTerm(localDocumentIdentifier, term)
                        if (bySync != null && byTerm != null && bySync.first != byTerm.first) {
                            mergedDuplicateTerms++
                            if (remoteUpdatedAt > byTerm.second) {
                                writableDatabase.update("dictionary_entries", dictionaryEntryValues(
                                    remoteEntry, localDocumentIdentifier, localCategoryIdentifier, byTerm.third, entryIndex
                                ), "identifier = ?", arrayOf(byTerm.first.toString()))
                                updatedEntries++
                            } else if (remoteUpdatedAt < byTerm.second) ignoredLocalNewer++
                            continue
                        }
                        val target = bySync ?: byTerm
                        if (target == null) {
                            writableDatabase.insertOrThrow("dictionary_entries", null, dictionaryEntryValues(
                                remoteEntry, localDocumentIdentifier, localCategoryIdentifier, remoteSyncId, entryIndex
                            ))
                            insertedEntries++
                        } else if (remoteUpdatedAt > target.second) {
                            val values = dictionaryEntryValues(
                                remoteEntry, localDocumentIdentifier, localCategoryIdentifier,
                                target.third, entryIndex
                            )
                            writableDatabase.update("dictionary_entries", values, "identifier = ?", arrayOf(target.first.toString()))
                            updatedEntries++
                        } else if (remoteUpdatedAt < target.second) {
                            ignoredLocalNewer++
                            if (bySync == null && byTerm != null) mergedDuplicateTerms++
                        }
                    }
                }

                val remoteLinks = remoteDocument.optJSONArray("dictionaryLinks")
                if (remoteLinks != null) for (linkIndex in 0 until remoteLinks.length()) {
                    val remoteLink = remoteLinks.optJSONObject(linkIndex) ?: continue
                    val remoteLinkSyncId = remoteLink.optString("syncId")
                    val remoteLinkUpdatedAt = remoteLink.optLong("updatedAt", 0)
                    if (remoteLinkSyncId.isNotBlank() &&
                        hasNewerOrEqualTombstone("dictionary_link", remoteLinkSyncId, remoteLinkUpdatedAt)) continue
                    val linkedIdentifier = localDocumentIdentifiersByKey[remoteLink.optString("linkedDocumentKey")] ?: continue
                    if (linkedIdentifier == localDocumentIdentifier) continue
                    val exists = readableDatabase.rawQuery(
                        "SELECT 1 FROM dictionary_document_links WHERE owner_document_identifier = ? AND linked_document_identifier = ?",
                        arrayOf(localDocumentIdentifier.toString(), linkedIdentifier.toString())
                    ).use { it.moveToFirst() }
                    if (!exists) {
                        writableDatabase.insertOrThrow("dictionary_document_links", null, ContentValues().apply {
                            put("owner_document_identifier", localDocumentIdentifier); put("linked_document_identifier", linkedIdentifier)
                            put("sync_id", remoteLinkSyncId.ifBlank { UUID.randomUUID().toString() })
                            put("updated_at", remoteLinkUpdatedAt)
                        })
                        insertedLinks++
                    }
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        return DictionaryMergeResult(insertedCategories, insertedEntries, updatedEntries, mergedDuplicateTerms, insertedLinks, ignoredLocalNewer)
    }

    private fun categoryRowBySyncId(syncIdentifier: String): Pair<Long, Long>? = readableDatabase.rawQuery(
        "SELECT identifier, updated_at FROM dictionary_categories WHERE sync_id = ?", arrayOf(syncIdentifier)
    ).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else null }

    private fun hasNewerOrEqualTombstone(entityType: String, syncIdentifier: String, updatedAt: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT deleted_at FROM sync_tombstones WHERE entity_type = ? AND sync_id = ?",
            arrayOf(entityType, syncIdentifier)
        ).use { cursor -> cursor.moveToFirst() && cursor.getLong(0) >= updatedAt }

    private fun categoryRowByName(documentIdentifier: Long, name: String): Pair<Long, Long>? = readableDatabase.rawQuery(
        "SELECT identifier, updated_at FROM dictionary_categories WHERE document_identifier = ? AND name = ? COLLATE NOCASE",
        arrayOf(documentIdentifier.toString(), name)
    ).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else null }

    private data class DictionaryEntryMergeRow(val first: Long, val second: Long, val third: String)

    private fun dictionaryEntryRowBySyncId(syncIdentifier: String): DictionaryEntryMergeRow? = readableDatabase.rawQuery(
        "SELECT identifier, updated_at, sync_id FROM dictionary_entries WHERE sync_id = ?", arrayOf(syncIdentifier)
    ).use { if (it.moveToFirst()) DictionaryEntryMergeRow(it.getLong(0), it.getLong(1), it.getString(2)) else null }

    private fun dictionaryEntryRowByTerm(documentIdentifier: Long, term: String): DictionaryEntryMergeRow? = readableDatabase.rawQuery(
        "SELECT identifier, updated_at, sync_id FROM dictionary_entries WHERE document_identifier = ? AND term = ? COLLATE NOCASE",
        arrayOf(documentIdentifier.toString(), term)
    ).use { if (it.moveToFirst()) DictionaryEntryMergeRow(it.getLong(0), it.getLong(1), it.getString(2)) else null }

    private fun dictionaryEntryValues(
        remote: JSONObject,
        documentIdentifier: Long,
        categoryIdentifier: Long,
        syncIdentifier: String,
        fallbackOrder: Int
    ) = ContentValues().apply {
        put("document_identifier", documentIdentifier); put("category_identifier", categoryIdentifier)
        put("term", remote.optString("term").trim()); put("description", remote.optString("description").trim())
        put("context", remote.optString("context").trim()); put("created_at", remote.optLong("createdAt", remote.optLong("updatedAt", 0)))
        put("order_position", remote.optInt("orderPosition", fallbackOrder)); put("sync_id", syncIdentifier)
        put("updated_at", remote.optLong("updatedAt", 0))
    }
}
