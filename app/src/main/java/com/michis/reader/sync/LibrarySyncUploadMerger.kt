package com.michis.reader.sync

import com.michis.reader.data.*

import org.json.JSONArray
import org.json.JSONObject

/** Conserva documentos exclusivos de ambos dispositivos al construir el siguiente estado remoto. */
class LibrarySyncUploadMerger {
    fun merge(localSnapshot: LibrarySyncSnapshot, remoteBytes: ByteArray): LibrarySyncSnapshot {
        val localRoot = JSONObject(localSnapshot.bytes.toString(Charsets.UTF_8))
        val remoteRoot = JSONObject(remoteBytes.toString(Charsets.UTF_8))
        require(localRoot.optInt("schemaVersion") == 2 && remoteRoot.optInt("schemaVersion") == 2) {
            "Solo se pueden combinar respaldos de esquema 2"
        }
        val localDocuments = localRoot.optJSONArray("documents") ?: JSONArray()
        val remoteDocuments = remoteRoot.optJSONArray("documents") ?: JSONArray()
        val combinedDocuments = linkedMapOf<String, JSONObject>()
        for (index in 0 until remoteDocuments.length()) remoteDocuments.optJSONObject(index)?.let { document ->
            document.optString("documentKey").takeIf { it.isNotBlank() }?.let { combinedDocuments[it] = document }
        }
        for (index in 0 until localDocuments.length()) localDocuments.optJSONObject(index)?.let { document ->
            document.optString("documentKey").takeIf { it.isNotBlank() }?.let { combinedDocuments[it] = document }
        }

        val combinedTombstones = linkedMapOf<String, JSONObject>()
        addTombstones(combinedTombstones, remoteRoot.optJSONArray("tombstones") ?: JSONArray())
        addTombstones(combinedTombstones, localRoot.optJSONArray("tombstones") ?: JSONArray())
        val root = JSONObject()
            .put("schemaVersion", 2)
            .put("generatedAt", localRoot.optString("generatedAt"))
            .put("documents", JSONArray(combinedDocuments.values))
            .put("tombstones", JSONArray(combinedTombstones.values))
        val itemCount = combinedDocuments.values.sumOf(::countReadingItems) + combinedTombstones.size
        return LibrarySyncSnapshot(root.toString(2).toByteArray(Charsets.UTF_8), combinedDocuments.size, itemCount)
    }

    private fun addTombstones(target: MutableMap<String, JSONObject>, source: JSONArray) {
        for (index in 0 until source.length()) {
            val tombstone = source.optJSONObject(index) ?: continue
            val key = "${tombstone.optString("entityType")}:${tombstone.optString("syncId")}"
            if (key == ":") continue
            val existing = target[key]
            if (existing == null || tombstone.optLong("deletedAt") >= existing.optLong("deletedAt")) target[key] = tombstone
        }
    }

    private fun countReadingItems(document: JSONObject): Int {
        val annotations = document.optJSONArray("annotations")?.length() ?: 0
        val categories = document.optJSONArray("dictionaryCategories") ?: JSONArray()
        var dictionaryItems = categories.length()
        for (index in 0 until categories.length()) dictionaryItems += categories.optJSONObject(index)?.optJSONArray("entries")?.length() ?: 0
        return annotations + dictionaryItems + (document.optJSONArray("dictionaryLinks")?.length() ?: 0)
    }
}
