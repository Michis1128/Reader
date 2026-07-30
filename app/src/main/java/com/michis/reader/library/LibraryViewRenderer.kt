package com.michis.reader.library

import com.michis.reader.R
import com.michis.reader.data.*
import com.michis.reader.reader.EpubPageEstimator

import android.app.Activity
import android.content.ClipData
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Construye las cuatro presentaciones de biblioteca y el orden manual por arrastre. */
internal class LibraryViewRenderer(
    private val activity: Activity,
    private val database: ReaderDatabase,
    private val documentList: LinearLayout,
    private val navigateToParent: () -> Unit,
    private val openFolder: (LibraryFolder) -> Unit,
    private val openDocument: (Long) -> Unit,
    private val openDocumentActions: (LibraryDocument) -> Unit,
    private val moveItem: (String, String) -> Unit
) {
    private var displayMode = 0
    private var customOrdering = false

    fun render(items: List<LibraryItem>, mode: Int, showParentFolder: Boolean, allowCustomOrdering: Boolean) {
        displayMode = mode
        customOrdering = allowCustomOrdering
        if (mode < 2) renderGrid(items, showParentFolder) else renderList(items, showParentFolder)
    }

    private fun renderGrid(items: List<LibraryItem>, showParentFolder: Boolean) {
        val entries = buildList<GridEntry> {
            if (showParentFolder) add(GridEntry.Parent)
            items.forEach { add(GridEntry.Item(it)) }
        }
        val landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (displayMode == 0) { if (landscape) 5 else 3 } else { if (landscape) 7 else 5 }
        val cellWidth = (activity.resources.displayMetrics.widthPixels - dp(40)) / columns
        val cardHeight = (cellWidth * 1.48f).toInt()
        entries.chunked(columns).forEach { rowItems ->
            documentList.addView(horizontalLayout {
                clipChildren = false
                clipToPadding = false
                rowItems.forEach { entry ->
                    val card = when (entry) {
                        GridEntry.Parent -> parentGridCard()
                        is GridEntry.Item -> gridCard(entry.value)
                    }
                    addView(card, LinearLayout.LayoutParams(0, cardHeight, 1f).apply {
                        marginStart = dp(3); marginEnd = dp(3); topMargin = dp(4); bottomMargin = dp(6)
                    })
                }
                repeat(columns - rowItems.size) { addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
            })
        }
    }

    private fun renderList(items: List<LibraryItem>, showParentFolder: Boolean) {
        val compact = displayMode == 3
        if (showParentFolder) documentList.addView(parentFolderCard(compact))
        items.forEach { item ->
            val card = when (item) {
                is LibraryItem.Folder -> folderCard(item.value, compact)
                is LibraryItem.Document -> if (displayMode == 2) detailedDocumentCard(item.value) else documentCard(item.value)
            }
            configureDragging(card, item)
            documentList.addView(card)
        }
    }

    private fun parentGridCard(): View = verticalLayout {
        gravity = Gravity.CENTER
        setPadding(dp(5), dp(7), dp(5), dp(7))
        setBackgroundResource(R.drawable.rounded_panel)
        addView(TextView(context).apply { text = "…"; textSize = if (displayMode == 0) 54f else 38f; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(TextView(context).apply { text = "Carpeta anterior"; textSize = if (displayMode == 0) 12f else 10f; maxLines = 2; gravity = Gravity.CENTER })
        contentDescription = "Regresar a la carpeta anterior"
        setOnClickListener { navigateToParent() }
    }

    private fun gridCard(item: LibraryItem): View = verticalLayout {
        gravity = Gravity.CENTER
        setPadding(dp(5), dp(7), dp(5), dp(7))
        setBackgroundResource(R.drawable.rounded_panel)
        when (item) {
            is LibraryItem.Folder -> {
                addView(TextView(context).apply {
                    text = "📁"; textSize = if (displayMode == 0) 54f else 38f; gravity = Gravity.CENTER
                    setTextColor(Color.rgb(92, 73, 122))
                }, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(cardTitle(item.value.name))
                setOnClickListener { openFolder(item.value) }
            }
            is LibraryItem.Document -> {
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageDrawable(ColorDrawable(Color.rgb(224, 218, 205)))
                    contentDescription = "Portada de ${item.value.title}"
                    BookCoverLoader.load(activity, item.value, this)
                }, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(cardTitle(item.value.title))
                setOnClickListener { openDocument(item.value.identifier) }
            }
        }
        configureDragging(this, item)
    }

    private fun cardTitle(value: String) = TextView(activity).apply {
        text = value; textSize = if (displayMode == 0) 12f else 10f; maxLines = 2; gravity = Gravity.CENTER
    }

    private fun parentFolderCard(compact: Boolean): View = horizontalLayout {
        setBackgroundResource(R.drawable.rounded_panel)
        setPadding(dp(16), if (compact) dp(10) else dp(15), dp(16), if (compact) dp(10) else dp(15))
        addView(TextView(context).apply { text = "…"; textSize = if (compact) 24f else 32f })
        addView(TextView(context).apply {
            text = "Carpeta anterior"; textSize = if (compact) 17f else 19f
            typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(context).apply { text = "‹"; textSize = 28f })
        setOnClickListener { navigateToParent() }
    }.withListMargins()

    private fun folderCard(folder: LibraryFolder, compact: Boolean): View = horizontalLayout {
        setBackgroundResource(R.drawable.rounded_panel)
        setPadding(dp(16), if (compact) dp(10) else dp(15), dp(16), if (compact) dp(10) else dp(15))
        addView(TextView(context).apply { text = "📁"; textSize = if (compact) 24f else 32f })
        addView(TextView(context).apply {
            text = folder.name; textSize = if (compact) 17f else 19f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(context).apply { text = "›"; textSize = 28f; setTextColor(Color.rgb(92, 73, 122)) })
        setOnClickListener { openFolder(folder) }
    }.withListMargins()

    private fun detailedDocumentCard(document: LibraryDocument): View = verticalLayout {
        setBackgroundResource(R.drawable.rounded_panel); setPadding(dp(18), dp(14), dp(18), dp(14))
        val quoteCount = database.annotations(document.identifier).count { it.kind == "cita" }
        addView(TextView(context).apply { text = document.title; textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD })
        addView(TextView(context).apply {
            val dictionaryStatus = if (database.effectiveDictionaryEntries(document.identifier).isEmpty()) "Sin diccionario" else "Diccionario activo"
            text = "${document.format} · Calculando páginas… · $quoteCount citas · $dictionaryStatus"
            setTextColor(Color.DKGRAY)
            EpubPageEstimator.estimate(activity, document) { pages ->
                text = "${document.format} · $pages páginas aprox. · $quoteCount citas · $dictionaryStatus"
            }
        })
        setOnClickListener { openDocument(document.identifier) }
    }.withListMargins()

    private fun documentCard(document: LibraryDocument): View = verticalLayout {
        setBackgroundResource(R.drawable.rounded_panel); setPadding(dp(18), dp(16), dp(18), dp(14))
        addView(TextView(context).apply { text = document.title; textSize = 19f; typeface = android.graphics.Typeface.DEFAULT_BOLD })
        addView(TextView(context).apply { text = document.format; textSize = 13f; setTextColor(Color.GRAY); setPadding(0, dp(4), 0, dp(8)) })
        setOnClickListener { openDocument(document.identifier) }
    }.withListMargins(bottom = 10)

    private fun configureDragging(view: View, item: LibraryItem) {
        view.setOnLongClickListener {
            if (!customOrdering) {
                if (item is LibraryItem.Document) openDocumentActions(item.value)
                return@setOnLongClickListener true
            }
            val data = ClipData.newPlainText("library-item", item.key)
            view.startDragAndDrop(data, View.DragShadowBuilder(view), item.key, 0)
            true
        }
        if (!customOrdering) return
        view.setOnDragListener { target, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_ENTERED -> { target.alpha = 0.62f; true }
                DragEvent.ACTION_DRAG_EXITED -> { target.alpha = 1f; true }
                DragEvent.ACTION_DROP -> {
                    target.alpha = 1f
                    val draggedKey = event.localState as? String ?: return@setOnDragListener false
                    moveItem(draggedKey, item.key)
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> { target.alpha = 1f; true }
                else -> true
            }
        }
    }

    private fun <T : View> T.withListMargins(bottom: Int = 9): T = apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4); bottomMargin = dp(bottom)
        }
    }

    private fun verticalLayout(block: LinearLayout.() -> Unit) = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; block() }
    private fun horizontalLayout(block: LinearLayout.() -> Unit) = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; block() }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private sealed interface GridEntry {
        data object Parent : GridEntry
        data class Item(val value: LibraryItem) : GridEntry
    }
}
