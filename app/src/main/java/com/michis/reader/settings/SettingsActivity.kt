package com.michis.reader.settings

import com.michis.reader.R
import com.michis.reader.databinding.ViewSettingsSectionBinding
import com.michis.reader.databinding.ViewSettingsFieldBinding
import com.michis.reader.databinding.ActivitySettingsBinding
import com.michis.reader.databinding.ViewHexColorEditorBinding
import com.michis.reader.spen.SpenControlPreferences
import com.michis.reader.sync.drive.GoogleDriveAuthorizationManager
import com.michis.reader.theme.*
import com.michis.reader.ui.LimitedHeightSpinner
import com.michis.reader.ui.ScreenHeader

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.widget.*
import androidx.activity.ComponentActivity
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
    private val readingModes = ReadingThemePalette.names

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = if (advancedSyncMode) buildAdvancedSyncScreen() else buildSettingsScreen()
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        AppThemePalette.markBackground(binding.rootContainer)
        ScreenHeader.configure(
            this,
            binding.screenHeader,
            if (advancedSyncMode) "Drive avanzado" else getString(R.string.settings_title)
        ) { finish() }
        binding.contentContainer.addView(screen, FrameLayout.LayoutParams(-1, -2))
        applyInsets(binding.rootContainer)
        setContentView(binding.root)
        AppThemePalette.apply(this)
    }

    private fun buildAdvancedSyncScreen() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(18)); AppThemePalette.markBackground(this)
        addView(description("Estas opciones cambian la vinculación técnica de Drive. La sincronización cotidiana puede hacerse desde la biblioteca."))
        addView(settingsSection(null) { addView(googleAccountPanel()) })
    }

    private fun buildSettingsScreen() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(18)); AppThemePalette.markBackground(this)
        addView(settingsSection("Cuenta y sincronización") {
            addView(googleAccountPanel())
        })
        addView(settingsSection("Lectura y modos rápidos") {
            addView(description("Con los controles ocultos, toca la esquina superior izquierda para alternar únicamente entre estos dos temas."))
            addView(settingsField("Tema global", globalThemeSpinner()))
            addView(settingsField("Modo rápido 1 (predeterminado: Día)", modeSpinner("quick_mode_1", "Día")))
            addView(settingsField("Modo rápido 2 (predeterminado: Noche)", modeSpinner("quick_mode_2", "Noche")))
            addView(settingsField("Tiempo de pantalla activa", screenTimeoutSpinner()))
        })
        addView(settingsSection("Apariencia de menús") {
            val customColorControls = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(settingsField("Color personalizado", hexColorEditor("menu_custom_color", "#FFF4E0")))
                visibility = if (normalizedMenuColorMode() == "custom") View.VISIBLE else View.GONE
            }
            addView(settingsField("Color de los menús", menuColorSpinner { mode ->
                customColorControls.visibility = if (mode == "custom") View.VISIBLE else View.GONE
                AppThemePalette.apply(this@SettingsActivity)
            }))
            addView(customColorControls)
        })
        addView(settingsSection("Diccionarios") {
            addView(settingsField("Color de resaltado", hexColorEditor("dictionary_highlight_color", "#665A7D9A")))
        })
        addView(settingsSection("Citas") {
            addView(description("Color predeterminado para las nuevas citas. Podrás cambiarlo al guardar o editar cada cita."))
            addView(settingsField("Color predeterminado", hexColorEditor("quote_default_color", "#66FFD54F")))
        })
        addView(settingsSection("Marcadores") {
            addView(description("Personaliza cómo se identifican los puntos guardados dentro de cada libro."))
            addView(settingsField("Color de marcador", hexColorEditor("bookmark_color", "#FF8D6E63")))
            addView(Switch(context).apply {
                text = "Permitir marcador tocando la esquina"; isChecked = readerSettings.preferences.getBoolean("corner_bookmark_enabled", true)
                setOnCheckedChangeListener { _, checked -> readerSettings.preferences.edit().putBoolean("corner_bookmark_enabled", checked).apply() }
            })
        })
        addView(settingsSection("Comandos aéreos del S Pen") {
            addView(description("Los gestos solo se reconocen mientras mantienes presionado el botón del S Pen."))
            SpenControlPreferences.gestures.forEach { gesture ->
                addView(settingsField(gesture.label, spenActionSpinner(gesture)))
            }
            addView(Button(context).apply {
                text = "Restaurar comandos predeterminados"; isAllCaps = false
                setOnClickListener { restoreDefaultSpenActions(); Toast.makeText(context, "Comandos restaurados", Toast.LENGTH_SHORT).show(); recreate() }
            })
            val result = TextView(context).apply { text = "Diagnóstico todavía no ejecutado"; setPadding(dp(12), dp(10), dp(12), dp(10)) }
            addView(Button(context).apply { text = "Comprobar compatibilidad del S Pen"; isAllCaps = false; setOnClickListener { diagnoseSpen(result) } })
            addView(result)
        })
        addView(settingsSection("Almacenamiento y privacidad") {
            addView(Button(context).apply {
                text = "Reiniciar libros…"; isAllCaps = false
                setOnClickListener { startActivity(Intent(this@SettingsActivity, ResetBooksActivity::class.java)) }
            })
            addView(description("Los libros EPUB permanecen en el dispositivo. La aplicación no envía archivos ni incluye telemetría."))
        })
    }

    private fun googleAccountPanel(): View = driveSettingsSection.createPanel()







    private fun modeSpinner(key: String, default: String) = LimitedHeightSpinner(this).apply {
        val preferences = readerSettings.preferences
        adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, readingModes)
        setSelection(readingModes.indexOf(preferences.getString(key, default)).coerceAtLeast(0))
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                preferences.edit().putString(key, readingModes[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun globalThemeSpinner() = LimitedHeightSpinner(this).apply {
        adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, ReadingThemePalette.names)
        setSelection(ReadingThemePalette.names.indexOf(readerSettings.theme).coerceAtLeast(0))
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                readerSettings.theme = ReadingThemePalette.names[position]
                if (readerSettings.menuColorMode == "theme") AppThemePalette.apply(this@SettingsActivity)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun screenTimeoutSpinner() = LimitedHeightSpinner(this).apply {
        val values = intArrayOf(2, 3, 5, 10, 15)
        adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, values.map { "$it minutos" })
        setSelection(values.indexOf(readerSettings.screenTimeoutMinutes).coerceAtLeast(0))
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                readerSettings.screenTimeoutMinutes = values[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun menuColorSpinner(onModeChanged: (String) -> Unit) = LimitedHeightSpinner(this).apply {
        val labels = arrayOf("Tema claro", "Tema oscuro", "Acorde al tema actual", "Personalizado")
        val values = arrayOf("light", "dark", "theme", "custom")
        adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, labels.toList())
        setSelection(values.indexOf(normalizedMenuColorMode()).coerceAtLeast(0))
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                readerSettings.menuColorMode = values[position]; onModeChanged(values[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun normalizedMenuColorMode(): String {
        val stored = readerSettings.menuColorMode
        if (!stored.startsWith("theme:")) return stored
        val background = ReadingThemePalette.colors(stored.removePrefix("theme:")).first
        return if (androidx.core.graphics.ColorUtils.calculateLuminance(background) < .45) "dark" else "light"
    }

    private fun spenActionSpinner(gesture: SpenControlPreferences.Gesture) = LimitedHeightSpinner(this).apply {
        val preferences = readerSettings.preferences
        adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, SpenControlPreferences.actionLabels)
        val selectedValue = preferences.getString(gesture.preferenceKey, gesture.defaultAction)
        setSelection(SpenControlPreferences.actionValues.indexOf(selectedValue).coerceAtLeast(0))
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                preferences.edit().putString(gesture.preferenceKey, SpenControlPreferences.actionValues[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun restoreDefaultSpenActions() {
        val editor = readerSettings.preferences.edit()
        SpenControlPreferences.gestures.forEach { editor.putString(it.preferenceKey, it.defaultAction) }
        editor.apply()
    }

    private fun diagnoseSpen(result: TextView) {
        val remote = SpenRemote.getInstance()
        val device = "${Build.MANUFACTURER} ${Build.MODEL}"
        val buttonAvailable = runCatching { remote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_BUTTON) }.getOrDefault(false)
        val motionAvailable = runCatching { remote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_AIR_MOTION) }.getOrDefault(false)
        result.text = "$device\nBotón remoto: ${yesNo(buttonAvailable)}\nMovimiento aéreo: ${yesNo(motionAvailable)}\nConectando…"
        if (!buttonAvailable && !motionAvailable) {
            result.append("\nEl dispositivo o el S Pen no ofrece funciones remotas BLE."); return
        }
        runCatching {
            if (remote.isConnected) remote.disconnect(this@SettingsActivity)
            remote.connect(this@SettingsActivity, object : SpenRemote.ConnectionResultCallback {
                override fun onSuccess(manager: SpenUnitManager) {
                    runOnUiThread {
                        result.text = "$device\nBotón remoto: ${yesNo(buttonAvailable)}\nMovimiento aéreo: ${yesNo(motionAvailable)}\nConexión al framework: Correcta"
                        runCatching { remote.disconnect(this@SettingsActivity) }
                    }
                }
                override fun onFailure(error: Int) { runOnUiThread {
                    result.text = "$device\nBotón remoto: ${yesNo(buttonAvailable)}\nMovimiento aéreo: ${yesNo(motionAvailable)}\nConexión rechazada. Código: $error"
                } }
            })
        }.onFailure { result.append("\nError: ${it.javaClass.simpleName}: ${it.message.orEmpty()}") }
    }

    private fun yesNo(value: Boolean) = if (value) "Disponible" else "No disponible"

    private fun hexColorEditor(preferenceKey: String, defaultColor: String): View {
        val preferences = readerSettings.preferences
        val binding = ViewHexColorEditorBinding.inflate(layoutInflater)
        val initialColor = parseColor(preferences.getString(preferenceKey, defaultColor))
        val preview = binding.colorPreview.apply {
            if (preferenceKey == "menu_custom_color") setCustomMenuColorPreview(this, initialColor)
            else setBackgroundColor(initialColor)
        }
        val input = binding.hexInput.apply {
            hint = defaultColor.removePrefix("#")
            setText(preferences.getString(preferenceKey, defaultColor)?.removePrefix("#"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(8), InputFilter { source, _, _, _, _, _ -> source.filter { it in "0123456789abcdefABCDEF" } })
        }
        var pendingColor = parseColor(preferences.getString(preferenceKey, defaultColor))
        var updating = false
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString().orEmpty()
                if (updating || text.length !in setOf(6, 8)) return
                val normalized = if (text.length == 6) "FF$text" else text
                runCatching { Color.parseColor("#$normalized") }.onSuccess { color ->
                    pendingColor = color
                    if (preferenceKey == "menu_custom_color") setCustomMenuColorPreview(preview, color) else preview.setBackgroundColor(color)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        preview.setOnClickListener {
            KvColorPickerOverlay.show(this, pendingColor) { color ->
                pendingColor = color
                val hexadecimal = String.format("#%08X", color)
                updating = true
                input.setText(hexadecimal.removePrefix("#")); input.setSelection(input.length())
                updating = false
                if (preferenceKey == "menu_custom_color") setCustomMenuColorPreview(preview, color) else preview.setBackgroundColor(color)
                preferences.edit().putString(preferenceKey, hexadecimal).apply()
                if (preferenceKey == "menu_custom_color") AppThemePalette.apply(this)
                Toast.makeText(this, "Color aplicado", Toast.LENGTH_SHORT).show()
            }
        }
        return binding.root
    }

    private fun parseColor(value: String?) = runCatching { Color.parseColor(value) }.getOrDefault(0x665A7D9A)

    private fun setCustomMenuColorPreview(preview: View, color: Int) {
        val opaqueColor = if (Color.alpha(color) == 255) color else androidx.core.graphics.ColorUtils.compositeColors(
            color, AppThemePalette.current(this).card
        )
        preview.background = GradientDrawable().apply {
            setColor(color)
            setStroke(dp(2), AppThemePalette.textFor(opaqueColor))
            cornerRadius = dp(9).toFloat()
        }
        preview.elevation = dp(1).toFloat()
    }

    private fun settingsSection(title: String?, build: LinearLayout.() -> Unit): View {
        val binding = ViewSettingsSectionBinding.inflate(layoutInflater)
        binding.sectionTitle.apply {
            text = title.orEmpty()
            visibility = if (title.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        binding.contentContainer.apply(build)
        binding.contentContainer.elevation = dp(2).toFloat()
        AppThemePalette.markCard(binding.contentContainer)
        return binding.root
    }

    private fun settingsField(label: String, control: View): View {
        val binding = ViewSettingsFieldBinding.inflate(layoutInflater)
        binding.fieldLabel.text = label
        binding.controlContainer.addView(control, FrameLayout.LayoutParams(-1, -2))
        return binding.root
    }
    private fun description(value: String) = TextView(this).apply {
        text = value; setTextColor(Color.DKGRAY); setPadding(dp(2), dp(4), dp(2), dp(7))
    }
    private fun applyInsets(view: View) {
        val originalLeft = view.paddingLeft; val originalTop = view.paddingTop
        val originalRight = view.paddingRight; val originalBottom = view.paddingBottom
        view.setOnApplyWindowInsetsListener { target, insets ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                target.setPadding(originalLeft + bars.left, originalTop + bars.top, originalRight + bars.right, originalBottom + bars.bottom)
            }; insets
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { private const val EXTRA_ADVANCED_SYNC = "advanced_sync_settings" }
}
