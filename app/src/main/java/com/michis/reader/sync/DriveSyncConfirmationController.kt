package com.michis.reader.sync

import com.michis.reader.databinding.DialogDriveSyncConfirmationBinding
import com.michis.reader.theme.AppThemePalette

import android.app.Activity
import android.app.AlertDialog
import android.content.Context

/** Explica las acciones manuales de Drive y recuerda cada advertencia por separado. */
class DriveSyncConfirmationController(private val activity: Activity) {
    private val warningPreferences = DriveSyncWarningPreferences(activity)

    fun confirm(direction: SyncDirection, confirmed: () -> Unit) {
        if (!warningPreferences.shouldShow(direction)) {
            confirmed()
            return
        }
        val binding = DialogDriveSyncConfirmationBinding.inflate(activity.layoutInflater)
        binding.explanationText.text = explanation(direction)
        AppThemePalette.markCard(binding.root)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (direction == SyncDirection.UPLOAD) "Subir cambios a Drive" else "Descargar cambios de Drive")
            .setView(binding.root)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar") { _, _ ->
                if (binding.doNotShowAgainCheckbox.isChecked) {
                    warningPreferences.hide(direction)
                }
                confirmed()
            }
            .create()
        dialog.setOnShowListener { AppThemePalette.apply(activity) }
        dialog.show()
    }

    fun restoreWarnings() {
        warningPreferences.restoreAll()
    }

    private fun explanation(direction: SyncDirection): String = when (direction) {
        SyncDirection.UPLOAD ->
            "Se enviarán a Google Drive tus avances, citas, marcadores, diccionarios y eliminaciones pendientes. " +
                "No se descargarán cambios. Si Drive contiene una versión más reciente, la app la conservará y no la reemplazará."
        SyncDirection.DOWNLOAD ->
            "Se buscarán libros nuevos o modificados y se descargarán avances, citas, marcadores y diccionarios guardados en Drive. " +
                "No se subirán tus cambios locales y las versiones locales más recientes se conservarán."
        SyncDirection.BIDIRECTIONAL -> "Se sincronizarán cambios locales y remotos de forma segura."
    }

}

internal class DriveSyncWarningPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun shouldShow(direction: SyncDirection): Boolean = preferences.getBoolean(preferenceKey(direction), true)

    fun hide(direction: SyncDirection) {
        preferences.edit().putBoolean(preferenceKey(direction), false).apply()
    }

    fun restoreAll() {
        preferences.edit()
            .putBoolean(KEY_SHOW_UPLOAD_WARNING, true)
            .putBoolean(KEY_SHOW_DOWNLOAD_WARNING, true)
            .apply()
    }

    private fun preferenceKey(direction: SyncDirection): String = when (direction) {
        SyncDirection.UPLOAD -> KEY_SHOW_UPLOAD_WARNING
        SyncDirection.DOWNLOAD -> KEY_SHOW_DOWNLOAD_WARNING
        SyncDirection.BIDIRECTIONAL -> KEY_SHOW_UPLOAD_WARNING
    }

    private companion object {
        const val PREFERENCES_NAME = "drive_sync_confirmations"
        const val KEY_SHOW_UPLOAD_WARNING = "show_upload_warning"
        const val KEY_SHOW_DOWNLOAD_WARNING = "show_download_warning"
    }
}
