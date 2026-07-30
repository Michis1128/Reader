package com.michis.reader.reader

import com.michis.reader.R
import com.michis.reader.theme.AppThemePalette

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
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
        list = LinearLayout(activity).apply {
            tag = ReaderMenuTags.SURFACE
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(30))
            addView(header())
            addView(message("El índice aparecerá al terminar de abrir el EPUB."))
        }
        return ScrollView(activity).apply { isFillViewport = true; addView(list) }
    }

    fun populate(documentTitle: String, links: List<Link>) {
        check(::list.isInitialized) { "El panel de contenido debe crearse antes de llenarse" }
        list.removeAllViews()
        list.addView(header())
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
        val childContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(CHILD_INDENT_DP), 0, 0, 0)
        }
        val groupHeader = TextView(activity).apply {
            text = "▸  $title"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(BASE_HEADER_PADDING_DP + depth * DEPTH_INDENT_DP), dp(15), dp(12), dp(15))
            setBackgroundResource(R.drawable.rounded_panel)
            setOnClickListener {
                val expanding = childContainer.visibility != View.VISIBLE
                childContainer.visibility = if (expanding) View.VISIBLE else View.GONE
                text = "${if (expanding) "▾" else "▸"}  $title"
            }
            if (destination != null) setOnLongClickListener {
                navigateTo(destination); closePanel(); true
            }
        }
        parent.addView(groupHeader, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        parent.addView(childContainer)
        children.forEach { link ->
            if (link.children.isNotEmpty()) {
                addExpandableGroup(childContainer, link.title ?: "Sección", link.children, depth + 1, link)
            } else {
                childContainer.addView(TextView(activity).apply {
                    text = link.title ?: "Sección"; textSize = 16f
                    setPadding(dp(BASE_ITEM_PADDING_DP + depth * DEPTH_INDENT_DP), dp(14), dp(12), dp(14))
                    setBackgroundResource(R.drawable.rounded_panel)
                    setOnClickListener { navigateTo(link); closePanel() }
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
        }
    }

    private fun header() = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(activity).apply {
            text = "Capítulos"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        addView(Button(activity).apply {
            text = "Cerrar"; isAllCaps = false; setOnClickListener { closePanel() }
        })
    }

    private fun message(textValue: String) = TextView(activity).apply {
        text = textValue; setPadding(dp(12), dp(30), dp(12), dp(12))
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val CHILD_INDENT_DP = 30
        const val DEPTH_INDENT_DP = 14
        const val BASE_HEADER_PADDING_DP = 15
        const val BASE_ITEM_PADDING_DP = 28
    }
}
