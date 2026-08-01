package com.michis.reader.ui

import android.app.Activity
import com.michis.reader.databinding.ViewScreenHeaderBinding
import com.michis.reader.theme.AppThemePalette

/** Inflates the fixed header shared by every non-immersive screen. */
object ScreenHeader {
    fun create(activity: Activity, title: CharSequence, onBack: () -> Unit): ViewScreenHeaderBinding {
        val binding = ViewScreenHeaderBinding.inflate(activity.layoutInflater)
        configure(activity, binding, title, onBack)
        return binding
    }

    fun configure(
        activity: Activity,
        binding: ViewScreenHeaderBinding,
        title: CharSequence,
        onBack: () -> Unit
    ) {
        binding.titleText.text = title
        binding.backButton.setOnClickListener { onBack() }
        binding.root.elevation = 5f * activity.resources.displayMetrics.density
        AppThemePalette.markSurface(binding.root)
    }
}
