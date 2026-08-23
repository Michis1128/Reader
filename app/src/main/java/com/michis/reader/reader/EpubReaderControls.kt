package com.michis.reader.reader

import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderButton

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlin.math.roundToInt

/** Estado y representación Compose de los controles superpuestos del lector. */
internal class EpubReaderControls(
    private val activity: FragmentActivity,
    private val documentTitle: String,
    private val landscapeLayout: Boolean,
    private val actions: Actions
) {
    data class Actions(
        val goBack: () -> Unit,
        val showTools: () -> Unit,
        val showContents: () -> Unit,
        val showSearch: () -> Unit,
        val showQuotes: () -> Unit,
        val showDictionary: () -> Unit,
        val toggleBookmark: () -> Unit,
        val showReadingSettings: () -> Unit,
        val beginProgressChange: () -> Unit,
        val changeProgress: (Int) -> Unit,
        val finishProgressChange: (Int) -> Unit,
        val jumpBack: () -> Unit,
        val clearJumpHistory: () -> Unit,
        val jumpForward: () -> Unit
    )

    var currentPageIndex by mutableIntStateOf(0)
        private set
    private var pageCount by mutableIntStateOf(1)
    private var dictionaryLabel by mutableStateOf("Diccionario")
    private var previousJumpPage by mutableStateOf<Int?>(null)
    private var nextJumpPage by mutableStateOf<Int?>(null)
    private var hasJumpHistory by mutableStateOf(false)
    private var progressChangeStarted = false
    private var themeRevision by mutableIntStateOf(0)

    fun createTopControls(): View = composeView { ReaderTopControls() }

    fun createBottomControls(): View = composeView { ReaderBottomControls() }

    fun createCompactProgress(): View = composeView { ReaderCompactProgress() }

    fun configurePageCount(count: Int) {
        pageCount = count.coerceAtLeast(1)
        currentPageIndex = currentPageIndex.coerceIn(0, pageCount - 1)
    }

    fun updateProgress(pageIndex: Int) {
        currentPageIndex = pageIndex.coerceIn(0, pageCount - 1)
    }

    fun updateDictionaryLabel(label: String) {
        dictionaryLabel = label
    }

    fun updateJumpHistory(previousPage: Int?, nextPage: Int?, hasNavigation: Boolean) {
        previousJumpPage = previousPage
        nextJumpPage = nextPage
        hasJumpHistory = hasNavigation
    }

    fun refreshTheme() {
        themeRevision++
    }

    private fun composeView(content: @Composable () -> Unit): View = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            themeRevision
            MichisReaderComposeTheme(content)
        }
    }

    private fun beginProgressIfNeeded() {
        if (progressChangeStarted) return
        progressChangeStarted = true
        actions.beginProgressChange()
    }

    private fun changeProgress(value: Float) {
        beginProgressIfNeeded()
        val pageIndex = value.roundToInt().coerceIn(0, pageCount - 1)
        currentPageIndex = pageIndex
        actions.changeProgress(pageIndex)
    }

    private fun finishProgress() {
        if (!progressChangeStarted) return
        progressChangeStarted = false
        actions.finishProgressChange(currentPageIndex)
    }

    @Composable
    private fun ReaderTopControls() {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompactControlButton("‹", actions.goBack)
                Text(
                    documentTitle,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (landscapeLayout) {
                    Row(
                        modifier = Modifier.weight(2f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactControlButton("Índice", actions.showContents)
                        CompactControlButton("Buscar", actions.showSearch)
                        CompactControlButton("Citas", actions.showQuotes)
                        CompactControlButton(dictionaryLabel, actions.showDictionary)
                        CompactControlButton("Marcador", actions.toggleBookmark)
                    }
                } else {
                    CompactControlButton("🔧", actions.showTools)
                }
                CompactControlButton("Aa", actions.showReadingSettings)
            }
        }
    }

    @Composable
    private fun ReaderBottomControls() {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 5.dp, end = 14.dp, bottom = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (hasJumpHistory) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        JumpButton(previousJumpPage?.let { "Regresar a página ${it + 1}" }, actions.jumpBack)
                        MichisReaderButton("Limpiar historial", actions.clearJumpHistory, Modifier.weight(1f))
                        JumpButton(nextJumpPage?.let { "Avanzar a página ${it + 1}" }, actions.jumpForward)
                    }
                }
                Text(
                    "Página ${currentPageIndex + 1} de $pageCount",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Slider(
                    value = currentPageIndex.toFloat(),
                    onValueChange = ::changeProgress,
                    valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                    onValueChangeFinished = ::finishProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ReaderCompactProgress() {
        val fraction = if (pageCount <= 1) 0f else currentPageIndex.toFloat() / (pageCount - 1)
        val interactionSource = remember { MutableInteractionSource() }
        Slider(
            value = currentPageIndex.toFloat(),
            onValueChange = ::changeProgress,
            valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
            onValueChangeFinished = ::finishProgress,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth().height(18.dp).alpha(0.68f),
            thumb = {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            },
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        )
    }

    @Composable
    private fun CompactControlButton(label: String, action: () -> Unit) {
        MichisReaderButton(label, action)
    }

    @Composable
    private fun androidx.compose.foundation.layout.RowScope.JumpButton(label: String?, action: () -> Unit) {
        if (label == null) {
            Box(Modifier.weight(1f))
        } else {
            MichisReaderButton(label, action, Modifier.weight(1f))
        }
    }
}
