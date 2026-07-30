package com.michis.reader.sync.drive

import com.michis.reader.sync.AutomaticDriveSyncScheduler
import com.michis.reader.theme.AppThemePalette

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
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
        setContentView(buildScreen()); AppThemePalette.apply(this)
        loadSources()
    }

    private fun buildScreen(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(14))
        AppThemePalette.markBackground(this)
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(14), bars.top + dp(10), dp(14), bars.bottom + dp(10))
            insets
        }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Button(context).apply { text = "‹"; contentDescription = "Volver"; setOnClickListener { finish() } })
            addView(TextView(context).apply {
                text = "Biblioteca de Google Drive"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, dp(58), 1f))
        })
        addView(TextView(context).apply {
            text = "Selecciona carpetas completas, libros EPUB individuales o una combinación de ambos."
            textSize = 15f; setTextColor(Color.DKGRAY); setPadding(dp(4), 0, dp(4), dp(12))
        })
        locationText = TextView(context).apply {
            text = "Mi unidad"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(92, 73, 122)); setPadding(dp(4), 0, dp(4), dp(10))
            setOnClickListener { navigateBack() }
        }
        addView(locationText)
        val search = EditText(context).apply {
            hint = "Buscar carpetas o libros EPUB"; isSingleLine = true
            background = AppThemePalette.cardBackground(this@DriveLibraryPickerActivity, 14f)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) })
        resultCount = TextView(context).apply { textSize = 13f; setTextColor(Color.DKGRAY); setPadding(dp(4), 0, 0, dp(8)) }
        addView(resultCount)
        listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(ScrollView(context).apply { addView(listContainer) }, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(Button(context).apply {
            text = "Aplicar selección"; isAllCaps = false; textSize = 16f
            setOnClickListener {
                repository.saveSelectedSources(accountIdentifier, selectedSources.values)
                AutomaticDriveSyncScheduler(this@DriveLibraryPickerActivity).enqueueImmediateSync()
                setResult(RESULT_OK); Toast.makeText(context, "Biblioteca de Drive actualizada", Toast.LENGTH_SHORT).show(); finish()
            }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(10) })
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderSources(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun loadSources() {
        listContainer.addView(ProgressBar(this).apply { isIndeterminate = true })
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
            listContainer.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; background = AppThemePalette.cardBackground(this@DriveLibraryPickerActivity, 13f)
                AppThemePalette.markCard(this)
                val selector = CheckBox(context).apply {
                    isChecked = source.identifier in selectedIdentifiers
                    buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(92, 73, 122))
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            selectedIdentifiers += source.identifier; selectedSources[source.identifier] = source
                        } else {
                            selectedIdentifiers -= source.identifier; selectedSources.remove(source.identifier)
                        }
                        resultCount.text = "${selectedIdentifiers.size} seleccionados · ${visible.size} elementos"
                    }
                }
                addView(selector, LinearLayout.LayoutParams(dp(48), -1))
                addView(TextView(context).apply {
                    text = "${if (source.isFolder) "📁" else "📄"}  ${source.name}"
                    textSize = 15f; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), 0, dp(8), 0)
                    setOnClickListener { if (source.isFolder) enterFolder(source) else selector.toggle() }
                }, LinearLayout.LayoutParams(0, -1, 1f))
                if (source.isFolder) addView(TextView(context).apply {
                    text = "›"; textSize = 30f; gravity = Gravity.CENTER
                    setTextColor(Color.rgb(92, 73, 122)); setOnClickListener { enterFolder(source) }
                }, LinearLayout.LayoutParams(dp(48), -1))
            }, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(7) })
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

    private fun message(value: String) = TextView(this).apply { text = value; gravity = Gravity.CENTER; setPadding(dp(16), dp(30), dp(16), dp(30)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ACCOUNT_IDENTIFIER = "account_identifier"
        const val EXTRA_ACCESS_TOKEN = "access_token"
    }
}
