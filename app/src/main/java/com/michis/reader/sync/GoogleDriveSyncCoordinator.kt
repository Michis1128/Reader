package com.michis.reader.sync

import com.michis.reader.data.*
import com.michis.reader.sync.drive.*

import android.content.Context
import java.io.File

data class FullSyncResult(
    val firstBackup: Boolean,
    val documentCount: Int,
    val downloadedDocumentCount: Int,
    val readingMerge: ReadingMergeResult,
    val dictionaryMerge: DictionaryMergeResult,
    val deletionMerge: DeletionMergeResult
)

class GoogleDriveSyncCoordinator(private val context: Context) {
    fun synchronize(
        accessToken: String,
        accountIdentifier: String,
        folder: GoogleDriveFolder,
        repository: GoogleDriveFolderRepository
    ): FullSyncResult = synchronized(SYNC_LOCK) {
        val bookSync = GoogleDriveBookLibraryRepository(context)
            .synchronizeSelectedFolder(accessToken, accountIdentifier)
        val localBefore = LibrarySyncSnapshotBuilder(context).build()
        saveSafetyCopy(localBefore.bytes)
        val remoteBytes = repository.downloadLibrarySnapshotOrNull(accessToken, accountIdentifier, folder.identifier)
        if (remoteBytes == null) {
            repository.uploadAndVerifyLibrarySnapshot(accessToken, accountIdentifier, folder.identifier, localBefore)
            return@synchronized FullSyncResult(true, localBefore.documentCount, bookSync.downloadedFiles, emptyReadingResult(), emptyDictionaryResult(), DeletionMergeResult(0, 0, 0))
        }

        val reading = LibraryReadingStateMerger(context).merge(remoteBytes)
        val dictionaries = LibraryDictionaryStateMerger(context).merge(remoteBytes)
        val deletions = LibraryDeletionSynchronizer(context).apply(remoteBytes)
        val localAfter = LibrarySyncSnapshotBuilder(context).build()
        val combined = LibrarySyncUploadMerger().merge(localAfter, remoteBytes)
        repository.uploadAndVerifyLibrarySnapshot(accessToken, accountIdentifier, folder.identifier, combined)
        FullSyncResult(false, combined.documentCount, bookSync.downloadedFiles, reading, dictionaries, deletions)
    }

    private fun saveSafetyCopy(bytes: ByteArray) {
        val directory = File(context.filesDir, "sync-safety").apply { mkdirs() }
        val temporary = File(directory, "library-state-before-full-sync.tmp")
        val destination = File(directory, "library-state-before-full-sync.json")
        temporary.writeBytes(bytes)
        check(temporary.renameTo(destination) || runCatching {
            temporary.copyTo(destination, overwrite = true); temporary.delete(); true
        }.getOrDefault(false)) { "No se pudo guardar la copia previa a la sincronización"
        }
    }

    private fun emptyReadingResult() = ReadingMergeResult(0, 0, 0, 0)
    private fun emptyDictionaryResult() = DictionaryMergeResult(0, 0, 0, 0, 0, 0)

    companion object { private val SYNC_LOCK = Any() }
}
