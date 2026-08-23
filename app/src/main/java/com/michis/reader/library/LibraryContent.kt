@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.michis.reader.library

import com.michis.reader.data.LibraryDocument
import com.michis.reader.data.LibraryFolder
import com.michis.reader.reader.EpubPageEstimator
import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderCard

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs
import kotlin.math.sign

internal data class LibraryContentState(
    val pathLabel: String = "Mi biblioteca",
    val showPath: Boolean = true,
    val items: List<LibraryItem> = emptyList(),
    val displayMode: Int = 0,
    val showParentFolder: Boolean = false,
    val allowCustomOrdering: Boolean = false,
    val emptyMessage: String? = null
)

@Composable
internal fun LibraryContent(
    state: LibraryContentState,
    navigateToParent: () -> Unit,
    openFolder: (LibraryFolder) -> Unit,
    openDocument: (Long) -> Unit,
    openDocumentActions: (LibraryDocument) -> Unit,
    moveItem: (String, Int) -> Unit,
    quoteCount: (Long) -> Int,
    hasDictionary: (Long) -> Boolean
) {
    MichisReaderComposeTheme {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (state.showPath) {
                Text(
                    state.pathLabel,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = navigateToParent, onLongClick = {})
                        .padding(horizontal = 4.dp, vertical = 10.dp)
                )
            }
            state.emptyMessage?.let {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(it) }
            } ?: if (state.displayMode < 2) {
                LibraryGrid(state, navigateToParent, openFolder, openDocument, openDocumentActions, moveItem)
            } else {
                LibraryList(state, navigateToParent, openFolder, openDocument, openDocumentActions, moveItem, quoteCount, hasDictionary)
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    state: LibraryContentState,
    navigateToParent: () -> Unit,
    openFolder: (LibraryFolder) -> Unit,
    openDocument: (Long) -> Unit,
    openDocumentActions: (LibraryDocument) -> Unit,
    moveItem: (String, Int) -> Unit
) {
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns = if (state.displayMode == 0) if (landscape) 5 else 3 else if (landscape) 7 else 5
    val entries = buildList<GridLibraryEntry> {
        if (state.showParentFolder) add(GridLibraryEntry.Parent)
        state.items.forEach { add(GridLibraryEntry.Item(it)) }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                GridLibraryEntry.Parent -> LibraryGridCard("Carpeta anterior", null, "…", Modifier, navigateToParent)
                is GridLibraryEntry.Item -> when (val item = entry.item) {
                    is LibraryItem.Folder -> LibraryGridCard(
                        item.value.name, null, "📚",
                        libraryInteractionModifier(item, state.allowCustomOrdering, { openFolder(item.value) }, openDocumentActions, moveItem),
                        {}
                    )
                    is LibraryItem.Document -> LibraryGridCard(
                        item.value.title, item.value, null,
                        libraryInteractionModifier(item, state.allowCustomOrdering, { openDocument(item.value.identifier) }, openDocumentActions, moveItem),
                        {}
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryGridCard(
    title: String,
    document: LibraryDocument?,
    icon: String?,
    interactionModifier: Modifier,
    open: () -> Unit
) {
    MichisReaderCard(modifier = interactionModifier.aspectRatio(0.68f).let {
        if (interactionModifier == Modifier) it.combinedClickable(onClick = open, onLongClick = {}) else it
    }) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f), contentAlignment = Alignment.Center) {
            if (document != null) BookCover(document, Modifier.fillMaxSize())
            else Text(icon.orEmpty(), style = MaterialTheme.typography.displayMedium)
        }
        Text(title, maxLines = 2, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LibraryList(
    state: LibraryContentState,
    navigateToParent: () -> Unit,
    openFolder: (LibraryFolder) -> Unit,
    openDocument: (Long) -> Unit,
    openDocumentActions: (LibraryDocument) -> Unit,
    moveItem: (String, Int) -> Unit,
    quoteCount: (Long) -> Int,
    hasDictionary: (Long) -> Boolean
) {
    val entries = buildList<ListLibraryEntry> {
        if (state.showParentFolder) add(ListLibraryEntry.Parent)
        state.items.forEach { add(ListLibraryEntry.Item(it)) }
    }
    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                ListLibraryEntry.Parent -> LibraryListCard("…", "Carpeta anterior", "Regresar", Modifier, navigateToParent)
                is ListLibraryEntry.Item -> when (val item = entry.item) {
                    is LibraryItem.Folder -> LibraryListCard(
                        "📚", item.value.name, "Abrir carpeta",
                        libraryInteractionModifier(item, state.allowCustomOrdering, { openFolder(item.value) }, openDocumentActions, moveItem)
                    ) {}
                    is LibraryItem.Document -> DocumentListCard(
                        item.value,
                        detailed = state.displayMode == 2,
                        interactionModifier = libraryInteractionModifier(item, state.allowCustomOrdering, { openDocument(item.value.identifier) }, openDocumentActions, moveItem),
                        quoteCount = quoteCount(item.value.identifier),
                        dictionaryActive = hasDictionary(item.value.identifier)
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun LibraryListCard(icon: String, title: String, subtitle: String, interactionModifier: Modifier, open: () -> Unit) {
    MichisReaderCard(if (interactionModifier == Modifier) interactionModifier.combinedClickable(onClick = open, onLongClick = {}) else interactionModifier) {
        Text("$icon  $title", style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DocumentListCard(
    document: LibraryDocument,
    detailed: Boolean,
    interactionModifier: Modifier,
    quoteCount: Int,
    dictionaryActive: Boolean,
    open: () -> Unit
) {
    val context = LocalContext.current
    var pages by remember(document.identifier) { mutableStateOf<Int?>(null) }
    if (detailed) LaunchedEffect(document.identifier) {
        EpubPageEstimator.estimate(context, document) { pages = it }
    }
    MichisReaderCard(if (interactionModifier == Modifier) interactionModifier.combinedClickable(onClick = open, onLongClick = {}) else interactionModifier) {
        Text(document.title, style = MaterialTheme.typography.titleMedium)
        if (detailed) {
            Text(
                "${document.format} · ${pages?.let { "$it páginas aprox." } ?: "Calculando páginas…"} · " +
                    "$quoteCount citas · ${if (dictionaryActive) "Diccionario activo" else "Sin diccionario"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else Text(document.format, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BookCover(document: LibraryDocument, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.clip(com.michis.reader.ui.compose.MichisReaderInputShape),
        factory = { context -> ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(ColorDrawable(Color.rgb(224, 218, 205)))
        } },
        update = { image ->
            image.contentDescription = "Portada de ${document.title}"
            BookCoverLoader.load(image.context, document, image)
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun libraryInteractionModifier(
    item: LibraryItem,
    customOrdering: Boolean,
    open: () -> Unit,
    openDocumentActions: (LibraryDocument) -> Unit,
    moveItem: (String, Int) -> Unit
): Modifier = if (customOrdering) {
    Modifier.clickable(onClick = open).pointerInput(item.key) {
        var accumulated = 0f
        val threshold = 52.dp.toPx()
        detectDragGesturesAfterLongPress(
            onDragEnd = { accumulated = 0f },
            onDragCancel = { accumulated = 0f }
        ) { change, amount ->
            change.consume()
            accumulated += if (abs(amount.y) >= abs(amount.x)) amount.y else amount.x
            if (abs(accumulated) >= threshold) {
                moveItem(item.key, accumulated.sign.toInt())
                accumulated = 0f
            }
        }
    }.graphicsLayer { alpha = 1f }
} else if (item is LibraryItem.Document) {
    Modifier.combinedClickable(onClick = open, onLongClick = { openDocumentActions(item.value) })
} else Modifier.combinedClickable(onClick = open, onLongClick = {})

private sealed interface GridLibraryEntry {
    val key: String
    data object Parent : GridLibraryEntry { override val key = "parent" }
    data class Item(val item: LibraryItem) : GridLibraryEntry { override val key = item.key }
}

private sealed interface ListLibraryEntry {
    val key: String
    data object Parent : ListLibraryEntry { override val key = "parent" }
    data class Item(val item: LibraryItem) : ListLibraryEntry { override val key = item.key }
}
