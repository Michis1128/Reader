package com.michis.reader.settings

import com.michis.reader.databinding.ViewAutomaticSyncControlsBinding
import com.michis.reader.databinding.ViewDriveSettingsPanelBinding
import com.michis.reader.databinding.ViewGoogleAccountHeaderBinding
import com.michis.reader.databinding.ViewActionButtonBinding
import com.michis.reader.databinding.ViewSettingsDescriptionBinding
import com.michis.reader.sync.*
import com.michis.reader.sync.drive.*
import com.michis.reader.theme.AppThemePalette

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import com.google.android.gms.auth.api.identity.AuthorizationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Construye y coordina exclusivamente la seccion de cuenta y Google Drive. */
class DriveSettingsSection(
    private val activity: ComponentActivity,
    private val advancedMode: Boolean,
    private val openAdvancedSettings: () -> Unit
) {
    private var fullSyncInProgress = false
    private var lastFullSyncText: String? = null
    private var pendingAuthorizationResult: ((AuthorizationResult?) -> Unit)? = null
    private var panelToRefresh: LinearLayout? = null
    private var synchronizationPanel: LinearLayout? = null
    private val syncScheduler = AutomaticDriveSyncScheduler(activity)
    private var syncObserverAttached = false

    private val libraryPickerLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) panelToRefresh?.let(::render)
        panelToRefresh = null
    }

    private val authorizationLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val callback = pendingAuthorizationResult
        pendingAuthorizationResult = null
        val resultIntent = result.data
        if (result.resultCode != Activity.RESULT_OK || resultIntent == null) {
            callback?.invoke(null)
            return@registerForActivityResult
        }
        val authorization = runCatching {
            GoogleDriveAuthorizationManager(activity).authorizationClient
                .getAuthorizationResultFromIntent(resultIntent)
        }.getOrNull()
        callback?.invoke(authorization)
    }

    fun createPanel(): View {
        val binding = ViewDriveSettingsPanelBinding.inflate(activity.layoutInflater)
        observeBackgroundSync(binding.panelContainer)
        render(binding.panelContainer)
        return binding.root
    }

    private fun observeBackgroundSync(container: LinearLayout) {
        synchronizationPanel = container
        if (syncObserverAttached) return
        syncObserverAttached = true
        syncScheduler.immediateSyncWorkInfos().observe(activity) { workInfos ->
            val workInfo = syncScheduler.latestImmediateWorkInfo(workInfos) ?: return@observe
            fullSyncInProgress = workInfo.state == WorkInfo.State.ENQUEUED ||
                workInfo.state == WorkInfo.State.BLOCKED || workInfo.state == WorkInfo.State.RUNNING
            lastFullSyncText = when (workInfo.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                    "Sincronización en espera de conexión. Puedes seguir usando la app."
                WorkInfo.State.RUNNING -> workInfo.progress.getString(
                    GoogleDriveSyncWorker.KEY_PROGRESS_MESSAGE
                ) ?: "Sincronización ejecutándose en segundo plano…"
                WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED -> syncScheduler.lastStatus()
                WorkInfo.State.CANCELLED -> "Sincronización cancelada."
            }
            synchronizationPanel?.let(::render)
        }
    }

    private fun render(container: LinearLayout) {
        container.removeAllViews()
        val accountManager = OptionalGoogleAccountManager(activity)
        val session = accountManager.currentSession()
        if (session == null) {
            container.addView(description("La lectura local no requiere una cuenta. Google se utilizará únicamente si decides activar la sincronización."))
            container.addView(actionButton("Iniciar sesión con Google") {
                setOnClickListener {
                    isEnabled = false
                    activity.lifecycleScope.launch {
                        runCatching { accountManager.signIn(activity) }
                            .onSuccess {
                                Toast.makeText(activity, "Sesión iniciada", Toast.LENGTH_SHORT).show()
                                render(container)
                            }
                            .onFailure { error ->
                                isEnabled = true
                                val message = when (error) {
                                    is GetCredentialCancellationException -> "Inicio de sesión cancelado"
                                    is NoCredentialException -> "No se encontró una cuenta de Google disponible"
                                    else -> "No se pudo iniciar sesión: ${error.message.orEmpty()}"
                                }
                                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                            }
                    }
                }
            })
        } else {
            renderSignedIn(container, session, accountManager)
        }
        AppThemePalette.apply(activity)
    }

    private fun renderSignedIn(
        container: LinearLayout,
        session: GoogleAccountSession,
        accountManager: OptionalGoogleAccountManager
    ) {
        val authorizationManager = GoogleDriveAuthorizationManager(activity)
        container.addView(accountHeader(session))
        if (authorizationManager.isAuthorized()) {
            val folderRepository = GoogleDriveFolderRepository(activity)
            val savedFolder = folderRepository.savedFolder(session.accountIdentifier)
            container.addView(description("Google Drive está conectado. Puedes elegir una carpeta existente como biblioteca compartida entre tus dispositivos."))
            if (savedFolder == null) {
                container.addView(description("La carpeta de sincronización todavía no ha sido preparada."))
            } else {
                container.addView(description("Carpeta vinculada: ${savedFolder.name}"))
                lastFullSyncText?.let { container.addView(description(it)) }
            }
            if (advancedMode || savedFolder == null) container.addView(actionButton(
                if (savedFolder == null) "Preparar carpeta Michis Reader" else "Verificar carpeta Michis Reader"
            ) {
                setOnClickListener {
                    isEnabled = false
                    prepareFolder(session, authorizationManager, folderRepository, container, this)
                }
            })
            val libraryRepository = GoogleDriveBookLibraryRepository(activity)
            val selectedSources = libraryRepository.selectedSources(session.accountIdentifier)
            container.addView(description(
                if (selectedSources.isEmpty()) "Todavía no has elegido libros o carpetas de Drive."
                else "Biblioteca de Drive: ${selectedSources.size} elementos seleccionados"
            ))
            container.addView(actionButton(
                if (selectedSources.isEmpty()) "Elegir libros y carpetas" else "Editar libros y carpetas"
            ) {
                setOnClickListener {
                    isEnabled = false
                    chooseLibrarySources(session, authorizationManager, container, this)
                }
            })
            if (savedFolder != null) {
                container.addView(description(
                    "Elige Subir para enviar tus avances y anotaciones. Elige Descargar para recibir libros y cambios guardados en Drive. Ninguna opción reemplaza cambios más recientes."
                ))
                container.addView(actionButton(if (fullSyncInProgress) "Trabajando en Drive…" else "Subir mis cambios") {
                    isEnabled = !fullSyncInProgress
                    setOnClickListener {
                        isEnabled = false
                        fullSyncInProgress = true
                        synchronize(session, authorizationManager, container, this, SyncDirection.UPLOAD)
                    }
                })
                container.addView(actionButton(if (fullSyncInProgress) "Trabajando en Drive…" else "Descargar cambios de Drive") {
                    isEnabled = !fullSyncInProgress
                    setOnClickListener {
                        isEnabled = false
                        fullSyncInProgress = true
                        synchronize(session, authorizationManager, container, this, SyncDirection.DOWNLOAD)
                    }
                })
                container.addView(automaticSyncControls())
            }
            if (advancedMode) container.addView(actionButton("Revocar acceso a Google Drive") {
                setOnClickListener {
                    isEnabled = false
                    authorizationManager.authorizationClient
                        .revokeAccess(authorizationManager.revokeRequest(session.accountIdentifier))
                        .addOnSuccessListener {
                            AutomaticDriveSyncScheduler(activity).setEnabled(false)
                            authorizationManager.clearLocalAuthorizationState()
                            Toast.makeText(activity, "Acceso a Drive revocado", Toast.LENGTH_SHORT).show()
                            render(container)
                        }
                        .addOnFailureListener { error ->
                            isEnabled = true
                            Toast.makeText(activity, "No se pudo revocar Drive: ${error.message.orEmpty()}", Toast.LENGTH_LONG).show()
                        }
                }
            })
        } else {
            container.addView(description("Google Drive todavía no está autorizado. El permiso permite leer la biblioteca EPUB que elijas y mantener sincronizados sus libros."))
            container.addView(actionButton("Activar sincronización con Drive") {
                setOnClickListener {
                    isEnabled = false
                    requestAuthorization(session, authorizationManager, container, this)
                }
            })
        }
        container.addView(actionButton("Cerrar sesión") {
            setOnClickListener {
                isEnabled = false
                activity.lifecycleScope.launch {
                    runCatching { accountManager.signOut() }.onFailure {
                        Toast.makeText(activity, "La sesión local se cerró, pero Google no pudo limpiar el selector de cuenta", Toast.LENGTH_LONG).show()
                    }
                    render(container)
                }
            }
        })
        if (!advancedMode) container.addView(actionButton("Ajustes avanzados de Drive") {
            setOnClickListener { openAdvancedSettings() }
        })
    }

    private fun accountHeader(session: GoogleAccountSession): View {
        val binding = ViewGoogleAccountHeaderBinding.inflate(activity.layoutInflater)
        binding.displayName.text = session.displayName.ifBlank { "Cuenta de Google" }
        binding.accountIdentifier.text = session.accountIdentifier
        binding.profilePicture.background = AppThemePalette.cardBackground(activity, 26f)
        loadProfilePicture(binding.profilePicture, session.profilePictureUri)
        return binding.root
    }

    private fun requestAuthorization(
        session: GoogleAccountSession,
        manager: GoogleDriveAuthorizationManager,
        container: LinearLayout,
        sourceButton: Button,
        onFailure: () -> Unit = {},
        onAuthorized: (String) -> Unit = {}
    ) {
        manager.authorizationClient.authorize(manager.authorizationRequest(session.accountIdentifier))
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        sourceButton.isEnabled = true; onFailure()
                        Toast.makeText(activity, "Google no proporcionó una pantalla de autorización", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    pendingAuthorizationResult = { resolved ->
                        if (resolved != null && manager.acceptAuthorizationResult(resolved)) {
                            Toast.makeText(activity, "Drive autorizado", Toast.LENGTH_SHORT).show()
                            resolved.accessToken?.let(onAuthorized); render(container)
                        } else {
                            sourceButton.isEnabled = true; onFailure()
                            Toast.makeText(activity, "No se concedió el permiso de Drive", Toast.LENGTH_LONG).show()
                        }
                    }
                    authorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } else if (manager.acceptAuthorizationResult(result)) {
                    Toast.makeText(activity, "Drive autorizado", Toast.LENGTH_SHORT).show()
                    result.accessToken?.let(onAuthorized); render(container)
                } else {
                    sourceButton.isEnabled = true; onFailure()
                    Toast.makeText(activity, "Google no concedió el permiso solicitado", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { error ->
                sourceButton.isEnabled = true; onFailure()
                Toast.makeText(activity, "No se pudo autorizar Drive: ${error.message.orEmpty()}", Toast.LENGTH_LONG).show()
            }
    }

    private fun prepareFolder(
        session: GoogleAccountSession,
        authorizationManager: GoogleDriveAuthorizationManager,
        repository: GoogleDriveFolderRepository,
        container: LinearLayout,
        sourceButton: Button
    ) = requestAuthorization(session, authorizationManager, container, sourceButton) { accessToken ->
        activity.lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.ensureSyncFolder(accessToken, session.accountIdentifier) } }
                .onSuccess { folder ->
                    Toast.makeText(activity, "Carpeta ${folder.name} preparada", Toast.LENGTH_SHORT).show(); render(container)
                }
                .onFailure { error ->
                    sourceButton.isEnabled = true
                    Toast.makeText(activity, "No se pudo preparar la carpeta: ${error.message.orEmpty()}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun chooseLibrarySources(
        session: GoogleAccountSession,
        authorizationManager: GoogleDriveAuthorizationManager,
        container: LinearLayout,
        sourceButton: Button
    ) = requestAuthorization(session, authorizationManager, container, sourceButton) { accessToken ->
        sourceButton.isEnabled = true; panelToRefresh = container
        libraryPickerLauncher.launch(Intent(activity, DriveLibraryPickerActivity::class.java).apply {
            putExtra(DriveLibraryPickerActivity.EXTRA_ACCOUNT_IDENTIFIER, session.accountIdentifier)
            putExtra(DriveLibraryPickerActivity.EXTRA_ACCESS_TOKEN, accessToken)
        })
    }

    private fun synchronize(
        session: GoogleAccountSession,
        authorizationManager: GoogleDriveAuthorizationManager,
        container: LinearLayout,
        sourceButton: Button,
        direction: SyncDirection
    ) = requestAuthorization(session, authorizationManager, container, sourceButton, onFailure = {
        fullSyncInProgress = false; render(container)
    }) { _ ->
        syncScheduler.enqueueImmediateSync(direction)
        lastFullSyncText = if (direction == SyncDirection.UPLOAD) {
            "Subida preparada para ejecutarse en segundo plano…"
        } else {
            "Descarga preparada para ejecutarse en segundo plano…"
        }
        sourceButton.isEnabled = true
        render(container)
        Toast.makeText(activity, "Puedes seguir usando la app mientras Drive trabaja", Toast.LENGTH_LONG).show()
    }

    private fun automaticSyncControls(): View {
        val binding = ViewAutomaticSyncControlsBinding.inflate(activity.layoutInflater)
        val scheduler = AutomaticDriveSyncScheduler(activity)
        binding.automaticSyncSwitch.apply {
            isChecked = scheduler.isEnabled()
            setOnCheckedChangeListener { _, enabled -> scheduler.setEnabled(enabled) }
        }
        val intervals = listOf(15L, 60L, 360L, 1_440L)
        binding.frequencySpinner.apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Cada 15 minutos", "Cada hora", "Cada 6 horas", "Cada 24 horas"))
            setSelection(intervals.indexOf(scheduler.intervalMinutes()).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    scheduler.setIntervalMinutes(intervals[position])
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        binding.wifiOnlySwitch.apply {
            isChecked = scheduler.wifiOnly()
            setOnCheckedChangeListener { _, checked -> scheduler.setWifiOnly(checked) }
        }
        binding.lastStatusText.text = "Último estado: ${scheduler.lastStatus()}"
        return binding.root
    }

    private fun loadProfilePicture(target: ImageView, value: String) {
        if (value.isBlank()) return
        activity.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(value)
                    val stream = if (uri.scheme == "http" || uri.scheme == "https") {
                        java.net.URL(value).openConnection().apply { connectTimeout = 5_000; readTimeout = 5_000 }.getInputStream()
                    } else activity.contentResolver.openInputStream(uri)
                    stream?.use(BitmapFactory::decodeStream)
                }.getOrNull()
            }
            if (bitmap != null && target.isAttachedToWindow) target.setImageBitmap(bitmap)
        }
    }

    private fun actionButton(value: String, configure: Button.() -> Unit): View {
        val binding = ViewActionButtonBinding.inflate(activity.layoutInflater)
        binding.actionButton.text = value
        binding.actionButton.configure()
        return binding.root
    }

    private fun description(value: String) =
        ViewSettingsDescriptionBinding.inflate(activity.layoutInflater).root.apply { text = value }
}
