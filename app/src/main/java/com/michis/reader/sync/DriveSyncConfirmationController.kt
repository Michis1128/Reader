package com.michis.reader.sync

import com.michis.reader.theme.compose.MichisReaderComposeTheme

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp

/** Explica las acciones manuales de Drive y recuerda cada advertencia por separado. */
class DriveSyncConfirmationController(private val activity: Activity) {
    private val warningPreferences = DriveSyncWarningPreferences(activity)

    fun confirm(direction: SyncDirection, confirmed: () -> Unit) {
        if (!warningPreferences.shouldShow(direction)) {
            confirmed()
            return
        }
        var doNotShowAgain by mutableStateOf(false)
        val content = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MichisReaderComposeTheme {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(explanation(direction))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(doNotShowAgain, { doNotShowAgain = it })
                            Text("No volver a mostrar esta advertencia para esta acción")
                        }
                    }
                }
            }
        }
        AlertDialog.Builder(activity)
            .setTitle(if (direction == SyncDirection.UPLOAD) "Subir cambios a Drive" else "Descargar cambios de Drive")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar") { _, _ ->
                if (doNotShowAgain) warningPreferences.hide(direction)
                confirmed()
            }.show()
    }

    fun restoreWarnings() = warningPreferences.restoreAll()

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
    fun hide(direction: SyncDirection) = preferences.edit().putBoolean(preferenceKey(direction), false).apply()
    fun restoreAll() {
        preferences.edit().putBoolean(KEY_SHOW_UPLOAD_WARNING, true).putBoolean(KEY_SHOW_DOWNLOAD_WARNING, true).apply()
    }

    private fun preferenceKey(direction: SyncDirection): String = when (direction) {
        SyncDirection.UPLOAD, SyncDirection.BIDIRECTIONAL -> KEY_SHOW_UPLOAD_WARNING
        SyncDirection.DOWNLOAD -> KEY_SHOW_DOWNLOAD_WARNING
    }

    private companion object {
        const val PREFERENCES_NAME = "drive_sync_confirmations"
        const val KEY_SHOW_UPLOAD_WARNING = "show_upload_warning"
        const val KEY_SHOW_DOWNLOAD_WARNING = "show_download_warning"
    }
}
