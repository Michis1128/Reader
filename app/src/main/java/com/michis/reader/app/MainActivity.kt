package com.michis.reader.app

import com.michis.reader.annotations.BookmarksActivity
import com.michis.reader.annotations.QuotesActivity
import com.michis.reader.data.LibraryDocument
import com.michis.reader.data.LibraryFolder
import com.michis.reader.data.ReaderDatabase
import com.michis.reader.databinding.ActivityMainBinding
import com.michis.reader.dictionary.DictionariesActivity
import com.michis.reader.library.LibraryBrowserState
import com.michis.reader.library.LibraryContent
import com.michis.reader.library.LibraryContentState
import com.michis.reader.library.LibraryDocumentActions
import com.michis.reader.library.LibraryImportCoordinator
import com.michis.reader.library.LibraryItem
import com.michis.reader.library.LibrarySortMode
import com.michis.reader.reader.ReadiumEpubActivity
import com.michis.reader.settings.SettingsActivity
import com.michis.reader.sync.LibrarySyncController
import com.michis.reader.sync.SyncDirection
import com.michis.reader.theme.AppThemePalette
import com.michis.reader.ui.SystemBarInsets

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private enum class MainSection { LIBRARY, CURRENTLY_READING }

    private lateinit var database: ReaderDatabase
    private lateinit var libraryBrowserState: LibraryBrowserState
    private lateinit var importCoordinator: LibraryImportCoordinator
    private lateinit var documentActions: LibraryDocumentActions
    private lateinit var syncController: LibrarySyncController
    private var mainSection = MainSection.LIBRARY
    private var controlsState by mutableStateOf(MainControlsState())
    private var libraryContentState by mutableStateOf(LibraryContentState())

    private val documentPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val uris = buildList {
            data.clipData?.let { clips -> repeat(clips.itemCount) { add(clips.getItemAt(it).uri) } }
            data.data?.let(::add)
        }
        importCoordinator.importDocuments(uris, data.flags)
    }
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { importCoordinator.importFolder(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::libraryBrowserState.isInitialized && mainSection != MainSection.LIBRARY) {
                    mainSection = MainSection.LIBRARY
                    refreshLibrary()
                } else if (::libraryBrowserState.isInitialized && libraryBrowserState.canNavigateBack) {
                    navigateToParentFolder()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        database = ReaderDatabase.getInstance(this)
        libraryBrowserState = LibraryBrowserState(this, database)
        binding = ActivityMainBinding.inflate(layoutInflater)
        configureComposeContent()
        importCoordinator = LibraryImportCoordinator(
            contentResolver,
            database,
            { refreshLibrary() },
            ::openReader
        ) { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        documentActions = LibraryDocumentActions(this, database, {
            refreshCurrentSection(controlsState.query)
        }) { controlsState = controlsState.copy(syncStatus = it) }
        syncController = LibrarySyncController(
            this,
            { status, enabled -> controlsState = controlsState.copy(syncStatus = status, syncActionsEnabled = enabled) },
            ::showGeneralSettings
        ) { refreshCurrentSection(controlsState.query) }
        SystemBarInsets.apply(binding.root)
        setContentView(binding.root)
        AppThemePalette.apply(this)
        restoreLastDocumentFolder()
        importCoordinator.importIncoming(intent)
        refreshLibrary()
        if (savedInstanceState == null && intent.action == Intent.ACTION_MAIN && intent.data == null && ReaderResumeState.shouldResumeReader(this)) {
            val identifier = ReaderResumeState.lastDocumentIdentifier(this)
            if (database.findDocument(identifier) != null) binding.root.post { openReader(identifier) }
            else ReaderResumeState.markReaderExited(this)
        }
    }

    private fun configureComposeContent() {
        AppThemePalette.markBackground(binding.rootContainer)
        controlsState = controlsState.copy(
            filterLabel = libraryBrowserState.sortMode.label,
            displayIcon = libraryBrowserState.displayModeIcon()
        )
        binding.composeControls.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeControls.setContent {
            MainLibraryControls(
                state = controlsState,
                updateQuery = ::updateSearchQuery,
                openSettings = ::showGeneralSettings,
                upload = { syncController.synchronize(SyncDirection.UPLOAD) },
                download = { syncController.synchronize(SyncDirection.DOWNLOAD) },
                importBooks = ::showImportMenu,
                openLibrary = ::openLibraryRoot,
                openCurrentlyReading = ::showCurrentlyReading,
                openQuotes = { startActivity(Intent(this, QuotesActivity::class.java)) },
                openBookmarks = { startActivity(Intent(this, BookmarksActivity::class.java)) },
                openDictionaries = { startActivity(Intent(this, DictionariesActivity::class.java)) },
                openFilters = ::showLibraryFilters,
                changeDisplay = ::cycleLibraryDisplayMode
            )
        }
        binding.composeLibraryContent.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeLibraryContent.setContent {
            LibraryContent(
                state = libraryContentState,
                navigateToParent = ::navigateToParentFolder,
                openFolder = ::openLibraryFolder,
                openDocument = ::openReader,
                openDocumentActions = ::showDocumentActions,
                moveItem = ::moveLibraryItemByOffset,
                quoteCount = { database.annotationCount(it, "cita") },
                hasDictionary = database::hasEffectiveDictionaryEntries
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importCoordinator.importIncoming(intent)
    }

    override fun onResume() {
        super.onResume()
        AppThemePalette.apply(this)
        if (::syncController.isInitialized) syncController.refreshStatus()
        if (mainSection == MainSection.CURRENTLY_READING) refreshCurrentlyReading(controlsState.query)
    }

    private fun updateSearchQuery(query: String) {
        controlsState = controlsState.copy(query = query)
        refreshCurrentSection(query)
    }

    private fun openDocumentPicker() {
        documentPickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/epub+zip"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        })
    }

    private fun showImportMenu() {
        AlertDialog.Builder(this).setTitle("Importar a la biblioteca")
            .setItems(arrayOf("Elegir libros EPUB", "Elegir una carpeta")) { _, index ->
                if (index == 0) openDocumentPicker() else folderPickerLauncher.launch(null)
            }.show()
    }

    private fun refreshLibrary(query: String = controlsState.query) {
        val currentFolderIdentifier = libraryBrowserState.currentFolderIdentifier
        val documents = database.findDocumentsInFolder(currentFolderIdentifier, query)
        val folders = if (query.isBlank()) database.libraryFolders(currentFolderIdentifier) else emptyList()
        libraryContentState = LibraryContentState(
            pathLabel = libraryBrowserState.pathLabel,
            showPath = true,
            items = libraryBrowserState.orderedItems(folders, documents),
            displayMode = libraryBrowserState.displayMode,
            showParentFolder = libraryBrowserState.canNavigateBack,
            allowCustomOrdering = libraryBrowserState.sortMode == LibrarySortMode.CUSTOM && query.isBlank(),
            emptyMessage = if (documents.isEmpty() && folders.isEmpty() && !libraryBrowserState.canNavigateBack) {
                "Aún no hay libros EPUB.\nImporta uno para comenzar."
            } else null
        )
    }

    private fun refreshCurrentSection(query: String) {
        when (mainSection) {
            MainSection.LIBRARY -> refreshLibrary(query)
            MainSection.CURRENTLY_READING -> refreshCurrentlyReading(query)
        }
    }

    private fun openLibraryRoot() {
        mainSection = MainSection.LIBRARY
        libraryBrowserState.openRoot()
        refreshLibrary()
    }

    private fun restoreLastDocumentFolder() {
        libraryBrowserState.restoreLastDocumentFolder(ReaderResumeState.lastDocumentIdentifier(this))
    }

    private fun openLibraryFolder(folder: LibraryFolder) {
        libraryBrowserState.openFolder(folder)
        controlsState = controlsState.copy(query = "")
        refreshLibrary("")
    }

    private fun navigateToParentFolder() {
        if (libraryBrowserState.navigateToParent()) refreshLibrary("")
    }

    private fun cycleLibraryDisplayMode() {
        libraryBrowserState.cycleDisplayMode()
        controlsState = controlsState.copy(displayIcon = libraryBrowserState.displayModeIcon())
        refreshCurrentSection(controlsState.query)
    }

    private fun showLibraryFilters() {
        val modes = LibrarySortMode.entries
        AlertDialog.Builder(this)
            .setTitle("Ordenar biblioteca")
            .setSingleChoiceItems(modes.map { it.label }.toTypedArray(), libraryBrowserState.sortMode.ordinal) { dialog, index ->
                libraryBrowserState.selectSortMode(modes[index])
                controlsState = controlsState.copy(filterLabel = modes[index].label)
                dialog.dismiss()
                refreshLibrary()
                if (modes[index] == LibrarySortMode.CUSTOM) {
                    Toast.makeText(this, "Mantén presionado y arrastra libros o carpetas para ordenarlos", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun moveLibraryItemByOffset(draggedKey: String, direction: Int) {
        if (libraryBrowserState.sortMode != LibrarySortMode.CUSTOM || direction == 0) return
        val documents = database.findDocumentsInFolder(libraryBrowserState.currentFolderIdentifier, "")
        val folders = database.libraryFolders(libraryBrowserState.currentFolderIdentifier)
        val ordered = libraryBrowserState.orderedItems(folders, documents)
        val currentIndex = ordered.indexOfFirst { it.key == draggedKey }
        if (currentIndex < 0) return
        val target = ordered.getOrNull(currentIndex + direction) ?: return
        libraryBrowserState.moveCustomItem(ordered, draggedKey, target.key)
        refreshLibrary("")
    }

    private fun showDocumentActions(document: LibraryDocument) = documentActions.show(document)

    private fun showCurrentlyReading() {
        mainSection = MainSection.CURRENTLY_READING
        refreshCurrentlyReading(controlsState.query)
    }

    private fun refreshCurrentlyReading(query: String) {
        val documents = database.findCurrentlyReadingDocuments(query)
        libraryContentState = LibraryContentState(
            showPath = false,
            items = documents.map(LibraryItem::Document),
            displayMode = libraryBrowserState.displayMode,
            emptyMessage = if (documents.isEmpty()) {
                if (query.isBlank()) "Los libros que abras aparecerán aquí, empezando por el más reciente."
                else "No hay lecturas recientes que coincidan con la búsqueda."
            } else null
        )
    }

    private fun showGeneralSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun openReader(identifier: Long) {
        val document = database.findDocument(identifier) ?: return
        if (!document.format.equals("EPUB", ignoreCase = true)) {
            Toast.makeText(this, "Michis Reader solo admite libros EPUB", Toast.LENGTH_SHORT).show()
            return
        }
        database.markDocumentOpened(identifier)
        startActivity(Intent(this, ReadiumEpubActivity::class.java).putExtra("document_identifier", identifier))
    }
}
