package com.michis.reader.sync

import org.json.JSONArray
import org.json.JSONObject

/** Reglas puras que impiden que una sincronización direccional sobrescriba cambios más recientes. */
internal object DirectionalSyncPolicy {
    fun shouldUpload(localUpdatedAt: Long, remoteUpdatedAt: Long?): Boolean =
        remoteUpdatedAt == null || localUpdatedAt > remoteUpdatedAt

    fun mergeTombstones(remote: JSONArray, local: JSONArray): JSONArray {
        val latestByKey = linkedMapOf<String, JSONObject>()
        fun include(source: JSONArray) {
            repeat(source.length()) { index ->
                val item = source.optJSONObject(index) ?: return@repeat
                val key = "${item.optString("entityType")}:${item.optString("syncId")}"
                val previous = latestByKey[key]
                if (previous == null || item.optLong("deletedAt") > previous.optLong("deletedAt")) {
                    latestByKey[key] = JSONObject(item.toString())
                }
            }
        }
        include(remote)
        include(local)
        return JSONArray().apply { latestByKey.values.forEach(::put) }
    }

    fun mergeBookState(remote: JSONObject, local: JSONObject): BookStateMerge {
        val localDocumentIsNewer = local.optLong("updatedAt") > remote.optLong("updatedAt")
        val merged = JSONObject((if (localDocumentIsNewer) local else remote).toString())
        var containsLocalChanges = localDocumentIsNewer

        val annotations = mergeVersionedArray(remote.optJSONArray("annotations"), local.optJSONArray("annotations"))
        merged.put("annotations", annotations.value)
        containsLocalChanges = containsLocalChanges || annotations.containsLocalChanges

        val categories = mergeCategories(
            remote.optJSONArray("dictionaryCategories"),
            local.optJSONArray("dictionaryCategories")
        )
        merged.put("dictionaryCategories", categories.value)
        containsLocalChanges = containsLocalChanges || categories.containsLocalChanges

        val links = mergeVersionedArray(remote.optJSONArray("dictionaryLinks"), local.optJSONArray("dictionaryLinks"))
        merged.put("dictionaryLinks", links.value)
        containsLocalChanges = containsLocalChanges || links.containsLocalChanges
        merged.put("linkedDictionaryDocumentKeys", mergedLinkedKeys(remote, local))
        return BookStateMerge(merged, containsLocalChanges)
    }

    private fun mergeCategories(remote: JSONArray?, local: JSONArray?): ArrayMerge {
        val remoteByIdentifier = remote.versionedItems()
        val localByIdentifier = local.versionedItems()
        var containsLocalChanges = false
        val merged = linkedMapOf<String, JSONObject>()
        remoteByIdentifier.forEach { (identifier, category) -> merged[identifier] = JSONObject(category.toString()) }
        localByIdentifier.forEach { (identifier, localCategory) ->
            val remoteCategory = remoteByIdentifier[identifier]
            val localIsNewer = remoteCategory == null ||
                localCategory.optLong("updatedAt") > remoteCategory.optLong("updatedAt")
            val category = JSONObject((if (localIsNewer) localCategory else remoteCategory).toString())
            val entries = mergeVersionedArray(
                remoteCategory?.optJSONArray("entries"),
                localCategory.optJSONArray("entries")
            )
            category.put("entries", entries.value)
            if (localIsNewer || entries.containsLocalChanges) containsLocalChanges = true
            merged[identifier] = category
        }
        return ArrayMerge(JSONArray().apply { merged.values.forEach(::put) }, containsLocalChanges)
    }

    private fun mergeVersionedArray(remote: JSONArray?, local: JSONArray?): ArrayMerge {
        val remoteByIdentifier = remote.versionedItems()
        val merged = linkedMapOf<String, JSONObject>()
        remoteByIdentifier.forEach { (identifier, item) -> merged[identifier] = JSONObject(item.toString()) }
        var containsLocalChanges = false
        local.versionedItems().forEach { (identifier, localItem) ->
            val remoteItem = remoteByIdentifier[identifier]
            if (remoteItem == null || localItem.optLong("updatedAt") > remoteItem.optLong("updatedAt")) {
                merged[identifier] = JSONObject(localItem.toString())
                containsLocalChanges = true
            }
        }
        return ArrayMerge(JSONArray().apply { merged.values.forEach(::put) }, containsLocalChanges)
    }

    private fun mergedLinkedKeys(remote: JSONObject, local: JSONObject): JSONArray {
        val keys = linkedSetOf<String>()
        fun include(source: JSONArray?) {
            if (source == null) return
            repeat(source.length()) { index -> source.optString(index).takeIf(String::isNotBlank)?.let(keys::add) }
        }
        include(remote.optJSONArray("linkedDictionaryDocumentKeys"))
        include(local.optJSONArray("linkedDictionaryDocumentKeys"))
        return JSONArray().apply { keys.forEach(::put) }
    }

    private fun JSONArray?.versionedItems(): Map<String, JSONObject> = buildMap {
        val source = this@versionedItems ?: return@buildMap
        repeat(source.length()) { index ->
            val item = source.optJSONObject(index) ?: return@repeat
            item.optString("syncId").takeIf(String::isNotBlank)?.let { put(it, item) }
        }
    }

    data class BookStateMerge(val value: JSONObject, val containsLocalChanges: Boolean)
    private data class ArrayMerge(val value: JSONArray, val containsLocalChanges: Boolean)
}
