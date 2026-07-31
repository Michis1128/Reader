package com.michis.reader.library

import com.michis.reader.R
import com.michis.reader.annotations.BookQuotesActivity
import com.michis.reader.data.*
import com.michis.reader.dictionary.DictionaryActivity
import com.michis.reader.theme.AppThemePalette

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.widget.CheckBox

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
        if (annotations.isNotEmpty()) container.addView(Button(activity).apply {
            text = "Seleccionar ${if (kind == "cita") "citas" else "marcadores"} para eliminar"
            isAllCaps = false; setOnClickListener { showAnnotationSelection(kind, annotations) }
        })
        if (kind == "cita") {
            annotations.groupBy { it.documentIdentifier }.forEach { (documentIdentifier, quotes) ->
                val document = database.findDocument(documentIdentifier) ?: return@forEach
                container.addView(card {
                    addView(title(document.title))
                    addView(TextView(context).apply { text = "${quotes.size} citas"; setTextColor(Color.DKGRAY) })
                    setOnClickListener {
                        activity.startActivity(Intent(activity, BookQuotesActivity::class.java)
                            .putExtra(BookQuotesActivity.EXTRA_DOCUMENT_IDENTIFIER, documentIdentifier))
                    }
                })
            }
            return
        }
        annotations.forEach { annotation ->
            container.addView(card {
                addView(TextView(context).apply {
                    text = annotation.kind.uppercase(); textSize = 12f
                    setTextColor(annotation.color.takeIf { it != 0 } ?: Color.rgb(53, 89, 224))
                })
                addView(TextView(context).apply {
                    text = annotation.selectedText.ifBlank { "Página marcada" }; textSize = 17f
                })
                addView(TextView(context).apply {
                    text = "De: ${database.findDocument(annotation.documentIdentifier)?.title ?: "Libro eliminado"}"
                    textSize = 13f; setTextColor(Color.GRAY)
                })
                if (annotation.note.isNotBlank()) addView(TextView(context).apply {
                    text = annotation.note; setTextColor(Color.DKGRAY)
                })
                if (annotation.pageNumber > 0) addView(TextView(context).apply {
                    text = "Página ${annotation.pageNumber}"; textSize = 13f; setTextColor(Color.GRAY)
                })
                setOnLongClickListener { showAnnotationActions(annotation, kind); true }
            })
        }
    }

    private fun showAnnotationSelection(kind: String, annotations: List<SavedAnnotation>) {
        prepareSection()
        val selected = linkedSetOf<Long>()
        container.addView(TextView(activity).apply {
            text = "Selecciona uno o varios ${if (kind == "cita") "fragmentos" else "marcadores"}."
            textSize = 17f; setPadding(dp(12), dp(12), dp(12), dp(16))
        })
        annotations.forEach { annotation ->
            container.addView(CheckBox(activity).apply {
                val book = database.findDocument(annotation.documentIdentifier)?.title ?: "Libro eliminado"
                text = "${annotation.selectedText.ifBlank { "Página marcada" }}\n$book"
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected += annotation.identifier else selected -= annotation.identifier
                }
            })
        }
        container.addView(Button(activity).apply {
            text = "Eliminar seleccionados"; isAllCaps = false; setOnClickListener {
                if (selected.isEmpty()) return@setOnClickListener
                AlertDialog.Builder(activity).setTitle("Eliminar selección")
                    .setMessage("Se eliminarán ${selected.size} elementos y el cambio se sincronizará con Drive.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Eliminar") { _, _ ->
                        selected.forEach(database::deleteAnnotation); showAnnotations(kind)
                    }.show()
            }
        })
        container.addView(Button(activity).apply {
            text = "Cancelar"; isAllCaps = false; setOnClickListener { showAnnotations(kind) }
        })
    }

    fun showDictionaries() {
        prepareSection()
        val documents = database.documentsWithDictionaries()
        if (documents.isEmpty()) addEmpty("Los libros que tengan diccionario aparecerán aquí.")
        documents.forEach { document ->
            container.addView(card {
                addView(title(document.title))
                addView(TextView(context).apply {
                    val count = database.dictionaryCategories(document.identifier).size
                    text = "$count subcategoría${if (count == 1) "" else "s"}"
                    setTextColor(Color.DKGRAY)
                })
                setOnClickListener {
                    activity.startActivity(Intent(activity, DictionaryActivity::class.java)
                        .putExtra(DictionaryActivity.EXTRA_DOCUMENT_IDENTIFIER, document.identifier))
                }
            })
        }
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

    private fun addEmpty(message: String) = container.addView(TextView(activity).apply {
        text = message; gravity = Gravity.CENTER; textSize = 17f; setPadding(dp(20), dp(70), dp(20), 0)
    })

    private fun card(content: LinearLayout.() -> Unit) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; background = activity.getDrawable(R.drawable.rounded_panel)
        setPadding(dp(18), dp(16), dp(18), dp(16)); content()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun title(value: String) = TextView(activity).apply {
        text = value; textSize = 19f; typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
