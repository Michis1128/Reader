package com.michis.reader.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderSettingsRepositoryTest {
    private lateinit var settings: ReaderSettingsRepository

    @Before
    fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settings = ReaderSettingsRepository.get(context)
        settings.preferences.edit().clear().commit()
    }

    @After
    fun cleanUp() {
        settings.preferences.edit().clear().commit()
    }

    @Test
    fun legacyFontSizeRemainsAvailable() {
        settings.preferences.edit().putFloat("font_size", 24f).commit()

        assertEquals(24f, settings.fontSizeDp, 0f)
    }

    @Test
    fun fontSizeIsLimitedToSupportedRange() {
        settings.fontSizeDp = 200f
        assertEquals(ReaderSettingsRepository.MAXIMUM_FONT_SIZE_DP, settings.fontSizeDp, 0f)

        settings.fontSizeDp = 2f
        assertEquals(ReaderSettingsRepository.MINIMUM_FONT_SIZE_DP, settings.fontSizeDp, 0f)
    }

    @Test
    fun unsupportedScreenTimeoutFallsBackToDefault() {
        settings.preferences.edit()
            .putInt(ReaderSettingsRepository.KEY_SCREEN_TIMEOUT_MINUTES, 99)
            .commit()

        assertEquals(ReaderSettingsRepository.DEFAULT_SCREEN_TIMEOUT_MINUTES, settings.screenTimeoutMinutes)
    }

    @Test
    fun newInstallUsesReducedPageMarginsByDefault() {
        assertEquals(PageMarginMode.REDUCED, settings.pageMarginMode)
        assertEquals(0.5, settings.pageMarginMode.horizontalFactor, 0.0)
    }

    @Test
    fun legacyPageMarginChoiceIsPreserved() {
        settings.preferences.edit().putBoolean("page_margins", true).commit()
        assertEquals(PageMarginMode.NORMAL, settings.pageMarginMode)

        settings.preferences.edit().putBoolean("page_margins", false).commit()
        assertEquals(PageMarginMode.CUSTOM, settings.pageMarginMode)
        assertEquals(0f, settings.customPageMarginLeftDp, 0f)
        assertEquals(0f, settings.customPageMarginRightDp, 0f)
    }

    @Test
    fun customPageMarginsAreLimitedToSupportedRange() {
        settings.customPageMarginTopDp = 200f
        settings.customPageMarginBottomDp = -20f

        assertEquals(ReaderSettingsRepository.MAXIMUM_CUSTOM_PAGE_MARGIN_DP, settings.customPageMarginTopDp, 0f)
        assertEquals(ReaderSettingsRepository.MINIMUM_CUSTOM_PAGE_MARGIN_DP, settings.customPageMarginBottomDp, 0f)
    }
}
