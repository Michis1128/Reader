package com.michis.reader.reader

import com.michis.reader.databinding.ItemEpubContentsGroupBinding
import com.michis.reader.databinding.ItemEpubContentsLinkBinding
import com.michis.reader.databinding.PanelEpubContentsBinding

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import org.readium.r2.shared.publication.Link

/** Representa y controla el arbol jerarquico de la tabla de contenido EPUB. */
class EpubContentsPanel(
    private val activity: FragmentActivity,
    private val navigateTo: (Link) -> Unit,
    private val closePanel: () -> Unit
) {
    private lateinit var list: LinearLayout

    fun create(): View {
        val binding = PanelEpubContentsBinding.inflate(activity.layoutInflater)
        binding.panelContainer.tag = ReaderMenuTags.SURFACE
        binding.closeButton.setOnClickListener { closePanel() }
        list = binding.contentsList
        list.addView(message("El índice aparecerá al terminar de abrir el EPUB."))
        return binding.root
    }

    fun populate(documentTitle: String, links: List<Link>) {
        check(::list.isInitialized) { "El panel de contenido debe crearse antes de llenarse" }
        list.removeAllViews()
        if (links.isEmpty()) {
            list.addView(message("Este EPUB no contiene tabla de contenido."))
        } else {
            addExpandableGroup(list, documentTitle, links, depth = 0, destination = null)
        }
    }

    private fun addExpandableGroup(
        parent: LinearLayout,
        title: String,
        children: List<Link>,
        depth: Int,
        destination: Link?
    ) {
        val binding = ItemEpubContentsGroupBinding.inflate(activity.layoutInflater, parent, false)
        val childContainer = binding.childContainer
        val groupHeader = binding.groupHeader.apply {
            text = "▸  $title"
            setPadding(
                dp(BASE_HEADER_PADDING_DP + depth * DEPTH_INDENT_DP),
                paddingTop,
                paddingRight,
                paddingBottom
            )
            setOnClickListener {
                val expanding = childContainer.visibility != View.VISIBLE
                childContainer.visibility = if (expanding) View.VISIBLE else View.GONE
                text = "${if (expanding) "▾" else "▸"}  $title"
            }
            if (destination != null) setOnLongClickListener {
                navigateTo(destination); closePanel(); true
            }
        }
        parent.addView(binding.root)
        children.forEach { link ->
            if (link.children.isNotEmpty()) {
                addExpandableGroup(childContainer, link.title ?: "Sección", link.children, depth + 1, link)
            } else {
                val linkBinding = ItemEpubContentsLinkBinding.inflate(activity.layoutInflater, childContainer, false)
                linkBinding.linkTitle.apply {
                    text = link.title ?: "Sección"
                    setPadding(
                        dp(BASE_ITEM_PADDING_DP + depth * DEPTH_INDENT_DP),
                        paddingTop,
                        paddingRight,
                        paddingBottom
                    )
                    setOnClickListener { navigateTo(link); closePanel() }
                }
                childContainer.addView(linkBinding.root)
            }
        }
    }

    private fun message(textValue: String) = TextView(activity).apply {
        text = textValue; setPadding(dp(12), dp(30), dp(12), dp(12))
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val DEPTH_INDENT_DP = 14
        const val BASE_HEADER_PADDING_DP = 15
        const val BASE_ITEM_PADDING_DP = 28
    }
}
