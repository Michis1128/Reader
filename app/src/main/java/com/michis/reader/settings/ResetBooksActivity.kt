package com.michis.reader.settings

import com.michis.reader.R
import com.michis.reader.data.*
import com.michis.reader.databinding.ActivityResetBooksBinding
import com.michis.reader.databinding.ItemResetBookBinding
import com.michis.reader.databinding.ViewEmptyStateBinding
import com.michis.reader.sync.AutomaticDriveSyncScheduler
import com.michis.reader.sync.drive.*
import com.michis.reader.theme.AppThemePalette
import com.michis.reader.ui.ScreenHeader
import com.michis.reader.ui.SystemBarInsets

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity

class ResetBooksActivity : ComponentActivity() {
    private lateinit var binding: ActivityResetBooksBinding
    private lateinit var database: ReaderDatabase
    private lateinit var listContainer: LinearLayout
    private lateinit var selectionStatus: TextView
    private lateinit var queryInput: EditText
    private val selectedIdentifiers = linkedSetOf<Long>()
    private var allDocuments = emptyList<LibraryDocument>()
    private var visibleDocuments = emptyList<LibraryDocument>()
    private var sortMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ReaderDatabase.getInstance(this)
        allDocuments = database.findDocuments()
        binding = ActivityResetBooksBinding.inflate(layoutInflater)
        configureScreen()
        setContentView(binding.root)
        AppThemePalette.apply(this)
        renderDocuments()
    }

    private fun configureScreen() {
        AppThemePalette.markBackground(binding.rootContainer)
        ScreenHeader.configure(this, binding.screenHeader, getString(R.string.reset_books_title)) { finish() }
        binding.resetDescription.text =
            "El libro se conservará, pero se borrarán su progreso, citas, notas, marcadores y diccionario."
        queryInput = binding.queryInput
        selectionStatus = binding.selectionStatus
        listContainer = binding.listContainer
        queryInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderDocuments()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.sortSpinner.apply {
            adapter = themedSortAdapter(arrayOf("Título", "Autor", "Formato", "Abiertos recientemente"))
            background = AppThemePalette.cardBackground(this@ResetBooksActivity, 12f)
            setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(AppThemePalette.current(this@ResetBooksActivity).surface))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    sortMode = position
                    renderDocuments()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        binding.selectAllButton.setOnClickListener {
            selectedIdentifiers += allDocuments.map { it.identifier }
            renderDocuments()
        }
        binding.selectVisibleButton.setOnClickListener {
            selectedIdentifiers += visibleDocuments.map { it.identifier }
            renderDocuments()
        }
        binding.clearSelectionButton.setOnClickListener {
            selectedIdentifiers.clear()
            renderDocuments()
        }
        binding.resetSelectedButton.setOnClickListener { confirmReset() }
        SystemBarInsets.apply(binding.rootContainer)
    }

    private fun renderDocuments() {
        if (!::listContainer.isInitialized || !::queryInput.isInitialized) return
        val query = queryInput.text?.toString().orEmpty().trim()
        visibleDocuments = allDocuments.filter {
            query.isBlank() || listOf(it.title, it.author, it.format, it.fileName).any { value -> value.contains(query, ignoreCase = true) }
        }.let { documents ->
            when (sortMode) {
                1 -> documents.sortedWith(compareBy<LibraryDocument> { it.author.ifBlank { "￿" }.lowercase() }.thenBy { it.title.lowercase() })
                2 -> documents.sortedWith(compareBy<LibraryDocument> { it.format.lowercase() }.thenBy { it.title.lowercase() })
                3 -> documents.sortedByDescending { it.lastOpenedAt }
                else -> documents.sortedBy { it.title.lowercase() }
            }
        }
        listContainer.removeAllViews()
        selectionStatus.text = "${selectedIdentifiers.size} seleccionados · ${visibleDocuments.size} resultados"
        visibleDocuments.forEach { document ->
            val itemBinding = ItemResetBookBinding.inflate(layoutInflater, listContainer, false)
            itemBinding.bookCheckbox.apply {
                text = buildString {
                    append(document.title)
                    append("\n${document.format}")
                    if (document.author.isNotBlank()) append(" · ${document.author}")
                }
                isChecked = document.identifier in selectedIdentifiers
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedIdentifiers += document.identifier else selectedIdentifiers -= document.identifier
                    selectionStatus.text = "${selectedIdentifiers.size} seleccionados · ${visibleDocuments.size} resultados"
                }
            }
            AppThemePalette.markCard(itemBinding.bookCheckbox)
            listContainer.addView(itemBinding.root)
        }
        if (visibleDocuments.isEmpty()) {
            val emptyBinding = ViewEmptyStateBinding.inflate(layoutInflater, listContainer, false)
            emptyBinding.root.text = "No se encontraron libros"
            listContainer.addView(emptyBinding.root)
        }
        AppThemePalette.apply(this)
    }

    private fun confirmReset() {
        val selected = allDocuments.filter { it.identifier in selectedIdentifiers }
        if (selected.isEmpty()) { Toast.makeText(this, "Selecciona al menos un libro", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Reiniciar ${selected.size} libros")
            .setMessage("Esta operación borrará los datos de lectura asociados y se sincronizará con Drive. Los archivos de los libros no se eliminarán.")
            .setPositiveButton("Reiniciar") { _, _ ->
                var completed = 0
                selected.forEach { if (runCatching { database.resetBook(it.identifier) }.isSuccess) completed++ }
                enqueueDriveSyncIfAvailable()
                Toast.makeText(this, "$completed libros reiniciados", Toast.LENGTH_LONG).show()
                finish()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun themedSortAdapter(options: Array<String>) = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options) {
        override fun getView(position: Int, recycledView: View?, parent: ViewGroup): View =
            themedOption(super.getView(position, recycledView, parent), AppThemePalette.current(this@ResetBooksActivity).card)

        override fun getDropDownView(position: Int, recycledView: View?, parent: ViewGroup): View =
            themedOption(super.getDropDownView(position, recycledView, parent), AppThemePalette.current(this@ResetBooksActivity).surface)

        private fun themedOption(view: View, backgroundColor: Int): View = view.apply {
            setBackgroundColor(backgroundColor)
            (this as? TextView)?.apply {
                setTextColor(AppThemePalette.textFor(backgroundColor))
                setPadding(dp(16), dp(12), dp(16), dp(12))
                textSize = 16f
            }
        }
    }

    private fun enqueueDriveSyncIfAvailable() {
        val session = OptionalGoogleAccountManager(this).currentSession() ?: return
        if (!GoogleDriveAuthorizationManager(this).isAuthorized()) return
        if (GoogleDriveFolderRepository(this).savedFolder(session.accountIdentifier) == null) return
        AutomaticDriveSyncScheduler(this).enqueueImmediateSync()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
