package com.michis.reader.settings

import com.michis.reader.spen.SpenControlPreferences
import com.michis.reader.theme.KvColorPickerOverlay
import com.michis.reader.theme.ReadingThemePalette
import com.michis.reader.theme.compose.MichisReaderComposeTheme
import com.michis.reader.ui.compose.MichisReaderButton
import com.michis.reader.ui.compose.MichisReaderCard
import com.michis.reader.ui.compose.MichisReaderInputShape
import com.michis.reader.ui.compose.MichisReaderScreenHeader

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.samsung.android.sdk.penremote.SpenRemote
import com.samsung.android.sdk.penremote.SpenUnitManager

class SettingsActivity : ComponentActivity() {
    private val readerSettings by lazy { ReaderSettingsRepository.get(this) }
    private val advancedSyncMode by lazy { intent.getBooleanExtra(EXTRA_ADVANCED_SYNC, false) }
    private val driveSettingsSection by lazy {
        DriveSettingsSection(this, advancedSyncMode) {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(EXTRA_ADVANCED_SYNC, true))
        }
    }
    private var themeRevision by mutableIntStateOf(0)
    private var spenDiagnostic by mutableStateOf("Diagnóstico todavía no ejecutado")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            key(themeRevision) {
                MichisReaderComposeTheme {
                    SettingsScreen(
                        advancedMode = advancedSyncMode,
                        readerSettings = readerSettings,
                        drivePanel = { DrivePanel(driveSettingsSection) },
                        navigateBack = ::finish,
                        refreshTheme = { themeRevision += 1 },
                        chooseColor = ::chooseColor,
                        restoreSpen = ::restoreDefaultSpenActions,
                        diagnoseSpen = ::diagnoseSpen,
                        spenDiagnostic = spenDiagnostic,
                        openResetBooks = { startActivity(Intent(this, ResetBooksActivity::class.java)) }
                    )
                }
            }
        }
    }

    private fun chooseColor(preferenceKey: String, defaultColor: String) {
        val current = parseColor(readerSettings.preferences.getString(preferenceKey, defaultColor))
        KvColorPickerOverlay.show(this, current) { color ->
            readerSettings.preferences.edit().putString(preferenceKey, String.format("#%08X", color)).apply()
            themeRevision += 1
            Toast.makeText(this, "Color aplicado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreDefaultSpenActions() {
        val editor = readerSettings.preferences.edit()
        SpenControlPreferences.gestures.forEach { editor.putString(it.preferenceKey, it.defaultAction) }
        editor.apply()
        themeRevision += 1
        Toast.makeText(this, "Comandos restaurados", Toast.LENGTH_SHORT).show()
    }

    private fun diagnoseSpen() {
        val remote = SpenRemote.getInstance()
        val device = "${Build.MANUFACTURER} ${Build.MODEL}"
        val buttonAvailable = runCatching { remote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_BUTTON) }.getOrDefault(false)
        val motionAvailable = runCatching { remote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_AIR_MOTION) }.getOrDefault(false)
        spenDiagnostic = "$device\nBotón remoto: ${yesNo(buttonAvailable)}\nMovimiento aéreo: ${yesNo(motionAvailable)}\nConectando…"
        if (!buttonAvailable && !motionAvailable) {
            spenDiagnostic += "\nEl dispositivo o el S Pen no ofrece funciones remotas BLE."
            return
        }
        runCatching {
            if (remote.isConnected) remote.disconnect(this)
            remote.connect(this, object : SpenRemote.ConnectionResultCallback {
                override fun onSuccess(manager: SpenUnitManager) = runOnUiThread {
                    spenDiagnostic = "$device\nBotón remoto: ${yesNo(buttonAvailable)}\nMovimiento aéreo: ${yesNo(motionAvailable)}\nConexión al framework: Correcta"
                    runCatching { remote.disconnect(this@SettingsActivity) }
                }
                override fun onFailure(error: Int) = runOnUiThread {
                    spenDiagnostic = "$device\nBotón remoto: ${yesNo(buttonAvailable)}\nMovimiento aéreo: ${yesNo(motionAvailable)}\nConexión rechazada. Código: $error"
                }
            })
        }.onFailure { spenDiagnostic += "\nError: ${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
    }

    private fun yesNo(value: Boolean) = if (value) "Disponible" else "No disponible"
    private fun parseColor(value: String?) = runCatching { AndroidColor.parseColor(value) }.getOrDefault(0x665A7D9A)

    companion object { private const val EXTRA_ADVANCED_SYNC = "advanced_sync_settings" }
}

