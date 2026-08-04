@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import com.michis.reader.databinding.ItemEpubSettingsFamilyBinding
import com.michis.reader.databinding.PanelEpubSettingsBinding
import com.michis.reader.databinding.ViewActionButtonBinding
import com.michis.reader.databinding.ViewEpubSettingsLabelBinding
import com.michis.reader.databinding.ViewEpubSettingsSliderBinding
import com.michis.reader.databinding.ViewEpubSettingsToggleBinding
import com.michis.reader.databinding.ViewNumberStepperBinding
import com.michis.reader.settings.*
import com.michis.reader.theme.*
import com.michis.reader.ui.LimitedHeightSpinner

import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
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
    private lateinit var familyContainer: LinearLayout

    fun create(): View {
        val panelBinding = PanelEpubSettingsBinding.inflate(activity.layoutInflater)
        panelBinding.panelContainer.tag = ReaderMenuTags.SURFACE
        panelBinding.closeButton.setOnClickListener { closePanel() }
        familyContainer = panelBinding.familyContainer
        familyContainer.apply {
            addView(family("Texto y tipografía") {
                addView(label("Tamaño de letra (dp)"))
                addView(numberStepper(settings.fontSizeDp.toDouble(), 8.0, 72.0, 1.0) { value ->
                    settings.fontSizeDp = value.toFloat()
                    submitPreferences(EpubPreferences(fontSize = value / 16.0))
                })
                addView(label("Tipo de fuente"))
                addView(actionButton("Seleccionar fuente…") { selectFont() })
                addView(label("Grosor de fuente"))
                val currentWeight = settings.fontWeight
                addView(slider(100, (((currentWeight - 0.5f) / 1.5f) * 100).toInt()) { progress ->
                    val value = 0.5 + progress / 100.0 * 1.5
                    settings.fontWeight = value.toFloat()
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
                addView(spinner(arrayOf("Paginado", "Desplazamiento continuo"), if (settings.continuousScroll) 1 else 0) { index ->
                    settings.continuousScroll = index == 1
                    submitPreferences(EpubPreferences(scroll = index == 1))
                })
                addView(toggle("Animar cambios de página", settings.pageTurnAnimations) { checked ->
                    settings.pageTurnAnimations = checked
                    if (checked && settings.continuousScroll) {
                        Toast.makeText(activity, "La animación se muestra en modo Paginado", Toast.LENGTH_LONG).show()
                    }
                })
                addView(toggle(
                    "Mostrar dos páginas en horizontal",
                    settings.twoPagesLandscape(
                        activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    )
                ) { checked ->
                    settings.setTwoPagesLandscape(checked)
                    submitPreferences(EpubPreferences(
                        columnCount = if (checked) ColumnCount.TWO else ColumnCount.ONE,
                        spread = if (checked) Spread.ALWAYS else Spread.NEVER
                    ))
                })
            })
            addView(family("Pantalla") {
                addView(label("Orientación"))
                addView(spinner(arrayOf("Automática", "Vertical", "Horizontal"), settings.readerOrientation) { index ->
                    settings.readerOrientation = index
                    activity.requestedOrientation = when (index) {
                        1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                })
                addView(label("Márgenes de página"))
                val marginModes = PageMarginMode.entries
                val customMarginControls = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (settings.pageMarginMode == PageMarginMode.CUSTOM) View.VISIBLE else View.GONE
                    addView(label("Superior (dp)"))
                    addView(numberStepper(settings.customPageMarginTopDp.toDouble(), 0.0, 96.0, 1.0) { value ->
                        settings.customPageMarginTopDp = value.toFloat()
                        submitPreferences(EpubPreferences(pageMargins = PageMarginMode.CUSTOM.horizontalFactor))
                    })
                    addView(label("Inferior (dp)"))
                    addView(numberStepper(settings.customPageMarginBottomDp.toDouble(), 0.0, 96.0, 1.0) { value ->
                        settings.customPageMarginBottomDp = value.toFloat()
                        submitPreferences(EpubPreferences(pageMargins = PageMarginMode.CUSTOM.horizontalFactor))
                    })
                    addView(label("Izquierdo (dp)"))
                    addView(numberStepper(settings.customPageMarginLeftDp.toDouble(), 0.0, 96.0, 1.0) { value ->
                        settings.customPageMarginLeftDp = value.toFloat()
                        submitPreferences(EpubPreferences(pageMargins = PageMarginMode.CUSTOM.horizontalFactor))
                    })
                    addView(label("Derecho (dp)"))
                    addView(numberStepper(settings.customPageMarginRightDp.toDouble(), 0.0, 96.0, 1.0) { value ->
                        settings.customPageMarginRightDp = value.toFloat()
                        submitPreferences(EpubPreferences(pageMargins = PageMarginMode.CUSTOM.horizontalFactor))
                    })
                }
                addView(spinner(
                    marginModes.map(PageMarginMode::displayName).toTypedArray(),
                    marginModes.indexOf(settings.pageMarginMode)
                ) { index ->
                    val selectedMode = marginModes[index]
                    settings.pageMarginMode = selectedMode
                    customMarginControls.visibility = if (selectedMode == PageMarginMode.CUSTOM) View.VISIBLE else View.GONE
                    submitPreferences(EpubPreferences(pageMargins = selectedMode.horizontalFactor))
                })
                addView(customMarginControls)
            })
            addView(family("Aplicación") {
                addView(actionButton("Configuración general") {
                    activity.startActivity(Intent(activity, SettingsActivity::class.java))
                })
            })
        }
        return panelBinding.root
    }

    private fun numberStepper(initial: Double, minimum: Double, maximum: Double, step: Double, changed: (Double) -> Unit): View {
        var value = initial.coerceIn(minimum, maximum)
        val binding = ViewNumberStepperBinding.inflate(activity.layoutInflater)
        val input = binding.valueInput.apply { setText(format(value)) }
        fun update(next: Double) {
            value = next.coerceIn(minimum, maximum); input.setText(format(value)); changed(value)
        }
        input.setOnFocusChangeListener { _, focused -> if (!focused) update(input.text.toString().toDoubleOrNull() ?: value) }
        binding.decreaseButton.setOnClickListener { update(value - step) }
        binding.increaseButton.setOnClickListener { update(value + step) }
        return binding.root
    }

    private fun slider(maximum: Int, initial: Int, changed: (Int) -> Unit) =
        ViewEpubSettingsSliderBinding.inflate(activity.layoutInflater).root.apply {
            max = maximum; progress = initial.coerceIn(0, maximum)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) changed(progress)
                }
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

    private fun actionButton(value: String, action: () -> Unit): View {
        val binding = ViewActionButtonBinding.inflate(activity.layoutInflater)
        binding.actionButton.text = value
        binding.actionButton.setOnClickListener { action() }
        return binding.root
    }

    private fun toggle(value: String, checked: Boolean, changed: (Boolean) -> Unit): View {
        val binding = ViewEpubSettingsToggleBinding.inflate(activity.layoutInflater)
        binding.toggleCheckbox.text = value
        binding.toggleCheckbox.isChecked = checked
        binding.toggleCheckbox.setOnCheckedChangeListener { _, isChecked -> changed(isChecked) }
        return binding.root
    }

    private fun label(value: String) =
        ViewEpubSettingsLabelBinding.inflate(activity.layoutInflater).root.apply { text = value }

    private fun family(title: String, build: LinearLayout.() -> Unit): View {
        val binding = ItemEpubSettingsFamilyBinding.inflate(activity.layoutInflater, familyContainer, false)
        binding.root.tag = ReaderMenuTags.CARD
        binding.root.elevation = dp(2).toFloat()
        binding.familyTitle.text = title
        binding.familyContent.apply(build)
        return binding.root
    }

    private fun format(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

}

object ReaderMenuTags {
    const val SURFACE = "reader_menu_surface"
    const val CARD = "reader_menu_card"
}
