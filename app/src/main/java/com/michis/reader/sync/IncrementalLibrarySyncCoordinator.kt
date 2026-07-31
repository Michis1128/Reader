package com.michis.reader.sync

import android.content.Context
import com.michis.reader.data.*
import com.michis.reader.sync.drive.GoogleDriveFolder
import com.michis.reader.sync.drive.GoogleDriveFolderRepository
import org.json.JSONArray
import org.json.JSONObject

/** Sincroniza estados JSON pequeños por libro y mantiene compatibilidad con el respaldo monolítico anterior. */
class IncrementalLibrarySyncCoordinator(private val context: Context) {
    private val database = ReaderDatabase.getInstance(context)

    fun synchronizeAll(
        accessToken: String,
        accountIdentifier: String,
        folder: GoogleDriveFolder,
        repository: GoogleDriveFolderRepository,
        downloadedBookCount: Int,
        onStep: (String) -> Unit = {}
    ): FullSyncResult {
        onStep("3/7 · Leyendo el manifiesto de cambios")
        var manifest = repository.downloadNamedJsonOrNull(accessToken, folder.identifier, MANIFEST_FILE_NAME)
            ?.let { JSONObject(it.toString(Charsets.UTF_8)) }
        var firstBackup = false
        if (manifest == null) {
            onStep("4/7 · Migrando el respaldo anterior")
            repository.downloadLibrarySnapshotOrNull(accessToken, accountIdentifier, folder.identifier)?.let { bytes ->
                mergeRemoteRoot(JSONObject(bytes.toString(Charsets.UTF_8)))
            }
            manifest = emptyManifest()
            firstBackup = true
        }

        var localRoot = localRoot()
        val localByKey = localRoot.documentsByKey()
        val remoteEntries = manifest.optJSONObject("books") ?: JSONObject()
        val remoteDocuments = JSONArray()
        remoteEntries.keys().forEach { key ->
            val entry = remoteEntries.optJSONObject(key) ?: return@forEach
            val local = localByKey[key] ?: return@forEach
            val localIsUntouched = local.optDouble("progress", 0.0) <= 0.0 && local.optInt("readerLocation", 0) <= 0
            if (entry.optLong("updatedAt", 0) > stateUpdatedAt(local) || localIsUntouched) {
                val fileName = entry.optString("fileName", stateFileName(key))
                repository.downloadNamedJsonOrNull(accessToken, folder.identifier, fileName)?.let { bytes ->
                    remoteDocuments.put(JSONObject(bytes.toString(Charsets.UTF_8)))
                }
            }
        }
        if (remoteDocuments.length() > 0 || (manifest.optJSONArray("tombstones")?.length() ?: 0) > 0) {
            onStep("4/7 · Restaurando los libros modificados")
            mergeRemoteRoot(JSONObject().put("schemaVersion", 2)
                .put("documents", remoteDocuments)
                .put("tombstones", manifest.optJSONArray("tombstones") ?: JSONArray()))
        }

        onStep("5/7 · Preparando cambios locales")
        localRoot = localRoot()
        val newEntries = JSONObject()
        localRoot.documents().forEach { document ->
            val key = document.optString("documentKey")
            if (key.isBlank()) return@forEach
            val localUpdatedAt = stateUpdatedAt(document)
            val previous = remoteEntries.optJSONObject(key)
            val fileName = previous?.optString("fileName")?.takeIf { it.isNotBlank() } ?: stateFileName(key)
            if (previous == null || localUpdatedAt > previous.optLong("updatedAt", 0)) {
                onStep("6/7 · Subiendo ${document.optString("title", "libro")}")
                repository.uploadNamedJson(accessToken, folder.identifier, fileName, document.toString().toByteArray())
            }
            newEntries.put(key, JSONObject().put("fileName", fileName).put("updatedAt", localUpdatedAt))
        }
        manifest = JSONObject()
            .put("schemaVersion", MANIFEST_SCHEMA_VERSION)
            .put("books", newEntries)
            .put("tombstones", localRoot.optJSONArray("tombstones") ?: JSONArray())
            .put("updatedAt", System.currentTimeMillis())
        repository.uploadNamedJson(accessToken, folder.identifier, MANIFEST_FILE_NAME, manifest.toString(2).toByteArray())
        onStep("7/7 · Sincronización incremental completada")
        return FullSyncResult(
            firstBackup = firstBackup,
            documentCount = newEntries.length(),
            downloadedDocumentCount = downloadedBookCount,
            readingMerge = ReadingMergeResult(0, 0, 0, 0),
            dictionaryMerge = DictionaryMergeResult(0, 0, 0, 0, 0, 0),
            deletionMerge = DeletionMergeResult(0, 0, 0)
        )
    }

