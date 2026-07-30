package com.michis.reader.settings

import com.michis.reader.R
import com.michis.reader.data.*
import com.michis.reader.sync.AutomaticDriveSyncScheduler
import com.michis.reader.sync.drive.*
import com.michis.reader.theme.AppThemePalette
import com.michis.reader.ui.LimitedHeightSpinner

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import androidx.activity.ComponentActivity

class ResetBooksActivity : ComponentActivity() {
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
        setContentView(buildScreen()); AppThemePalette.apply(this)
        renderDocuments()
    }

    private fun buildScreen(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(12)); AppThemePalette.markBackground(this)
        setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(dp(14) + bars.left, dp(10) + bars.top, dp(14) + bars.right, dp(12) + bars.bottom)
            }; insets
        }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Button(context).apply { text = "‹"; contentDescription = "Volver"; setOnClickListener { finish() } })
            addView(TextView(context).apply { text = "Reiniciar libros"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, dp(58), 1f))
        })
        addView(TextView(context).apply {
            text = "El libro se conservará, pero se borrarán su progreso, citas, notas, marcadores y diccionario."
            setTextColor(Color.DKGRAY); setPadding(dp(3), 0, dp(3), dp(12))
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundResource(R.drawable.rounded_panel)
            queryInput = EditText(context).apply {
                hint = "Buscar por título, autor o archivo EPUB"; isSingleLine = true
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderDocuments()
                    override fun afterTextChanged(s: Editable?) = Unit
                })
            }
            addView(queryInput)
            addView(TextView(context).apply { text = "Ordenar por"; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(10), 0, dp(3)) })
            addView(LimitedHeightSpinner(context).apply {
                adapter = themedSortAdapter(arrayOf("Título", "Autor", "Formato", "Abiertos recientemente"))
                background = AppThemePalette.cardBackground(this@ResetBooksActivity, 12f)
                setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(AppThemePalette.current(this@ResetBooksActivity).surface))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { sortMode = position; renderDocuments() }
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            })
        })
        selectionStatus = TextView(context).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(dp(3), dp(12), dp(3), dp(8)) }
        addView(selectionStatus)
        addView(Button(context).apply {
            text = "Seleccionar todos los libros"; isAllCaps = false
            setOnClickListener { selectedIdentifiers += allDocuments.map { it.identifier }; renderDocuments() }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Button(context).apply {
                text = "Seleccionar visibles"; isAllCaps = false
                setOnClickListener { selectedIdentifiers += visibleDocuments.map { it.identifier }; renderDocuments() }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(Button(context).apply {
                text = "Limpiar"; isAllCaps = false
                setOnClickListener { selectedIdentifiers.clear(); renderDocuments() }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        })
        listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(ScrollView(context).apply { addView(listContainer) }, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(Button(context).apply {
            text = "Reiniciar seleccionados"; isAllCaps = false; textSize = 16f
            setOnClickListener { confirmReset() }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
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
            listContainer.addView(CheckBox(this).apply {
                text = buildString {
                    append(document.title)
                    append("\n${document.format}")
                    if (document.author.isNotBlank()) append(" · ${document.author}")
                }
                textSize = 16f; isChecked = document.identifier in selectedIdentifiers
                setPadding(dp(14), dp(9), dp(14), dp(9)); setBackgroundResource(R.drawable.rounded_panel)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedIdentifiers += document.identifier else selectedIdentifiers -= document.identifier
                    selectionStatus.text = "${selectedIdentifiers.size} seleccionados · ${visibleDocuments.size} resultados"
                }
            }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(7) })
        }
        if (visibleDocuments.isEmpty()) listContainer.addView(TextView(this).apply { text = "No se encontraron libros"; gravity = Gravity.CENTER; setPadding(dp(12), dp(40), dp(12), dp(40)) })
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
