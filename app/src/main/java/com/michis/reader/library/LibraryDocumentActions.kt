package com.michis.reader.library

import com.michis.reader.data.LibraryDocument
import com.michis.reader.data.ReaderDatabase
import com.michis.reader.sync.AutomaticDriveSyncScheduler
import com.michis.reader.sync.drive.GoogleDriveAuthorizationManager
import com.michis.reader.sync.drive.GoogleDriveFolderRepository
import com.michis.reader.sync.drive.OptionalGoogleAccountManager
import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderInputShape

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp

/** Acciones destructivas y de mantenimiento disponibles al mantener pulsado un libro. */
internal class LibraryDocumentActions(
    private val activity: Activity,
    private val database: ReaderDatabase,
    private val refreshLibrary: () -> Unit,
    private val updateSyncStatus: (String) -> Unit
) {
    fun show(document: LibraryDocument) {
        val resetLabel = if (document.lastOpenedAt > 0L) {
            "Reiniciar libro (también quitar de Leyendo actualmente)"
        } else "Reiniciar libro"
        AlertDialog.Builder(activity).setTitle(document.title)
            .setItems(arrayOf("Editar metadatos", resetLabel, "Eliminar de la biblioteca")) { _, option ->
                when (option) {
                    0 -> editMetadata(document)
                    1 -> confirmReset(document)
                    else -> confirmRemoval(document)
                }
            }.show()
    }

    private fun confirmReset(document: LibraryDocument) {
        AlertDialog.Builder(activity).setTitle("Reiniciar libro")
            .setMessage(
                "Se borrarán el progreso, citas, notas, marcadores y diccionarios de \"${document.title}\". " +
                    "El libro y sus metadatos se conservarán. " +
                    (if (document.lastOpenedAt > 0L) "También desaparecerá de Leyendo actualmente. " else "") +
                    "El cambio también se sincronizará con Drive."
            )
            .setPositiveButton("Reiniciar") { _, _ ->
                runCatching { database.resetBook(document.identifier) }
                    .onSuccess { result ->
                        refreshLibrary()
                        enqueueResetSync()
                        message("Libro reiniciado: ${result.annotationsDeleted} anotaciones eliminadas", true)
                    }
                    .onFailure { message("No se pudo reiniciar: ${it.message.orEmpty()}", true) }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun enqueueResetSync() {
        val session = OptionalGoogleAccountManager(activity).currentSession() ?: return
        if (!GoogleDriveAuthorizationManager(activity).isAuthorized()) return
        if (GoogleDriveFolderRepository(activity).savedFolder(session.accountIdentifier) == null) return
        AutomaticDriveSyncScheduler(activity).enqueueImmediateSync()
        updateSyncStatus("Sincronización: reinicio pendiente de enviar a Drive")
    }

    private fun editMetadata(document: LibraryDocument) {
        var title by mutableStateOf(document.title)
        var author by mutableStateOf(document.author)
        val content = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MichisReaderComposeTheme {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Título") }, singleLine = true, shape = MichisReaderInputShape)
                        OutlinedTextField(author, { author = it }, Modifier.fillMaxWidth(), label = { Text("Autor") }, singleLine = true, shape = MichisReaderInputShape)
                        Text("Archivo original: ${document.fileName}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        AlertDialog.Builder(activity).setTitle("Editar metadatos").setView(content)
            .setPositiveButton("Guardar") { _, _ ->
                if (title.isBlank()) message("El título no puede estar vacío")
                else {
                    database.updateDocumentMetadata(document.identifier, title, author)
                    refreshLibrary()
                    message("Metadatos actualizados")
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun confirmRemoval(document: LibraryDocument) {
        AlertDialog.Builder(activity).setTitle("Eliminar de la biblioteca")
            .setMessage("Se eliminará \"${document.title}\" junto con sus datos locales. El archivo original de almacenamiento o Google Drive no será eliminado.")
            .setPositiveButton("Eliminar") { _, _ ->
                database.deleteDocument(document.identifier)
                refreshLibrary()
                message("Libro eliminado de la biblioteca")
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun message(value: String, long: Boolean = false) = Toast.makeText(
        activity, value, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    ).show()
}
