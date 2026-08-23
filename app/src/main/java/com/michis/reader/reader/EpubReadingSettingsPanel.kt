@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import com.michis.reader.settings.PageMarginMode
import com.michis.reader.settings.ReaderSettingsRepository
import com.michis.reader.settings.SettingsActivity
import com.michis.reader.theme.ReadingThemePalette
import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderButton
import com.michis.reader.ui.compose.MichisReaderButtonRow
import com.michis.reader.ui.compose.MichisReaderCard
import com.michis.reader.ui.compose.MichisReaderInputShape

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.TextAlign

/** Construye el menú Aa en Compose y persiste sus controles globales. */
class EpubReadingSettingsPanel(
    private val activity: FragmentActivity,
    private val settings: ReaderSettingsRepository,
    private val submitPreferences: (EpubPreferences) -> Unit,
    private val selectTheme: (Int) -> Unit,
    private val selectFont: () -> Unit,
    private val closePanel: () -> Unit,
    private val themeOptionsVisibilityChanged: (Boolean) -> Unit
) {
    private var themeRevision by mutableIntStateOf(0)

    fun create(): View = ComposeView(activity).apply {
        tag = ReaderMenuTags.SURFACE
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            themeRevision
            MichisReaderComposeTheme {
                EpubReadingSettingsContent(
                    activity = activity,
                    settings = settings,
                    submitPreferences = submitPreferences,
                    selectTheme = selectTheme,
                    selectFont = selectFont,
                    closePanel = closePanel,
                    themeOptionsVisibilityChanged = themeOptionsVisibilityChanged
                )
            }
        }
    }

    fun refreshTheme() {
        themeRevision++
    }
}

