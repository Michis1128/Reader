package com.michis.reader.library

import com.michis.reader.annotations.BookQuotesActivity
import com.michis.reader.data.*
import com.michis.reader.databinding.ItemLibrarySectionCardBinding
import com.michis.reader.databinding.ItemLibrarySectionSelectionBinding
import com.michis.reader.databinding.ViewEmptyStateBinding
import com.michis.reader.databinding.ViewLibrarySectionActionBinding
import com.michis.reader.databinding.ViewLibrarySectionSelectionActionsBinding
import com.michis.reader.databinding.ViewLibrarySectionSelectionInstructionBinding
import com.michis.reader.dictionary.DictionaryActivity
import com.michis.reader.theme.AppThemePalette

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Presenta las secciones de citas, marcadores y diccionarios de la biblioteca. */
internal class LibrarySectionsController(
    private val activity: Activity,
    private val database: ReaderDatabase,
    private val container: LinearLayout,
    private val pathLabel: TextView
) {
    fun showAnnotations(kind: String) {
        prepareSection()
        val annotations = database.annotations().filter { it.kind == kind }
        if (annotations.isEmpty()) addEmpty("Las citas, notas y marcadores de todos tus libros aparecerán aquí.")
        if (annotations.isNotEmpty()) {
            val actionBinding = ViewLibrarySectionActionBinding.inflate(activity.layoutInflater, container, false)
            actionBinding.actionButton.apply {
                text = "Seleccionar ${if (kind == "cita") "citas" else "marcadores"} para eliminar"
                setOnClickListener { showAnnotationSelection(kind, annotations) }
            }
            container.addView(actionBinding.root)
        }
        if (kind == "cita") {
            annotations.groupBy { it.documentIdentifier }.forEach { (documentIdentifier, quotes) ->
                val document = database.findDocument(documentIdentifier) ?: return@forEach
                val binding = sectionCard()
                binding.titleText.text = document.title
                binding.subtitleText.apply { text = "${quotes.size} citas"; visibility = View.VISIBLE }
                binding.root.setOnClickListener {
                    activity.startActivity(Intent(activity, BookQuotesActivity::class.java)
                        .putExtra(BookQuotesActivity.EXTRA_DOCUMENT_IDENTIFIER, documentIdentifier))
                }
                container.addView(binding.root)
            }
            applyCurrentTheme()
            return
        }
        annotations.forEach { annotation ->
            val binding = sectionCard()
            binding.eyebrowText.apply {
                text = annotation.kind.uppercase()
                setTextColor(annotation.color.takeIf { it != 0 } ?: Color.rgb(53, 89, 224))
                visibility = View.VISIBLE
            }
            binding.titleText.text = annotation.selectedText.ifBlank { "Página marcada" }
            binding.subtitleText.apply {
                text = "De: ${database.findDocument(annotation.documentIdentifier)?.title ?: "Libro eliminado"}"
                visibility = View.VISIBLE
            }
            binding.noteText.apply {
                text = annotation.note
                visibility = if (annotation.note.isBlank()) View.GONE else View.VISIBLE
            }
            binding.pageText.apply {
                text = "Página ${annotation.pageNumber}"
                visibility = if (annotation.pageNumber > 0) View.VISIBLE else View.GONE
            }
            binding.root.setOnLongClickListener { showAnnotationActions(annotation, kind); true }
            container.addView(binding.root)
        }
        applyCurrentTheme()
    }

    private fun showAnnotationSelection(kind: String, annotations: List<SavedAnnotation>) {
        prepareSection()
        val selected = linkedSetOf<Long>()
        val instructionBinding = ViewLibrarySectionSelectionInstructionBinding.inflate(
            activity.layoutInflater,
            container,
            false
        )
        instructionBinding.root.text =
            "Selecciona uno o varios ${if (kind == "cita") "fragmentos" else "marcadores"}."
        container.addView(instructionBinding.root)
        val actionsBinding = ViewLibrarySectionSelectionActionsBinding.inflate(activity.layoutInflater, container, false)
        annotations.forEach { annotation ->
            val binding = ItemLibrarySectionSelectionBinding.inflate(activity.layoutInflater, container, false)
            binding.selectionCheckbox.apply {
                val book = database.findDocument(annotation.documentIdentifier)?.title ?: "Libro eliminado"
                text = "${annotation.selectedText.ifBlank { "Página marcada" }}\n$book"
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected += annotation.identifier else selected -= annotation.identifier
                }
            }
            AppThemePalette.markCard(binding.selectionCheckbox)
            container.addView(binding.root)
        }
        actionsBinding.deleteButton.setOnClickListener {
            if (selected.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(activity).setTitle("Eliminar selección")
                .setMessage("Se eliminarán ${selected.size} elementos y el cambio se sincronizará con Drive.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar") { _, _ ->
                    selected.forEach(database::deleteAnnotation); showAnnotations(kind)
                }.show()
        }
        actionsBinding.cancelButton.setOnClickListener { showAnnotations(kind) }
        container.addView(actionsBinding.root)
        applyCurrentTheme()
    }

    fun showDictionaries() {
        prepareSection()
        val documents = database.documentsWithDictionaries()
        if (documents.isEmpty()) addEmpty("Los libros que tengan diccionario aparecerán aquí.")
        documents.forEach { document ->
            val binding = sectionCard()
            val count = database.dictionaryCategories(document.identifier).size
            binding.titleText.text = document.title
            binding.subtitleText.apply {
                text = "$count subcategoría${if (count == 1) "" else "s"}"
                visibility = View.VISIBLE
            }
            binding.root.setOnClickListener {
                activity.startActivity(Intent(activity, DictionaryActivity::class.java)
                    .putExtra(DictionaryActivity.EXTRA_DOCUMENT_IDENTIFIER, document.identifier))
            }
            container.addView(binding.root)
        }
        applyCurrentTheme()
    }

    private fun showAnnotationActions(annotation: SavedAnnotation, kind: String) {
        AlertDialog.Builder(activity).setTitle("Organizar elemento")
            .setItems(arrayOf("Mover arriba", "Mover abajo", "Eliminar")) { _, index ->
                when (index) {
                    0 -> database.moveAnnotation(annotation.identifier, -1)
                    1 -> database.moveAnnotation(annotation.identifier, 1)
                    2 -> database.deleteAnnotation(annotation.identifier)
                }
                showAnnotations(kind)
            }.show()
    }

    private fun prepareSection() {
        pathLabel.visibility = View.GONE
        container.removeAllViews()
    }

    private fun applyCurrentTheme() {
        container.post { AppThemePalette.apply(activity) }
    }

    private fun addEmpty(message: String) {
        val binding = ViewEmptyStateBinding.inflate(activity.layoutInflater, container, false)
        binding.root.text = message
        container.addView(binding.root)
    }

    private fun sectionCard(): ItemLibrarySectionCardBinding {
        val binding = ItemLibrarySectionCardBinding.inflate(activity.layoutInflater, container, false)
        AppThemePalette.markCard(binding.root)
        return binding
    }

}
