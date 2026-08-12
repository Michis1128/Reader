package com.michis.reader.sync

import android.content.Context
import com.michis.reader.data.*
import com.michis.reader.sync.drive.*

data class FullSyncResult(
    val documentCount: Int,
    val downloadedDocumentCount: Int
)

/** Coordina la detección de EPUB modificados y los estados incrementales por libro. */
class GoogleDriveSyncCoordinator(private val context: Context) {
    fun download(
        accessToken: String,
        accountIdentifier: String,
        folder: GoogleDriveFolder,
        repository: GoogleDriveFolderRepository,
        onStep: (String) -> Unit = {}
    ): FullSyncResult = synchronized(SYNC_LOCK) {
        onStep("1/5 · Buscando libros nuevos o modificados en Drive")
        val books = GoogleDriveBookLibraryRepository(context)
            .synchronizeSelectedFolder(accessToken, accountIdentifier)
        onStep("2/5 · ${books.downloadedFiles} libros descargados")
        IncrementalLibrarySyncCoordinator(context).downloadAll(
            accessToken, accountIdentifier, folder, repository, books.downloadedFiles, onStep
        )
    }

    fun upload(
        accessToken: String,
        accountIdentifier: String,
        folder: GoogleDriveFolder,
        repository: GoogleDriveFolderRepository,
        onStep: (String) -> Unit = {}
    ): FullSyncResult = synchronized(SYNC_LOCK) {
        onStep("1/4 · Preparando tus cambios locales")
        IncrementalLibrarySyncCoordinator(context).uploadAll(
            accessToken, accountIdentifier, folder, repository, onStep
        )
    }
    fun synchronize(
        accessToken: String,
        accountIdentifier: String,
        folder: GoogleDriveFolder,
        repository: GoogleDriveFolderRepository,
        onStep: (String) -> Unit = {}
    ): FullSyncResult = synchronized(SYNC_LOCK) {
        onStep("1/7 · Consultando cambios de Google Drive")
        val books = GoogleDriveBookLibraryRepository(context)
            .synchronizeSelectedFolder(accessToken, accountIdentifier)
        onStep("2/7 · ${books.discoveredFiles} libros revisados, ${books.downloadedFiles} descargados")
        IncrementalLibrarySyncCoordinator(context).synchronizeAll(
            accessToken = accessToken,
            accountIdentifier = accountIdentifier,
            folder = folder,
            repository = repository,
            downloadedBookCount = books.downloadedFiles,
            onStep = onStep
        )
    }

    fun synchronizeBook(
        accessToken: String,
        accountIdentifier: String,
        folder: GoogleDriveFolder,
        repository: GoogleDriveFolderRepository,
        documentIdentifier: Long
    ) = synchronized(SYNC_LOCK) {
        IncrementalLibrarySyncCoordinator(context).synchronizeBook(
            accessToken, accountIdentifier, folder, repository, documentIdentifier
        )
    }

    companion object { private val SYNC_LOCK = Any() }
}
