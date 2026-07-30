package com.michis.reader.sync

import com.michis.reader.data.*
import com.michis.reader.sync.drive.*

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GoogleDriveSyncWorker(appContext: Context, parameters: WorkerParameters) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val scheduler = AutomaticDriveSyncScheduler(applicationContext)
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
            val syncResult = GoogleDriveSyncCoordinator(applicationContext).synchronize(
                accessToken, session.accountIdentifier, folder, repository
            )
            scheduler.saveStatus("Correcta: ${syncResult.documentCount} libros sincronizados")
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

    private suspend fun com.google.android.gms.tasks.Task<AuthorizationResult>.awaitResult(): AuthorizationResult =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }

    companion object { const val KEY_MANUAL_EXECUTION = "manual_execution" }
}
