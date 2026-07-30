package com.michis.reader.library

import com.michis.reader.data.*

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

/** Importa libros EPUB individuales y árboles de carpetas mediante SAF. */
internal class LibraryImportCoordinator(
    private val contentResolver: ContentResolver,
    private val database: ReaderDatabase,
    private val refreshLibrary: () -> Unit,
    private val openDocument: (Long) -> Unit,
    private val showMessage: (String) -> Unit
) {
    fun importDocuments(uris: List<Uri>, permissionFlags: Int) {
        val uniqueUris = uris.distinct()
        uniqueUris.forEach { uri ->
            val identifier = importDocument(uri, permissionFlags)
            if (uniqueUris.size == 1 && identifier >= 0) openDocument(identifier)
        }
        refreshLibrary()
    }

    fun importIncoming(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let {
            val identifier = importDocument(it, intent.flags)
            refreshLibrary()
            if (identifier >= 0) openDocument(identifier)
        }
    }

    fun importFolder(treeUri: Uri, permissionFlags: Int) {
        persistReadPermission(treeUri, permissionFlags)
        val result = runCatching {
            var importedCount = 0
            fun importChildren(parentIdentifier: String) {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentIdentifier)
                contentResolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
                    null, null, null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val identifier = cursor.getString(0)
                        if (cursor.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            importChildren(identifier)
                        } else {
                            val importedIdentifier = importDocument(
                                DocumentsContract.buildDocumentUriUsingTree(treeUri, identifier),
                                permissionFlags
                            )
                            if (importedIdentifier >= 0) importedCount++
                        }
                    }
                }
            }
            importChildren(DocumentsContract.getTreeDocumentId(treeUri))
            importedCount
        }
        result.onSuccess {
            refreshLibrary()
            showMessage("$it libros EPUB importados")
        }.onFailure { showMessage("No se pudo leer la carpeta") }
    }

    private fun importDocument(uri: Uri, permissionFlags: Int): Long {
        val fileName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "libro.epub"
        if (!fileName.endsWith(".epub", ignoreCase = true)) {
            showMessage("Se omitió $fileName porque no es un libro EPUB")
            return -1
        }
        persistReadPermission(uri, permissionFlags)
        return database.saveDocument(uri.toString(), fileName)
    }

    private fun persistReadPermission(uri: Uri, permissionFlags: Int) {
        val grantedFlags = permissionFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (grantedFlags == 0) return
        runCatching { contentResolver.takePersistableUriPermission(uri, grantedFlags) }
    }
}
