package com.michis.reader.app

import com.michis.reader.R
import com.michis.reader.data.*
import com.michis.reader.databinding.ActivityMainBinding
import com.michis.reader.library.*
import com.michis.reader.reader.ReadiumEpubActivity
import com.michis.reader.settings.SettingsActivity
import com.michis.reader.sync.*
import com.michis.reader.theme.AppThemePalette
import com.michis.reader.ui.SystemBarInsets

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.content.res.ColorStateList
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var database: ReaderDatabase
    private lateinit var documentList: LinearLayout
    private lateinit var emptyMessage: TextView
    private lateinit var searchInput: EditText
    private lateinit var libraryBrowserState: LibraryBrowserState
    private lateinit var libraryViewRenderer: LibraryViewRenderer
    private lateinit var importCoordinator: LibraryImportCoordinator
    private lateinit var documentActions: LibraryDocumentActions
    private lateinit var librarySections: LibrarySectionsController
    private lateinit var syncController: LibrarySyncController
    private lateinit var libraryDisplayButton: Button
    private lateinit var libraryFilterButton: Button
    private lateinit var syncStatusText: TextView
    private lateinit var syncButton: Button
    private lateinit var libraryPathText: TextView
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
                if (::libraryBrowserState.isInitialized && libraryBrowserState.canNavigateBack) {
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
        documentActions = LibraryDocumentActions(this, database, { refreshLibrary() }) {
            syncStatusText.text = it
        }
        librarySections = LibrarySectionsController(this, database, documentList, libraryPathText)
        syncController = LibrarySyncController(
            this, syncStatusText, syncButton, ::showGeneralSettings
        ) { refreshLibrary(searchInput.text?.toString().orEmpty()) }
        SystemBarInsets.apply(mainScreen)
        setContentView(mainScreen)
        AppThemePalette.apply(this)
        updateBrandColors()
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
        searchInput = binding.searchInput
        libraryDisplayButton = binding.libraryDisplayButton.apply { text = displayModeIcon() }
        libraryFilterButton = binding.libraryFilterButton.apply {
            text = "Filtro: ${libraryBrowserState.sortMode.label}"
        }
        syncStatusText = binding.syncStatusText
        syncButton = binding.syncButton
        libraryPathText = binding.libraryPathText
        syncButton.setOnClickListener { syncController.synchronize() }
        binding.settingsButton.setOnClickListener { showGeneralSettings() }
        binding.importButton.setOnClickListener { showImportMenu() }
        searchInput.addTextChangedListener(SimpleTextWatcher { refreshLibrary(it) })
        binding.libraryTabButton.setOnClickListener { openLibraryRoot() }
        binding.quotesTabButton.setOnClickListener { showAnnotations("cita") }
        binding.bookmarksTabButton.setOnClickListener { showAnnotations("marcador") }
        binding.dictionariesTabButton.setOnClickListener { showDictionaries() }
        libraryDisplayButton.setOnClickListener { cycleLibraryDisplayMode() }
        libraryFilterButton.setOnClickListener { showLibraryFilters() }
        libraryPathText.setOnClickListener { navigateToParentFolder() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importCoordinator.importIncoming(intent)
    }

    override fun onResume() {
        super.onResume()
        AppThemePalette.apply(this)
        updateBrandColors()
        if (::syncController.isInitialized) syncController.refreshStatus()
    }

    private fun updateBrandColors() {
        binding.appMark.imageTintList = ColorStateList.valueOf(AppThemePalette.current(this).accent)
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

    private fun refreshLibrary(query: String = searchInput.text?.toString().orEmpty()) {
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

    private fun openLibraryRoot() {
        libraryBrowserState.openRoot(); libraryPathText.text = libraryBrowserState.pathLabel; refreshLibrary()
    }

    private fun restoreLastDocumentFolder() {
        libraryBrowserState.restoreLastDocumentFolder(ReaderResumeState.lastDocumentIdentifier(this))
        libraryPathText.text = libraryBrowserState.pathLabel
    }

    private fun openLibraryFolder(folder: LibraryFolder) {
        libraryBrowserState.openFolder(folder)
        libraryPathText.text = libraryBrowserState.pathLabel
        searchInput.setText(""); refreshLibrary("")
    }

    private fun navigateToParentFolder() {
        if (!libraryBrowserState.navigateToParent()) return
        libraryPathText.text = libraryBrowserState.pathLabel
        refreshLibrary("")
    }

    private fun cycleLibraryDisplayMode() {
        libraryBrowserState.cycleDisplayMode()
        libraryDisplayButton.text = displayModeIcon(); refreshLibrary()
    }

    private fun displayModeIcon() = libraryBrowserState.displayModeIcon()

    private fun showLibraryFilters() {
        val modes = LibrarySortMode.entries
        AlertDialog.Builder(this)
            .setTitle("Ordenar biblioteca")
            .setSingleChoiceItems(modes.map { it.label }.toTypedArray(), libraryBrowserState.sortMode.ordinal) { dialog, index ->
                libraryBrowserState.selectSortMode(modes[index])
                libraryFilterButton.text = "Filtro: ${modes[index].label}"
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

    private fun showAnnotations(kind: String) = librarySections.showAnnotations(kind)

    private fun showDictionaries() = librarySections.showDictionaries()
    private fun showGeneralSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun openReader(identifier: Long) {
        val document = database.findDocument(identifier) ?: return
        if (!document.format.equals("EPUB", ignoreCase = true)) {
            Toast.makeText(this, "Michis Reader solo admite libros EPUB", Toast.LENGTH_SHORT).show(); return
        }
        startActivity(Intent(this, ReadiumEpubActivity::class.java).putExtra("document_identifier", identifier))
    }
}

private class SimpleTextWatcher(private val changed: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) = changed(value?.toString().orEmpty())
    override fun afterTextChanged(value: android.text.Editable?) = Unit
}
