package com.michis.reader.reader

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.graphics.ColorUtils

/** Encapsula inmersión, barras del sistema, notch y tiempo de pantalla activa. */
internal class ReaderWindowController(private val activity: Activity) {
    private val inactivityHandler = Handler(Looper.getMainLooper())

    @Suppress("DEPRECATION")
    fun configureEdgeToEdge() {
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.window.setDecorFitsSystemWindows(false)
    }

    fun resetScreenTimeout(minutes: Int) {
        inactivityHandler.removeCallbacksAndMessages(null)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        inactivityHandler.postDelayed(
            { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) },
            minutes * 60_000L
        )
    }

    fun stopScreenTimeout() {
        inactivityHandler.removeCallbacksAndMessages(null)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun setSystemBarsVisible(visible: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        activity.window.insetsController?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (visible) controller.show(WindowInsets.Type.systemBars())
            else controller.hide(WindowInsets.Type.systemBars())
        }
    }

    fun applySystemBarPadding(
        root: View,
        topControls: View,
        bottomControls: View,
        settingsPanel: View,
        contentsPanel: View
    ) {
        val originalTopPadding = topControls.paddingTop
        val originalBottomPadding = bottomControls.paddingBottom
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                topControls.setPadding(
                    topControls.paddingLeft,
                    originalTopPadding + bars.top,
                    topControls.paddingRight,
                    topControls.paddingBottom
                )
                bottomControls.setPadding(
                    bottomControls.paddingLeft,
                    bottomControls.paddingTop,
                    bottomControls.paddingRight,
                    originalBottomPadding + bars.bottom
                )
                settingsPanel.setPadding(settingsPanel.paddingLeft, bars.top, settingsPanel.paddingRight, bars.bottom)
                contentsPanel.setPadding(contentsPanel.paddingLeft, bars.top, contentsPanel.paddingRight, bars.bottom)
            }
            insets
        }
    }

    fun updateSystemBarContrast(background: Int) {
        val useDarkIcons = ColorUtils.calculateLuminance(background) >= .45
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            activity.window.insetsController?.setSystemBarsAppearance(if (useDarkIcons) flags else 0, flags)
        } else {
            @Suppress("DEPRECATION")
            val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = if (useDarkIcons) {
                activity.window.decorView.systemUiVisibility or flags
            } else {
                activity.window.decorView.systemUiVisibility and flags.inv()
            }
        }
    }
}
