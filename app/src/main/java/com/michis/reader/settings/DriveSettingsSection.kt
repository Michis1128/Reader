package com.michis.reader.settings

import com.michis.reader.sync.AutomaticDriveSyncScheduler
import com.michis.reader.sync.DriveSyncConfirmationController
import com.michis.reader.sync.GoogleDriveSyncWorker
import com.michis.reader.sync.SyncDirection
import com.michis.reader.sync.drive.DriveLibraryPickerActivity
import com.michis.reader.sync.drive.GoogleAccountSession
import com.michis.reader.sync.drive.GoogleDriveAuthorizationManager
import com.michis.reader.sync.drive.GoogleDriveBookLibraryRepository
import com.michis.reader.sync.drive.GoogleDriveFolderRepository
import com.michis.reader.sync.drive.OptionalGoogleAccountManager
import com.michis.reader.ui.compose.MichisReaderButton
import com.michis.reader.ui.compose.MichisReaderInputShape

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import com.google.android.gms.auth.api.identity.AuthorizationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Coordina la UI Compose de cuenta y Drive sin asumir que el usuario desea iniciar sesión. */
class DriveSettingsSection(
    private val activity: ComponentActivity,
    private val advancedMode: Boolean,
    private val openAdvancedSettings: () -> Unit
) {
    private val scheduler = AutomaticDriveSyncScheduler(activity)
    private val confirmation = DriveSyncConfirmationController(activity)
    private var pendingAuthorizationResult: ((AuthorizationResult?) -> Unit)? = null
    private var revision by mutableIntStateOf(0)
    private var busy by mutableStateOf(false)
    private var fullSyncInProgress by mutableStateOf(false)
    private var lastFullSyncText by mutableStateOf<String?>(null)

    private val libraryPickerLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        busy = false
        if (result.resultCode == Activity.RESULT_OK) refresh()
    }

    private val authorizationLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val callback = pendingAuthorizationResult
        pendingAuthorizationResult = null
        val authorization = result.data?.takeIf { result.resultCode == Activity.RESULT_OK }?.let { intent ->
            runCatching {
                GoogleDriveAuthorizationManager(activity).authorizationClient.getAuthorizationResultFromIntent(intent)
            }.getOrNull()
        }
        callback?.invoke(authorization)
    }

    init {
        scheduler.immediateSyncWorkInfos().observe(activity) { workInfos ->
            val workInfo = scheduler.latestImmediateWorkInfo(workInfos) ?: return@observe
            fullSyncInProgress = workInfo.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING)
            lastFullSyncText = when (workInfo.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Sincronización en espera de conexión. Puedes seguir usando la app."
                WorkInfo.State.RUNNING -> workInfo.progress.getString(GoogleDriveSyncWorker.KEY_PROGRESS_MESSAGE)
                    ?: "Sincronización ejecutándose en segundo plano…"
                WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED -> scheduler.lastStatus()
                WorkInfo.State.CANCELLED -> "Sincronización cancelada."
            }
            refresh()
        }
    }

    @Composable
    fun Content() {
        @Suppress("UNUSED_EXPRESSION") revision
        val accountManager = remember { OptionalGoogleAccountManager(activity) }
        val session = accountManager.currentSession()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (session == null) SignedOutContent(accountManager) else SignedInContent(session, accountManager)
        }
    }

    @Composable
    private fun SignedOutContent(accountManager: OptionalGoogleAccountManager) {
        SettingsDescription("La lectura local no requiere una cuenta. Google se utilizará únicamente si decides activar la sincronización.")
        MichisReaderButton("Iniciar sesión con Google", { signIn(accountManager) }, Modifier.fillMaxWidth(), !busy)
    }

    @Composable
    private fun SignedInContent(session: GoogleAccountSession, accountManager: OptionalGoogleAccountManager) {
        val authorization = GoogleDriveAuthorizationManager(activity)
        AccountHeader(session)
        if (authorization.isAuthorized()) AuthorizedContent(session, authorization)
        else {
            SettingsDescription("Google Drive todavía no está autorizado. El permiso permite leer la biblioteca EPUB que elijas y mantener sincronizados sus libros.")
            MichisReaderButton("Activar sincronización con Drive", {
                busy = true
                requestAuthorization(session, authorization) { busy = false; refresh() }
            }, Modifier.fillMaxWidth(), !busy)
        }
        MichisReaderButton("Cerrar sesión", { signOut(accountManager) }, Modifier.fillMaxWidth(), !busy)
        if (!advancedMode) MichisReaderButton("Ajustes avanzados de Drive", openAdvancedSettings, Modifier.fillMaxWidth())
    }

    @Composable
    private fun AuthorizedContent(session: GoogleAccountSession, authorization: GoogleDriveAuthorizationManager) {
        val folderRepository = remember { GoogleDriveFolderRepository(activity) }
        val savedFolder = folderRepository.savedFolder(session.accountIdentifier)
        val selectedSources = GoogleDriveBookLibraryRepository(activity).selectedSources(session.accountIdentifier)
        SettingsDescription("Google Drive está conectado. Puedes elegir libros o carpetas como biblioteca compartida entre tus dispositivos.")
        SettingsDescription(savedFolder?.let { "Carpeta vinculada: ${it.name}" } ?: "La carpeta de sincronización todavía no ha sido preparada.")
        lastFullSyncText?.let { SettingsDescription(it) }
        if (advancedMode || savedFolder == null) {
            MichisReaderButton(
                if (savedFolder == null) "Preparar carpeta Michis Reader" else "Verificar carpeta Michis Reader",
                { prepareFolder(session, authorization, folderRepository) },
                Modifier.fillMaxWidth(), !busy
            )
        }
        SettingsDescription(if (selectedSources.isEmpty()) "Todavía no has elegido libros o carpetas de Drive." else "Biblioteca de Drive: ${selectedSources.size} elementos seleccionados")
        MichisReaderButton(
            if (selectedSources.isEmpty()) "Elegir libros y carpetas" else "Editar libros y carpetas",
            { chooseLibrarySources(session, authorization) }, Modifier.fillMaxWidth(), !busy
        )
        if (savedFolder != null) {
            SettingsDescription("Elige Subir para enviar tus avances y anotaciones. Elige Descargar para recibir libros y cambios guardados en Drive. Ninguna opción reemplaza cambios más recientes.")
            MichisReaderButton(
                if (fullSyncInProgress) "Trabajando en Drive…" else "Subir mis cambios",
                { synchronize(session, authorization, SyncDirection.UPLOAD) }, Modifier.fillMaxWidth(), !fullSyncInProgress && !busy
            )
            MichisReaderButton(
                if (fullSyncInProgress) "Trabajando en Drive…" else "Descargar cambios de Drive",
                { synchronize(session, authorization, SyncDirection.DOWNLOAD) }, Modifier.fillMaxWidth(), !fullSyncInProgress && !busy
            )
            AutomaticSyncControls()
        }
        if (advancedMode) {
            MichisReaderButton("Revocar acceso a Google Drive", { revokeDrive(session, authorization) }, Modifier.fillMaxWidth(), !busy)
            MichisReaderButton("Volver a mostrar advertencias de sincronización", {
                confirmation.restoreWarnings()
                Toast.makeText(activity, "Las advertencias volverán a mostrarse", Toast.LENGTH_SHORT).show()
            }, Modifier.fillMaxWidth())
        }
    }

    @Composable
    private fun AccountHeader(session: GoogleAccountSession) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AndroidView(
                modifier = Modifier.size(52.dp).clip(CircleShape),
                factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                update = { loadProfilePicture(it, session.profilePictureUri) }
            )
            Column(Modifier.weight(1f)) {
                Text(session.displayName.ifBlank { "Cuenta de Google" }, style = MaterialTheme.typography.titleMedium)
                Text(session.accountIdentifier, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun AutomaticSyncControls() {
        var enabled by remember { mutableStateOf(scheduler.isEnabled()) }
        var wifiOnly by remember { mutableStateOf(scheduler.wifiOnly()) }
        val intervals = listOf(15L, 60L, 360L, 1_440L)
        val labels = listOf("Cada 15 minutos", "Cada hora", "Cada 6 horas", "Cada 24 horas")
        var interval by remember { mutableStateOf(scheduler.intervalMinutes()) }
        Text("Sincronización automática", style = MaterialTheme.typography.titleMedium)
        DriveToggle("Sincronizar cuando haya conexión", enabled) { enabled = it; scheduler.setEnabled(it) }
        DriveDropdown("Frecuencia", labels, labels[intervals.indexOf(interval).coerceAtLeast(0)]) { label ->
            interval = intervals[labels.indexOf(label)]
            scheduler.setIntervalMinutes(interval)
        }
        DriveToggle("Sincronizar solo con Wi-Fi", wifiOnly) { wifiOnly = it; scheduler.setWifiOnly(it) }
        SettingsDescription("Último estado: ${scheduler.lastStatus()}")
    }

    private fun signIn(manager: OptionalGoogleAccountManager) {
        busy = true
        activity.lifecycleScope.launch {
            runCatching { manager.signIn(activity) }
                .onSuccess { Toast.makeText(activity, "Sesión iniciada", Toast.LENGTH_SHORT).show() }
                .onFailure { error ->
                    val message = when (error) {
                        is GetCredentialCancellationException -> "Inicio de sesión cancelado"
                        is NoCredentialException -> "No se encontró una cuenta de Google disponible"
                        else -> "No se pudo iniciar sesión: ${error.message.orEmpty()}"
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }
            busy = false
            refresh()
        }
    }

    private fun signOut(manager: OptionalGoogleAccountManager) {
        busy = true
        activity.lifecycleScope.launch {
            runCatching { manager.signOut() }.onFailure {
                Toast.makeText(activity, "La sesión local se cerró, pero Google no pudo limpiar el selector de cuenta", Toast.LENGTH_LONG).show()
            }
            busy = false
            refresh()
        }
    }

    private fun requestAuthorization(
        session: GoogleAccountSession,
        manager: GoogleDriveAuthorizationManager,
        finished: () -> Unit = {},
        authorized: (String) -> Unit = {}
    ) {
        manager.authorizationClient.authorize(manager.authorizationRequest(session.accountIdentifier))
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        busy = false; finished(); message("Google no proporcionó una pantalla de autorización", true)
                        return@addOnSuccessListener
                    }
                    pendingAuthorizationResult = { resolved ->
                        if (resolved != null && manager.acceptAuthorizationResult(resolved)) {
                            message("Drive autorizado")
                            resolved.accessToken?.let(authorized)
                        } else message("No se concedió el permiso de Drive", true)
                        busy = false; finished(); refresh()
                    }
                    authorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } else if (manager.acceptAuthorizationResult(result)) {
                    message("Drive autorizado")
                    result.accessToken?.let(authorized)
                    busy = false; finished(); refresh()
                } else {
                    busy = false; finished(); message("Google no concedió el permiso solicitado", true)
                }
            }
            .addOnFailureListener { error ->
                busy = false; finished(); message("No se pudo autorizar Drive: ${error.message.orEmpty()}", true)
            }
    }

    private fun prepareFolder(session: GoogleAccountSession, manager: GoogleDriveAuthorizationManager, repository: GoogleDriveFolderRepository) {
        busy = true
        requestAuthorization(session, manager, finished = { busy = false }, authorized = { token ->
            activity.lifecycleScope.launch {
                runCatching { withContext(Dispatchers.IO) { repository.ensureSyncFolder(token, session.accountIdentifier) } }
                    .onSuccess { message("Carpeta ${it.name} preparada") }
                    .onFailure { message("No se pudo preparar la carpeta: ${it.message.orEmpty()}", true) }
                busy = false; refresh()
            }
        })
    }

    private fun chooseLibrarySources(session: GoogleAccountSession, manager: GoogleDriveAuthorizationManager) {
        busy = true
        requestAuthorization(session, manager, finished = { busy = false }, authorized = { token ->
            libraryPickerLauncher.launch(Intent(activity, DriveLibraryPickerActivity::class.java).apply {
                putExtra(DriveLibraryPickerActivity.EXTRA_ACCOUNT_IDENTIFIER, session.accountIdentifier)
                putExtra(DriveLibraryPickerActivity.EXTRA_ACCESS_TOKEN, token)
            })
        })
    }

    private fun synchronize(session: GoogleAccountSession, manager: GoogleDriveAuthorizationManager, direction: SyncDirection) {
        confirmation.confirm(direction) {
            busy = true
            requestAuthorization(session, manager, finished = { busy = false }) {
                scheduler.enqueueImmediateSync(direction)
                lastFullSyncText = if (direction == SyncDirection.UPLOAD) "Subida preparada para ejecutarse en segundo plano…" else "Descarga preparada para ejecutarse en segundo plano…"
                message("Puedes seguir usando la app mientras Drive trabaja", true)
                refresh()
            }
        }
    }

    private fun revokeDrive(session: GoogleAccountSession, manager: GoogleDriveAuthorizationManager) {
        busy = true
        manager.authorizationClient.revokeAccess(manager.revokeRequest(session.accountIdentifier))
            .addOnSuccessListener {
                scheduler.setEnabled(false)
                manager.clearLocalAuthorizationState()
                busy = false; message("Acceso a Drive revocado"); refresh()
            }
            .addOnFailureListener { busy = false; message("No se pudo revocar Drive: ${it.message.orEmpty()}", true) }
    }

    private fun loadProfilePicture(target: ImageView, value: String) {
        if (value.isBlank() || target.tag == value) return
        target.tag = value
        activity.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(value)
                    val stream = if (uri.scheme in setOf("http", "https")) {
                        java.net.URL(value).openConnection().apply { connectTimeout = 5_000; readTimeout = 5_000 }.getInputStream()
                    } else activity.contentResolver.openInputStream(uri)
                    stream?.use(BitmapFactory::decodeStream)
                }.getOrNull()
            }
            if (bitmap != null && target.isAttachedToWindow && target.tag == value) target.setImageBitmap(bitmap)
        }
    }

    private fun refresh() { revision += 1 }
    private fun message(value: String, long: Boolean = false) = Toast.makeText(activity, value, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

@Composable
private fun DriveToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, change)
    }
}

@Composable
private fun DriveDropdown(label: String, options: List<String>, selected: String, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var current by remember(selected) { mutableStateOf(selected) }
    Text(label, style = MaterialTheme.typography.labelLarge)
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MichisReaderInputShape) {
            Text(current, Modifier.fillMaxWidth())
        }
        DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 240.dp)) {
            options.forEach { option -> DropdownMenuItem({ Text(option) }, {
                current = option; expanded = false; select(option)
            }) }
        }
    }
}
