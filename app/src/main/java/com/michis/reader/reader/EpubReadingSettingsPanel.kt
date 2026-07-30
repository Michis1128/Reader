@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import com.michis.reader.R
import com.michis.reader.settings.*
import com.michis.reader.theme.*
import com.michis.reader.ui.LimitedHeightSpinner

import android.content.Intent
import android.content.pm.ActivityInfo
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.TextAlign

/** Construye el menu Aa del lector EPUB y persiste sus controles globales. */
class EpubReadingSettingsPanel(
    private val activity: FragmentActivity,
    private val settings: ReaderSettingsRepository,
    private val submitPreferences: (EpubPreferences) -> Unit,
    private val selectTheme: (Int) -> Unit,
    private val selectFont: () -> Unit,
    private val closePanel: () -> Unit
) {
    fun create(): View {
        val preferences = settings.preferences
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
            tag = ReaderMenuTags.SURFACE
            addView(header("Lectura", "Cerrar", closePanel))
            addView(family("Texto y tipografía") {
                addView(label("Tamaño de letra (dp)"))
                addView(numberStepper(settings.fontSizeDp.toDouble(), 8.0, 72.0, 1.0) { value ->
                    settings.fontSizeDp = value.toFloat()
                    submitPreferences(EpubPreferences(fontSize = value / 16.0))
                })
                addView(label("Tipo de fuente"))
                addView(Button(activity).apply {
                    text = "Seleccionar fuente…"; isAllCaps = false; setOnClickListener { selectFont() }
                })
                addView(label("Grosor de fuente"))
                val currentWeight = preferences.getFloat(KEY_FONT_WEIGHT, 1f).coerceIn(0.5f, 2f)
                addView(slider(100, (((currentWeight - 0.5f) / 1.5f) * 100).toInt()) { progress ->
                    val value = 0.5 + progress / 100.0 * 1.5
                    preferences.edit().putFloat(KEY_FONT_WEIGHT, value.toFloat()).apply()
                    submitPreferences(EpubPreferences(fontWeight = value))
                })
                addView(label("Interlineado"))
                addView(numberStepper(settings.lineHeight.toDouble(), 1.0, 2.5, .1) { value ->
                    settings.lineHeight = value.toFloat(); submitPreferences(EpubPreferences(lineHeight = value))
                })
                addView(label("Alineación"))
                addView(spinner(arrayOf("Justificado", "Izquierda", "Centro", "Derecha"), settings.textAlignment) { index ->
                    settings.textAlignment = index
                    submitPreferences(EpubPreferences(textAlign = arrayOf(TextAlign.JUSTIFY, TextAlign.START, TextAlign.CENTER, TextAlign.END)[index]))
                })
            })
            addView(family("Tema y página") {
                addView(label("Tema"))
                addView(spinner(ReadingThemePalette.names, ReadingThemePalette.names.indexOf(settings.theme).coerceAtLeast(0), selectTheme))
                addView(label("Cambio de página"))
                addView(spinner(arrayOf("Paginado", "Desplazamiento continuo"), if (preferences.getBoolean(KEY_CONTINUOUS_SCROLL, false)) 1 else 0) { index ->
                    preferences.edit().putBoolean(KEY_CONTINUOUS_SCROLL, index == 1).apply()
                    submitPreferences(EpubPreferences(scroll = index == 1))
                })
                addView(CheckBox(activity).apply {
                    text = "Animar cambios de página"; isChecked = preferences.getBoolean(KEY_PAGE_ANIMATIONS, true)
                    setOnCheckedChangeListener { _, checked ->
                        preferences.edit().putBoolean(KEY_PAGE_ANIMATIONS, checked).apply()
                        if (checked && preferences.getBoolean(KEY_CONTINUOUS_SCROLL, false)) {
                            Toast.makeText(activity, "La animación se muestra en modo Paginado", Toast.LENGTH_LONG).show()
                        }
                    }
                })
                addView(CheckBox(activity).apply {
                    text = "Mostrar dos páginas en horizontal"
                    isChecked = preferences.getBoolean(KEY_TWO_PAGES_LANDSCAPE,
                        activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                    setOnCheckedChangeListener { _, checked ->
                        preferences.edit().putBoolean(KEY_TWO_PAGES_LANDSCAPE, checked).apply()
                        submitPreferences(EpubPreferences(
                            columnCount = if (checked) ColumnCount.TWO else ColumnCount.ONE,
                            spread = if (checked) Spread.ALWAYS else Spread.NEVER
                        ))
                    }
                })
                addView(CheckBox(activity).apply {
                    text = "Activar márgenes de página"; isChecked = preferences.getBoolean(KEY_PAGE_MARGINS, true)
                    setOnCheckedChangeListener { _, checked ->
                        preferences.edit().putBoolean(KEY_PAGE_MARGINS, checked).apply()
                        submitPreferences(EpubPreferences(pageMargins = if (checked) 1.0 else 0.0))
                    }
                })
            })
            addView(family("Pantalla") {
                addView(label("Orientación"))
                addView(spinner(arrayOf("Automática", "Vertical", "Horizontal"), preferences.getInt(KEY_ORIENTATION, 0)) { index ->
                    preferences.edit().putInt(KEY_ORIENTATION, index).apply()
                    activity.requestedOrientation = when (index) {
                        1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                })
            })
            addView(family("Aplicación") {
                addView(Button(activity).apply {
                    text = "Configuración general"; isAllCaps = false
                    setOnClickListener { activity.startActivity(Intent(activity, SettingsActivity::class.java)) }
                })
            })
        }
        return ScrollView(activity).apply { isFillViewport = true; addView(content) }
    }

    private fun numberStepper(initial: Double, minimum: Double, maximum: Double, step: Double, changed: (Double) -> Unit): View {
        var value = initial.coerceIn(minimum, maximum)
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(format(value)); gravity = Gravity.CENTER; setSingleLine()
        }
        fun update(next: Double) {
            value = next.coerceIn(minimum, maximum); input.setText(format(value)); changed(value)
        }
        input.setOnFocusChangeListener { _, focused -> if (!focused) update(input.text.toString().toDoubleOrNull() ?: value) }
        return LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Button(activity).apply { text = "−"; setOnClickListener { update(value - step) } }, LinearLayout.LayoutParams(dp(54), dp(50)))
            addView(input, LinearLayout.LayoutParams(0, dp(50), 1f))
            addView(Button(activity).apply { text = "+"; setOnClickListener { update(value + step) } }, LinearLayout.LayoutParams(dp(54), dp(50)))
        }
    }

    private fun slider(maximum: Int, initial: Int, changed: (Int) -> Unit) = SeekBar(activity).apply {
        max = maximum; progress = initial.coerceIn(0, maximum)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) changed(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun spinner(options: Array<String>, selectedIndex: Int, selected: (Int) -> Unit) = LimitedHeightSpinner(activity).apply {
        adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, options.toList())
        setSelection(selectedIndex.coerceIn(options.indices))
        onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = selected(position)
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun header(title: String, actionText: String, action: () -> Unit) = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(activity).apply { text = title; textSize = 24f; typeface = android.graphics.Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, dp(56), 1f))
        addView(Button(activity).apply { text = actionText; isAllCaps = false; setOnClickListener { action() } })
    }

    private fun label(value: String) = TextView(activity).apply {
        text = value; textSize = 16f; setPadding(dp(2), dp(12), dp(2), dp(6))
    }

    private fun family(title: String, build: LinearLayout.() -> Unit) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; tag = ReaderMenuTags.CARD
        setPadding(dp(14), dp(10), dp(14), dp(14)); setBackgroundResource(R.drawable.rounded_panel); elevation = dp(2).toFloat()
        addView(TextView(activity).apply { text = title; textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(4)) })
        build()
    }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) } }

    private fun format(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val KEY_FONT_WEIGHT = "font_weight"
        const val KEY_CONTINUOUS_SCROLL = "continuous_scroll"
        const val KEY_PAGE_ANIMATIONS = "page_turn_animations"
        const val KEY_TWO_PAGES_LANDSCAPE = "two_pages_landscape"
        const val KEY_PAGE_MARGINS = "page_margins"
        const val KEY_ORIENTATION = "reader_orientation"
    }
}

object ReaderMenuTags {
    const val SURFACE = "reader_menu_surface"
    const val CARD = "reader_menu_card"
}
