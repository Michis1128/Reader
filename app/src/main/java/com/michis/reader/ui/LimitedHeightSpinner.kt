package com.michis.reader.ui

import com.michis.reader.theme.AppThemePalette

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.SpinnerAdapter
import android.widget.TextView

/** Spinner cuyo desplegable muestra como máximo cuatro opciones antes de desplazarse. */
class LimitedHeightSpinner @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyleAttribute: Int = android.R.attr.spinnerStyle
) : Spinner(context, attributes, defaultStyleAttribute, MODE_DROPDOWN) {
    private var optionsPopup: PopupWindow? = null
    private var optionsList: ListView? = null
    private var touchStartedAt = 0L

    var keepPopupOpenOnSelection: Boolean = false
    var onPopupVisibilityChanged: ((Boolean) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touchStartedAt = event.eventTime
            MotionEvent.ACTION_UP -> if (event.eventTime - touchStartedAt >= ViewConfiguration.getLongPressTimeout()) {
                isPressed = false
                cancelLongPress()
                return true
            }
            MotionEvent.ACTION_CANCEL -> touchStartedAt = 0L
        }
        return super.onTouchEvent(event)
    }

    override fun performLongClick(): Boolean = true

    override fun performClick(): Boolean {
        val currentAdapter = adapter ?: return false
        if (currentAdapter.count == 0) return false
        optionsPopup?.dismiss()

        val activity = context as? android.app.Activity ?: return super.performClick()
        val rowHeight = dp(52)
        val visibleRows = minOf(currentAdapter.count, MAX_VISIBLE_OPTIONS)
        val list = ListView(context).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
            setItemChecked(selectedItemPosition, true)
            dividerHeight = maxOf(1, dp(1))
            isVerticalScrollBarEnabled = currentAdapter.count > MAX_VISIBLE_OPTIONS
            setOnItemClickListener { _, _, position, _ ->
                this@LimitedHeightSpinner.setSelection(position)
                setItemChecked(position, true)
                if (keepPopupOpenOnSelection) refreshOpenPopupTheme() else optionsPopup?.dismiss()
            }
        }
        optionsList = list
        styleOptionsList(list, currentAdapter)
        val popupHeight = rowHeight * visibleRows + dp(maxOf(0, visibleRows - 1))
        optionsPopup = PopupWindow(list, maxOf(width, dp(220)), popupHeight, true).apply {
            setBackgroundDrawable(AppThemePalette.cardBackground(activity, 12f))
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setOnDismissListener {
                optionsPopup = null
                optionsList = null
                onPopupVisibilityChanged?.invoke(false)
            }
            showAsDropDown(this@LimitedHeightSpinner, 0, dp(2), Gravity.START)
        }
        onPopupVisibilityChanged?.invoke(true)
        return true
    }

    fun refreshOpenPopupTheme() {
        val list = optionsList ?: return
        val currentAdapter = adapter ?: return
        styleOptionsList(list, currentAdapter)
        val activity = context as? android.app.Activity ?: return
        optionsPopup?.setBackgroundDrawable(AppThemePalette.cardBackground(activity, 12f))
        optionsPopup?.update()
    }

    private fun styleOptionsList(list: ListView, currentAdapter: SpinnerAdapter) {
        val activity = context as? android.app.Activity ?: return
        val palette = AppThemePalette.current(activity)
        list.adapter = PopupOptionsAdapter(currentAdapter, palette.surface)
        list.divider = ColorDrawable(palette.outline)
        list.setBackgroundColor(palette.surface)
        list.setItemChecked(selectedItemPosition, true)
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
