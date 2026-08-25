package com.michis.reader.sync

import com.michis.reader.data.*

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant

data class LibrarySyncSnapshot(val bytes: ByteArray, val documentCount: Int, val itemCount: Int)

/** Genera un respaldo portable sin incluir URI locales ni el contenido de los libros. */
class LibrarySyncSnapshotBuilder(
    private val context: Context,
    private val database: ReaderDatabase = ReaderDatabase.getInstance(context)
) {
    private val fingerprintCache = context.getSharedPreferences("document_fingerprint_cache", Context.MODE_PRIVATE)

    fun build(): LibrarySyncSnapshot {
        val documentsJson = JSONArray()
        var itemCount = 0
        val documents = database.findDocuments()
        val fingerprints = documents.associate { it.identifier to documentFingerprint(it) }
        val dictionaryLinks = database.syncDictionaryLinks()
        documents.forEach { document ->
            val documentSync = database.documentSyncMetadata(document.identifier)
            val annotations = database.annotations(document.identifier)
            val categories = database.dictionaryCategories(document.identifier)
            val entries = database.dictionaryEntriesForDocument(document.identifier)
            val documentLinks = dictionaryLinks.filter { it.ownerDocumentIdentifier == document.identifier }
            val linkedDocumentKeys = documentLinks.mapNotNull { fingerprints[it.linkedDocumentIdentifier]?.first }
            itemCount += annotations.size + categories.size + entries.size + linkedDocumentKeys.size

            val annotationsJson = JSONArray()
            annotations.forEach { annotation ->
                val sync = database.annotationSyncMetadata(annotation.identifier)
                annotationsJson.put(JSONObject()
                .put("syncId", sync.syncIdentifier)
                .put("updatedAt", sync.updatedAt)
                .put("kind", annotation.kind)
                .put("selectedText", annotation.selectedText)
                .put("note", annotation.note)
                .put("color", annotation.color)
                .put("location", annotation.location)
                .put("pageNumber", annotation.pageNumber)
                .put("createdAt", annotation.createdAt)
                .put("orderPosition", annotation.orderPosition)
                .put("locatorJson", annotation.locatorJson))
            }

            val categoriesJson = JSONArray()
            categories.forEachIndexed { index, category ->
                val categorySync = database.dictionaryCategorySyncMetadata(category.identifier)
                val categoryEntries = entries.filter { it.categoryIdentifier == category.identifier }
                val entriesJson = JSONArray()
                categoryEntries.forEachIndexed { entryIndex, entry ->
                    val entrySync = database.dictionaryEntrySyncMetadata(entry.identifier)
                    entriesJson.put(JSONObject()
                    .put("syncId", entrySync.syncIdentifier)
                    .put("updatedAt", entrySync.updatedAt)
                    .put("term", entry.term)
                    .put("description", entry.description)
                    .put("context", entry.context)
                    .put("orderPosition", entryIndex))
                }
                categoriesJson.put(JSONObject()
                    .put("syncId", categorySync.syncIdentifier)
                    .put("updatedAt", categorySync.updatedAt)
                    .put("name", category.name)
                    .put("orderPosition", index)
                    .put("entries", entriesJson))
            }

            val fingerprint = fingerprints.getValue(document.identifier)
            val linksJson = JSONArray()
            documentLinks.forEach { link ->
                val linkedKey = fingerprints[link.linkedDocumentIdentifier]?.first ?: return@forEach
                linksJson.put(JSONObject()
                    .put("syncId", link.syncIdentifier)
                    .put("updatedAt", link.updatedAt)
                    .put("linkedDocumentKey", linkedKey))
            }
            documentsJson.put(JSONObject()
                .put("syncId", documentSync.syncIdentifier)
                .put("updatedAt", documentSync.updatedAt)
                .put("documentKey", fingerprint.first)
                .put("documentKeyType", fingerprint.second)
                .put("fileName", document.fileName)
                .put("title", document.title)
                .put("author", document.author)
                .put("format", document.format)
                .put("progress", document.progress.toDouble())
                .put("readerLocation", database.readerLocation(document.identifier))
                .put("lastOpenedAt", document.lastOpenedAt)
                .put("completedAt", document.completedAt)
                .put("annotations", annotationsJson)
                .put("dictionaryCategories", categoriesJson)
                .put("linkedDictionaryDocumentKeys", JSONArray(linkedDocumentKeys))
                .put("dictionaryLinks", linksJson))
        }

        val tombstonesJson = JSONArray()
        database.syncTombstones().forEach { tombstone -> tombstonesJson.put(JSONObject()
            .put("entityType", tombstone.entityType)
            .put("syncId", tombstone.syncIdentifier)
            .put("documentSyncId", tombstone.documentSyncIdentifier)
            .put("deletedAt", tombstone.deletedAt)) }
        itemCount += tombstonesJson.length()
        val root = JSONObject()
            .put("schemaVersion", 2)
            .put("generatedAt", Instant.now().toString())
            .put("documents", documentsJson)
            .put("tombstones", tombstonesJson)
        return LibrarySyncSnapshot(root.toString(2).toByteArray(Charsets.UTF_8), documentsJson.length(), itemCount)
    }

    private fun documentFingerprint(document: LibraryDocument): Pair<String, String> {
        val cacheKey = database.documentSyncMetadata(document.identifier).syncIdentifier
        fingerprintCache.getString(cacheKey, null)?.let { cached ->
            val separator = cached.indexOf(':')
            if (separator > 0) return cached.substring(separator + 1) to cached.substring(0, separator)
        }
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val documentStream = context.contentResolver.openInputStream(Uri.parse(document.uri))
                ?: error("No se pudo abrir ${document.uri} para calcular su huella")
            documentStream.buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().toHex() to "sha256-content"
        }.getOrElse {
            val fallback = "${document.fileName.lowercase()}|${document.title}|${document.author}|${document.format}"
            MessageDigest.getInstance("SHA-256").digest(fallback.toByteArray()).toHex() to "sha256-metadata-fallback"
        }.also { (value, type) -> fingerprintCache.edit().putString(cacheKey, "$type:$value").apply() }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
