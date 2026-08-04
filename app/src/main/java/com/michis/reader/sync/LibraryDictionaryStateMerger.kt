package com.michis.reader.sync

import com.michis.reader.data.*
import com.michis.reader.dictionary.DictionarySyncMerger

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LibraryDictionaryStateMerger(private val context: Context) {
    fun merge(remoteBytes: ByteArray, createSafetyBackup: Boolean = true): DictionaryMergeResult {
        val remoteRoot = JSONObject(remoteBytes.toString(Charsets.UTF_8))
        require(remoteRoot.optInt("schemaVersion", -1) == 2) { "La fusión requiere un respaldo de esquema 2" }
        val localSnapshot = LibrarySyncSnapshotBuilder(context).build()
        if (createSafetyBackup) SyncSafetyBackupRepository(context).save(localSnapshot.bytes)
        val localRoot = JSONObject(localSnapshot.bytes.toString(Charsets.UTF_8))
        val localByKey = (localRoot.optJSONArray("documents") ?: JSONArray()).byDocumentKey()
        val database = ReaderDatabase.getInstance(context)
        val localIdentifiersByKey = buildMap {
            localByKey.forEach { (key, document) ->
                database.documentIdentifierBySyncIdentifier(document.optString("syncId"))?.let { put(key, it) }
            }
        }
        val remoteDocuments = remoteRoot.optJSONArray("documents") ?: JSONArray()
        val matched = buildList {
            for (index in 0 until remoteDocuments.length()) {
                val remote = remoteDocuments.optJSONObject(index) ?: continue
                val localIdentifier = localIdentifiersByKey[remote.optString("documentKey")] ?: continue
                add(localIdentifier to remote)
            }
        }
        return database.mergeDictionaryState(matched, localIdentifiersByKey)
    }

    private fun JSONArray.byDocumentKey(): Map<String, JSONObject> = buildMap {
        for (index in 0 until length()) {
            val document = optJSONObject(index) ?: continue
            document.optString("documentKey").takeIf { it.isNotBlank() }?.let { put(it, document) }
        }
    }
}
