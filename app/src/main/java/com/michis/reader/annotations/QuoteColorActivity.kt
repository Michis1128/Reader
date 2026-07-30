package com.michis.reader.annotations

import com.michis.reader.R
import com.michis.reader.data.*
import com.michis.reader.settings.ReaderSettingsRepository
import com.michis.reader.theme.*

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity

class QuoteColorActivity : ComponentActivity() {
    private var selectedColor = Color.rgb(255, 213, 79)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedColor = runCatching {
            val settings = ReaderSettingsRepository.get(this)
            Color.parseColor(settings.preferences.getString(
                ReaderSettingsRepository.KEY_QUOTE_DEFAULT_COLOR,
                ReaderSettingsRepository.DEFAULT_QUOTE_COLOR
            ))
        }.getOrDefault(Color.rgb(255, 213, 79))
        val selectedText = intent.getStringExtra(EXTRA_TEXT).orEmpty().trim()
        if (selectedText.isBlank()) { finish(); return }
        val noteInput = EditText(this).apply { hint = "Nota opcional"; minLines = 2 }
        val colorPreview = TextView(this).apply {
            text = "Color del resaltado"; gravity = Gravity.CENTER; setTextColor(Color.BLACK); setBackgroundColor(selectedColor)
        }
        colorPreview.setOnClickListener {
            KvColorPickerOverlay.show(this, selectedColor) { color ->
                selectedColor = color
                colorPreview.setBackgroundColor(color)
                colorPreview.setTextColor(if (Color.luminance(color) < .45f) Color.WHITE else Color.BLACK)
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(18)); AppThemePalette.markBackground(this)
            addView(TextView(context).apply { text = "Fragmento"; textSize = 17f; setPadding(dp(2), dp(16), 0, dp(7)) })
            addView(TextView(context).apply { text = selectedText; textSize = 18f; setPadding(dp(16), dp(14), dp(16), dp(14)); setBackgroundResource(R.drawable.rounded_panel) })
            addView(TextView(context).apply { text = "Apariencia y nota"; textSize = 17f; setPadding(dp(2), dp(16), 0, dp(7)) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); setBackgroundResource(R.drawable.rounded_panel)
                addView(colorPreview, LinearLayout.LayoutParams(-1, dp(48)))
                addView(noteInput, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(Button(context).apply {
                    text = "Aplicar color y guardar cita"; isAllCaps = false
                    setOnClickListener {
                        ReaderDatabase.getInstance(this@QuoteColorActivity).addAnnotation(
                            intent.getLongExtra(EXTRA_DOCUMENT_IDENTIFIER, -1), "cita", selectedText,
                            noteInput.text.toString(), selectedColor, intent.getIntExtra(EXTRA_LOCATION, 0),
                            intent.getIntExtra(EXTRA_PAGE_NUMBER, 0)
                        )
                        Toast.makeText(context, "Cita guardada", Toast.LENGTH_SHORT).show(); finish()
                    }
                }, LinearLayout.LayoutParams(-1, dp(54)))
            })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; AppThemePalette.markBackground(this)
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(5), dp(18), dp(5)); elevation = dp(5).toFloat()
                AppThemePalette.markSurface(this)
                addView(Button(context).apply { text = "‹"; contentDescription = "Regresar"; setOnClickListener { finish() } })
                addView(TextView(context).apply { text = "Nueva cita"; textSize = 27f }, LinearLayout.LayoutParams(0, dp(60), 1f))
            })
            addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
            applyInsets(this)
        }
        setContentView(root)
        AppThemePalette.apply(this)
        KvColorPickerOverlay.show(this, selectedColor) { color ->
            selectedColor = color
            colorPreview.setBackgroundColor(color)
            colorPreview.setTextColor(if (Color.luminance(color) < .45f) Color.WHITE else Color.BLACK)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun applyInsets(view: View) {
        val left = view.paddingLeft; val top = view.paddingTop; val right = view.paddingRight; val bottom = view.paddingBottom
        view.setOnApplyWindowInsetsListener { target, insets ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                target.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            }; insets
        }
    }

    companion object {
        const val EXTRA_DOCUMENT_IDENTIFIER = "document_identifier"
        const val EXTRA_TEXT = "selected_text"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_PAGE_NUMBER = "page_number"
    }
}