@Composable
private fun EpubReadingSettingsContent(
    activity: FragmentActivity,
    settings: ReaderSettingsRepository,
    submitPreferences: (EpubPreferences) -> Unit,
    selectTheme: (Int) -> Unit,
    selectFont: () -> Unit,
    closePanel: () -> Unit,
    themeOptionsVisibilityChanged: (Boolean) -> Unit
) {
    var themeSelectionFocused by remember { mutableStateOf(false) }
    var selectedThemeIndex by remember {
        mutableIntStateOf(ReadingThemePalette.names.indexOf(settings.theme).coerceAtLeast(0))
    }
    val composeView = LocalView.current
    SideEffect {
        if (themeSelectionFocused) composeView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (themeSelectionFocused) Color.Transparent else MaterialTheme.colorScheme.surface
    ) {
        if (themeSelectionFocused) {
            FocusedThemeSelector(
                options = ReadingThemePalette.names,
                selectedIndex = selectedThemeIndex,
                select = { index ->
                    selectedThemeIndex = index
                    selectTheme(index)
                    themeOptionsVisibilityChanged(true)
                },
                dismiss = {
                    themeSelectionFocused = false
                    themeOptionsVisibilityChanged(false)
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Lectura",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    MichisReaderButton("Cerrar", closePanel)
                }
                TextAndTypographySettings(settings, submitPreferences, selectFont)
                ThemeAndPageSettings(
                    activity = activity,
                    settings = settings,
                    submitPreferences = submitPreferences,
                    selectedThemeIndex = selectedThemeIndex,
                    openThemeSelection = {
                        themeSelectionFocused = true
                        themeOptionsVisibilityChanged(true)
                    }
                )
                ScreenSettings(activity, settings, submitPreferences)
                MichisReaderCard {
                    Text("Aplicación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    MichisReaderButton(
                        "Configuración general",
                        { activity.startActivity(Intent(activity, SettingsActivity::class.java)) },
                        Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun TextAndTypographySettings(
    settings: ReaderSettingsRepository,
    submitPreferences: (EpubPreferences) -> Unit,
    selectFont: () -> Unit
) {
    var fontWeight by remember { mutableFloatStateOf(settings.fontWeight) }
    MichisReaderCard {
        FamilyTitle("Texto y tipografía")
        SettingLabel("Tamaño de letra (dp)")
        NumberStepper(settings.fontSizeDp.toDouble(), 8.0, 72.0, 1.0) { value ->
            settings.fontSizeDp = value.toFloat()
            submitPreferences(EpubPreferences(fontSize = value / 16.0))
        }
        SettingLabel("Tipo de fuente")
        MichisReaderButton("Seleccionar fuente…", selectFont, Modifier.fillMaxWidth())
        SettingLabel("Grosor de fuente")
        Slider(
            value = fontWeight,
            onValueChange = { value ->
                fontWeight = value
                settings.fontWeight = value
                submitPreferences(EpubPreferences(fontWeight = value.toDouble()))
            },
            valueRange = 0.5f..2f
        )
        SettingLabel("Interlineado")
        NumberStepper(settings.lineHeight.toDouble(), 1.0, 2.5, 0.1) { value ->
            settings.lineHeight = value.toFloat()
            submitPreferences(EpubPreferences(lineHeight = value))
        }
        SettingLabel("Alineación")
        SettingsDropdown(
            options = ALIGNMENT_NAMES,
            selectedIndex = settings.textAlignment,
            selected = { index ->
                settings.textAlignment = index
                submitPreferences(EpubPreferences(textAlign = ALIGNMENTS[index]))
            }
        )
    }
}

@Composable
private fun ThemeAndPageSettings(
    activity: FragmentActivity,
    settings: ReaderSettingsRepository,
    submitPreferences: (EpubPreferences) -> Unit,
    selectedThemeIndex: Int,
    openThemeSelection: () -> Unit
) {
    var pageModeIndex by remember { mutableIntStateOf(if (settings.continuousScroll) 1 else 0) }
    var animationsEnabled by remember { mutableStateOf(settings.pageTurnAnimations) }
    var twoPagesEnabled by remember {
        mutableStateOf(settings.twoPagesLandscape(activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE))
    }
    MichisReaderCard {
        FamilyTitle("Tema y página")
        SettingLabel("Tema")
        MichisReaderButton(
            ReadingThemePalette.names[selectedThemeIndex],
            openThemeSelection,
            Modifier.fillMaxWidth()
        )
        SettingLabel("Cambio de página")
        SettingsDropdown(PAGE_MODE_NAMES, pageModeIndex) { index ->
            pageModeIndex = index
            settings.continuousScroll = index == 1
            submitPreferences(EpubPreferences(scroll = index == 1))
        }
        SettingsToggle("Animar cambios de página", animationsEnabled) { checked ->
            animationsEnabled = checked
            settings.pageTurnAnimations = checked
            if (checked && settings.continuousScroll) {
                Toast.makeText(activity, "La animación se muestra en modo Paginado", Toast.LENGTH_LONG).show()
            }
        }
        SettingsToggle("Mostrar dos páginas en horizontal", twoPagesEnabled) { checked ->
            twoPagesEnabled = checked
            settings.setTwoPagesLandscape(checked)
            submitPreferences(EpubPreferences(
                columnCount = if (checked) ColumnCount.TWO else ColumnCount.ONE,
                spread = if (checked) Spread.ALWAYS else Spread.NEVER
            ))
        }
    }
}

@Composable
private fun ScreenSettings(
    activity: FragmentActivity,
    settings: ReaderSettingsRepository,
    submitPreferences: (EpubPreferences) -> Unit
) {
    var orientationIndex by remember { mutableIntStateOf(settings.readerOrientation) }
    var marginMode by remember { mutableStateOf(settings.pageMarginMode) }
    MichisReaderCard {
        FamilyTitle("Pantalla")
        SettingLabel("Orientación")
        SettingsDropdown(ORIENTATION_NAMES, orientationIndex) { index ->
            orientationIndex = index
            settings.readerOrientation = index
            activity.requestedOrientation = when (index) {
                1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        SettingLabel("Márgenes de página")
        val marginModes = PageMarginMode.entries
        SettingsDropdown(
            options = marginModes.map(PageMarginMode::displayName).toTypedArray(),
            selectedIndex = marginModes.indexOf(marginMode)
        ) { index ->
            marginMode = marginModes[index]
            settings.pageMarginMode = marginMode
            submitPreferences(EpubPreferences(pageMargins = marginMode.horizontalFactor))
        }
        if (marginMode == PageMarginMode.CUSTOM) {
            CustomMarginStepper("Superior (dp)", settings.customPageMarginTopDp) { settings.customPageMarginTopDp = it; submitCustomMargins(submitPreferences) }
            CustomMarginStepper("Inferior (dp)", settings.customPageMarginBottomDp) { settings.customPageMarginBottomDp = it; submitCustomMargins(submitPreferences) }
            CustomMarginStepper("Izquierdo (dp)", settings.customPageMarginLeftDp) { settings.customPageMarginLeftDp = it; submitCustomMargins(submitPreferences) }
            CustomMarginStepper("Derecho (dp)", settings.customPageMarginRightDp) { settings.customPageMarginRightDp = it; submitCustomMargins(submitPreferences) }
        }
    }
}

private fun submitCustomMargins(submitPreferences: (EpubPreferences) -> Unit) {
    submitPreferences(EpubPreferences(pageMargins = PageMarginMode.CUSTOM.horizontalFactor))
}

@Composable
private fun CustomMarginStepper(label: String, initial: Float, changed: (Float) -> Unit) {
    SettingLabel(label)
    NumberStepper(initial.toDouble(), 0.0, 96.0, 1.0) { changed(it.toFloat()) }
}

@Composable
private fun FocusedThemeSelector(
    options: Array<String>,
    selectedIndex: Int,
    select: (Int) -> Unit,
    dismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 56.dp, start = 12.dp, end = 12.dp)) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = dismiss,
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (index == selectedIndex) "✓  $option" else option,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { select(index) }
                )
            }
        }
    }
}

@Composable
private fun SettingsDropdown(options: Array<String>, selectedIndex: Int, selected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var currentIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex.coerceIn(options.indices)) }
    Box(modifier = Modifier.fillMaxWidth()) {
        MichisReaderButton(options[currentIndex], { expanded = true }, Modifier.fillMaxWidth())
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        currentIndex = index
                        expanded = false
                        selected(index)
                    }
                )
            }
        }
    }
}

