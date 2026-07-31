package com.michis.reader.library

import com.michis.reader.data.*
import com.michis.reader.databinding.DialogEditBookMetadataBinding
import com.michis.reader.sync.AutomaticDriveSyncScheduler
import com.michis.reader.sync.drive.*

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast

/** Acciones destructivas y de mantenimiento disponibles al mantener pulsado un libro. */
internal class LibraryDocumentActions(
    private val activity: Activity,
    private val database: ReaderDatabase,
    private val refreshLibrary: () -> Unit,
    private val updateSyncStatus: (String) -> Unit
) {
    fun show(document: LibraryDocument) {
        AlertDialog.Builder(activity).setTitle(document.title)
            .setItems(arrayOf("Editar metadatos", "Reiniciar libro", "Eliminar de la biblioteca")) { _, option ->
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
                "Se borrarán el progreso, citas, notas, marcadores y diccionarios de " +
                    "\"${document.title}\". El libro y sus metadatos se conservarán. " +
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
        val binding = DialogEditBookMetadataBinding.inflate(activity.layoutInflater).apply {
            titleInput.setText(document.title)
            titleInput.selectAll()
            authorInput.setText(document.author)
            originalFileText.text = "Archivo original: ${document.fileName}"
        }
        AlertDialog.Builder(activity).setTitle("Editar metadatos").setView(binding.root)
            .setPositiveButton("Guardar") { _, _ ->
                if (binding.titleInput.text.isNullOrBlank()) {
                    message("El título no puede estar vacío")
                } else {
                    database.updateDocumentMetadata(
                        document.identifier,
                        binding.titleInput.text.toString(),
                        binding.authorInput.text.toString()
                    )
                    refreshLibrary()
                    message("Metadatos actualizados")
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun confirmRemoval(document: LibraryDocument) {
        AlertDialog.Builder(activity).setTitle("Eliminar de la biblioteca")
            .setMessage(
                "Se eliminará \"${document.title}\" junto con sus datos locales. " +
                    "El archivo original de almacenamiento o Google Drive no será eliminado."
            )
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
