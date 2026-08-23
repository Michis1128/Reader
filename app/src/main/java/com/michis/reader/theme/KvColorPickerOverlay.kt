package com.michis.reader.theme

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.kavi.droid.color.picker.ui.KvColorPickerBottomSheet
import com.michis.reader.theme.compose.MichisReaderComposeTheme

/** Muestra KvColorPicker sobre una pantalla clásica basada en Views. */
object KvColorPickerOverlay {
    @OptIn(ExperimentalMaterial3Api::class)
    fun show(activity: ComponentActivity, initialColor: Int, onColorApplied: (Int) -> Unit) {
        val root = activity.findViewById<FrameLayout>(android.R.id.content)
        root.findViewWithTag<ComposeView>(OVERLAY_TAG)?.let(root::removeView)

        val overlay = ComposeView(activity).apply {
            tag = OVERLAY_TAG
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }
        root.addView(overlay, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        overlay.setContent {
            MichisReaderComposeTheme {
                val isVisible = remember { mutableStateOf(true) }
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                LaunchedEffect(isVisible.value) {
                    if (!isVisible.value) root.post {
                        if (overlay.parent === root) root.removeView(overlay)
                    }
                }

                if (isVisible.value) {
                    KvColorPickerBottomSheet(
                        lastSelectedColor = androidx.compose.ui.graphics.Color(initialColor),
                        showSheet = isVisible,
                        sheetState = sheetState,
                        onColorSelected = { selectedColor ->
                            // La app trabaja con colores ARGB opacos o translúcidos de Android.
                            onColorApplied(selectedColor.toArgb())
                        }
                    )
                }
            }
        }
    }

    private const val OVERLAY_TAG = "kv_color_picker_overlay"
}
