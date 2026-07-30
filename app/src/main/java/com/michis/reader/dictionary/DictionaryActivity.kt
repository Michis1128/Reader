package com.michis.reader.dictionary

import com.michis.reader.R
import com.michis.reader.data.*
import com.michis.reader.settings.ReaderSettingsRepository
import com.michis.reader.theme.*

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class DictionaryActivity : ComponentActivity() {
    private lateinit var database: ReaderDatabase
    private lateinit var document: LibraryDocument
    private lateinit var titleView: TextView
    private lateinit var content: LinearLayout
    private var selectedCategory: DictionaryCategory? = null
    private val pendingTerm by lazy { intent.getStringExtra(EXTRA_SELECTED_TEXT).orEmpty().trim() }
    private val pendingContext by lazy { intent.getStringExtra(EXTRA_SELECTED_CONTEXT).orEmpty().trim() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })
        database = ReaderDatabase.getInstance(this)
        document = database.findDocument(intent.getLongExtra(EXTRA_DOCUMENT_IDENTIFIER, -1)) ?: run { finish(); return }
        setContentView(buildScreen()); AppThemePalette.apply(this)
        val requestedEntry = database.findDictionaryEntry(intent.getLongExtra(EXTRA_ENTRY_IDENTIFIER, -1))
            ?.takeIf { it.documentIdentifier in database.effectiveDictionaryOwnerIdentifiers(document.identifier) }
        val requestedCategory = requestedEntry?.let { entry ->
            database.dictionaryCategories(entry.documentIdentifier).firstOrNull { it.identifier == entry.categoryIdentifier }
        }
        if (requestedEntry != null && requestedCategory != null) showEntryEditor(requestedCategory, requestedEntry) else showCategories()
    }

    private fun buildScreen(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(16)); AppThemePalette.markBackground(this)
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Button(context).apply { text = "‹"; setOnClickListener { navigateBack() } })
            titleView = TextView(context).apply { textSize = 24f; typeface = android.graphics.Typeface.DEFAULT_BOLD; maxLines = 2 }
            addView(titleView, LinearLayout.LayoutParams(0, dp(64), 1f))
        })
        val scroll = ScrollView(context)
        content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content); addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        applyInsets(this)
    }

    private fun showCategories() {
        selectedCategory = null; content.removeAllViews(); titleView.text = "Diccionario · ${document.title}"
        if (pendingTerm.isNotBlank()) content.addView(TextView(this).apply {
            text = "Selecciona una subcategoría para “$pendingTerm” y decide si deseas escribir su descripción ahora o después."
            textSize = 16f; setPadding(dp(12), dp(14), dp(12), dp(18)); setBackgroundResource(R.drawable.rounded_panel)
        })
        content.addView(sectionTitle("Subcategorías"))
        content.addView(Button(this).apply {
            text = "Compartir este diccionario con otros libros"; isAllCaps = false
            setOnClickListener { showSharingOptions() }
        })
        val categories = database.dictionaryCategories(document.identifier)
        categories.forEach { category -> content.addView(card(category.name) { if (pendingTerm.isBlank()) showEntries(category) else showEntryEditor(category, null) }) }
        if (categories.isEmpty()) content.addView(message("Este libro todavía no tiene subcategorías. Crea la primera, por ejemplo: Personajes, Palabras o Lugares."))
        val categoryName = EditText(this).apply { hint = "Nombre de la nueva subcategoría"; setSingleLine(); setBackgroundResource(R.drawable.rounded_panel) }
        content.addView(categoryName, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(16) })
        content.addView(Button(this).apply {
            text = "Crear subcategoría"; isAllCaps = false
            setOnClickListener {
                val identifier = database.createDictionaryCategory(document.identifier, categoryName.text.toString())
                if (identifier < 0) Toast.makeText(context, "Escribe un nombre", Toast.LENGTH_SHORT).show()
                else {
                    val created = database.dictionaryCategories(document.identifier).first { it.identifier == identifier }
                    if (pendingTerm.isBlank()) showEntries(created) else showEntryEditor(created, null)
                }
            }
        })
    }

    private fun showEntries(category: DictionaryCategory) {
        selectedCategory = category; content.removeAllViews(); titleView.text = category.name
        val entries = database.dictionaryEntries(category.identifier)
        entries.forEach { entry -> content.addView(card(entry.term, entry.description.ifBlank { "Sin descripción" }) { showEntryEditor(category, entry) }) }
        if (entries.isEmpty()) content.addView(message("Esta subcategoría aún no tiene elementos."))
    }

    private fun showSharingOptions() {
        selectedCategory = null; content.removeAllViews(); titleView.text = "Compartir diccionario"
        content.addView(message("Selecciona los libros que también podrán usar las categorías y entradas de ${document.title}."))
        val linked = database.linkedDocuments(document.identifier)
        database.findDocuments().filter { it.identifier != document.identifier }.forEach { target ->
            content.addView(CheckBox(this).apply {
                text = target.title; isChecked = target.identifier in linked
                setOnCheckedChangeListener { _, checked -> database.setDictionaryLinked(document.identifier, target.identifier, checked) }
            })
        }
        content.addView(Button(this).apply { text = "Listo"; isAllCaps = false; setOnClickListener { showCategories() } })
    }

    private fun showEntryEditor(category: DictionaryCategory, existing: DictionaryEntry?) {
        selectedCategory = category; content.removeAllViews(); titleView.text = category.name
        val termInput = EditText(this).apply {
            hint = "Palabra o frase"; setText(existing?.term ?: pendingTerm); setSingleLine(); setBackgroundResource(R.drawable.rounded_panel)
        }
        val descriptionInput = EditText(this).apply {
            hint = "Descripción (puedes agregarla después)"; setText(existing?.description.orEmpty()); minLines = 4
            gravity = Gravity.TOP; setBackgroundResource(R.drawable.rounded_panel); setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        content.addView(sectionTitle(if (existing == null) "Nuevo elemento" else "Editar elemento"))
        content.addView(termInput, LinearLayout.LayoutParams(-1, dp(54)))
        content.addView(descriptionInput, LinearLayout.LayoutParams(-1, dp(130)).apply { topMargin = dp(12) })
        content.addView(Button(this).apply {
            text = "Guardar ahora"; isAllCaps = false; setOnClickListener {
                if (existing == null) {
                    val result = database.saveDictionaryEntry(document.identifier, category.identifier, termInput.text.toString(), descriptionInput.text.toString(), pendingContext)
                    if (result == ReaderDatabase.DUPLICATE_DICTIONARY_ENTRY) showDuplicateMessage() else pendingSaved()
                } else { database.updateDictionaryDescription(existing.identifier, descriptionInput.text.toString()); pendingSaved() }
            }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
        if (existing == null) content.addView(Button(this).apply {
            text = "Guardar y describir después"; isAllCaps = false; setOnClickListener {
                val result = database.saveDictionaryEntry(document.identifier, category.identifier, termInput.text.toString(), "", pendingContext)
                if (result == ReaderDatabase.DUPLICATE_DICTIONARY_ENTRY) showDuplicateMessage() else pendingSaved()
            }
        }) else content.addView(Button(this).apply {
            text = "Eliminar elemento"; isAllCaps = false; setTextColor(Color.rgb(170, 35, 35)); setOnClickListener {
                database.deleteDictionaryEntry(existing.identifier); showEntries(category)
            }
        })
    }

    private fun pendingSaved() {
        Toast.makeText(this, "Elemento guardado", Toast.LENGTH_SHORT).show()
        if (pendingTerm.isNotBlank()) finish() else selectedCategory?.let(::showEntries)
    }

    private fun showDuplicateMessage() = Toast.makeText(
        this, "Esta palabra o frase ya existe en el diccionario de este libro", Toast.LENGTH_LONG
    ).show()

    private fun navigateBack() {
        if (selectedCategory != null) showCategories() else finish()
    }

    private fun card(title: String, subtitle: String = "Toca para abrir", action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); setBackgroundResource(R.drawable.rounded_panel)
        addView(TextView(context).apply { text = title; textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD })
        addView(TextView(context).apply { text = subtitle; setTextColor(Color.DKGRAY); setPadding(0, dp(4), 0, 0) })
        setOnClickListener { action() }
    }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) } }
    private fun sectionTitle(value: String) = TextView(this).apply { text = value; textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, dp(18), 0, dp(10)) }
    private fun message(value: String) = TextView(this).apply { text = value; textSize = 16f; gravity = Gravity.CENTER; setPadding(dp(20), dp(34), dp(20), dp(34)) }
    private fun applyInsets(view: View) {
        val originalLeft = view.paddingLeft; val originalTop = view.paddingTop
        val originalRight = view.paddingRight; val originalBottom = view.paddingBottom
        view.setOnApplyWindowInsetsListener { target, insets ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).let {
            target.setPadding(originalLeft + it.left, originalTop + it.top, originalRight + it.right, originalBottom + it.bottom)
        }; insets
    } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_DOCUMENT_IDENTIFIER = "document_identifier"
        const val EXTRA_SELECTED_TEXT = "selected_text"
        const val EXTRA_SELECTED_CONTEXT = "selected_context"
        const val EXTRA_ENTRY_IDENTIFIER = "dictionary_entry_identifier"
    }
}