@Composable
private fun NumberStepper(
    initial: Double,
    minimum: Double,
    maximum: Double,
    step: Double,
    changed: (Double) -> Unit
) {
    var value by remember { mutableStateOf(initial.coerceIn(minimum, maximum)) }
    var input by remember { mutableStateOf(formatNumber(value)) }
    fun update(next: Double) {
        value = next.coerceIn(minimum, maximum)
        input = formatNumber(value)
        changed(value)
    }
    MichisReaderButtonRow {
        MichisReaderButton("−", { update(value - step) })
        OutlinedTextField(
            value = input,
            onValueChange = { candidate ->
                if (candidate.isEmpty() || candidate.matches(Regex("[0-9]*([.,][0-9]*)?"))) input = candidate
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focus ->
                    if (!focus.isFocused) update(input.replace(',', '.').toDoubleOrNull() ?: value)
                },
            singleLine = true,
            shape = MichisReaderInputShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        MichisReaderButton("+", { update(value + step) })
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, changed: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = changed)
    }
}

@Composable
private fun FamilyTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingLabel(value: String) {
    Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

object ReaderMenuTags {
    const val SURFACE = "reader_menu_surface"
    const val CARD = "reader_menu_card"
}

private val ALIGNMENT_NAMES = arrayOf("Justificado", "Izquierda", "Centro", "Derecha")
private val ALIGNMENTS = arrayOf(TextAlign.JUSTIFY, TextAlign.START, TextAlign.CENTER, TextAlign.END)
private val PAGE_MODE_NAMES = arrayOf("Paginado", "Desplazamiento continuo")
private val ORIENTATION_NAMES = arrayOf("Automática", "Vertical", "Horizontal")
