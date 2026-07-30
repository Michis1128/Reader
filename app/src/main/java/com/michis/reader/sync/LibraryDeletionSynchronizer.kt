package com.michis.reader.sync

import com.michis.reader.data.*

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LibraryDeletionSynchronizer(private val context: Context) {
    fun preview(remoteBytes: ByteArray): List<PendingSyncDeletion> {
        val root = validatedRoot(remoteBytes)
        return ReaderDatabase.getInstance(context).previewSyncDeletions(root.remoteTombstones())
    }

    fun apply(remoteBytes: ByteArray): DeletionMergeResult {
        val root = validatedRoot(remoteBytes)
        val localSnapshot = LibrarySyncSnapshotBuilder(context).build()
        saveSafetyCopy(localSnapshot.bytes)
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

    private fun saveSafetyCopy(bytes: ByteArray) {
        val directory = File(context.filesDir, "sync-safety").apply { mkdirs() }
        val temporary = File(directory, "library-state-before-deletions.tmp")
        val destination = File(directory, "library-state-before-deletions.json")
        temporary.writeBytes(bytes)
        check(temporary.renameTo(destination) || runCatching {
            temporary.copyTo(destination, overwrite = true); temporary.delete(); true
        }.getOrDefault(false)) { "No se pudo guardar la copia local de seguridad" }
    }
}
