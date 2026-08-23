package com.michis.reader.app

import com.michis.reader.R
import com.michis.reader.annotations.BookmarksActivity
import com.michis.reader.data.*
import com.michis.reader.databinding.ActivityMainBinding
import com.michis.reader.dictionary.DictionariesActivity
import com.michis.reader.library.*
import com.michis.reader.reader.ReadiumEpubActivity
import com.michis.reader.settings.SettingsActivity
import com.michis.reader.sync.*
import com.michis.reader.theme.AppThemePalette
import com.michis.reader.ui.SystemBarInsets

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private enum class MainSection { LIBRARY, CURRENTLY_READING, ANNOTATIONS }
    private lateinit var database: ReaderDatabase
    private lateinit var documentList: LinearLayout
    private lateinit var emptyMessage: TextView
    private lateinit var libraryBrowserState: LibraryBrowserState
    private lateinit var libraryViewRenderer: LibraryViewRenderer
    private lateinit var importCoordinator: LibraryImportCoordinator
    private lateinit var documentActions: LibraryDocumentActions
    private lateinit var librarySections: LibrarySectionsController
    private lateinit var syncController: LibrarySyncController
    private lateinit var libraryPathText: TextView
    private var controlsState by mutableStateOf(MainControlsState())
    private var mainSection = MainSection.LIBRARY
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
        configureScreen()
        val mainScreen = binding.root
        libraryViewRenderer = LibraryViewRenderer(
            this, database, documentList, ::navigateToParentFolder, ::openLibraryFolder, ::openReader,
            ::showDocumentActions, ::moveLibraryItem
        )
        importCoordinator = LibraryImportCoordinator(
            contentResolver, database, { refreshLibrary() }, ::openReader
        ) { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        documentActions = LibraryDocumentActions(this, database, {
            refreshCurrentSection(controlsState.query)
        }) {
            controlsState = controlsState.copy(syncStatus = it)
        }
        librarySections = LibrarySectionsController(this, database, documentList, libraryPathText)
        syncController = LibrarySyncController(
            this,
            { status, enabled -> controlsState = controlsState.copy(syncStatus = status, syncActionsEnabled = enabled) },
            ::showGeneralSettings
        ) { refreshCurrentSection(controlsState.query) }
        SystemBarInsets.apply(mainScreen)
        setContentView(mainScreen)
        AppThemePalette.apply(this)
        restoreLastDocumentFolder()
        importCoordinator.importIncoming(intent)
        refreshLibrary()
        if (savedInstanceState == null && intent.action == Intent.ACTION_MAIN && intent.data == null && ReaderResumeState.shouldResumeReader(this)) {
            val lastDocumentIdentifier = ReaderResumeState.lastDocumentIdentifier(this)
            if (database.findDocument(lastDocumentIdentifier) != null) {
                mainScreen.post { openReader(lastDocumentIdentifier) }
            } else {
                ReaderResumeState.markReaderExited(this)
            }
        }
    }

    private fun configureScreen() {
        AppThemePalette.markBackground(binding.rootContainer)
        documentList = binding.documentList
        emptyMessage = binding.emptyMessage
        libraryPathText = binding.libraryPathText
        controlsState = controlsState.copy(
            filterLabel = libraryBrowserState.sortMode.label,
            displayIcon = displayModeIcon()
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
                openQuotes = ::showQuotes,
                openBookmarks = { startActivity(Intent(this, BookmarksActivity::class.java)) },
                openDictionaries = { startActivity(Intent(this, DictionariesActivity::class.java)) },
                openFilters = ::showLibraryFilters,
                changeDisplay = ::cycleLibraryDisplayMode
            )
        }
        libraryPathText.setOnClickListener { navigateToParentFolder() }
    }

    private fun updateSearchQuery(query: String) {
        controlsState = controlsState.copy(query = query)
        refreshCurrentSection(query)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importCoordinator.importIncoming(intent)
    }

    override fun onResume() {
        super.onResume()
        AppThemePalette.apply(this)
        if (::syncController.isInitialized) syncController.refreshStatus()
        if (::librarySections.isInitialized && mainSection == MainSection.CURRENTLY_READING) {
            refreshCurrentlyReading(controlsState.query)
        }
    }

    private fun openDocumentPicker() {
        documentPickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/epub+zip"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        })
    }

    private fun showImportMenu() {
        AlertDialog.Builder(this).setTitle("Importar a la biblioteca")
            .setItems(arrayOf("Elegir libros EPUB", "Elegir una carpeta")) { _, index ->
                if (index == 0) openDocumentPicker() else openFolderPicker()
            }.show()
    }

    private fun openFolderPicker() {
        folderPickerLauncher.launch(null)
    }

    private fun refreshLibrary(query: String = controlsState.query) {
        documentList.removeAllViews()
        libraryPathText.visibility = View.VISIBLE
        val currentFolderIdentifier = libraryBrowserState.currentFolderIdentifier
        val documents = database.findDocumentsInFolder(currentFolderIdentifier, query)
        val folders = if (query.isBlank()) database.libraryFolders(currentFolderIdentifier) else emptyList()
        if (documents.isEmpty() && folders.isEmpty() && !libraryBrowserState.canNavigateBack) documentList.addView(emptyMessage)
        val items = libraryBrowserState.orderedItems(folders, documents)
        libraryViewRenderer.render(
            items, libraryBrowserState.displayMode, libraryBrowserState.canNavigateBack,
            libraryBrowserState.sortMode == LibrarySortMode.CUSTOM && query.isBlank()
        )
        AppThemePalette.apply(this)
    }

    private fun refreshCurrentSection(query: String) {
        when (mainSection) {
            MainSection.LIBRARY -> refreshLibrary(query)
            MainSection.CURRENTLY_READING -> refreshCurrentlyReading(query)
            MainSection.ANNOTATIONS -> Unit
        }
    }

    private fun openLibraryRoot() {
        mainSection = MainSection.LIBRARY
        libraryBrowserState.openRoot(); libraryPathText.text = libraryBrowserState.pathLabel; refreshLibrary()
    }

    private fun restoreLastDocumentFolder() {
        libraryBrowserState.restoreLastDocumentFolder(ReaderResumeState.lastDocumentIdentifier(this))
        libraryPathText.text = libraryBrowserState.pathLabel
    }

    private fun openLibraryFolder(folder: LibraryFolder) {
        libraryBrowserState.openFolder(folder)
        libraryPathText.text = libraryBrowserState.pathLabel
        controlsState = controlsState.copy(query = "")
        refreshLibrary("")
    }

    private fun navigateToParentFolder() {
        if (!libraryBrowserState.navigateToParent()) return
        libraryPathText.text = libraryBrowserState.pathLabel
        refreshLibrary("")
    }

    private fun cycleLibraryDisplayMode() {
        libraryBrowserState.cycleDisplayMode()
        controlsState = controlsState.copy(displayIcon = displayModeIcon())
        refreshCurrentSection(controlsState.query)
    }

    private fun displayModeIcon() = libraryBrowserState.displayModeIcon()

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

    private fun moveLibraryItem(draggedKey: String, targetKey: String) {
        if (libraryBrowserState.sortMode != LibrarySortMode.CUSTOM) return
        val documents = database.findDocumentsInFolder(libraryBrowserState.currentFolderIdentifier, "")
        val folders = database.libraryFolders(libraryBrowserState.currentFolderIdentifier)
        libraryBrowserState.moveCustomItem(libraryBrowserState.orderedItems(folders, documents), draggedKey, targetKey)
        refreshLibrary("")
    }

    private fun showDocumentActions(document: LibraryDocument) = documentActions.show(document)

    private fun showCurrentlyReading() {
        mainSection = MainSection.CURRENTLY_READING
        refreshCurrentlyReading(controlsState.query)
    }

    private fun refreshCurrentlyReading(query: String) {
        documentList.removeAllViews()
        libraryPathText.visibility = View.GONE
        val documents = database.findCurrentlyReadingDocuments(query)
        if (documents.isEmpty()) {
            emptyMessage.text = if (query.isBlank()) {
                "Los libros que abras aparecerán aquí, empezando por el más reciente."
            } else {
                "No hay lecturas recientes que coincidan con la búsqueda."
            }
            documentList.addView(emptyMessage)
        }
        libraryViewRenderer.render(
            documents.map(LibraryItem::Document),
            libraryBrowserState.displayMode,
            showParentFolder = false,
            allowCustomOrdering = false
        )
        AppThemePalette.apply(this)
    }

    private fun showQuotes() {
        mainSection = MainSection.ANNOTATIONS
        librarySections.showQuotes()
    }

    private fun showGeneralSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun openReader(identifier: Long) {
        val document = database.findDocument(identifier) ?: return
        if (!document.format.equals("EPUB", ignoreCase = true)) {
            Toast.makeText(this, "Michis Reader solo admite libros EPUB", Toast.LENGTH_SHORT).show(); return
        }
        database.markDocumentOpened(identifier)
        startActivity(Intent(this, ReadiumEpubActivity::class.java).putExtra("document_identifier", identifier))
    }
}
