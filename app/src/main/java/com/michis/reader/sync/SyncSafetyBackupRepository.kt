package com.michis.reader.sync

import android.content.Context
import java.io.File

/** Conserva una única copia local coherente antes de aplicar cambios remotos. */
internal class SyncSafetyBackupRepository(private val context: Context) {
    fun saveCurrentLibraryState() = save(LibrarySyncSnapshotBuilder(context).build().bytes)

    fun save(bytes: ByteArray) {
        val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
        val temporary = File(directory, TEMPORARY_FILE_NAME)
        val destination = File(directory, BACKUP_FILE_NAME)
        temporary.writeBytes(bytes)
        val saved = temporary.renameTo(destination) || runCatching {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
            true
        }.getOrDefault(false)
        check(saved) { "No se pudo guardar la copia local de seguridad" }
    }

    private companion object {
        const val DIRECTORY_NAME = "sync-safety"
        const val TEMPORARY_FILE_NAME = "library-state-before-merge.tmp"
        const val BACKUP_FILE_NAME = "library-state-before-merge.json"
    }
}
