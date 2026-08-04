@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import com.michis.reader.R
import com.michis.reader.annotations.*
import com.michis.reader.app.ReaderResumeState
import com.michis.reader.data.*
import com.michis.reader.databinding.ActivityReadiumEpubBinding
import com.michis.reader.databinding.ViewEpubBottomControlsBinding
import com.michis.reader.databinding.ViewEpubTopControlsBinding
import com.michis.reader.databinding.ViewEpubSearchPanelBinding
import com.michis.reader.dictionary.DictionaryActivity
import com.michis.reader.settings.*
import com.michis.reader.spen.*
import com.michis.reader.sync.AutomaticDriveSyncScheduler
import com.michis.reader.theme.*

import android.app.AlertDialog
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import org.readium.r2.navigator.preferences.*
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class ReadiumEpubActivity : FragmentActivity() {
    private lateinit var screenBinding: ActivityReadiumEpubBinding
    private val readerSettings by lazy { ReaderSettingsRepository.get(this) }
    private lateinit var database: ReaderDatabase
    private lateinit var document: LibraryDocument
    private lateinit var rootLayout: FrameLayout
    private lateinit var topControls: LinearLayout
    private lateinit var bottomControls: LinearLayout
    private lateinit var settingsPanel: View
    private lateinit var contentsPanel: View
    private lateinit var searchPanel: View
    private lateinit var searchPanelBinding: ViewEpubSearchPanelBinding
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
    private var currentPreferences = EpubPreferences(publisherStyles = false)
    private var controlsAreVisible = true
    private var userIsDraggingProgress = false
    private var touchStartedX = 0f
    private var touchStartedY = 0f
    private var touchStartedAt = 0L
    private var activeQuickMode = 0
    private lateinit var decorationController: EpubDecorationController
    private var quickModeGestureIsActive = false
    private var lastBookmarkActionAt = 0L
    private val pageJumpHistory = PageJumpHistory()
    private var sliderJumpOriginPage: Int? = null
    private var pendingContentsJumpOriginPage: Int? = null
    private var pendingContentsJumpToken = 0
    private var pendingSearchJumpOriginPage: Int? = null
    private var pendingSearchJumpToken = 0
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private lateinit var spenRemoteController: SpenRemoteController
    private var pagePositions = emptyList<org.readium.r2.shared.publication.Locator>()
    private var publication: Publication? = null
    private var searchResults = emptyList<Locator>()
    private var currentSearchResultIndex = -1
    private var searchRequestToken = 0

    private val quotesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val requestedLocation = result.data?.getIntExtra(BookQuotesActivity.EXTRA_QUOTE_LOCATION, -1) ?: -1
        navigateToExternalLocation(requestedLocation)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        configureEdgeToEdgeWindow()
        database = ReaderDatabase.getInstance(this)
        spenRemoteController = SpenRemoteController(this, ::executeSpenGesture)
        document = database.findDocument(intent.getLongExtra("document_identifier", -1)) ?: run { finish(); return }
        decorationController = EpubDecorationController(
            database,
            document.identifier,
            readerSettings,
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
        applyInitialVisualTheme()
        configureReaderScreenTimeout()
        openWithReadium()
    }

    private fun buildScreen(): View {
        screenBinding = ActivityReadiumEpubBinding.inflate(layoutInflater)
        rootLayout = screenBinding.rootLayout
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
        applySafeSystemBarPadding(rootLayout)
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
        val options = arrayOf("Buscar", "Índice", "Citas", if (database.effectiveDictionaryEntries(document.identifier).isEmpty()) "Crear diccionario" else "Diccionario", "Agregar o quitar marcador")
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
        submitPreferences = ::submit,
        selectTheme = ::applyReadingTheme,
        selectFont = ::showFontSelectionDialog,
        closePanel = { settingsPanel.visibility = View.GONE }
    ).create()

    private fun buildSearchPanel(): View {
        searchPanelBinding = ViewEpubSearchPanelBinding.inflate(layoutInflater)
        searchPanelBinding.searchPanel.tag = MENU_CARD_TAG
        searchPanelBinding.searchButton.setOnClickListener { performBookSearch() }
        searchPanelBinding.searchInput.setOnEditorActionListener { _, _, _ ->
            performBookSearch()
            true
        }
        searchPanelBinding.previousResultButton.setOnClickListener { moveBetweenSearchResults(-1) }
        searchPanelBinding.nextResultButton.setOnClickListener { moveBetweenSearchResults(1) }
        searchPanelBinding.closeSearchButton.setOnClickListener { closeSearchPanel() }
        return searchPanelBinding.root
    }


    private fun buildContentsPanel(): View {
        contentsController = EpubContentsPanel(
            activity = this,
            navigateTo = { link ->
                val originPage = currentPageIndex()
                val jumpToken = ++pendingContentsJumpToken
                pendingContentsJumpOriginPage = originPage
                navigator.go(link, animated = pageAnimationsEnabled())
                rootLayout.postDelayed({
                    if (pendingContentsJumpToken == jumpToken && pendingContentsJumpOriginPage == originPage) {
                        pendingContentsJumpOriginPage = null
                    }
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
            publication = opened
            pagePositions = opened.positions()
            val savedLocation = database.readerLocation(document.identifier)
            val initialLocator = pagePositions.firstOrNull { it.locations.position == savedLocation }
                ?: opened.locateProgression(document.progress.toDouble().coerceIn(0.0, 1.0))
            currentPreferences = loadInitialPreferences()
            val factory = EpubNavigatorFactory(opened)
            supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
                initialLocator = initialLocator,
                initialPreferences = currentPreferences,
                paginationListener = object : EpubNavigatorFragment.PaginationListener {
                    override fun onPageLoaded() {
                        applyTopAnchoredContent()
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

    private fun loadInitialPreferences(): EpubPreferences {
        val preferences = readerSettings.preferences
        val size = readerSettings.fontSizeDp / 16.0
        val themeIndex = ReadingThemePalette.names.indexOf(preferences.getString("theme", "Sepia")).coerceAtLeast(0)
        val colors = ReadingThemePalette.colors(themeIndex)
        val alignment = readerSettings.textAlignment
        val twoPages = preferences.getBoolean("two_pages_landscape",
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        val fontFamilies = arrayOf(FontFamily.SANS_SERIF, FontFamily.SERIF, FontFamily.CURSIVE, FontFamily.MONOSPACE,
            FontFamily.OPEN_DYSLEXIC, FontFamily.ACCESSIBLE_DFA, FontFamily.IA_WRITER_DUOSPACE)
        val fontFamily = fontFamilies[preferences.getInt("font_family", 0).coerceIn(fontFamilies.indices)]
        requestedOrientation = when (preferences.getInt("reader_orientation", 0)) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        applyMenuColors(colors)
        return EpubPreferences(
            fontSize = size, publisherStyles = false,
            pageMargins = if (preferences.getBoolean("page_margins", true)) 1.0 else 0.0,
            scroll = preferences.getBoolean("continuous_scroll", false),
            lineHeight = readerSettings.lineHeight.toDouble(),
            fontWeight = preferences.getFloat("font_weight", 1.0f).toDouble(),
            fontFamily = fontFamily,
            textAlign = arrayOf(TextAlign.JUSTIFY, TextAlign.START, TextAlign.CENTER, TextAlign.END)[alignment],
            columnCount = if (twoPages) ColumnCount.TWO else ColumnCount.ONE,
            spread = if (twoPages) Spread.ALWAYS else Spread.NEVER,
            theme = when (themeIndex) { 1 -> Theme.DARK; 2 -> Theme.SEPIA; else -> Theme.LIGHT },
            backgroundColor = ReadiumColor(colors.first), textColor = ReadiumColor(colors.second)
        )
    }

    private fun applyInitialVisualTheme() {
        val selectedTheme = readerSettings.theme
        val colors = ReadingThemePalette.colors(selectedTheme)
        rootLayout.setBackgroundColor(colors.first)
        applyMenuColors(colors)
    }

    private fun submit(changes: EpubPreferences) {
        currentPreferences += changes
        if (::navigator.isInitialized) navigator.submitPreferences(currentPreferences)
    }

    private fun applyTopAnchoredContent() {
        if (!::navigator.isInitialized) {
            rootLayout.postDelayed(::applyTopAnchoredContent, 120)
            return
        }
        lifecycleScope.launch {
            navigator.evaluateJavascript(
                """
                (() => {
                  const elements = [document.documentElement, document.body].filter(Boolean);
                  elements.forEach(element => {
                    element.style.setProperty('justify-content', 'flex-start', 'important');
                    element.style.setProperty('align-content', 'start', 'important');
                  });
                })();
                """.trimIndent()
            )
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
                            openQuoteColorPicker(selectedText, selection.locator.locations.position ?: 0)
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
        if (::document.isInitialized) ReaderResumeState.markReaderActive(this, document.identifier)
        configureReaderScreenTimeout()
        spenRemoteController.connect()
        if (::topControls.isInitialized && ::bottomControls.isInitialized && ::settingsPanel.isInitialized) {
            val selectedTheme = readerSettings.theme
            val themeIndex = ReadingThemePalette.names.indexOf(selectedTheme).coerceAtLeast(0)
            if (::navigator.isInitialized) applyReadingTheme(themeIndex)
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
        ReaderResumeState.markReaderExited(this)
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

    private fun performBookSearch() {
        val query = searchPanelBinding.searchInput.text.toString().trim()
        val opened = publication
        if (query.isBlank() || opened == null) {
            searchPanelBinding.searchResultLabel.text = "Escribe un texto para buscar"
            return
        }
        val initialPageBeforeSearch = currentPageIndex()
        val requestToken = ++searchRequestToken
        searchPanelBinding.searchResultLabel.text = "Buscando…"
        searchPanelBinding.previousResultButton.isEnabled = false
        searchPanelBinding.nextResultButton.isEnabled = false
        lifecycleScope.launch {
            val matches = mutableListOf<Locator>()
            val iterator = opened.search(
                query,
                SearchService.Options(caseSensitive = false, wholeWord = false, exact = true)
            )
            iterator?.forEach { result -> matches += result.locators }
            iterator?.close()
            if (requestToken != searchRequestToken) return@launch
            searchResults = matches
            currentSearchResultIndex = if (matches.isEmpty()) -1 else 0
            decorationController.showSearchResults(matches, currentSearchResultIndex)
            updateSearchControls()
            if (matches.isNotEmpty()) navigateToSearchResult(0, initialPageBeforeSearch)
        }
    }

    private fun moveBetweenSearchResults(direction: Int) {
        if (searchResults.isEmpty()) return
        val target = (currentSearchResultIndex + direction).coerceIn(searchResults.indices)
        if (target == currentSearchResultIndex) return
        currentSearchResultIndex = target
        lifecycleScope.launch { decorationController.showSearchResults(searchResults, target) }
        updateSearchControls()
        navigateToSearchResult(target)
    }

    private fun navigateToSearchResult(index: Int, originPage: Int = currentPageIndex()) {
        val locator = searchResults.getOrNull(index) ?: return
        val jumpToken = ++pendingSearchJumpToken
        pendingSearchJumpOriginPage = originPage
        navigator.go(locator, animated = pageAnimationsEnabled())
        rootLayout.postDelayed({
            if (pendingSearchJumpToken == jumpToken && pendingSearchJumpOriginPage == originPage) {
                pendingSearchJumpOriginPage = null
            }
        }, 2_000L)
    }

    private fun updateSearchControls() {
        val count = searchResults.size
        searchPanelBinding.searchResultLabel.text = if (count == 0) "Sin coincidencias" else
            "Coincidencia ${currentSearchResultIndex + 1} de $count"
        searchPanelBinding.previousResultButton.isEnabled = currentSearchResultIndex > 0
        searchPanelBinding.nextResultButton.isEnabled = currentSearchResultIndex in 0 until count - 1
    }

    private fun toggleSearchPanel() {
        settingsPanel.visibility = View.GONE
        contentsPanel.visibility = View.GONE
        if (searchPanel.visibility == View.VISIBLE) {
            closeSearchPanel()
            return
        }
        searchPanel.visibility = View.VISIBLE
        applyMenuColors(ReadingThemePalette.colors(readerSettings.theme))
        searchPanelBinding.searchInput.requestFocus()
    }

    private fun closeSearchPanel() {
        searchPanel.visibility = View.GONE
        lifecycleScope.launch { decorationController.clearSearchResults() }
    }

    private fun openQuoteColorPicker(selectedText: String, location: Int) {
        launchReaderMenu(Intent(this, QuoteColorActivity::class.java)
            .putExtra(QuoteColorActivity.EXTRA_DOCUMENT_IDENTIFIER, document.identifier)
            .putExtra(QuoteColorActivity.EXTRA_TEXT, selectedText)
            .putExtra(QuoteColorActivity.EXTRA_LOCATION, location)
            .putExtra(QuoteColorActivity.EXTRA_PAGE_NUMBER, progressSlider.progress + 1))
    }

    private fun observeProgress() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collect { locator ->
                    val progression = locator.locations.totalProgression ?: return@collect
                    database.updateProgress(document.identifier, locator.locations.position ?: 0, progression.toFloat())
                    val pageIndex = ((locator.locations.position ?: 1) - 1).coerceIn(0, (pagePositions.size - 1).coerceAtLeast(0))
                    pendingContentsJumpOriginPage?.let { origin ->
                        if (pageIndex != origin) {
                            recordPageJump(origin, pageIndex)
                            pendingContentsJumpOriginPage = null
                        }
                    }
                    pendingSearchJumpOriginPage?.let { origin ->
                        if (pageIndex != origin) {
                            recordPageJump(origin, pageIndex)
                            pendingSearchJumpOriginPage = null
                        }
                    }
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
        pageJumpHistory.recordJump(originPage, destinationPage)
        updatePageJumpActions()
    }

    private fun returnToPreviousJump() {
        val destination = pageJumpHistory.moveBack() ?: return
        navigateToPage(destination, animated = false)
        updatePageJumpActions()
    }

    private fun advanceToNextJump() {
        val destination = pageJumpHistory.moveForward() ?: return
        navigateToPage(destination, animated = false)
        updatePageJumpActions()
    }

    private fun clearPageJumpHistory() {
        pageJumpHistory.clear()
        updatePageJumpActions()
        Toast.makeText(this, "Historial de saltos eliminado", Toast.LENGTH_SHORT).show()
    }

    private fun updatePageJumpActions() {
        if (!::pageJumpActions.isInitialized) return
        val backPage = pageJumpHistory.previousPageIndex
        val forwardPage = pageJumpHistory.nextPageIndex
        jumpBackButton.apply {
            visibility = if (backPage == null) View.INVISIBLE else View.VISIBLE
            if (backPage != null) text = "Regresar a página ${backPage + 1}"
        }
        clearJumpHistoryButton.visibility = if (pageJumpHistory.hasNavigation) View.VISIBLE else View.GONE
        jumpForwardButton.apply {
            visibility = if (forwardPage == null) View.INVISIBLE else View.VISIBLE
            if (forwardPage != null) text = "Avanzar a página ${forwardPage + 1}"
        }
        pageJumpActions.visibility = if (pageJumpHistory.hasNavigation) View.VISIBLE else View.GONE
    }

    private fun navigateOnePage(direction: Int) {
        if (direction < 0) navigator.goBackward(animated = pageAnimationsEnabled())
        else navigator.goForward(animated = pageAnimationsEnabled())
    }

    private fun pageAnimationsEnabled(): Boolean =
        readerSettings.preferences.getBoolean("page_turn_animations", true)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!::navigator.isInitialized) return super.onKeyDown(keyCode, event)
        val gesture = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> SpenControlPreferences.gestures[0]
            KeyEvent.KEYCODE_B -> SpenControlPreferences.gestures[1]
            KeyEvent.KEYCODE_PAGE_DOWN -> SpenControlPreferences.gestures[2]
            KeyEvent.KEYCODE_PAGE_UP -> SpenControlPreferences.gestures[3]
            KeyEvent.KEYCODE_DPAD_UP -> SpenControlPreferences.gestures[4]
            KeyEvent.KEYCODE_DPAD_DOWN -> SpenControlPreferences.gestures[5]
            KeyEvent.KEYCODE_PLUS -> SpenControlPreferences.gestures[6]
            KeyEvent.KEYCODE_MINUS -> SpenControlPreferences.gestures[7]
            else -> return super.onKeyDown(keyCode, event)
        }
        if (spenRemoteController.isConnected) return true
        executeSpenGesture(gesture)
        return true
    }

    private fun executeSpenGesture(gesture: SpenControlPreferences.Gesture) {
        if (!::navigator.isInitialized) return
        val currentPage = ((navigator.currentLocator.value.locations.position ?: 1) - 1)
            .coerceIn(0, (pagePositions.size - 1).coerceAtLeast(0))
        val action = readerSettings.preferences.getString(gesture.preferenceKey, gesture.defaultAction)
        when (action) {
            SpenControlPreferences.NONE -> Unit
            SpenControlPreferences.NEXT -> navigateOnePage(1)
            SpenControlPreferences.PREVIOUS -> navigateOnePage(-1)
            SpenControlPreferences.TOGGLE_CONTROLS -> toggleControls()
            SpenControlPreferences.BOOKMARK -> saveCurrentBookmark()
            SpenControlPreferences.LARGER_TEXT -> changeFontSizeFromSpen(1f)
            SpenControlPreferences.SMALLER_TEXT -> changeFontSizeFromSpen(-1f)
            SpenControlPreferences.QUICK_THEME -> {
                activeQuickMode = 1 - activeQuickMode; applyQuickMode(activeQuickMode)
            }
            SpenControlPreferences.ADD_DICTIONARY -> useSelectedTextFromSpen(addToDictionary = true)
            SpenControlPreferences.ADD_QUOTE -> useSelectedTextFromSpen(addToDictionary = false)
        }
        configureReaderScreenTimeout()
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
            else openQuoteColorPicker(selectedText, selection?.locator?.locations?.position ?: 0)
        }
    }

    private fun changeFontSizeFromSpen(change: Float) {
        val preferences = readerSettings.preferences
        val size = (readerSettings.fontSizeDp + change).coerceIn(
            ReaderSettingsRepository.MINIMUM_FONT_SIZE_DP,
            ReaderSettingsRepository.MAXIMUM_FONT_SIZE_DP
        )
        readerSettings.fontSizeDp = size
        submit(EpubPreferences(fontSize = size / 16.0))
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

    private fun showFontSelectionDialog() {
        val names = arrayOf("Sans Serif", "Serif", "Cursiva", "Monoespaciada", "OpenDyslexic", "Accessible DfA", "iA Writer Duospace")
        val fonts = arrayOf(FontFamily.SANS_SERIF, FontFamily.SERIF, FontFamily.CURSIVE, FontFamily.MONOSPACE,
            FontFamily.OPEN_DYSLEXIC, FontFamily.ACCESSIBLE_DFA, FontFamily.IA_WRITER_DUOSPACE)
        AlertDialog.Builder(this).setTitle("Tipo de fuente").setItems(names) { _, index ->
            readerSettings.preferences.edit().putInt("font_family", index).apply()
            submit(EpubPreferences(fontFamily = fonts[index]))
        }.show()
    }

    private fun applyQuickMode(index: Int) {
        val preferences = readerSettings.preferences
        val key = if (index == 0) "quick_mode_1" else "quick_mode_2"
        val themeName = preferences.getString(key, if (index == 0) "Día" else "Noche") ?: "Día"
        applyReadingTheme(ReadingThemePalette.names.indexOf(themeName).coerceAtLeast(0))
        navigator.clearSelection()
        Toast.makeText(this, themeName, Toast.LENGTH_SHORT).show()
    }

    private fun applyReadingTheme(index: Int) {
        val colors = ReadingThemePalette.colors(index)
        val baseTheme = when (index) { 1 -> Theme.DARK; 2 -> Theme.SEPIA; else -> Theme.LIGHT }
        submit(EpubPreferences(
            theme = baseTheme,
            backgroundColor = ReadiumColor(colors.first),
            textColor = ReadiumColor(colors.second)
        ))
        readerSettings.theme = ReadingThemePalette.names[index]
        rootLayout.setBackgroundColor(colors.first)
        applyMenuColors(colors)
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
                    activeQuickMode = 1 - activeQuickMode; applyQuickMode(activeQuickMode); return true
                }
                val movement = kotlin.math.hypot(event.x - touchStartedX, event.y - touchStartedY)
                val shortTap = movement < dp(12) && event.eventTime - touchStartedAt < 350
                val settingsWasClosed = closePanelIfTouchIsOutside(settingsPanel, event)
                val contentsWasClosed = closePanelIfTouchIsOutside(contentsPanel, event)
                val searchWasClosed = closePanelIfTouchIsOutside(searchPanel, event)
                if (searchWasClosed) lifecycleScope.launch { decorationController.clearSearchResults() }
                val closedAnOpenPanel = settingsWasClosed || contentsWasClosed || searchWasClosed
                if (!controlsAreVisible && shortTap && !closedAnOpenPanel && event.x > resources.displayMetrics.widthPixels * .88f && event.y < dp(140) &&
                    readerSettings.preferences.getBoolean("corner_bookmark_enabled", true)) {
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
        inactivityHandler.removeCallbacksAndMessages(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val minutes = readerSettings.screenTimeoutMinutes
        inactivityHandler.postDelayed({ window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }, minutes * 60_000L)
    }

    private fun applyMenuColors(themeColors: Pair<Int, Int>) {
        val themeIndex = ReadingThemePalette.names.indices.firstOrNull { ReadingThemePalette.colors(it) == themeColors } ?: 0
        val palette = AppThemePalette.forReader(this, ReadingThemePalette.names[themeIndex])
        val background = palette.surface
        val foreground = palette.primaryText
        val cardColor = palette.card
        fun roundedControl(view: View, color: Int, radiusDp: Int, outline: Int? = null) {
            val savedPadding = intArrayOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
            view.backgroundTintList = null
            view.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(radiusDp).toFloat()
                outline?.let { setStroke(dp(1), it) }
            }
            view.setPadding(savedPadding[0], savedPadding[1], savedPadding[2], savedPadding[3])
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { parameters ->
                val horizontalMargin = dp(4)
                val verticalMargin = dp(5)
                parameters.marginStart = maxOf(parameters.marginStart, horizontalMargin)
                parameters.marginEnd = maxOf(parameters.marginEnd, horizontalMargin)
                parameters.topMargin = maxOf(parameters.topMargin, verticalMargin)
                parameters.bottomMargin = maxOf(parameters.bottomMargin, verticalMargin)
                view.layoutParams = parameters
            }
        }
        fun recolor(view: View, inheritedText: Int = foreground) {
            val textColor = if (view.tag == MENU_CARD_TAG) AppThemePalette.textFor(cardColor) else inheritedText
            if (view.tag == MENU_SURFACE_TAG) view.setBackgroundColor(background)
            if (view.tag == MENU_CARD_TAG) {
                val savedPadding = intArrayOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
                view.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(cardColor); cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), androidx.core.graphics.ColorUtils.setAlphaComponent(AppThemePalette.textFor(cardColor), 42))
                }
                view.setPadding(savedPadding[0], savedPadding[1], savedPadding[2], savedPadding[3])
            }
            when (view) {
                is CompoundButton -> {
                    view.setTextColor(textColor)
                    view.buttonTintList = android.content.res.ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(palette.accent, androidx.core.graphics.ColorUtils.blendARGB(textColor, cardColor, .55f))
                    )
                }
                is Button -> {
                    roundedControl(view, palette.accent, radiusDp = 24)
                    view.minimumHeight = dp(48)
                    view.setTextColor(palette.onAccent)
                }
                is SeekBar -> {
                    view.progressTintList = android.content.res.ColorStateList.valueOf(palette.accent)
                    view.thumbTintList = android.content.res.ColorStateList.valueOf(palette.accent)
                    view.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(palette.outline)
                }
                is EditText -> {
                    roundedControl(view, background, radiusDp = 16, outline = palette.outline)
                    view.setTextColor(AppThemePalette.textFor(background))
                    view.setHintTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(AppThemePalette.textFor(background), 145))
                }
                is Spinner -> roundedControl(view, background, radiusDp = 16, outline = palette.outline)
                is TextView -> view.setTextColor(textColor)
            }
            if (view is ViewGroup) repeat(view.childCount) { recolor(view.getChildAt(it), textColor) }
        }
        topControls.setBackgroundColor(background)
        bottomControls.setBackgroundColor(background)
        settingsPanel.setBackgroundColor(background)
        recolor(topControls); recolor(bottomControls); recolor(compactProgressSlider); recolor(settingsPanel); recolor(contentsPanel); recolor(searchPanel)
        updateReaderSystemBarContrast(background)
    }

    private fun updateReaderSystemBarContrast(background: Int) {
        val useDarkIcons = androidx.core.graphics.ColorUtils.calculateLuminance(background) >= .45
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (useDarkIcons) flags else 0, flags)
        } else {
            @Suppress("DEPRECATION")
            val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (useDarkIcons) window.decorView.systemUiVisibility or flags
            else window.decorView.systemUiVisibility and flags.inv()
        }
    }

    override fun onPause() {
        spenRemoteController.disconnect()
        inactivityHandler.removeCallbacksAndMessages(null); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onStop() {
        // onStop is also invoked when the user minimizes the app. Persisting and
        // enqueueing here lets WorkManager finish the book-only synchronization
        // even if Android subsequently destroys the reader process.
        if (!isChangingConfigurations) persistProgressAndScheduleBookSync()
        super.onStop()
    }

    override fun finish() {
        persistProgressAndScheduleBookSync()
        ReaderResumeState.markReaderExited(this)
        super.finish()
    }

    private fun persistProgressAndScheduleBookSync() {
        if (!::navigator.isInitialized || !::document.isInitialized || !::database.isInitialized) return
        val locator = navigator.currentLocator.value
        locator.locations.totalProgression?.let { progression ->
            database.updateProgress(
                document.identifier,
                locator.locations.position ?: 0,
                progression.toFloat()
            )
        }
        AutomaticDriveSyncScheduler(this).enqueueBookSync(document.identifier)
    }

    private fun launchReaderMenu(intent: Intent) {
        ReaderResumeState.markReaderExited(this)
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
        setSystemBarsVisible(controlsAreVisible)
    }

    private fun toggleSettingsPanel() {
        contentsPanel.visibility = View.GONE
        if (searchPanel.visibility == View.VISIBLE) closeSearchPanel()
        val willOpen = settingsPanel.visibility != View.VISIBLE
        settingsPanel.visibility = if (willOpen) View.VISIBLE else View.GONE
        if (willOpen) {
            val selectedTheme = readerSettings.theme
            applyMenuColors(ReadingThemePalette.colors(selectedTheme))
        }
    }
    private fun toggleContentsPanel() {
        settingsPanel.visibility = View.GONE
        if (searchPanel.visibility == View.VISIBLE) closeSearchPanel()
        contentsPanel.visibility = if (contentsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun closePanelIfTouchIsOutside(panel: View, event: MotionEvent): Boolean {
        if (panel.visibility != View.VISIBLE) return false
        val bounds = Rect()
        panel.getGlobalVisibleRect(bounds)
        if (bounds.contains(event.rawX.toInt(), event.rawY.toInt())) return false
        panel.visibility = View.GONE
        return true
    }


    @Suppress("DEPRECATION")
    private fun configureEdgeToEdgeWindow() {
        window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(false)
    }
    private fun setSystemBarsVisible(visible: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) window.insetsController?.let {
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (visible) it.show(WindowInsets.Type.systemBars()) else it.hide(WindowInsets.Type.systemBars())
        }
    }
    private fun applySafeSystemBarPadding(view: View) {
        val originalTopPadding = topControls.paddingTop
        val originalBottomPadding = bottomControls.paddingBottom
        view.setOnApplyWindowInsetsListener { target, insets ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                topControls.setPadding(topControls.paddingLeft, originalTopPadding + bars.top, topControls.paddingRight, topControls.paddingBottom)
                bottomControls.setPadding(bottomControls.paddingLeft, bottomControls.paddingTop, bottomControls.paddingRight, originalBottomPadding + bars.bottom)
                settingsPanel.setPadding(settingsPanel.paddingLeft, bars.top, settingsPanel.paddingRight, bars.bottom)
                contentsPanel.setPadding(contentsPanel.paddingLeft, bars.top, contentsPanel.paddingRight, bars.bottom)
            }; insets
        }
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
