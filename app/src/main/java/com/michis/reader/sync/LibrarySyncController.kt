package com.michis.reader.sync

import com.michis.reader.data.*
import com.michis.reader.sync.drive.*

import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.work.WorkInfo

/** Estado y ejecución manual de la sincronización de biblioteca con Drive. */
internal class LibrarySyncController(
    private val activity: ComponentActivity,
    private val statusText: TextView,
    private val syncButton: Button,
    private val openSettings: () -> Unit,
    private val refreshLibrary: () -> Unit
) {
    private val scheduler = AutomaticDriveSyncScheduler(activity)

    init {
        scheduler.immediateSyncWorkInfos().observe(activity) { workInfos ->
            val workInfo = scheduler.latestImmediateWorkInfo(workInfos) ?: return@observe
            val active = workInfo.state == WorkInfo.State.ENQUEUED ||
                workInfo.state == WorkInfo.State.BLOCKED || workInfo.state == WorkInfo.State.RUNNING
            syncButton.isEnabled = !active
            statusText.text = when (workInfo.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                    "Sincronización: en espera de conexión…"
                WorkInfo.State.RUNNING -> "Sincronización: ${workInfo.progress.getString(
                    GoogleDriveSyncWorker.KEY_PROGRESS_MESSAGE
                ) ?: "trabajando en segundo plano…"}"
                WorkInfo.State.SUCCEEDED -> {
                    refreshLibrary()
                    "Sincronización: ${scheduler.lastStatus()}"
                }
                WorkInfo.State.FAILED -> "Sincronización: ${scheduler.lastStatus()}"
                WorkInfo.State.CANCELLED -> "Sincronización: cancelada"
            }
        }
    }

    fun refreshStatus() {
        val session = OptionalGoogleAccountManager(activity).currentSession()
        val authorization = GoogleDriveAuthorizationManager(activity)
        val folder = session?.let { GoogleDriveFolderRepository(activity).savedFolder(it.accountIdentifier) }
        statusText.text = when {
            session == null -> "Sincronización: cuenta de Google no conectada"
            !authorization.isAuthorized() -> "Sincronización: Drive no autorizado"
            folder == null -> "Sincronización: carpeta no preparada"
            else -> "Sincronización: ${scheduler.lastStatus()}"
        }
    }

    fun synchronize() {
        val session = OptionalGoogleAccountManager(activity).currentSession()
        val authorization = GoogleDriveAuthorizationManager(activity)
        val folderRepository = GoogleDriveFolderRepository(activity)
        val folder = session?.let { folderRepository.savedFolder(it.accountIdentifier) }
        if (session == null || !authorization.isAuthorized() || folder == null) {
            message("Completa la conexión con Drive desde Configuración", true)
            openSettings()
            return
        }
        setWorkingState("Sincronización: conectando…")
        authorization.authorizationClient
            .authorize(authorization.authorizationRequest(session.accountIdentifier))
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    finishWorking("Sincronización: autorización pendiente")
                    message("Abre Configuración para renovar el permiso de Drive", true)
                    openSettings()
                    return@addOnSuccessListener
                }
                val accessToken = result.accessToken
                if (!authorization.acceptAuthorizationResult(result) || accessToken.isNullOrBlank()) {
                    finishWorking("Sincronización: Google no concedió acceso")
                    return@addOnSuccessListener
                }
                scheduler.enqueueImmediateSync()
                statusText.text = "Sincronización: preparada para ejecutarse en segundo plano…"
                message("La sincronización continuará en segundo plano")
            }
            .addOnFailureListener { error ->
                finishWorking("Sincronización: error de autorización")
                message(error.message.orEmpty(), true)
            }
    }

    private fun setWorkingState(status: String) {
        syncButton.isEnabled = false
        statusText.text = status
    }

    private fun finishWorking(status: String) {
        syncButton.isEnabled = true
        statusText.text = status
    }

    private fun message(value: String, long: Boolean = false) = Toast.makeText(
        activity, value, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    ).show()
}
