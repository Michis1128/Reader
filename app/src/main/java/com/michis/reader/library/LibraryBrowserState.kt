package com.michis.reader.library

import com.michis.reader.data.*

import android.content.Context

internal enum class LibrarySortMode(val label: String) {
    TITLE("Título"), RECENTLY_OPENED("Más recientes"), LAST_ADDED("Últimos agregados"),
    AUTHOR("Autor"), CUSTOM("Orden personalizable")
}

internal sealed interface LibraryItem {
    val key: String
    data class Folder(val value: LibraryFolder) : LibraryItem {
        override val key = "folder:${value.remoteIdentifier}"
    }
    data class Document(val value: LibraryDocument) : LibraryItem {
        override val key = "document:${value.identifier}"
    }
}

/** Estado navegable y modo de presentación de la biblioteca. */
internal class LibraryBrowserState(
    context: Context,
    private val database: ReaderDatabase
) {
    private val preferences = context.getSharedPreferences("library_preferences", Context.MODE_PRIVATE)
    private val folderStack = mutableListOf<LibraryFolder>()

    var displayMode: Int = preferences.getInt("display_mode", 0).coerceIn(0, 3)
        private set

    var sortMode: LibrarySortMode = LibrarySortMode.entries.getOrElse(
        preferences.getInt("sort_mode", LibrarySortMode.TITLE.ordinal)
    ) { LibrarySortMode.TITLE }
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

    fun selectSortMode(mode: LibrarySortMode) {
        sortMode = mode
        preferences.edit().putInt("sort_mode", mode.ordinal).apply()
    }

    fun orderedItems(folders: List<LibraryFolder>, documents: List<LibraryDocument>): List<LibraryItem> {
        val items = folders.map(LibraryItem::Folder) + documents.map(LibraryItem::Document)
        return when (sortMode) {
            LibrarySortMode.TITLE -> items.sortedBy { it.titleForSorting().lowercase() }
            LibrarySortMode.RECENTLY_OPENED -> items.sortedWith(
                compareByDescending<LibraryItem> { (it as? LibraryItem.Document)?.value?.lastOpenedAt ?: Long.MIN_VALUE }
                    .thenBy { it.titleForSorting().lowercase() }
            )
            LibrarySortMode.LAST_ADDED -> items.sortedWith(
                compareByDescending<LibraryItem> { (it as? LibraryItem.Document)?.value?.identifier ?: Long.MIN_VALUE }
                    .thenBy { it.titleForSorting().lowercase() }
            )
            LibrarySortMode.AUTHOR -> items.sortedWith(
                compareBy<LibraryItem> { (it as? LibraryItem.Document)?.value?.author.orEmpty().lowercase() }
                    .thenBy { it.titleForSorting().lowercase() }
            )
            LibrarySortMode.CUSTOM -> customOrder(items)
        }
    }

    fun moveCustomItem(items: List<LibraryItem>, draggedKey: String, targetKey: String) {
        if (draggedKey == targetKey) return
        val reordered = customOrder(items).toMutableList()
        val from = reordered.indexOfFirst { it.key == draggedKey }
        val to = reordered.indexOfFirst { it.key == targetKey }
        if (from < 0 || to < 0) return
        val moved = reordered.removeAt(from)
        reordered.add(if (from < to) to - 1 else to, moved)
        preferences.edit().putString(customOrderKey(), reordered.joinToString("|") { it.key }).apply()
    }

    private fun customOrder(items: List<LibraryItem>): List<LibraryItem> {
        val positions = preferences.getString(customOrderKey(), "").orEmpty()
            .split('|').filter { it.isNotBlank() }.withIndex().associate { it.value to it.index }
        return items.sortedWith(compareBy<LibraryItem> { positions[it.key] ?: Int.MAX_VALUE }
            .thenBy { it.titleForSorting().lowercase() })
    }

    private fun customOrderKey() = "custom_order_${currentFolderIdentifier ?: "root"}"

    private fun LibraryItem.titleForSorting() = when (this) {
        is LibraryItem.Folder -> value.name
        is LibraryItem.Document -> value.title
    }
}
