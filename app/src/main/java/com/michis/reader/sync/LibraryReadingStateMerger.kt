package com.michis.reader.sync

import com.michis.reader.data.*

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LibraryReadingStateMerger(private val context: Context) {
    fun merge(remoteBytes: ByteArray, createSafetyBackup: Boolean = true): ReadingMergeResult {
        val remoteRoot = JSONObject(remoteBytes.toString(Charsets.UTF_8))
        require(remoteRoot.optInt("schemaVersion", -1) == 2) { "La fusión requiere un respaldo de esquema 2" }

        val localSnapshot = LibrarySyncSnapshotBuilder(context).build()
        if (createSafetyBackup) SyncSafetyBackupRepository(context).save(localSnapshot.bytes)
        val localRoot = JSONObject(localSnapshot.bytes.toString(Charsets.UTF_8))
        val localByDocumentKey = (localRoot.optJSONArray("documents") ?: JSONArray()).byDocumentKey()
        val remoteDocuments = remoteRoot.optJSONArray("documents") ?: JSONArray()
        val database = ReaderDatabase.getInstance(context)
        val matchedDocuments = buildList {
            for (index in 0 until remoteDocuments.length()) {
                val remoteDocument = remoteDocuments.optJSONObject(index) ?: continue
                val key = remoteDocument.optString("documentKey")
                val localDocument = localByDocumentKey[key] ?: continue
                val localSyncIdentifier = localDocument.optString("syncId")
                val localIdentifier = database.documentIdentifierBySyncIdentifier(localSyncIdentifier) ?: continue
                add(localIdentifier to remoteDocument)
            }
        }
        return database.mergeReadingState(matchedDocuments)
    }

    private fun JSONArray.byDocumentKey(): Map<String, JSONObject> = buildMap {
        for (index in 0 until length()) {
            val document = optJSONObject(index) ?: continue
            document.optString("documentKey").takeIf { it.isNotBlank() }?.let { put(it, document) }
        }
    }
}
