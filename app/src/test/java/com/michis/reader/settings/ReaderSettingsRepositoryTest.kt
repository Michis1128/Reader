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
}