    fun synchronizeBook(
        accessToken: String,
        accountIdentifier: String,
        folder: GoogleDriveFolder,
        repository: GoogleDriveFolderRepository,
        documentIdentifier: Long
    ) {
        if (database.findDocument(documentIdentifier) == null) return
        var manifest = repository.downloadNamedJsonOrNull(accessToken, folder.identifier, MANIFEST_FILE_NAME)
            ?.let { JSONObject(it.toString(Charsets.UTF_8)) }
        if (manifest == null) {
            repository.downloadLibrarySnapshotOrNull(accessToken, accountIdentifier, folder.identifier)?.let { bytes ->
                mergeRemoteRoot(JSONObject(bytes.toString(Charsets.UTF_8)))
            }
            manifest = emptyManifest()
        }
        var localRoot = localRoot()
        var localDocument = localRoot.documentByIdentifier(documentIdentifier) ?: return
        val key = localDocument.optString("documentKey")
        val entries = manifest.optJSONObject("books") ?: JSONObject()
        entries.optJSONObject(key)?.let { remoteEntry ->
            val fileName = remoteEntry.optString("fileName", stateFileName(key))
            repository.downloadNamedJsonOrNull(accessToken, folder.identifier, fileName)?.let { bytes ->
                mergeRemoteRoot(JSONObject().put("schemaVersion", 2)
                    .put("documents", JSONArray().put(JSONObject(bytes.toString(Charsets.UTF_8))))
                    .put("tombstones", manifest.optJSONArray("tombstones") ?: JSONArray()))
            }
        }
        localRoot = localRoot()
        localDocument = localRoot.documentByIdentifier(documentIdentifier) ?: return
        val updatedAt = stateUpdatedAt(localDocument)
        val fileName = entries.optJSONObject(key)?.optString("fileName")?.takeIf { it.isNotBlank() } ?: stateFileName(key)
        repository.uploadNamedJson(accessToken, folder.identifier, fileName, localDocument.toString().toByteArray())
        entries.put(key, JSONObject().put("fileName", fileName).put("updatedAt", updatedAt))
        manifest.put("schemaVersion", MANIFEST_SCHEMA_VERSION)
            .put("books", entries)
            .put("tombstones", localRoot.optJSONArray("tombstones") ?: JSONArray())
            .put("updatedAt", System.currentTimeMillis())
        repository.uploadNamedJson(accessToken, folder.identifier, MANIFEST_FILE_NAME, manifest.toString(2).toByteArray())
    }

    private fun mergeRemoteRoot(root: JSONObject) {
        val bytes = root.toString().toByteArray()
        LibraryReadingStateMerger(context).merge(bytes)
        LibraryDictionaryStateMerger(context).merge(bytes)
        LibraryDeletionSynchronizer(context).apply(bytes)
    }

    private fun localRoot() = JSONObject(LibrarySyncSnapshotBuilder(context).build().bytes.toString(Charsets.UTF_8))

    private fun JSONObject.documents(): List<JSONObject> = buildList {
        val array = optJSONArray("documents") ?: JSONArray()
        repeat(array.length()) { array.optJSONObject(it)?.let(::add) }
    }

    private fun JSONObject.documentsByKey() = documents().associateBy { it.optString("documentKey") }

    private fun JSONObject.documentByIdentifier(identifier: Long): JSONObject? {
        val syncIdentifier = database.documentSyncMetadata(identifier).syncIdentifier
        return documents().firstOrNull { it.optString("syncId") == syncIdentifier }
    }

    private fun stateUpdatedAt(document: JSONObject): Long {
        var latest = document.optLong("updatedAt", 0)
        fun include(array: JSONArray?) {
            if (array == null) return
            repeat(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                latest = maxOf(latest, item.optLong("updatedAt", 0))
                include(item.optJSONArray("entries"))
            }
        }
        include(document.optJSONArray("annotations"))
        include(document.optJSONArray("dictionaryCategories"))
        include(document.optJSONArray("dictionaryLinks"))
        return latest
    }

    private fun emptyManifest() = JSONObject().put("schemaVersion", MANIFEST_SCHEMA_VERSION)
        .put("books", JSONObject()).put("tombstones", JSONArray())

    private fun stateFileName(key: String) = "book-state-$key.json"

    companion object {
        private const val MANIFEST_FILE_NAME = "library-manifest.json"
        private const val MANIFEST_SCHEMA_VERSION = 1
    }
}