@Composable
private fun SettingsScreen(
    advancedMode: Boolean,
    readerSettings: ReaderSettingsRepository,
    drivePanel: @Composable () -> Unit,
    navigateBack: () -> Unit,
    refreshTheme: () -> Unit,
    chooseColor: (String, String) -> Unit,
    restoreSpen: () -> Unit,
    diagnoseSpen: () -> Unit,
    spenDiagnostic: String,
    openResetBooks: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { MichisReaderScreenHeader(if (advancedMode) "Drive avanzado" else "Configuración", navigateBack) }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding).navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (advancedMode) {
                item { SettingsDescription("Estas opciones cambian la vinculación técnica de Drive. La sincronización cotidiana puede hacerse desde la biblioteca.") }
                item { SettingsFamily(null) { drivePanel() } }
            } else {
                item { SettingsFamily("Cuenta y sincronización") { drivePanel() } }
                item { ReadingSettingsFamily(readerSettings, refreshTheme) }
                item { MenuAppearanceFamily(readerSettings, refreshTheme, chooseColor) }
                item { ColorFamily("Diccionarios", "Color de resaltado", "dictionary_highlight_color", "#665A7D9A", readerSettings, chooseColor) }
                item {
                    SettingsFamily("Citas") {
                        SettingsDescription("Color predeterminado para las nuevas citas. Podrás cambiarlo al guardar o editar cada cita.")
                        ColorSetting("Color predeterminado", "quote_default_color", "#66FFD54F", readerSettings, chooseColor)
                    }
                }
                item {
                    SettingsFamily("Marcadores") {
                        SettingsDescription("Personaliza cómo se identifican los puntos guardados dentro de cada libro.")
                        ColorSetting("Color de marcador", "bookmark_color", "#FF8D6E63", readerSettings, chooseColor)
                        ToggleSetting("Permitir marcador tocando la esquina", readerSettings.cornerBookmarkEnabled) {
                            readerSettings.cornerBookmarkEnabled = it
                        }
                    }
                }
                item { SpenSettingsFamily(readerSettings, restoreSpen, diagnoseSpen, spenDiagnostic, refreshTheme) }
                item {
                    SettingsFamily("Almacenamiento y privacidad") {
                        MichisReaderButton("Reiniciar libros…", openResetBooks, Modifier.fillMaxWidth())
                        SettingsDescription("Los libros EPUB permanecen en el dispositivo. La aplicación no envía archivos ni incluye telemetría.")
                    }
                }
            }
        }
    }
}

@Composable
private fun DrivePanel(section: DriveSettingsSection) {
    section.Content()
}

@Composable
private fun ReadingSettingsFamily(settings: ReaderSettingsRepository, refreshTheme: () -> Unit) {
    SettingsFamily("Lectura y modos rápidos") {
        SettingsDescription("Con los controles ocultos, toca la esquina superior izquierda para alternar únicamente entre estos dos temas.")
        DropdownSetting("Tema global", ReadingThemePalette.names.toList(), settings.theme) {
            settings.theme = it
            refreshTheme()
        }
        DropdownSetting("Modo rápido 1 (predeterminado: Día)", ReadingThemePalette.names.toList(), settings.quickMode(0)) { settings.setQuickMode(0, it) }
        DropdownSetting("Modo rápido 2 (predeterminado: Noche)", ReadingThemePalette.names.toList(), settings.quickMode(1)) { settings.setQuickMode(1, it) }
        val timeoutValues = listOf(2, 3, 5, 10, 15)
        DropdownSetting("Tiempo de pantalla activa", timeoutValues.map { "$it minutos" }, "${settings.screenTimeoutMinutes} minutos") { label ->
            settings.screenTimeoutMinutes = label.substringBefore(' ').toIntOrNull() ?: settings.screenTimeoutMinutes
        }
    }
}

