@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import com.michis.reader.R
import com.michis.reader.annotations.*
import com.michis.reader.data.*
import com.michis.reader.databinding.ActivityReadiumEpubBinding
import com.michis.reader.databinding.ViewEpubBottomControlsBinding
import com.michis.reader.databinding.ViewEpubTopControlsBinding
import com.michis.reader.databinding.ViewEpubSearchPanelBinding
import com.michis.reader.dictionary.DictionaryActivity
import com.michis.reader.settings.*
import com.michis.reader.spen.*
import com.michis.reader.theme.*

import android.app.AlertDialog
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class ReadiumEpubActivity : FragmentActivity() {
    private lateinit var screenBinding: ActivityReadiumEpubBinding
    private val readerSettings by lazy { ReaderSettingsRepository.get(this) }
    private val readerWindow by lazy { ReaderWindowController(this) }
    private lateinit var database: ReaderDatabase
    private lateinit var document: LibraryDocument
    private lateinit var rootLayout: FrameLayout
    private lateinit var topControls: LinearLayout
    private lateinit var bottomControls: LinearLayout
    private lateinit var settingsPanel: View
    private lateinit var contentsPanel: View
    private lateinit var searchPanel: View
    private lateinit var searchPanelBinding: ViewEpubSearchPanelBinding
    private lateinit var searchController: EpubSearchController
    private lateinit var panelCoordinator: ReaderPanelCoordinator
    private lateinit var contentsController: EpubContentsPanel
    private lateinit var progressSlider: SeekBar
    private lateinit var compactProgressSlider: SeekBar
    private lateinit var progressLabel: TextView
    private lateinit var dictionaryButton: Button
    private lateinit var pageJumpActions: LinearLayout
    private lateinit var jumpBackButton: Button
    private lateinit var clearJumpHistoryButton: Button
    private lateinit var jumpForwardButton: Button
    private lateinit var navigator: EpubNavigatorFragment
    private lateinit var appearanceController: EpubAppearanceController
    private var controlsAreVisible = true
    private var userIsDraggingProgress = false
    private var touchStartedX = 0f
    private var touchStartedY = 0f
    private var touchStartedAt = 0L
    private var activeQuickMode = 0
    private lateinit var decorationController: EpubDecorationController
    private var quickModeGestureIsActive = false
    private var lastBookmarkActionAt = 0L
    private val navigationHistory = ReaderNavigationHistory()
    private var sliderJumpOriginPage: Int? = null
    private lateinit var spenRemoteController: SpenRemoteController
    private lateinit var spenActionController: SpenReaderActionController
    private var sessionController: ReaderSessionController? = null
    private var pagePositions = emptyList<org.readium.r2.shared.publication.Locator>()

    private val quotesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val requestedLocation = result.data?.getIntExtra(BookQuotesActivity.EXTRA_QUOTE_LOCATION, -1) ?: -1
        navigateToExternalLocation(requestedLocation)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        readerWindow.configureEdgeToEdge()
        database = ReaderDatabase.getInstance(this)
        document = database.findDocument(intent.getLongExtra("document_identifier", -1)) ?: run { finish(); return }
        sessionController = ReaderSessionController(this, database, document)
        spenActionController = SpenReaderActionController(
            readerSettings,
            SpenReaderActionController.Actions(
                nextPage = { navigateOnePage(1) },
                previousPage = { navigateOnePage(-1) },
                toggleControls = ::toggleControls,
                toggleBookmark = ::saveCurrentBookmark,
                increaseText = { changeFontSizeFromSpen(1f) },
                decreaseText = { changeFontSizeFromSpen(-1f) },
                toggleQuickTheme = {
                    activeQuickMode = 1 - activeQuickMode
                    appearanceController.applyQuickMode(activeQuickMode)
                },
                addSelectionToDictionary = { useSelectedTextFromSpen(addToDictionary = true) },
                addSelectionAsQuote = { useSelectedTextFromSpen(addToDictionary = false) },
                interactionCompleted = ::configureReaderScreenTimeout
            )
        )
        spenRemoteController = SpenRemoteController(this, ::executeSpenGesture)
        decorationController = EpubDecorationController(
            context = this,
            database = database,
            documentIdentifier = document.identifier,
            settings = readerSettings,
            openDictionaryEntry = { entryIdentifier ->
                launchReaderMenu(Intent(this, DictionaryActivity::class.java)
                    .putExtra(DictionaryActivity.EXTRA_DOCUMENT_IDENTIFIER, document.identifier)
                    .putExtra(DictionaryActivity.EXTRA_ENTRY_IDENTIFIER, entryIdentifier))
            },
            editQuote = { quoteIdentifier ->
                launchReaderMenu(Intent(this, QuoteColorActivity::class.java)
                    .putExtra(QuoteColorActivity.EXTRA_QUOTE_IDENTIFIER, quoteIdentifier))
            }
        )
        setContentView(buildScreen())
        appearanceController.applyInitialTheme()
        configureReaderScreenTimeout()
        openWithReadium()
    }

    private fun buildScreen(): View {
        screenBinding = ActivityReadiumEpubBinding.inflate(layoutInflater)
        rootLayout = screenBinding.rootLayout
        appearanceController = EpubAppearanceController(
            activity = this,
            settings = readerSettings,
            scope = lifecycleScope,
            readerRoot = rootLayout,
            navigator = { if (::navigator.isInitialized) navigator else null },
            applyMenuColors = ::applyMenuColors
        )
        rootLayout.setBackgroundColor(ReadingThemePalette.colors(readerSettings.theme).first)
        topControls = configureTopControls(screenBinding.screenTopControls)
        bottomControls = configureBottomControls(screenBinding.screenBottomControls)
        compactProgressSlider = screenBinding.compactProgressSlider
        configureCompactProgressSlider()
        settingsPanel = screenBinding.settingsPanelHost.apply {
            addView(buildSettingsPanel(), FrameLayout.LayoutParams(-1, -1))
            visibility = View.GONE
        }
        contentsPanel = screenBinding.contentsPanelHost.apply {
            layoutParams = (layoutParams as FrameLayout.LayoutParams).apply {
                width = (resources.displayMetrics.widthPixels * .88f).toInt()
            }
            addView(buildContentsPanel(), FrameLayout.LayoutParams(-1, -1))
            visibility = View.GONE
        }
        searchPanel = screenBinding.searchPanelHost.apply {
            addView(buildSearchPanel(), FrameLayout.LayoutParams(-1, -2))
            visibility = View.GONE
        }
        panelCoordinator = ReaderPanelCoordinator(
            mapOf(
                ReaderPanel.SETTINGS to settingsPanel,
                ReaderPanel.CONTENTS to contentsPanel,
                ReaderPanel.SEARCH to searchPanel
            )
        ) { closedPanel ->
            if (closedPanel == ReaderPanel.SEARCH && ::decorationController.isInitialized) {
                lifecycleScope.launch { decorationController.clearSearchResults() }
            }
        }
        readerWindow.applySystemBarPadding(rootLayout, topControls, bottomControls, settingsPanel, contentsPanel)
        return rootLayout
    }

    private fun configureTopControls(binding: ViewEpubTopControlsBinding): LinearLayout {
        binding.documentTitle.text = document.title
        binding.backButton.setOnClickListener { finish() }
        binding.toolsButton.setOnClickListener { showEpubMoreMenu() }
        binding.contentsButton.setOnClickListener { toggleContentsPanel() }
        binding.searchButton.setOnClickListener { toggleSearchPanel() }
        binding.quotesButton.setOnClickListener { openBookQuotes() }
        dictionaryButton = binding.dictionaryButton.apply { setOnClickListener { openDictionary() } }
        binding.bookmarkButton.setOnClickListener { saveCurrentBookmark() }
        binding.readingSettingsButton.setOnClickListener { toggleSettingsPanel() }
        val landscapeLayout = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        binding.toolsButton.visibility = if (landscapeLayout) View.GONE else View.VISIBLE
        binding.landscapeToolsContainer.visibility = if (landscapeLayout) View.VISIBLE else View.GONE
        binding.root.setBackgroundColor(AppThemePalette.forReader(this, readerSettings.theme).surface)
        return binding.root
    }

    private fun showEpubMoreMenu() {
        val options = arrayOf("Buscar", "Índice", "Citas", if (database.hasEffectiveDictionaryEntries(document.identifier)) "Diccionario" else "Crear diccionario", "Agregar o quitar marcador")
        AlertDialog.Builder(this).setTitle("Herramientas").setItems(options) { _, index ->
            when (index) {
                0 -> toggleSearchPanel()
                1 -> toggleContentsPanel()
                2 -> openBookQuotes()
                3 -> openDictionary()
                4 -> saveCurrentBookmark()
            }
        }.show()
    }

    private fun configureBottomControls(binding: ViewEpubBottomControlsBinding): LinearLayout {
        progressLabel = binding.progressLabel
        progressSlider = binding.progressSlider
        pageJumpActions = binding.pageJumpActions
        jumpBackButton = binding.jumpBackButton.apply { setOnClickListener { returnToPreviousJump() } }
        clearJumpHistoryButton = binding.clearJumpHistoryButton.apply { setOnClickListener { clearPageJumpHistory() } }
        jumpForwardButton = binding.jumpForwardButton.apply { setOnClickListener { advanceToNextJump() } }
        progressSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userIsDraggingProgress = true
                sliderJumpOriginPage = currentPageIndex()
            }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    progressLabel.text = "Página ${progress + 1} de ${pagePositions.size.coerceAtLeast(1)}"
                    navigateToPage(progress, animated = false)
                }
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                userIsDraggingProgress = false
                sliderJumpOriginPage?.let { recordPageJump(it, seekBar.progress) }
                sliderJumpOriginPage = null
                navigateToPage(seekBar.progress)
            }
        })
        binding.root.setBackgroundColor(AppThemePalette.forReader(this, readerSettings.theme).surface)
        return binding.root
    }

    private fun configureCompactProgressSlider() = compactProgressSlider.apply {
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userIsDraggingProgress = true
                sliderJumpOriginPage = currentPageIndex()
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                progressSlider.progress = progress
                navigateToPage(progress, animated = false)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                userIsDraggingProgress = false
                progressSlider.progress = seekBar.progress
                sliderJumpOriginPage?.let { recordPageJump(it, seekBar.progress) }
                sliderJumpOriginPage = null
                navigateToPage(seekBar.progress)
            }
        })
    }

    private fun buildSettingsPanel(): View = EpubReadingSettingsPanel(
        activity = this,
        settings = readerSettings,
        submitPreferences = appearanceController::submit,
        selectTheme = appearanceController::applyReadingTheme,
        selectFont = appearanceController::showFontSelection,
        closePanel = { settingsPanel.visibility = View.GONE }
    ).create()

    private fun buildSearchPanel(): View {
        searchPanelBinding = ViewEpubSearchPanelBinding.inflate(layoutInflater)
        searchController = EpubSearchController(
            binding = searchPanelBinding,
            scope = lifecycleScope,
            decorations = decorationController,
            navigationHistory = navigationHistory,
            currentPageIndex = ::currentPageIndex,
            animationsEnabled = ::pageAnimationsEnabled,
            navigate = { locator, animated -> navigator.go(locator, animated = animated) },
            scheduleDelayed = { action, delay -> rootLayout.postDelayed(action, delay) }
        )
        searchPanelBinding.searchPanel.tag = MENU_CARD_TAG
        searchPanelBinding.searchButton.setOnClickListener { searchController.performSearch() }
        searchPanelBinding.searchInput.setOnEditorActionListener { _, _, _ ->
            searchController.performSearch()
            true
        }
        searchPanelBinding.previousResultButton.setOnClickListener { searchController.move(-1) }
        searchPanelBinding.nextResultButton.setOnClickListener { searchController.move(1) }
        searchPanelBinding.closeSearchButton.setOnClickListener { closeSearchPanel() }
        return searchPanelBinding.root
    }


    private fun buildContentsPanel(): View {
        contentsController = EpubContentsPanel(
            activity = this,
            navigateTo = { link ->
                val originPage = currentPageIndex()
                val jumpToken = navigationHistory.beginPending(PendingJumpSource.CONTENTS, originPage)
                navigator.go(link, animated = pageAnimationsEnabled())
                rootLayout.postDelayed({
                    navigationHistory.cancelPending(PendingJumpSource.CONTENTS, jumpToken)
                }, 2_000L)
            },
            closePanel = { contentsPanel.visibility = View.GONE }
        )
        return contentsController.create()
    }

    private fun openWithReadium() {
        lifecycleScope.launch {
            val httpClient = DefaultHttpClient()
            val assetRetriever = AssetRetriever(contentResolver, httpClient)
            val opener = PublicationOpener(DefaultPublicationParser(this@ReadiumEpubActivity, httpClient, assetRetriever, null))
            val url = Uri.parse(document.uri).toAbsoluteUrl() ?: run { showError("Ubicación no válida"); return@launch }
            val asset = assetRetriever.retrieve(url).getOrElse { showError(it.toString()); return@launch }
            val opened = opener.open(asset, allowUserInteraction = false).getOrElse { showError(it.toString()); return@launch }
            searchController.attachPublication(opened)
            pagePositions = opened.positions()
            val savedLocation = database.readerLocation(document.identifier)
            val initialLocator = pagePositions.firstOrNull { it.locations.position == savedLocation }
                ?: opened.locateProgression(document.progress.toDouble().coerceIn(0.0, 1.0))
            val initialPreferences = appearanceController.initialPreferences()
            val factory = EpubNavigatorFactory(opened)
            supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
                initialLocator = initialLocator,
                initialPreferences = initialPreferences,
                paginationListener = object : EpubNavigatorFragment.PaginationListener {
                    override fun onPageLoaded() {
                        appearanceController.applyDocumentLayout()
                    }
                },
                configuration = EpubNavigatorFragment.Configuration(selectionActionModeCallback = selectionActions())
            )
            supportFragmentManager.commitNow {
                replace(screenBinding.navigatorContainer.id, EpubNavigatorFragment::class.java, null, NAVIGATOR_TAG)
            }
            navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment
            refreshDictionaryButton()
            progressSlider.max = (pagePositions.size - 1).coerceAtLeast(1)
            compactProgressSlider.max = progressSlider.max
            contentsController.populate(document.title, opened.tableOfContents)
            observeProgress()
            decorationController.attach(opened, navigator)
            decorationController.refreshAll()
            val requestedLocation = intent.getIntExtra(BookQuotesActivity.EXTRA_QUOTE_LOCATION, -1)
            if (requestedLocation >= 0) {
                val target = pagePositions.indexOfFirst { it.locations.position == requestedLocation }
                if (target >= 0) {
                    val origin = currentPageIndex()
                    recordPageJump(origin, target)
                    navigateToPage(target, animated = false)
                }
            }
        }
    }

    private fun selectionActions() = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(0, ACTION_ADD_QUOTE, 0, "Cita")
            menu.add(0, ACTION_ADD_DICTIONARY, 1, "Diccionario")
            menu.add(0, ACTION_COPY, 10, "Copiar")
            menu.add(0, ACTION_SEARCH, 11, "Buscar")
            menu.add(0, ACTION_TRANSLATE, 12, "Traducir")
            menu.add(0, ACTION_SHARE, 13, "Compartir")
            return true
        }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId !in setOf(ACTION_ADD_QUOTE, ACTION_ADD_DICTIONARY, ACTION_COPY, ACTION_SEARCH, ACTION_TRANSLATE, ACTION_SHARE)) return false
            lifecycleScope.launch {
                val selection = navigator.currentSelection() ?: return@launch
                val selectedText = selection.locator.text.highlight.orEmpty().trim()
                if (selectedText.isNotBlank()) {
                    when (item.itemId) {
                        ACTION_ADD_QUOTE -> {
                            openQuoteColorPicker(selectedText, selection.locator)
                        }
                        ACTION_ADD_DICTIONARY -> {
                            launchReaderMenu(Intent(this@ReadiumEpubActivity, DictionaryActivity::class.java)
                                .putExtra(DictionaryActivity.EXTRA_DOCUMENT_IDENTIFIER, document.identifier)
                                .putExtra(DictionaryActivity.EXTRA_SELECTED_TEXT, selectedText)
                                .putExtra(DictionaryActivity.EXTRA_SELECTED_CONTEXT, selection.locator.text.before.orEmpty()))
                        }
                        ACTION_COPY -> copyText(selectedText)
                        ACTION_SEARCH -> searchText(selectedText)
                        ACTION_TRANSLATE -> processText(selectedText)
                        ACTION_SHARE -> shareText(selectedText)
                    }
                }
                navigator.clearSelection(); mode.finish()
            }
            return true
        }
        override fun onDestroyActionMode(mode: ActionMode) = Unit
    }

    override fun onResume() {
        super.onResume()
        sessionController?.onResume()
        configureReaderScreenTimeout()
        if (::spenRemoteController.isInitialized) spenRemoteController.connect()
        if (::topControls.isInitialized && ::bottomControls.isInitialized && ::settingsPanel.isInitialized) {
            val selectedTheme = readerSettings.theme
            val themeIndex = ReadingThemePalette.names.indexOf(selectedTheme).coerceAtLeast(0)
            if (::navigator.isInitialized) appearanceController.applyReadingTheme(themeIndex)
            else {
                rootLayout.setBackgroundColor(ReadingThemePalette.colors(selectedTheme).first)
                applyMenuColors(ReadingThemePalette.colors(selectedTheme))
            }
        }
        if (::database.isInitialized && ::document.isInitialized && ::dictionaryButton.isInitialized) {
            refreshDictionaryButton()
            if (::navigator.isInitialized) lifecycleScope.launch { decorationController.refreshAll() }
        }
    }

    private fun refreshDictionaryButton() {
        dictionaryButton.text = decorationController.dictionaryButtonLabel()
    }

    private fun openDictionary() {
        launchReaderMenu(Intent(this, DictionaryActivity::class.java).putExtra(DictionaryActivity.EXTRA_DOCUMENT_IDENTIFIER, document.identifier))
    }

    private fun openBookQuotes() {
        sessionController?.markReaderMenuOpened()
        quotesLauncher.launch(Intent(this, BookQuotesActivity::class.java)
            .putExtra(BookQuotesActivity.EXTRA_DOCUMENT_IDENTIFIER, document.identifier)
            .putExtra(BookQuotesActivity.EXTRA_RETURN_TO_READER, true))
    }

    private fun navigateToExternalLocation(location: Int) {
        if (location < 0 || !::navigator.isInitialized) return
        val targetPage = pagePositions.indexOfFirst { it.locations.position == location }
        if (targetPage < 0) return
        val originPage = currentPageIndex()
        recordPageJump(originPage, targetPage)
        navigateToPage(targetPage)
    }

    private fun toggleSearchPanel() {
        if (!panelCoordinator.toggle(ReaderPanel.SEARCH)) return
        applyMenuColors(ReadingThemePalette.colors(readerSettings.theme))
        searchPanelBinding.searchInput.requestFocus()
    }

    private fun closeSearchPanel() {
        panelCoordinator.close(ReaderPanel.SEARCH)
    }

    private fun openQuoteColorPicker(selectedText: String, locator: org.readium.r2.shared.publication.Locator) {
        launchReaderMenu(Intent(this, QuoteColorActivity::class.java)
            .putExtra(QuoteColorActivity.EXTRA_DOCUMENT_IDENTIFIER, document.identifier)
            .putExtra(QuoteColorActivity.EXTRA_TEXT, selectedText)
            .putExtra(QuoteColorActivity.EXTRA_LOCATION, locator.locations.position ?: 0)
            .putExtra(QuoteColorActivity.EXTRA_LOCATOR_JSON, locator.toJSON().toString())
            .putExtra(QuoteColorActivity.EXTRA_PAGE_NUMBER, progressSlider.progress + 1))
    }

    private fun observeProgress() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collect { locator ->
                    val progression = locator.locations.totalProgression ?: return@collect
                    database.updateProgress(document.identifier, locator.locations.position ?: 0, progression.toFloat())
                    val pageIndex = ((locator.locations.position ?: 1) - 1).coerceIn(0, (pagePositions.size - 1).coerceAtLeast(0))
                    if (navigationHistory.confirmArrival(pageIndex)) updatePageJumpActions()
                    if (!userIsDraggingProgress) progressSlider.progress = pageIndex
                    if (!userIsDraggingProgress) compactProgressSlider.progress = pageIndex
                    if (!userIsDraggingProgress) progressLabel.text = "Página ${pageIndex + 1} de ${pagePositions.size.coerceAtLeast(1)}"
                }
            }
        }
    }

    private fun navigateToPage(pageIndex: Int, animated: Boolean = pageAnimationsEnabled()) {
        pagePositions.getOrNull(pageIndex)?.let { navigator.go(it, animated = animated && pageAnimationsEnabled()) }
    }

    private fun currentPageIndex(): Int {
        if (!::navigator.isInitialized || pagePositions.isEmpty()) return progressSlider.progress
        val position = navigator.currentLocator.value.locations.position ?: return progressSlider.progress
        return (position - 1).coerceIn(0, pagePositions.lastIndex)
    }

    private fun recordPageJump(originPage: Int, destinationPage: Int) {
        if (originPage == destinationPage || originPage !in pagePositions.indices || destinationPage !in pagePositions.indices) return
        navigationHistory.record(originPage, destinationPage)
        updatePageJumpActions()
    }

    private fun returnToPreviousJump() {
        val destination = navigationHistory.moveBack() ?: return
        navigateToPage(destination, animated = false)
        updatePageJumpActions()
    }

    private fun advanceToNextJump() {
        val destination = navigationHistory.moveForward() ?: return
        navigateToPage(destination, animated = false)
        updatePageJumpActions()
    }

    private fun clearPageJumpHistory() {
        navigationHistory.clear()
        updatePageJumpActions()
        Toast.makeText(this, "Historial de saltos eliminado", Toast.LENGTH_SHORT).show()
    }

    private fun updatePageJumpActions() {
        if (!::pageJumpActions.isInitialized) return
        val backPage = navigationHistory.previousPageIndex
        val forwardPage = navigationHistory.nextPageIndex
        jumpBackButton.apply {
            visibility = if (backPage == null) View.INVISIBLE else View.VISIBLE
            if (backPage != null) text = "Regresar a página ${backPage + 1}"
        }
        clearJumpHistoryButton.visibility = if (navigationHistory.hasNavigation) View.VISIBLE else View.GONE
        jumpForwardButton.apply {
            visibility = if (forwardPage == null) View.INVISIBLE else View.VISIBLE
            if (forwardPage != null) text = "Avanzar a página ${forwardPage + 1}"
        }
        pageJumpActions.visibility = if (navigationHistory.hasNavigation) View.VISIBLE else View.GONE
    }

    private fun navigateOnePage(direction: Int) {
        if (direction < 0) navigator.goBackward(animated = pageAnimationsEnabled())
        else navigator.goForward(animated = pageAnimationsEnabled())
    }

    private fun pageAnimationsEnabled(): Boolean =
        readerSettings.pageTurnAnimations

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!::navigator.isInitialized) return super.onKeyDown(keyCode, event)
        val gesture = spenActionController.gestureForKeyCode(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (spenRemoteController.isConnected) return true
        executeSpenGesture(gesture)
        return true
    }

    private fun executeSpenGesture(gesture: SpenControlPreferences.Gesture) {
        if (!::navigator.isInitialized) return
        spenActionController.execute(gesture)
    }

    private fun useSelectedTextFromSpen(addToDictionary: Boolean) {
        lifecycleScope.launch {
            val selection = navigator.currentSelection()
            val selectedText = selection?.locator?.text?.highlight.orEmpty().trim()
            if (selectedText.isBlank()) {
                Toast.makeText(this@ReadiumEpubActivity, "No se puede realizar la acción porque no hay texto seleccionado", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (addToDictionary) launchReaderMenu(Intent(this@ReadiumEpubActivity, DictionaryActivity::class.java)
                .putExtra(DictionaryActivity.EXTRA_DOCUMENT_IDENTIFIER, document.identifier)
                .putExtra(DictionaryActivity.EXTRA_SELECTED_TEXT, selectedText)
                .putExtra(DictionaryActivity.EXTRA_SELECTED_CONTEXT, selection?.locator?.text?.before.orEmpty()))
            else selection?.locator?.let { openQuoteColorPicker(selectedText, it) }
        }
    }

    private fun changeFontSizeFromSpen(change: Float) {
        val preferences = readerSettings.preferences
        val size = (readerSettings.fontSizeDp + change).coerceIn(
            ReaderSettingsRepository.MINIMUM_FONT_SIZE_DP,
            ReaderSettingsRepository.MAXIMUM_FONT_SIZE_DP
        )
        readerSettings.fontSizeDp = size
        appearanceController.submit(EpubPreferences(fontSize = size / 16.0))
        Toast.makeText(this, "Texto: ${size.toInt()} dp", Toast.LENGTH_SHORT).show()
    }

    private fun saveCurrentBookmark() {
        if (!::navigator.isInitialized) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBookmarkActionAt < 3_000L) {
            Toast.makeText(this, "Espera 3 segundos antes de volver a cambiar el marcador", Toast.LENGTH_SHORT).show(); return
        }
        lastBookmarkActionAt = now
        val locator = navigator.currentLocator.value
        val location = locator.locations.position ?: 0
        val existing = database.bookmarkAt(document.identifier, location)
        if (existing != null) {
            database.deleteAnnotation(existing.identifier)
            Toast.makeText(this, "Marcador eliminado de esta página", Toast.LENGTH_SHORT).show()
        } else {
            val markerColor = runCatching { Color.parseColor(readerSettings.preferences.getString(ReaderSettingsRepository.KEY_BOOKMARK_COLOR, ReaderSettingsRepository.DEFAULT_BOOKMARK_COLOR)) }
                .getOrDefault(Color.rgb(244, 180, 0))
            database.addAnnotation(document.identifier, "marcador", locator.title.orEmpty(), "", markerColor, location, progressSlider.progress + 1)
            Toast.makeText(this, "Marcador agregado a esta página", Toast.LENGTH_SHORT).show()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::compactProgressSlider.isInitialized && compactProgressSlider.visibility == View.VISIBLE) {
            val sliderBounds = Rect()
            compactProgressSlider.getGlobalVisibleRect(sliderBounds)
            if (sliderBounds.contains(event.rawX.toInt(), event.rawY.toInt())) return super.dispatchTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                configureReaderScreenTimeout()
                touchStartedX = event.x; touchStartedY = event.y; touchStartedAt = event.eventTime
                quickModeGestureIsActive = !controlsAreVisible && event.x < dp(90) && event.y < dp(140)
                if (quickModeGestureIsActive) { if (::navigator.isInitialized) navigator.clearSelection(); return true }
            }
            MotionEvent.ACTION_UP -> {
                if (quickModeGestureIsActive) {
                    quickModeGestureIsActive = false
                    activeQuickMode = 1 - activeQuickMode
                    appearanceController.applyQuickMode(activeQuickMode)
                    return true
                }
                val movement = kotlin.math.hypot(event.x - touchStartedX, event.y - touchStartedY)
                val shortTap = movement < dp(12) && event.eventTime - touchStartedAt < 350
                val closedAnOpenPanel = panelCoordinator.closeOutside(event.rawX.toInt(), event.rawY.toInt())
                if (!controlsAreVisible && shortTap && !closedAnOpenPanel && event.x > resources.displayMetrics.widthPixels * .88f && event.y < dp(140) &&
                    readerSettings.cornerBookmarkEnabled) {
                    if (::navigator.isInitialized) navigator.clearSelection(); saveCurrentBookmark(); return true
                }
                val documentTop = if (controlsAreVisible) topControls.bottom.toFloat() else 0f
                val documentBottom = if (controlsAreVisible) bottomControls.top.toFloat() else resources.displayMetrics.heightPixels.toFloat()
                val tapIsInsideDocument = event.y in documentTop..documentBottom
                if (shortTap && !closedAnOpenPanel && settingsPanel.visibility != View.VISIBLE && contentsPanel.visibility != View.VISIBLE && searchPanel.visibility != View.VISIBLE && tapIsInsideDocument &&
                    (event.x < resources.displayMetrics.widthPixels * .25f || event.x > resources.displayMetrics.widthPixels * .75f)) {
                    val direction = if (event.x < resources.displayMetrics.widthPixels * .25f) -1 else 1
                    rootLayout.postDelayed({
                        if (android.os.SystemClock.uptimeMillis() - decorationController.lastDecorationActivationAt > 300) {
                            if (::navigator.isInitialized) navigator.clearSelection()
                            navigateOnePage(direction)
                        }
                    }, 90)
                }
                if (shortTap && settingsPanel.visibility != View.VISIBLE && contentsPanel.visibility != View.VISIBLE && searchPanel.visibility != View.VISIBLE &&
                    event.x in resources.displayMetrics.widthPixels * .25f..resources.displayMetrics.widthPixels * .75f &&
                    event.y in resources.displayMetrics.heightPixels * .25f..resources.displayMetrics.heightPixels * .75f) {
                    if (closedAnOpenPanel) return true
                    rootLayout.postDelayed({
                        if (android.os.SystemClock.uptimeMillis() - decorationController.lastDecorationActivationAt > 300) toggleControls()
                    }, 90)
                } else if (closedAnOpenPanel) return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun configureReaderScreenTimeout() {
        readerWindow.resetScreenTimeout(readerSettings.screenTimeoutMinutes)
    }

    private fun applyMenuColors(themeColors: Pair<Int, Int>) {
        val themeIndex = ReadingThemePalette.names.indices.firstOrNull { ReadingThemePalette.colors(it) == themeColors } ?: 0
        val palette = AppThemePalette.applyReaderMenus(
            this,
            ReadingThemePalette.names[themeIndex],
            listOf(topControls, bottomControls, compactProgressSlider, settingsPanel, contentsPanel, searchPanel)
        )
        readerWindow.updateSystemBarContrast(palette.surface)
    }

    override fun onPause() {
        if (::spenRemoteController.isInitialized) spenRemoteController.disconnect()
        readerWindow.stopScreenTimeout()
        super.onPause()
    }

    override fun onStop() {
        // onStop is also invoked when the user minimizes the app. Persisting and
        // enqueueing here lets WorkManager finish the book-only synchronization
        // even if Android subsequently destroys the reader process.
        sessionController?.onStop(currentLocatorOrNull(), isChangingConfigurations)
        super.onStop()
    }

    override fun finish() {
        sessionController?.onFinish(currentLocatorOrNull())
        super.finish()
    }

    private fun currentLocatorOrNull(): Locator? = if (::navigator.isInitialized) navigator.currentLocator.value else null

    private fun launchReaderMenu(intent: Intent) {
        sessionController?.markReaderMenuOpened()
        startActivity(intent)
    }

    private fun copyText(text: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Texto", text))
    }
    private fun searchText(text: String) = runCatching { startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text)) }.getOrNull()
    private fun processText(text: String) = runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_PROCESS_TEXT, text); putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }, "Traducir con…")) }.getOrNull()
    private fun shareText(text: String) = startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
    }, "Compartir con…"))

    private fun toggleControls() {
        controlsAreVisible = !controlsAreVisible
        topControls.visibility = if (controlsAreVisible) View.VISIBLE else View.INVISIBLE
        bottomControls.visibility = if (controlsAreVisible) View.VISIBLE else View.INVISIBLE
        compactProgressSlider.visibility = if (controlsAreVisible) View.GONE else View.VISIBLE
        if (controlsAreVisible) updatePageJumpActions()
        readerWindow.setSystemBarsVisible(controlsAreVisible)
    }

    private fun toggleSettingsPanel() {
        val willOpen = panelCoordinator.toggle(ReaderPanel.SETTINGS)
        if (willOpen) {
            val selectedTheme = readerSettings.theme
            applyMenuColors(ReadingThemePalette.colors(selectedTheme))
        }
    }
    private fun toggleContentsPanel() {
        panelCoordinator.toggle(ReaderPanel.CONTENTS)
    }

    private fun showError(message: String) { Toast.makeText(this, "No se pudo abrir el EPUB: $message", Toast.LENGTH_LONG).show(); finish() }
    private fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    companion object {
        private const val NAVIGATOR_TAG = "readium_epub_navigator"
        private const val ACTION_ADD_QUOTE = 0x4220
        private const val ACTION_ADD_DICTIONARY = 0x4221
        private const val ACTION_COPY = 0x4222
        private const val ACTION_SEARCH = 0x4223
        private const val ACTION_TRANSLATE = 0x4224
        private const val ACTION_SHARE = 0x4225
        private const val MENU_SURFACE_TAG = "reader_menu_surface"
        private const val MENU_CARD_TAG = "reader_menu_card"
    }
}
