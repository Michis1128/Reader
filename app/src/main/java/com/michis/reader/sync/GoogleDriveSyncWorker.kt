package com.michis.reader.sync

import com.michis.reader.data.*
import com.michis.reader.sync.drive.*

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.auth.api.identity.AuthorizationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GoogleDriveSyncWorker(appContext: Context, parameters: WorkerParameters) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val scheduler = AutomaticDriveSyncScheduler(applicationContext)
        publishProgress(scheduler, "Preparando la sincronización…")
        if (!scheduler.isEnabled() && !inputData.getBoolean(KEY_MANUAL_EXECUTION, false)) return@withContext Result.success()
        val session = OptionalGoogleAccountManager(applicationContext).currentSession()
            ?: return@withContext statusSuccess(scheduler, "Omitida: no hay una cuenta conectada")
        val authorization = GoogleDriveAuthorizationManager(applicationContext)
        if (!authorization.isAuthorized()) return@withContext statusSuccess(scheduler, "Omitida: Drive no está autorizado")
        val repository = GoogleDriveFolderRepository(applicationContext)
        val folder = repository.savedFolder(session.accountIdentifier)
            ?: return@withContext statusSuccess(scheduler, "Omitida: no hay una carpeta vinculada")
        try {
            val authorizationResult = authorization.authorizationClient
                .authorize(authorization.authorizationRequest(session.accountIdentifier)).awaitResult()
            if (authorizationResult.hasResolution() || !authorization.acceptAuthorizationResult(authorizationResult)) {
                return@withContext statusSuccess(scheduler, "Pendiente: abre la app para renovar la autorización")
            }
            val accessToken = authorizationResult.accessToken
                ?: return@withContext statusSuccess(scheduler, "Pendiente: Google no entregó un token temporal")
            val documentIdentifier = inputData.getLong(KEY_DOCUMENT_IDENTIFIER, -1L)
            if (documentIdentifier >= 0) {
                publishProgress(scheduler, "Sincronizando los cambios del último libro…")
                GoogleDriveSyncCoordinator(applicationContext).synchronizeBook(
                    accessToken, session.accountIdentifier, folder, repository, documentIdentifier
                )
                scheduler.saveStatus("Correcta: último libro sincronizado")
            } else {
                val direction = SyncDirection.fromStorageValue(inputData.getString(KEY_SYNC_DIRECTION))
                val coordinator = GoogleDriveSyncCoordinator(applicationContext)
                val syncResult = when (direction) {
                    SyncDirection.UPLOAD -> coordinator.upload(
                        accessToken, session.accountIdentifier, folder, repository
                    ) { step -> publishProgress(scheduler, step) }
                    SyncDirection.DOWNLOAD -> coordinator.download(
                        accessToken, session.accountIdentifier, folder, repository
                    ) { step -> publishProgress(scheduler, step) }
                    SyncDirection.BIDIRECTIONAL -> coordinator.synchronize(
                        accessToken, session.accountIdentifier, folder, repository
                    ) { step -> publishProgress(scheduler, step) }
                }
                scheduler.saveStatus(when (direction) {
                    SyncDirection.UPLOAD -> "Correcta: cambios locales subidos"
                    SyncDirection.DOWNLOAD -> "Correcta: ${syncResult.downloadedDocumentCount} libros descargados y datos actualizados"
                    SyncDirection.BIDIRECTIONAL -> "Correcta: ${syncResult.documentCount} libros sincronizados"
                })
            }
            Result.success()
        } catch (error: Exception) {
            scheduler.saveStatus("Error temporal: ${error.message.orEmpty()}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun statusSuccess(scheduler: AutomaticDriveSyncScheduler, message: String): Result {
        scheduler.saveStatus(message)
        return Result.success()
    }

    private fun publishProgress(scheduler: AutomaticDriveSyncScheduler, message: String) {
        setProgressAsync(workDataOf(KEY_PROGRESS_MESSAGE to message))
        scheduler.saveStatus("En curso: $message")
    }

    private suspend fun com.google.android.gms.tasks.Task<AuthorizationResult>.awaitResult(): AuthorizationResult =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }

    companion object {
        const val KEY_MANUAL_EXECUTION = "manual_execution"
        const val KEY_DOCUMENT_IDENTIFIER = "document_identifier"
        const val KEY_PROGRESS_MESSAGE = "progress_message"
        const val KEY_SYNC_DIRECTION = "sync_direction"
    }
}

enum class SyncDirection(val storageValue: String) {
    BIDIRECTIONAL("bidirectional"),
    UPLOAD("upload"),
    DOWNLOAD("download");

    companion object {
        fun fromStorageValue(value: String?): SyncDirection = entries.firstOrNull {
            it.storageValue == value
        } ?: BIDIRECTIONAL
    }
}
