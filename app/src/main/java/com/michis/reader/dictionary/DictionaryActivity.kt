package com.michis.reader.dictionary

import com.michis.reader.R
import com.michis.reader.data.*
import com.michis.reader.databinding.ActivityDictionaryBinding
import com.michis.reader.databinding.ItemDictionaryCategoryBinding
import com.michis.reader.databinding.ItemDictionaryEntryBinding
import com.michis.reader.databinding.ItemDictionarySelectionBinding
import com.michis.reader.databinding.ViewDictionaryCategoryCreatorBinding
import com.michis.reader.databinding.ViewActionButtonBinding
import com.michis.reader.databinding.ViewDictionaryEntryEditorBinding
import com.michis.reader.databinding.ViewDictionaryMessageBinding
import com.michis.reader.databinding.ViewDictionarySectionTitleBinding
import com.michis.reader.databinding.ViewDictionarySelectionActionsBinding
import com.michis.reader.settings.ReaderSettingsRepository
import com.michis.reader.theme.*
import com.michis.reader.ui.ScreenHeader

import android.graphics.Color
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class DictionaryActivity : ComponentActivity() {
    private lateinit var binding: ActivityDictionaryBinding
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
        binding = ActivityDictionaryBinding.inflate(layoutInflater)
        AppThemePalette.markBackground(binding.rootContainer)
        ScreenHeader.configure(this, binding.screenHeader, "") { navigateBack() }
        titleView = binding.screenHeader.titleText
        content = binding.contentContainer
        applyInsets(binding.rootContainer)
        setContentView(binding.root)
        AppThemePalette.apply(this)
        val requestedEntry = database.findDictionaryEntry(intent.getLongExtra(EXTRA_ENTRY_IDENTIFIER, -1))
            ?.takeIf { it.documentIdentifier in database.effectiveDictionaryOwnerIdentifiers(document.identifier) }
        val requestedCategory = requestedEntry?.let { entry ->
            database.dictionaryCategories(entry.documentIdentifier).firstOrNull { it.identifier == entry.categoryIdentifier }
        }
        if (requestedEntry != null && requestedCategory != null) showEntryEditor(requestedCategory, requestedEntry) else showCategories()
    }

    private fun showCategories() {
        selectedCategory = null; content.removeAllViews(); titleView.text = "Diccionario · ${document.title}"
        content.addView(message(
            "Organiza palabras, personajes o conceptos en subcategorías. Primero crea una subcategoría y después agrega dentro las palabras o frases."
        ).apply { setBackgroundResource(R.drawable.rounded_panel) })
        if (pendingTerm.isNotBlank()) content.addView(message(
            "Vas a guardar “$pendingTerm”. Toca la subcategoría donde debe aparecer; después podrás escribir su descripción ahora o dejarla pendiente."
        ).apply { setBackgroundResource(R.drawable.rounded_panel) })
        content.addView(sectionTitle("Subcategorías"))
        content.addView(actionButton("Compartir este diccionario con otros libros") { showSharingOptions() })
        val categories = database.dictionaryCategories(document.identifier)
        if (categories.isNotEmpty() && pendingTerm.isBlank()) {
            content.addView(actionButton("Seleccionar subcategorías para eliminar") {
                showCategorySelection(categories)
            })
        }
        categories.forEach { category ->
            content.addView(categoryRow(category) {
                if (pendingTerm.isBlank()) showEntries(category) else showEntryEditor(category, null)
            })
        }
        if (categories.isEmpty()) content.addView(message("Este libro todavía no tiene subcategorías. Crea la primera, por ejemplo: Personajes, Palabras o Lugares."))
        val creatorBinding = ViewDictionaryCategoryCreatorBinding.inflate(layoutInflater, content, false)
        creatorBinding.createCategoryButton.setOnClickListener {
            val identifier = database.createDictionaryCategory(
                document.identifier,
                creatorBinding.categoryNameInput.text.toString()
            )
            if (identifier < 0) {
                Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show()
            } else {
                val created = database.dictionaryCategories(document.identifier)
                    .first { it.identifier == identifier }
                if (pendingTerm.isBlank()) showEntries(created) else showEntryEditor(created, null)
            }
        }
        content.addView(creatorBinding.root)
    }

    private fun showEntries(category: DictionaryCategory) {
        selectedCategory = category; content.removeAllViews(); titleView.text = category.name
        val entries = database.dictionaryEntries(category.identifier)
        content.addView(actionButton("Agregar palabra o frase") { showEntryEditor(category, null) })
        if (entries.isNotEmpty()) {
            content.addView(actionButton("Seleccionar elementos para eliminar") {
                showEntrySelection(category, entries)
            })
        }
        entries.forEach { entry ->
            content.addView(entryRow(entry) { showEntryEditor(category, entry) })
        }
        if (entries.isEmpty()) content.addView(message("Esta subcategoría aún no tiene elementos."))
    }

    private fun showCategorySelection(categories: List<DictionaryCategory>) {
        selectedCategory = null; content.removeAllViews(); titleView.text = "Eliminar subcategorías"
        content.addView(message("Selecciona una o varias subcategorías. También se eliminarán todas las entradas que contienen."))
        val selected = linkedSetOf<Long>()
        categories.forEach { category ->
            val itemBinding = ItemDictionarySelectionBinding.inflate(layoutInflater, content, false)
            itemBinding.selectionCheckbox.apply {
                text = category.name
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected += category.identifier else selected -= category.identifier
                }
            }
            content.addView(itemBinding.root)
        }
        val actionsBinding = ViewDictionarySelectionActionsBinding.inflate(layoutInflater, content, false)
        actionsBinding.deleteButton.apply {
            text = "Eliminar seleccionadas"
            setOnClickListener {
                if (selected.isEmpty()) Toast.makeText(context, "Selecciona al menos una subcategoría", Toast.LENGTH_SHORT).show()
                else confirmDeletion("Eliminar subcategorías", "Se eliminarán ${selected.size} subcategorías y sus elementos.") {
                    selected.forEach(database::deleteDictionaryCategory); showCategories()
                }
            }
        }
        actionsBinding.cancelButton.setOnClickListener { showCategories() }
        content.addView(actionsBinding.root)
    }

    private fun showEntrySelection(category: DictionaryCategory, entries: List<DictionaryEntry>) {
        selectedCategory = category; content.removeAllViews(); titleView.text = "Eliminar de ${category.name}"
        content.addView(message("Selecciona todas las palabras o frases que deseas eliminar."))
        val selected = linkedSetOf<Long>()
        entries.forEach { entry ->
            val itemBinding = ItemDictionarySelectionBinding.inflate(layoutInflater, content, false)
            itemBinding.selectionCheckbox.apply {
                text = entry.term
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected += entry.identifier else selected -= entry.identifier
                }
            }
            content.addView(itemBinding.root)
        }
        val actionsBinding = ViewDictionarySelectionActionsBinding.inflate(layoutInflater, content, false)
        actionsBinding.deleteButton.apply {
            text = "Eliminar seleccionados"
            setOnClickListener {
                if (selected.isEmpty()) Toast.makeText(context, "Selecciona al menos un elemento", Toast.LENGTH_SHORT).show()
                else confirmDeletion("Eliminar elementos", "Se eliminarán ${selected.size} elementos del diccionario.") {
                    selected.forEach(database::deleteDictionaryEntry); showEntries(category)
                }
            }
        }
        actionsBinding.cancelButton.setOnClickListener { showEntries(category) }
        content.addView(actionsBinding.root)
    }

    private fun confirmDeletion(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("Cancelar", null).setPositiveButton("Eliminar") { _, _ -> action() }.show()
    }

    private fun showSharingOptions() {
        selectedCategory = null; content.removeAllViews(); titleView.text = "Compartir diccionario"
        content.addView(message("Selecciona los libros que también podrán usar las categorías y entradas de ${document.title}."))
        val linked = database.linkedDocuments(document.identifier)
        database.findDocuments().filter { it.identifier != document.identifier }.forEach { target ->
            val itemBinding = ItemDictionarySelectionBinding.inflate(layoutInflater, content, false)
            itemBinding.selectionCheckbox.apply {
                text = target.title
                isChecked = target.identifier in linked
                setOnCheckedChangeListener { _, checked -> database.setDictionaryLinked(document.identifier, target.identifier, checked) }
            }
            content.addView(itemBinding.root)
        }
        content.addView(actionButton("Listo") { showCategories() })
    }

    private fun showEntryEditor(category: DictionaryCategory, existing: DictionaryEntry?) {
        selectedCategory = category; content.removeAllViews(); titleView.text = category.name
        val editorBinding = ViewDictionaryEntryEditorBinding.inflate(layoutInflater, content, false).apply {
            editorTitle.text = if (existing == null) "Nuevo elemento" else "Editar elemento"
            termInput.setText(existing?.term ?: pendingTerm)
            descriptionInput.setText(existing?.description.orEmpty())
        }
        editorBinding.saveButton.setOnClickListener {
            if (existing == null) {
                val result = database.saveDictionaryEntry(
                    document.identifier,
                    category.identifier,
                    editorBinding.termInput.text.toString(),
                    editorBinding.descriptionInput.text.toString(),
                    pendingContext
                )
                if (result == ReaderDatabase.DUPLICATE_DICTIONARY_ENTRY) showDuplicateMessage() else pendingSaved()
            } else {
                database.updateDictionaryDescription(existing.identifier, editorBinding.descriptionInput.text.toString())
                pendingSaved()
            }
        }
        editorBinding.secondaryActionButton.apply {
            text = if (existing == null) "Guardar y describir después" else "Eliminar elemento"
            if (existing != null) setTextColor(Color.rgb(170, 35, 35))
            setOnClickListener {
                if (existing == null) {
                    val result = database.saveDictionaryEntry(
                        document.identifier,
                        category.identifier,
                        editorBinding.termInput.text.toString(),
                        "",
                        pendingContext
                    )
                    if (result == ReaderDatabase.DUPLICATE_DICTIONARY_ENTRY) showDuplicateMessage() else pendingSaved()
                } else {
                    database.deleteDictionaryEntry(existing.identifier)
                    showEntries(category)
                }
            }
        }
        content.addView(editorBinding.root)
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

    private fun categoryRow(category: DictionaryCategory, action: () -> Unit): View {
        val binding = ItemDictionaryCategoryBinding.inflate(layoutInflater, content, false)
        binding.categoryName.text = category.name
        binding.root.setOnClickListener { action() }
        styleDictionaryCard(binding.root, binding.categoryName, binding.categoryHint)
        return binding.root
    }

    private fun entryRow(entry: DictionaryEntry, action: () -> Unit): View {
        val binding = ItemDictionaryEntryBinding.inflate(layoutInflater, content, false)
        binding.entryTerm.text = entry.term
        binding.entryDescription.text = entry.description.ifBlank { "Sin descripción" }
        binding.root.setOnClickListener { action() }
        styleDictionaryCard(binding.root, binding.entryTerm, binding.entryDescription)
        return binding.root
    }

    private fun styleDictionaryCard(card: View, title: TextView, subtitle: TextView) {
        val palette = AppThemePalette.current(this)
        card.background = AppThemePalette.cardBackground(this)
        AppThemePalette.markCard(card)
        title.setTextColor(palette.primaryText)
        subtitle.setTextColor(palette.secondaryText)
    }
    private fun actionButton(value: String, action: () -> Unit): View {
        val binding = ViewActionButtonBinding.inflate(layoutInflater, content, false)
        binding.actionButton.text = value
        binding.actionButton.setOnClickListener { action() }
        return binding.root
    }

    private fun sectionTitle(value: String) =
        ViewDictionarySectionTitleBinding.inflate(layoutInflater, content, false).root.apply { text = value }

    private fun message(value: String) =
        ViewDictionaryMessageBinding.inflate(layoutInflater, content, false).root.apply { text = value }
    private fun applyInsets(view: View) {
        val originalLeft = view.paddingLeft; val originalTop = view.paddingTop
        val originalRight = view.paddingRight; val originalBottom = view.paddingBottom
        view.setOnApplyWindowInsetsListener { target, insets ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).let {
            target.setPadding(originalLeft + it.left, originalTop + it.top, originalRight + it.right, originalBottom + it.bottom)
        }; insets
    } }
    companion object {
        const val EXTRA_DOCUMENT_IDENTIFIER = "document_identifier"
        const val EXTRA_SELECTED_TEXT = "selected_text"
        const val EXTRA_SELECTED_CONTEXT = "selected_context"
        const val EXTRA_ENTRY_IDENTIFIER = "dictionary_entry_identifier"
    }
}
