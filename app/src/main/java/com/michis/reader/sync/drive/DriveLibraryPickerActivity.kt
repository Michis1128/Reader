package com.michis.reader.sync.drive

import com.michis.reader.databinding.ActivityDriveLibraryPickerBinding
import com.michis.reader.databinding.ItemDriveLibrarySourceBinding
import com.michis.reader.databinding.ViewEmptyStateBinding
import com.michis.reader.databinding.ViewLoadingIndicatorBinding
import com.michis.reader.sync.AutomaticDriveSyncScheduler
import com.michis.reader.theme.AppThemePalette
import com.michis.reader.ui.ScreenHeader

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DriveLibraryPickerActivity : ComponentActivity() {
    private lateinit var binding: ActivityDriveLibraryPickerBinding
    private val repository by lazy { GoogleDriveBookLibraryRepository(this) }
    private lateinit var accountIdentifier: String
    private lateinit var accessToken: String
    private lateinit var listContainer: LinearLayout
    private lateinit var resultCount: TextView
    private lateinit var locationText: TextView
    private val selectedIdentifiers = linkedSetOf<String>()
    private val selectedSources = linkedMapOf<String, DriveLibrarySource>()
    private var allSources = emptyList<DriveLibrarySource>()
    private val navigationStack = mutableListOf(DriveLibrarySource("root", "Mi unidad", true))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navigationStack.size > 1) {
                    navigateBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        accountIdentifier = intent.getStringExtra(EXTRA_ACCOUNT_IDENTIFIER).orEmpty()
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()
        if (accountIdentifier.isBlank() || accessToken.isBlank()) { finish(); return }
        repository.selectedSources(accountIdentifier).forEach {
            selectedIdentifiers += it.identifier; selectedSources[it.identifier] = it
        }
        binding = ActivityDriveLibraryPickerBinding.inflate(layoutInflater)
        configureScreen()
        setContentView(binding.root)
        AppThemePalette.apply(this)
        loadSources()
    }

    private fun configureScreen() {
        AppThemePalette.markBackground(binding.rootContainer)
        ScreenHeader.configure(this, binding.screenHeader, "Biblioteca de Google Drive") { finish() }
        binding.selectionDescription.text =
            "Selecciona carpetas completas, libros EPUB individuales o una combinación de ambos."
        listContainer = binding.listContainer
        resultCount = binding.resultCount
        locationText = binding.locationText
        locationText.setOnClickListener { navigateBack() }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                renderSources(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.applySelectionButton.setOnClickListener {
            repository.saveSelectedSources(accountIdentifier, selectedSources.values)
            AutomaticDriveSyncScheduler(this).enqueueImmediateSync()
            setResult(RESULT_OK)
            Toast.makeText(this, "Biblioteca de Drive actualizada", Toast.LENGTH_SHORT).show()
            finish()
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(14), bars.top + dp(10), dp(14), bars.bottom + dp(10))
            insets
        }
    }

    private fun loadSources() {
        listContainer.removeAllViews()
        listContainer.addView(ViewLoadingIndicatorBinding.inflate(layoutInflater, listContainer, false).root)
        lifecycleScope.launch {
            val parentIdentifier = navigationStack.last().identifier
            val result = runCatching { withContext(Dispatchers.IO) { repository.listChildren(accessToken, parentIdentifier) } }
            result.onSuccess { allSources = it; renderSources("") }
                .onFailure { listContainer.removeAllViews(); listContainer.addView(message("No se pudo cargar Drive: ${it.message.orEmpty()}")) }
        }
    }

    private fun renderSources(query: String) {
        if (!::listContainer.isInitialized) return
        listContainer.removeAllViews()
        val visible = allSources.filter { it.name.contains(query.trim(), ignoreCase = true) }
        resultCount.text = "${selectedIdentifiers.size} seleccionados · ${visible.size} resultados"
        visible.forEach { source ->
            val itemBinding = ItemDriveLibrarySourceBinding.inflate(layoutInflater, listContainer, false)
            itemBinding.sourceCheckbox.apply {
                isChecked = source.identifier in selectedIdentifiers
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedIdentifiers += source.identifier
                        selectedSources[source.identifier] = source
                    } else {
                        selectedIdentifiers -= source.identifier
                        selectedSources.remove(source.identifier)
                    }
                    resultCount.text = "${selectedIdentifiers.size} seleccionados · ${visible.size} elementos"
                }
            }
            itemBinding.sourceIcon.text = if (source.isFolder) "📁" else "📄"
            itemBinding.sourceName.apply {
                text = source.name
                setOnClickListener {
                    if (source.isFolder) enterFolder(source) else itemBinding.sourceCheckbox.toggle()
                }
            }
            itemBinding.navigationArrow.apply {
                visibility = if (source.isFolder) View.VISIBLE else View.GONE
                setOnClickListener { enterFolder(source) }
            }
            AppThemePalette.markCard(itemBinding.root)
            listContainer.addView(itemBinding.root)
        }
        if (visible.isEmpty()) listContainer.addView(message("No hay resultados"))
        AppThemePalette.apply(this)
    }

    private fun enterFolder(folder: DriveLibrarySource) {
        navigationStack += folder
        locationText.text = navigationStack.joinToString("  ›  ") { it.name }
        loadSources()
    }

    private fun navigateBack() {
        if (navigationStack.size <= 1) return
        navigationStack.removeAt(navigationStack.lastIndex)
        locationText.text = navigationStack.joinToString("  ›  ") { it.name }
        loadSources()
    }

    private fun message(value: String) =
        ViewEmptyStateBinding.inflate(layoutInflater, listContainer, false).root.apply { text = value }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ACCOUNT_IDENTIFIER = "account_identifier"
        const val EXTRA_ACCESS_TOKEN = "access_token"
    }
}
