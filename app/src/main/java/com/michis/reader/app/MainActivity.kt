package com.michis.reader.app

import com.michis.reader.R
import com.michis.reader.data.*
import com.michis.reader.library.*
import com.michis.reader.reader.ReadiumEpubActivity
import com.michis.reader.settings.SettingsActivity
import com.michis.reader.sync.*
import com.michis.reader.theme.AppThemePalette

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private enum class MainSection { LIBRARY, CURRENTLY_READING, ANNOTATIONS, DICTIONARIES }

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
        val mainScreen = buildScreen()
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
        librarySections = LibrarySectionsController(this, database, documentList, libraryPathText, ::openReader)
        syncController = LibrarySyncController(
            this, syncStatusText, syncButton, ::showGeneralSettings
        ) { refreshCurrentSection(searchInput.text?.toString().orEmpty()) }
        applySafeSystemBarPadding(mainScreen)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importCoordinator.importIncoming(intent)
    }

    override fun onResume() {
        super.onResume()
        AppThemePalette.apply(this)
        if (::syncController.isInitialized) syncController.refreshStatus()
        if (::librarySections.isInitialized && mainSection == MainSection.CURRENTLY_READING) {
            librarySections.showCurrentlyReading(searchInput.text?.toString().orEmpty())
        }
    }

    private fun buildScreen(): View = verticalLayout {
        setPadding(dp(16), dp(14), dp(16), 0)
        AppThemePalette.markBackground(this)
        addView(horizontalLayout {
            addView(TextView(context).apply {
                text = "Mi biblioteca"; textSize = 30f; setTextColor(Color.rgb(25, 27, 33)); typeface = android.graphics.Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            syncButton = Button(context).apply {
                text = "↻"; contentDescription = "Sincronizar ahora"; textSize = 20f; minWidth = 0
                setOnClickListener { syncController.synchronize() }
            }
            addView(syncButton)
            addView(Button(context).apply {
                text = "⚙"; contentDescription = "Configuración"; textSize = 20f; minWidth = 0
                setOnClickListener { showGeneralSettings() }
            })
        })
        addView(TextView(context).apply {
            text = "Tus lecturas, disponibles sin conexión"; textSize = 15f; setTextColor(Color.DKGRAY
            ); setPadding(0, dp(4), 0, dp(16))
        })
        syncStatusText = TextView(context).apply {
            textSize = 12f; setTextColor(Color.DKGRAY); setPadding(0, 0, 0, dp(8))
        }
        addView(syncStatusText)
        addView(horizontalLayout {
            searchInput = EditText(context).apply {
                hint = "Buscar por título, autor o archivo EPUB"; setSingleLine(); setBackgroundResource(R.drawable.rounded_panel)
                addTextChangedListener(SimpleTextWatcher(::refreshCurrentSection))
            }
            addView(searchInput, LinearLayout.LayoutParams(0, dp(54), 1f))
            addView(Button(context).apply {
                text = "+"; contentDescription = "Importar"; textSize = 22f; minWidth = 0
                setOnClickListener { showImportMenu() }
            }, LinearLayout.LayoutParams(dp(52), dp(54)).apply { marginStart = dp(8) })
        })
        addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(horizontalLayout {
            setPadding(0, dp(12), 0, dp(12))
            addView(tabButton("Biblioteca") { openLibraryRoot() })
            addView(tabButton("Leyendo actualmente") { showCurrentlyReading() })
            addView(tabButton("Citas") { showAnnotations("cita") })
            addView(tabButton("Marcadores") { showAnnotations("marcador") })
            addView(tabButton("Diccionarios") { showDictionaries() })
            libraryDisplayButton = tabButton(displayModeIcon()) { cycleLibraryDisplayMode() }
            addView(libraryDisplayButton)
            libraryFilterButton = tabButton("Filtro: ${libraryBrowserState.sortMode.label}") { showLibraryFilters() }
            addView(libraryFilterButton)
        }) })
        libraryPathText = TextView(context).apply {
            text = "Mi biblioteca"; textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(92, 73, 122)); setPadding(dp(6), dp(2), dp(6), dp(10))
            setOnClickListener { navigateToParentFolder() }
        }
        addView(libraryPathText)
        val scroll = ScrollView(context).apply { clipToPadding = false; setPadding(0, dp(4), 0, dp(6)) }
        documentList = verticalLayout { clipChildren = false; clipToPadding = false }
        emptyMessage = TextView(context).apply {
            text = "Aún no hay libros EPUB.\nImporta uno para comenzar."; textSize = 18f; gravity = Gravity.CENTER
            setTextColor(Color.GRAY); setPadding(dp(20), dp(80), dp(20), dp(30))
        }
        scroll.addView(documentList)
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun tabButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false; setOnClickListener { action() }
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

    private fun refreshCurrentSection(query: String) {
        when (mainSection) {
            MainSection.LIBRARY -> refreshLibrary(query)
            MainSection.CURRENTLY_READING -> librarySections.showCurrentlyReading(query)
            MainSection.ANNOTATIONS, MainSection.DICTIONARIES -> Unit
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

    private fun showCurrentlyReading() {
        mainSection = MainSection.CURRENTLY_READING
        librarySections.showCurrentlyReading(searchInput.text?.toString().orEmpty())
    }

    private fun showAnnotations(kind: String) {
        mainSection = MainSection.ANNOTATIONS
        librarySections.showAnnotations(kind)
    }

    private fun showDictionaries() {
        mainSection = MainSection.DICTIONARIES
        librarySections.showDictionaries()
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
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun applySafeSystemBarPadding(view: View) {
        val originalLeft = view.paddingLeft
        val originalTop = view.paddingTop
        val originalRight = view.paddingRight
        view.setOnApplyWindowInsetsListener { target, windowInsets ->
            val leftInset: Int
            val topInset: Int
            val rightInset: Int
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val systemBars = windowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                leftInset = systemBars.left
                topInset = systemBars.top
                rightInset = systemBars.right
            } else {
                @Suppress("DEPRECATION")
                leftInset = windowInsets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                topInset = windowInsets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                rightInset = windowInsets.systemWindowInsetRight
            }
            target.setPadding(
                originalLeft + leftInset,
                originalTop + topInset,
                originalRight + rightInset,
                target.paddingBottom
            )
            windowInsets
        }
        view.requestApplyInsets()
    }
    private fun verticalLayout(block: LinearLayout.() -> Unit) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; block() }
    private fun horizontalLayout(block: LinearLayout.() -> Unit) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; block() }

}

private class SimpleTextWatcher(private val changed: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) = changed(value?.toString().orEmpty())
    override fun afterTextChanged(value: android.text.Editable?) = Unit
}
