package com.michis.reader.library

import com.michis.reader.data.*

import android.content.Context

/** Estado navegable y modo de presentación de la biblioteca. */
internal class LibraryBrowserState(
    context: Context,
    private val database: ReaderDatabase
) {
    private val preferences = context.getSharedPreferences("library_preferences", Context.MODE_PRIVATE)
    private val folderStack = mutableListOf<LibraryFolder>()

    var displayMode: Int = preferences.getInt("display_mode", 0).coerceIn(0, 3)
        private set

    val canNavigateBack: Boolean get() = folderStack.isNotEmpty()
    val currentFolderIdentifier: String? get() = folderStack.lastOrNull()?.remoteIdentifier
    val pathLabel: String get() = if (folderStack.isEmpty()) {
        "Mi biblioteca"
    } else {
        "Mi biblioteca  ›  " + folderStack.joinToString("  ›  ") { it.name }
    }

    fun restoreLastDocumentFolder(documentIdentifier: Long) {
        folderStack.clear()
        if (documentIdentifier < 0 || database.findDocument(documentIdentifier) == null) return
        folderStack += database.libraryFolderPath(database.documentFolderRemoteIdentifier(documentIdentifier))
    }

    fun openRoot() = folderStack.clear()

    fun openFolder(folder: LibraryFolder) {
        if (folderStack.lastOrNull()?.remoteIdentifier != folder.remoteIdentifier) folderStack += folder
    }

    fun navigateToParent(): Boolean {
        if (folderStack.isEmpty()) return false
        folderStack.removeAt(folderStack.lastIndex)
        return true
    }

    fun cycleDisplayMode(): Int {
        displayMode = (displayMode + 1) % 4
        preferences.edit().putInt("display_mode", displayMode).apply()
        return displayMode
    }

    fun displayModeIcon(): String = arrayOf("▦", "⠿", "☰", "≡")[displayMode]
}
