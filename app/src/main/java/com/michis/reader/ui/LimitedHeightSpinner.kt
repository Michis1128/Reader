package com.michis.reader.ui

import com.michis.reader.theme.AppThemePalette

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.SpinnerAdapter
import android.widget.TextView

/** Spinner cuyo desplegable muestra como máximo cuatro opciones antes de desplazarse. */
class LimitedHeightSpinner(context: Context) : Spinner(context, MODE_DROPDOWN) {
    private var optionsPopup: PopupWindow? = null

    override fun performClick(): Boolean {
        val currentAdapter = adapter ?: return false
        if (currentAdapter.count == 0) return false
        optionsPopup?.dismiss()

        val activity = context as? android.app.Activity ?: return super.performClick()
        val palette = AppThemePalette.current(activity)
        val rowHeight = dp(52)
        val visibleRows = minOf(currentAdapter.count, MAX_VISIBLE_OPTIONS)
        val list = ListView(context).apply {
            adapter = PopupOptionsAdapter(currentAdapter, palette.surface)
            choiceMode = ListView.CHOICE_MODE_SINGLE
            setItemChecked(selectedItemPosition, true)
            divider = ColorDrawable(palette.outline)
            dividerHeight = maxOf(1, dp(1))
            setBackgroundColor(palette.surface)
            isVerticalScrollBarEnabled = currentAdapter.count > MAX_VISIBLE_OPTIONS
            setOnItemClickListener { _, _, position, _ ->
                this@LimitedHeightSpinner.setSelection(position)
                optionsPopup?.dismiss()
            }
        }
        val popupHeight = rowHeight * visibleRows + dp(maxOf(0, visibleRows - 1))
        optionsPopup = PopupWindow(list, maxOf(width, dp(220)), popupHeight, true).apply {
            setBackgroundDrawable(AppThemePalette.cardBackground(activity, 12f))
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setOnDismissListener { optionsPopup = null }
            showAsDropDown(this@LimitedHeightSpinner, 0, dp(2), Gravity.START)
        }
        return true
    }

    override fun onDetachedFromWindow() {
        optionsPopup?.dismiss()
        super.onDetachedFromWindow()
    }

    private inner class PopupOptionsAdapter(
        private val source: SpinnerAdapter,
        private val backgroundColor: Int
    ) : BaseAdapter() {
        override fun getCount() = source.count
        override fun getItem(position: Int) = source.getItem(position)
        override fun getItemId(position: Int) = source.getItemId(position)
        override fun getView(position: Int, recycledView: View?, parent: ViewGroup): View {
            val option = source.getDropDownView(position, recycledView, parent)
            option.minimumHeight = dp(52)
            option.setBackgroundColor(backgroundColor)
            styleOptionText(option)
            return option
        }

        private fun styleOptionText(view: View) {
            if (view is TextView) {
                view.setTextColor(AppThemePalette.textFor(backgroundColor))
                view.gravity = Gravity.CENTER_VERTICAL
                view.setPadding(dp(16), dp(8), dp(16), dp(8))
            } else if (view is ViewGroup) {
                repeat(view.childCount) { styleOptionText(view.getChildAt(it)) }
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MAX_VISIBLE_OPTIONS = 4
    }
}
