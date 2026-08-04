package com.michis.reader.reader

import android.graphics.Rect
import android.view.View

internal enum class ReaderPanel { SETTINGS, CONTENTS, SEARCH }

/** Mantiene un solo panel flotante activo y centraliza su cierre por toque exterior. */
internal class ReaderPanelCoordinator(
    private val panels: Map<ReaderPanel, View>,
    private val panelClosed: (ReaderPanel) -> Unit = {}
) {
    fun toggle(panel: ReaderPanel): Boolean {
        val shouldOpen = panels[panel]?.visibility != View.VISIBLE
        closeAll(except = if (shouldOpen) panel else null)
        panels[panel]?.visibility = if (shouldOpen) View.VISIBLE else View.GONE
        if (!shouldOpen) panelClosed(panel)
        return shouldOpen
    }

    fun close(panel: ReaderPanel): Boolean {
        val view = panels[panel] ?: return false
        if (view.visibility != View.VISIBLE) return false
        view.visibility = View.GONE
        panelClosed(panel)
        return true
    }

    fun closeOutside(rawX: Int, rawY: Int): Boolean {
        val active = panels.entries.firstOrNull { it.value.visibility == View.VISIBLE } ?: return false
        val bounds = Rect()
        active.value.getGlobalVisibleRect(bounds)
        if (bounds.contains(rawX, rawY)) return false
        return close(active.key)
    }

    private fun closeAll(except: ReaderPanel?) {
        panels.forEach { (panel, view) ->
            if (panel != except && view.visibility == View.VISIBLE) {
                view.visibility = View.GONE
                panelClosed(panel)
            }
        }
    }
}