@Composable
private fun MenuAppearanceFamily(
    settings: ReaderSettingsRepository,
    refreshTheme: () -> Unit,
    chooseColor: (String, String) -> Unit
) {
    val labels = listOf("Tema claro", "Tema oscuro", "Acorde al tema actual", "Personalizado")
    val values = listOf("light", "dark", "theme", "custom")
    var mode by remember { mutableStateOf(normalizedMenuMode(settings)) }
    SettingsFamily("Apariencia de menús") {
        DropdownSetting("Color de los menús", labels, labels[values.indexOf(mode).coerceAtLeast(0)]) { label ->
            mode = values[labels.indexOf(label)]
            settings.menuColorMode = mode
            refreshTheme()
        }
        if (mode == "custom") ColorSetting("Color personalizado", "menu_custom_color", "#FFF4E0", settings, chooseColor)
    }
}

private fun normalizedMenuMode(settings: ReaderSettingsRepository): String {
    val stored = settings.menuColorMode
    if (!stored.startsWith("theme:")) return stored
    val background = ReadingThemePalette.colors(stored.removePrefix("theme:")).first
    return if (androidx.core.graphics.ColorUtils.calculateLuminance(background) < .45) "dark" else "light"
}

@Composable
private fun SpenSettingsFamily(
    settings: ReaderSettingsRepository,
    restore: () -> Unit,
    diagnose: () -> Unit,
    diagnostic: String,
    refresh: () -> Unit
) {
    SettingsFamily("Comandos aéreos del S Pen") {
        SettingsDescription("Los gestos solo se reconocen mientras mantienes presionado el botón del S Pen.")
        SpenControlPreferences.gestures.forEach { gesture ->
            val selectedValue = settings.preferences.getString(gesture.preferenceKey, gesture.defaultAction)
            val selectedIndex = SpenControlPreferences.actionValues.indexOf(selectedValue).coerceAtLeast(0)
            DropdownSetting(gesture.label, SpenControlPreferences.actionLabels.toList(), SpenControlPreferences.actionLabels[selectedIndex]) { label ->
                val index = SpenControlPreferences.actionLabels.indexOf(label)
                settings.preferences.edit().putString(gesture.preferenceKey, SpenControlPreferences.actionValues[index]).apply()
            }
        }
        MichisReaderButton("Restaurar comandos predeterminados", restore, Modifier.fillMaxWidth())
        MichisReaderButton("Comprobar compatibilidad del S Pen", diagnose, Modifier.fillMaxWidth())
        SettingsDescription(diagnostic)
    }
}

@Composable
private fun ColorFamily(
    title: String,
    label: String,
    key: String,
    default: String,
    settings: ReaderSettingsRepository,
    chooseColor: (String, String) -> Unit
) = SettingsFamily(title) { ColorSetting(label, key, default, settings, chooseColor) }

@Composable
private fun ColorSetting(
    label: String,
    preferenceKey: String,
    defaultColor: String,
    settings: ReaderSettingsRepository,
    chooseColor: (String, String) -> Unit
) {
    val value = settings.preferences.getString(preferenceKey, defaultColor).orEmpty()
    val color = runCatching { AndroidColor.parseColor(value) }.getOrDefault(AndroidColor.GRAY)
    val preview = Color(color).compositeOver(MaterialTheme.colorScheme.surfaceVariant)
    val content = if (preview.luminance() < .45f) Color.White else Color(0xFF151619)
    Text(label, style = MaterialTheme.typography.labelLarge)
    OutlinedButton(
        onClick = { chooseColor(preferenceKey, defaultColor) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = MichisReaderInputShape,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            androidx.compose.material3.Surface(color = preview, contentColor = content, shape = RoundedCornerShape(10.dp)) {
                Text(value, Modifier.fillMaxWidth().padding(12.dp))
            }
        }
    }
}

@Composable
private fun DropdownSetting(label: String, options: List<String>, selected: String, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var current by remember(selected) { mutableStateOf(selected) }
    Text(label, style = MaterialTheme.typography.labelLarge)
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = MichisReaderInputShape
        ) { Text(current, modifier = Modifier.fillMaxWidth()) }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 240.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    current = option
                    expanded = false
                    select(option)
                })
            }
        }
    }
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    var value by remember(checked) { mutableStateOf(checked) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = { value = it; change(it) })
    }
}

@Composable
private fun SettingsFamily(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!title.isNullOrBlank()) Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        MichisReaderCard { content() }
    }
}

@Composable
internal fun SettingsDescription(value: String) {
    Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
