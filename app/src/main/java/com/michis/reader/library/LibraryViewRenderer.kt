package com.michis.reader.library

import com.michis.reader.R
import com.michis.reader.data.*
import com.michis.reader.databinding.ItemLibraryCoverLargeBinding
import com.michis.reader.databinding.ItemLibraryCoverSmallBinding
import com.michis.reader.databinding.ItemLibraryDocumentCompactBinding
import com.michis.reader.databinding.ItemLibraryDocumentDetailedBinding
import com.michis.reader.databinding.ItemLibraryFolderBinding
import com.michis.reader.databinding.ViewLibraryGridRowBinding
import com.michis.reader.reader.EpubPageEstimator
import com.michis.reader.theme.AppThemePalette

import android.app.Activity
import android.content.ClipData
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.DragEvent
import android.view.View
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
            val rowBinding = ViewLibraryGridRowBinding.inflate(activity.layoutInflater, documentList, false)
            documentList.addView(rowBinding.root.apply {
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

    private fun parentGridCard(): View {
        val card = inflateGridCard()
        card.coverImage.visibility = View.GONE
        card.folderIcon.apply { visibility = View.VISIBLE; text = "…" }
        card.title.text = "Carpeta anterior"
        card.root.contentDescription = "Regresar a la carpeta anterior"
        card.root.setOnClickListener { navigateToParent() }
        AppThemePalette.markCard(card.root)
        return card.root
    }

    private fun gridCard(item: LibraryItem): View {
        val card = inflateGridCard()
        when (item) {
            is LibraryItem.Folder -> {
                card.coverImage.visibility = View.GONE
                card.folderIcon.apply { visibility = View.VISIBLE; text = "📚" }
                card.title.text = item.value.name
                card.root.setOnClickListener { openFolder(item.value) }
            }
            is LibraryItem.Document -> {
                card.folderIcon.visibility = View.GONE
                card.coverImage.apply {
                    visibility = View.VISIBLE
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageDrawable(ColorDrawable(Color.rgb(224, 218, 205)))
                    contentDescription = "Portada de ${item.value.title}"
                    BookCoverLoader.load(activity, item.value, this)
                }
                card.title.text = item.value.title
                card.root.setOnClickListener { openDocument(item.value.identifier) }
            }
        }
        AppThemePalette.markCard(card.root)
        configureDragging(card.root, item)
        return card.root
    }

    private fun inflateGridCard(): GridCardViews = if (displayMode == 0) {
        val binding = ItemLibraryCoverLargeBinding.inflate(activity.layoutInflater, documentList, false)
        GridCardViews(binding.root, binding.coverImage, binding.folderIcon, binding.itemTitle)
    } else {
        val binding = ItemLibraryCoverSmallBinding.inflate(activity.layoutInflater, documentList, false)
        GridCardViews(binding.root, binding.coverImage, binding.folderIcon, binding.itemTitle)
    }

    private fun parentFolderCard(compact: Boolean): View {
        val binding = folderBinding(compact)
        binding.folderIcon.text = "…"
        binding.folderName.text = "Carpeta anterior"
        binding.navigationArrow.text = "‹"
        binding.root.contentDescription = "Regresar a la carpeta anterior"
        binding.root.setOnClickListener { navigateToParent() }
        return binding.root
    }

    private fun folderCard(folder: LibraryFolder, compact: Boolean): View {
        val binding = folderBinding(compact)
        binding.folderIcon.text = "📚"
        binding.folderName.text = folder.name
        binding.navigationArrow.text = "›"
        binding.root.setOnClickListener { openFolder(folder) }
        return binding.root
    }

    private fun folderBinding(compact: Boolean): ItemLibraryFolderBinding {
        val binding = ItemLibraryFolderBinding.inflate(activity.layoutInflater, documentList, false)
        val verticalPadding = dp(if (compact) 10 else 15)
        binding.root.setPadding(dp(16), verticalPadding, dp(16), verticalPadding)
        binding.folderIcon.textSize = if (compact) 24f else 32f
        binding.folderName.textSize = if (compact) 17f else 19f
        AppThemePalette.markCard(binding.root)
        return binding
    }

    private fun detailedDocumentCard(document: LibraryDocument): View {
        val binding = ItemLibraryDocumentDetailedBinding.inflate(activity.layoutInflater, documentList, false)
        val quoteCount = database.annotations(document.identifier).count { it.kind == "cita" }
        val dictionaryStatus = if (database.effectiveDictionaryEntries(document.identifier).isEmpty()) "Sin diccionario" else "Diccionario activo"
        binding.documentTitle.text = document.title
        binding.documentDetails.text = "${document.format} · Calculando páginas… · $quoteCount citas · $dictionaryStatus"
        EpubPageEstimator.estimate(activity, document) { pages ->
            binding.documentDetails.text = "${document.format} · $pages páginas aprox. · $quoteCount citas · $dictionaryStatus"
        }
        binding.root.setOnClickListener { openDocument(document.identifier) }
        AppThemePalette.markCard(binding.root)
        return binding.root
    }

    private fun documentCard(document: LibraryDocument): View {
        val binding = ItemLibraryDocumentCompactBinding.inflate(activity.layoutInflater, documentList, false)
        binding.documentTitle.text = document.title
        binding.documentFormat.text = document.format
        binding.root.setOnClickListener { openDocument(document.identifier) }
        AppThemePalette.markCard(binding.root)
        return binding.root
    }

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

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private data class GridCardViews(
        val root: LinearLayout,
        val coverImage: ImageView,
        val folderIcon: TextView,
        val title: TextView
    )

    private sealed interface GridEntry {
        data object Parent : GridEntry
        data class Item(val value: LibraryItem) : GridEntry
    }
}
