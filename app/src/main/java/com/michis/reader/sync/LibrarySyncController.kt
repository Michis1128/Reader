package com.michis.reader.sync

import com.michis.reader.sync.drive.GoogleDriveAuthorizationManager
import com.michis.reader.sync.drive.GoogleDriveFolderRepository
import com.michis.reader.sync.drive.OptionalGoogleAccountManager

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.work.WorkInfo

/** Estado y ejecución manual de la sincronización de biblioteca con Drive. */
internal class LibrarySyncController(
    private val activity: ComponentActivity,
    private val updateUi: (status: String, actionsEnabled: Boolean) -> Unit,
    private val openSettings: () -> Unit,
    private val refreshLibrary: () -> Unit
) {
    private val scheduler = AutomaticDriveSyncScheduler(activity)
    private val confirmationController = DriveSyncConfirmationController(activity)

    init {
        scheduler.immediateSyncWorkInfos().observe(activity) { workInfos ->
            val workInfo = scheduler.latestImmediateWorkInfo(workInfos) ?: return@observe
            val active = workInfo.state == WorkInfo.State.ENQUEUED ||
                workInfo.state == WorkInfo.State.BLOCKED || workInfo.state == WorkInfo.State.RUNNING
            val status = when (workInfo.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Sincronización: en espera de conexión…"
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
            updateUi(status, !active)
        }
    }

    fun refreshStatus() {
        val session = OptionalGoogleAccountManager(activity).currentSession()
        val authorization = GoogleDriveAuthorizationManager(activity)
        val folder = session?.let { GoogleDriveFolderRepository(activity).savedFolder(it.accountIdentifier) }
        val status = when {
            session == null -> "Sincronización: cuenta de Google no conectada"
            !authorization.isAuthorized() -> "Sincronización: Drive no autorizado"
            folder == null -> "Sincronización: carpeta no preparada"
            else -> "Sincronización: ${scheduler.lastStatus()}"
        }
        updateUi(status, true)
    }

    fun synchronize(direction: SyncDirection) {
        confirmationController.confirm(direction) { synchronizeConfirmed(direction) }
    }

    private fun synchronizeConfirmed(direction: SyncDirection) {
        val session = OptionalGoogleAccountManager(activity).currentSession()
        val authorization = GoogleDriveAuthorizationManager(activity)
        val folder = session?.let { GoogleDriveFolderRepository(activity).savedFolder(it.accountIdentifier) }
        if (session == null || !authorization.isAuthorized() || folder == null) {
            message("Completa la conexión con Drive desde Configuración", true)
            openSettings()
            return
        }
        val action = if (direction == SyncDirection.UPLOAD) "subir" else "descargar"
        updateUi("Drive: preparando la acción de $action…", false)
        authorization.authorizationClient
            .authorize(authorization.authorizationRequest(session.accountIdentifier))
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    updateUi("Sincronización: autorización pendiente", true)
                    message("Abre Configuración para renovar el permiso de Drive", true)
                    openSettings()
                    return@addOnSuccessListener
                }
                val accessToken = result.accessToken
                if (!authorization.acceptAuthorizationResult(result) || accessToken.isNullOrBlank()) {
                    updateUi("Sincronización: Google no concedió acceso", true)
                    return@addOnSuccessListener
                }
                scheduler.enqueueImmediateSync(direction)
                updateUi("Drive: preparado para $action en segundo plano…", false)
                message("Los cambios se van a $action en segundo plano")
            }
            .addOnFailureListener { error ->
                updateUi("Sincronización: error de autorización", true)
                message(error.message.orEmpty(), true)
            }
    }

    private fun message(value: String, long: Boolean = false) = Toast.makeText(
        activity,
        value,
        if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    ).show()
}
