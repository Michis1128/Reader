package com.michis.reader.sync

import com.michis.reader.data.*

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LibraryDeletionSynchronizer(private val context: Context) {
    fun preview(remoteBytes: ByteArray): List<PendingSyncDeletion> {
        val root = validatedRoot(remoteBytes)
        return ReaderDatabase.getInstance(context).previewSyncDeletions(root.remoteTombstones())
    }

    fun apply(remoteBytes: ByteArray, createSafetyBackup: Boolean = true): DeletionMergeResult {
        val root = validatedRoot(remoteBytes)
        if (createSafetyBackup) SyncSafetyBackupRepository(context).saveCurrentLibraryState()
        return ReaderDatabase.getInstance(context).applySyncDeletions(root.remoteTombstones())
    }

    private fun validatedRoot(bytes: ByteArray): JSONObject = JSONObject(bytes.toString(Charsets.UTF_8)).also {
        require(it.optInt("schemaVersion", -1) == 2) { "Las eliminaciones requieren un respaldo de esquema 2" }
    }

    private fun JSONObject.remoteTombstones(): List<SyncTombstone> {
        val items = optJSONArray("tombstones") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val type = item.optString("entityType")
                val syncIdentifier = item.optString("syncId")
                if (type.isBlank() || syncIdentifier.isBlank()) continue
                add(SyncTombstone(type, syncIdentifier, item.optString("documentSyncId"), item.optLong("deletedAt", 0)))
            }
        }
    }

}
