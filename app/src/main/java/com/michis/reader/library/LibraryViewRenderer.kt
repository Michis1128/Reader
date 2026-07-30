package com.michis.reader.library

import com.michis.reader.R
import com.michis.reader.data.*
import com.michis.reader.reader.EpubPageEstimator
import com.michis.reader.theme.AppThemePalette

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Construye las cuatro presentaciones disponibles de la biblioteca. */
internal class LibraryViewRenderer(
    private val activity: Activity,
    private val database: ReaderDatabase,
    private val documentList: LinearLayout,
    private val navigateToParent: () -> Unit,
    private val openFolder: (LibraryFolder) -> Unit,
    private val openDocument: (Long) -> Unit,
    private val openDocumentActions: (LibraryDocument) -> Unit
) {
    private var displayMode: Int = 0

    fun render(folders: List<LibraryFolder>, documents: List<LibraryDocument>, mode: Int, showParentFolder: Boolean) {
        displayMode = mode
        if (mode < 2) {
            addFoldersAsGrid(folders, showParentFolder)
            addDocumentsAsGrid(documents)
        } else {
            if (showParentFolder) documentList.addView(parentFolderCard(compact = mode == 3))
            folders.forEach { documentList.addView(folderCard(it, compact = mode == 3)) }
            documents.forEach { document ->
                documentList.addView(if (mode == 2) detailedDocumentCard(document) else documentCard(document))
            }
        }
    }

    private fun parentFolderCard(compact: Boolean): View = horizontalLayout {
        background = activity.getDrawable(R.drawable.rounded_panel)
        setPadding(dp(16), if (compact) dp(10) else dp(15), dp(16), if (compact) dp(10) else dp(15))
        addView(TextView(context).apply { text = "…"; textSize = if (compact) 24f else 32f })
        addView(TextView(context).apply {
            text = "Carpeta anterior"; textSize = if (compact) 17f else 19f
            typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(context).apply { text = "‹"; textSize = 28f })
        contentDescription = "Regresar a la carpeta anterior"
        setOnClickListener { navigateToParent() }
    }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(9) } }

    private fun addFoldersAsGrid(folders: List<LibraryFolder>, showParentFolder: Boolean) {
        val entries = buildList<LibraryFolder?> {
            if (showParentFolder) add(null)
            addAll(folders)
        }
        if (entries.isEmpty()) return
        val landscape = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val columns = if (displayMode == 0) { if (landscape) 5 else 3 } else { if (landscape) 7 else 5 }
        val cellWidth = (activity.resources.displayMetrics.widthPixels - dp(40)) / columns
        val cardHeight = (cellWidth * 1.48f).toInt()
        entries.chunked(columns).forEach { rowFolders -> documentList.addView(horizontalLayout {
            clipChildren = false; clipToPadding = false
            rowFolders.forEach { folder -> addView(verticalLayout {
                gravity = Gravity.CENTER; setPadding(dp(5), dp(7), dp(5), dp(7)); background = activity.getDrawable(R.drawable.rounded_panel)
                addView(TextView(context).apply {
                    text = if (folder == null) "…" else "📁"
                    textSize = if (displayMode == 0) 54f else 38f; gravity = Gravity.CENTER
                    setTextColor(Color.rgb(92, 73, 122))
                }, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(TextView(context).apply {
                    text = folder?.name ?: "Carpeta anterior"
                    textSize = if (displayMode == 0) 12f else 10f; maxLines = 2; gravity = Gravity.CENTER
                })
                contentDescription = folder?.let { "Abrir carpeta ${it.name}" } ?: "Regresar a la carpeta anterior"
                setOnClickListener { if (folder == null) navigateToParent() else openFolder(folder) }
            }, LinearLayout.LayoutParams(0, cardHeight, 1f).apply { marginStart = dp(3); marginEnd = dp(3); topMargin = dp(4); bottomMargin = dp(6) }) }
            repeat(columns - rowFolders.size) { addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
        }) }
    }

    private fun folderCard(folder: LibraryFolder, compact: Boolean): View = horizontalLayout {
        background = activity.getDrawable(R.drawable.rounded_panel); setPadding(dp(16), if (compact) dp(10) else dp(15), dp(16), if (compact) dp(10) else dp(15))
        addView(TextView(context).apply { text = "📁"; textSize = if (compact) 24f else 32f })
        addView(TextView(context).apply {
            text = folder.name; textSize = if (compact) 17f else 19f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(context).apply { text = "›"; textSize = 28f; setTextColor(Color.rgb(92, 73, 122)) })
        setOnClickListener { openFolder(folder) }
    }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(9) } }

    private fun addDocumentsAsGrid(documents: List<LibraryDocument>) {
        val landscape = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val columns = if (displayMode == 0) { if (landscape) 5 else 3 } else { if (landscape) 7 else 5 }
        val cellWidth = (activity.resources.displayMetrics.widthPixels - dp(40)) / columns
        val cardHeight = (cellWidth * 1.48f).toInt()
        documents.chunked(columns).forEach { rowDocuments -> documentList.addView(horizontalLayout {
            clipChildren = false; clipToPadding = false
            rowDocuments.forEach { document -> addView(verticalLayout {
                gravity = Gravity.CENTER; setPadding(dp(5), dp(7), dp(5), dp(7)); background = activity.getDrawable(R.drawable.rounded_panel)
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP; setImageDrawable(ColorDrawable(Color.rgb(224, 218, 205)))
                    contentDescription = "Portada de ${document.title}"; BookCoverLoader.load(activity, document, this)
                }, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(TextView(context).apply { text = document.title; textSize = if (displayMode == 0) 12f else 10f; maxLines = 2; gravity = Gravity.CENTER })
                setOnClickListener { openDocument(document.identifier) }
                setOnLongClickListener { openDocumentActions(document); true }
            }, LinearLayout.LayoutParams(0, cardHeight, 1f).apply { marginStart = dp(3); marginEnd = dp(3); topMargin = dp(4); bottomMargin = dp(6) }) }
            repeat(columns - rowDocuments.size) { addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
        }) }
    }

    private fun detailedDocumentCard(document: LibraryDocument): View = verticalLayout {
        background = activity.getDrawable(R.drawable.rounded_panel); setPadding(dp(18), dp(14), dp(18), dp(14))
        val quoteCount = database.annotations(document.identifier).count { it.kind == "cita" }
        addView(TextView(context).apply { text = document.title; textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD })
        addView(TextView(context).apply {
            val dictionaryStatus = if (database.effectiveDictionaryEntries(document.identifier).isEmpty()) "Sin diccionario" else "Diccionario activo"
            text = "${document.format} · Calculando páginas… · $quoteCount citas · $dictionaryStatus"; setTextColor(Color.DKGRAY)
            val prefix = "${document.format} · "; val suffix = " · $quoteCount citas · $dictionaryStatus"
            EpubPageEstimator.estimate(activity, document) { pages ->
                text = "$prefix$pages páginas aprox.$suffix"
            }
        })
        setOnClickListener { openDocument(document.identifier) }
        setOnLongClickListener { openDocumentActions(document); true }
    }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(9) } }

    private fun documentCard(document: LibraryDocument): View = verticalLayout {
        background = activity.getDrawable(R.drawable.rounded_panel); setPadding(dp(18), dp(16), dp(18), dp(14))
        addView(TextView(context).apply {
            text = document.title; textSize = 19f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(30, 32, 38))
        })
        addView(TextView(context).apply {
            text = document.format; textSize = 13f; setTextColor(Color.GRAY
            ); setPadding(0, dp(4), 0, dp(8))
        })
        setOnClickListener { openDocument(document.identifier) }
        setOnLongClickListener { openDocumentActions(document); true }
    }.apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4); bottomMargin = dp(10) } }

    private fun verticalLayout(block: LinearLayout.() -> Unit) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; block()
    }
    private fun horizontalLayout(block: LinearLayout.() -> Unit) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; block()
    }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
