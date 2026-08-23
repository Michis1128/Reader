package com.michis.reader.reader

import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderButton
import com.michis.reader.ui.compose.MichisReaderCardShape

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import org.readium.r2.shared.publication.Link

/** Representa y controla el árbol jerárquico de la tabla de contenido EPUB. */
class EpubContentsPanel(
    private val activity: FragmentActivity,
    private val navigateTo: (Link) -> Unit,
    private val closePanel: () -> Unit
) {
    private var rootNode by mutableStateOf<ContentsNode?>(null)
    private var emptyMessage by mutableStateOf("El índice aparecerá al terminar de abrir el EPUB.")
    private var expandedNodeKeys by mutableStateOf(emptySet<String>())
    private var themeRevision by mutableIntStateOf(0)

    fun create(): View = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            themeRevision
            MichisReaderComposeTheme {
                EpubContentsPanelContent(
                    rootNode = rootNode,
                    emptyMessage = emptyMessage,
                    expandedNodeKeys = expandedNodeKeys,
                    toggleExpanded = ::toggleExpanded,
                    navigateTo = {
                        navigateTo(it)
                        closePanel()
                    },
                    closePanel = closePanel
                )
            }
        }
    }

    fun populate(documentTitle: String, links: List<Link>) {
        if (links.isEmpty()) {
            rootNode = null
            emptyMessage = "Este EPUB no contiene tabla de contenido."
            expandedNodeKeys = emptySet()
            return
        }
        rootNode = ContentsNode(
            key = "root",
            title = documentTitle,
            destination = null,
            children = links.mapIndexed { index, link -> link.toContentsNode("root.$index") }
        )
        expandedNodeKeys = emptySet()
    }

    fun refreshTheme() {
        themeRevision++
    }

    private fun toggleExpanded(key: String) {
        expandedNodeKeys = if (key in expandedNodeKeys) expandedNodeKeys - key else expandedNodeKeys + key
    }
}

private data class ContentsNode(
    val key: String,
    val title: String,
    val destination: Link?,
    val children: List<ContentsNode>
)

private data class VisibleContentsNode(val node: ContentsNode, val depth: Int)

private fun Link.toContentsNode(key: String): ContentsNode = ContentsNode(
    key = key,
    title = title ?: "Sección",
    destination = this,
    children = children.mapIndexed { index, child -> child.toContentsNode("$key.$index") }
)

private fun visibleNodes(root: ContentsNode, expandedKeys: Set<String>): List<VisibleContentsNode> {
    val visible = mutableListOf<VisibleContentsNode>()
    fun append(node: ContentsNode, depth: Int) {
        visible += VisibleContentsNode(node, depth)
        if (node.key in expandedKeys) node.children.forEach { append(it, depth + 1) }
    }
    append(root, 0)
    return visible
}

@Composable
private fun EpubContentsPanelContent(
    rootNode: ContentsNode?,
    emptyMessage: String,
    expandedNodeKeys: Set<String>,
    toggleExpanded: (String) -> Unit,
    navigateTo: (Link) -> Unit,
    closePanel: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Capítulos",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                MichisReaderButton("Cerrar", closePanel)
            }
            if (rootNode == null) {
                Text(emptyMessage, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleNodes(rootNode, expandedNodeKeys), key = { it.node.key }) { visibleNode ->
                        ContentsNodeCard(
                            visibleNode = visibleNode,
                            expanded = visibleNode.node.key in expandedNodeKeys,
                            toggleExpanded = toggleExpanded,
                            navigateTo = navigateTo
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContentsNodeCard(
    visibleNode: VisibleContentsNode,
    expanded: Boolean,
    toggleExpanded: (String) -> Unit,
    navigateTo: (Link) -> Unit
) {
    val node = visibleNode.node
    val hasChildren = node.children.isNotEmpty()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (visibleNode.depth * 18).dp)
            .combinedClickable(
                onClick = {
                    if (hasChildren) toggleExpanded(node.key) else node.destination?.let(navigateTo)
                },
                onLongClick = { node.destination?.let(navigateTo) }
            ),
        shape = if (visibleNode.depth == 0) MichisReaderCardShape else RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = if (hasChildren) "${if (expanded) "▾" else "▸"}  ${node.title}" else node.title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (hasChildren) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
