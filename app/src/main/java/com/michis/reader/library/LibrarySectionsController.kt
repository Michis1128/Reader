package com.michis.reader.library

import com.michis.reader.annotations.BookQuotesActivity
import com.michis.reader.data.ReaderDatabase
import com.michis.reader.databinding.ItemLibrarySectionCardBinding
import com.michis.reader.databinding.ViewEmptyStateBinding
import com.michis.reader.theme.AppThemePalette

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Presenta las secciones de citas y diccionarios que todavía forman parte de la biblioteca XML. */
internal class LibrarySectionsController(
    private val activity: Activity,
    private val database: ReaderDatabase,
    private val container: LinearLayout,
    private val pathLabel: TextView
) {
    fun showQuotes() {
        prepareSection()
        val quotes = database.annotations().filter { it.kind == "cita" }
        if (quotes.isEmpty()) addEmpty("Las citas y notas de todos tus libros aparecerán aquí.")
        quotes.groupBy { it.documentIdentifier }.forEach { (documentIdentifier, bookQuotes) ->
            val document = database.findDocument(documentIdentifier) ?: return@forEach
            val binding = sectionCard()
            binding.titleText.text = document.title
            binding.subtitleText.apply {
                text = "${bookQuotes.size} citas"
                visibility = View.VISIBLE
            }
            binding.root.setOnClickListener {
                activity.startActivity(
                    Intent(activity, BookQuotesActivity::class.java)
                        .putExtra(BookQuotesActivity.EXTRA_DOCUMENT_IDENTIFIER, documentIdentifier)
                )
            }
            container.addView(binding.root)
        }
        applyCurrentTheme()
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
