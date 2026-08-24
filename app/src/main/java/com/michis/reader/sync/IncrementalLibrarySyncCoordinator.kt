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
        val manifest = loadManifest(accessToken, accountIdentifier, folder, repository) {
            onStep("4/7 · Migrando el respaldo anterior")
        }

        var localRoot = localRoot()
        val localByKey = localRoot.documentsByKey()
        val remoteEntries = manifest.optJSONObject("books") ?: JSONObject()
        val remoteDocuments = JSONArray()
        val remoteVersions = RemoteBookStateVersionRepository(context, accountIdentifier)
        val downloadedVersions = mutableListOf<Pair<String, Long>>()
        remoteEntries.keys().forEach { key ->
            val entry = remoteEntries.optJSONObject(key) ?: return@forEach
            if (localByKey[key] == null) return@forEach
            val remoteVersion = entry.optLong("updatedAt", 0)
            if (remoteVersions.wasApplied(key, remoteVersion)) return@forEach
            val fileName = entry.optString("fileName", stateFileName(key))
            repository.downloadNamedJsonOrNull(accessToken, folder.identifier, fileName)?.let { bytes ->
                remoteDocuments.put(JSONObject(bytes.toString(Charsets.UTF_8)))
                downloadedVersions += key to remoteVersion
            }
        }
        if (remoteDocuments.length() > 0 || (manifest.optJSONArray("tombstones")?.length() ?: 0) > 0) {
            onStep("4/7 · Restaurando los libros modificados")
            mergeRemoteRoot(JSONObject().put("schemaVersion", 2)
                .put("documents", remoteDocuments)
                .put("tombstones", manifest.optJSONArray("tombstones") ?: JSONArray()))
            downloadedVersions.forEach { (key, version) -> remoteVersions.markApplied(key, version) }
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
            val remoteDocument = previous?.let {
                repository.downloadNamedJsonOrNull(accessToken, folder.identifier, fileName)
                    ?.let { bytes -> JSONObject(bytes.toString(Charsets.UTF_8)) }
            }
            val merge = remoteDocument?.let { DirectionalSyncPolicy.mergeBookState(it, document) }
            val payload = merge?.value ?: document
            val shouldUpload = previous == null || localUpdatedAt > previous.optLong("updatedAt", 0) ||
                merge?.containsLocalChanges == true
            val remoteRevision = if (shouldUpload) {
                onStep("6/7 · Subiendo ${document.optString("title", "libro")}")
                repository.uploadNamedJson(accessToken, folder.identifier, fileName, payload.toString().toByteArray())
                maxOf(localUpdatedAt, System.currentTimeMillis(), (previous?.optLong("updatedAt", 0) ?: 0) + 1)
            } else previous.optLong("updatedAt", localUpdatedAt)
            newEntries.put(key, JSONObject().put("fileName", fileName).put("updatedAt", remoteRevision))
            remoteVersions.markApplied(key, remoteRevision)
        }
        val updatedManifest = JSONObject()
            .put("schemaVersion", MANIFEST_SCHEMA_VERSION)
            .put("books", newEntries)
            .put("tombstones", localRoot.optJSONArray("tombstones") ?: JSONArray())
            .put("updatedAt", System.currentTimeMillis())
        repository.uploadNamedJson(accessToken, folder.identifier, MANIFEST_FILE_NAME, updatedManifest.toString(2).toByteArray())
        onStep("7/7 · Sincronización incremental completada")
        return FullSyncResult(
            documentCount = newEntries.length(),
            downloadedDocumentCount = downloadedBookCount
        )
    }

    fun downloadAll(
        accessToken: String,
        accountIdentifier: String,
        folder: GoogleDriveFolder,
        repository: GoogleDriveFolderRepository,
        downloadedBookCount: Int,
        onStep: (String) -> Unit = {}
    ): FullSyncResult {
        onStep("3/5 · Leyendo el manifiesto de Drive")
        val manifest = loadManifest(accessToken, accountIdentifier, folder, repository) {
            onStep("4/5 · Recuperando el respaldo anterior")
        }
        val localByKey = localRoot().documentsByKey()
        val remoteEntries = manifest.optJSONObject("books") ?: JSONObject()
        val remoteDocuments = JSONArray()
        val remoteVersions = RemoteBookStateVersionRepository(context, accountIdentifier)
        val downloadedVersions = mutableListOf<Pair<String, Long>>()
        remoteEntries.keys().forEach { key ->
            if (localByKey[key] == null) return@forEach
            val entry = remoteEntries.optJSONObject(key) ?: return@forEach
            val remoteVersion = entry.optLong("updatedAt", 0)
            if (remoteVersions.wasApplied(key, remoteVersion)) return@forEach
            val fileName = entry.optString("fileName", stateFileName(key))
            repository.downloadNamedJsonOrNull(accessToken, folder.identifier, fileName)?.let { bytes ->
                remoteDocuments.put(JSONObject(bytes.toString(Charsets.UTF_8)))
                downloadedVersions += key to remoteVersion
            }
        }
        if (remoteDocuments.length() > 0 || (manifest.optJSONArray("tombstones")?.length() ?: 0) > 0) {
            onStep("4/5 · Aplicando cambios descargados de forma segura")
            mergeRemoteRoot(JSONObject().put("schemaVersion", 2)
                .put("documents", remoteDocuments)
                .put("tombstones", manifest.optJSONArray("tombstones") ?: JSONArray()))
            downloadedVersions.forEach { (key, version) -> remoteVersions.markApplied(key, version) }
        }
        onStep("5/5 · Descarga completada")
        return FullSyncResult(localByKey.size, downloadedBookCount)
    }

    fun uploadAll(
        accessToken: String,
        accountIdentifier: String,
        folder: GoogleDriveFolder,
        repository: GoogleDriveFolderRepository,
        onStep: (String) -> Unit = {}
    ): FullSyncResult {
        onStep("2/4 · Leyendo el manifiesto para comparar versiones")
        val manifest = loadManifestForUpload(accessToken, accountIdentifier, folder, repository)
        val remoteEntries = manifest.optJSONObject("books") ?: JSONObject()
        val updatedEntries = JSONObject(remoteEntries.toString())
        val localRoot = localRoot()
        val remoteVersions = RemoteBookStateVersionRepository(context, accountIdentifier)
        var uploadedCount = 0
        localRoot.documents().forEach { document ->
            val key = document.optString("documentKey")
            if (key.isBlank()) return@forEach
            val localUpdatedAt = stateUpdatedAt(document)
            val previous = remoteEntries.optJSONObject(key)
            val fileName = previous?.optString("fileName")?.takeIf { it.isNotBlank() } ?: stateFileName(key)
            val remoteDocument = previous?.let {
                repository.downloadNamedJsonOrNull(accessToken, folder.identifier, fileName)
                    ?.let { bytes -> JSONObject(bytes.toString(Charsets.UTF_8)) }
            }
            val merge = remoteDocument?.let { DirectionalSyncPolicy.mergeBookState(it, document) }
            val payload = merge?.value ?: document
            val shouldUpload = previous == null ||
                DirectionalSyncPolicy.shouldUpload(localUpdatedAt, previous.optLong("updatedAt", 0)) ||
                merge?.containsLocalChanges == true
            if (shouldUpload) {
                onStep("3/4 · Subiendo ${document.optString("title", "libro")}")
                repository.uploadNamedJson(accessToken, folder.identifier, fileName, payload.toString().toByteArray())
                val remoteRevision = maxOf(
                    localUpdatedAt,
                    System.currentTimeMillis(),
                    (previous?.optLong("updatedAt", 0) ?: 0) + 1
                )
                updatedEntries.put(key, JSONObject().put("fileName", fileName).put("updatedAt", remoteRevision))
                remoteVersions.markApplied(key, remoteRevision)
                uploadedCount++
            }
        }
        val updatedManifest = JSONObject()
            .put("schemaVersion", MANIFEST_SCHEMA_VERSION)
            .put("books", updatedEntries)
            .put("tombstones", DirectionalSyncPolicy.mergeTombstones(
                manifest.optJSONArray("tombstones") ?: JSONArray(),
                localRoot.optJSONArray("tombstones") ?: JSONArray()
            ))
            .put("updatedAt", System.currentTimeMillis())
        repository.uploadNamedJson(accessToken, folder.identifier, MANIFEST_FILE_NAME, updatedManifest.toString(2).toByteArray())
        onStep("4/4 · Subida completada: $uploadedCount libros con cambios")
        return FullSyncResult(updatedEntries.length(), 0)
    }

    fun synchronizeBook(
        accessToken: String,
        accountIdentifier: String,
        folder: GoogleDriveFolder,
        repository: GoogleDriveFolderRepository,
        documentIdentifier: Long
    ) {
        if (database.findDocument(documentIdentifier) == null) return
        val manifest = loadManifest(accessToken, accountIdentifier, folder, repository)
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
        val previousEntry = entries.optJSONObject(key)
        val fileName = previousEntry?.optString("fileName")?.takeIf { it.isNotBlank() } ?: stateFileName(key)
        repository.uploadNamedJson(accessToken, folder.identifier, fileName, localDocument.toString().toByteArray())
        val remoteRevision = maxOf(
            updatedAt,
            System.currentTimeMillis(),
            (previousEntry?.optLong("updatedAt", 0) ?: 0) + 1
        )
        entries.put(key, JSONObject().put("fileName", fileName).put("updatedAt", remoteRevision))
        manifest.put("schemaVersion", MANIFEST_SCHEMA_VERSION)
            .put("books", entries)
            .put("tombstones", localRoot.optJSONArray("tombstones") ?: JSONArray())
            .put("updatedAt", System.currentTimeMillis())
        repository.uploadNamedJson(accessToken, folder.identifier, MANIFEST_FILE_NAME, manifest.toString(2).toByteArray())
        RemoteBookStateVersionRepository(context, accountIdentifier).markApplied(key, remoteRevision)
    }

    private fun mergeRemoteRoot(root: JSONObject) {
        val bytes = root.toString().toByteArray()
        SyncSafetyBackupRepository(context).saveCurrentLibraryState()
        LibraryReadingStateMerger(context).merge(bytes, createSafetyBackup = false)
        LibraryDictionaryStateMerger(context).merge(bytes, createSafetyBackup = false)
        LibraryDeletionSynchronizer(context).apply(bytes, createSafetyBackup = false)
    }

    private fun loadManifest(
        accessToken: String,
        accountIdentifier: String,
        folder: GoogleDriveFolder,
        repository: GoogleDriveFolderRepository,
        beforeLegacyMigration: () -> Unit = {}
    ): JSONObject {
        repository.downloadNamedJsonOrNull(accessToken, folder.identifier, MANIFEST_FILE_NAME)?.let { bytes ->
            return JSONObject(bytes.toString(Charsets.UTF_8))
        }
        beforeLegacyMigration()
        repository.downloadLibrarySnapshotOrNull(accessToken, accountIdentifier, folder.identifier)?.let { bytes ->
            mergeRemoteRoot(JSONObject(bytes.toString(Charsets.UTF_8)))
        }
        return emptyManifest()
    }

    private fun loadManifestForUpload(
        accessToken: String,
        accountIdentifier: String,
        folder: GoogleDriveFolder,
        repository: GoogleDriveFolderRepository
    ): JSONObject {
        repository.downloadNamedJsonOrNull(accessToken, folder.identifier, MANIFEST_FILE_NAME)?.let { bytes ->
            return JSONObject(bytes.toString(Charsets.UTF_8))
        }
        if (repository.downloadLibrarySnapshotOrNull(accessToken, accountIdentifier, folder.identifier) != null) {
            error("Hay un respaldo anterior en Drive. Descarga los cambios una vez antes de subir.")
        }
        return emptyManifest()
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
