package com.michis.reader.input

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
class ReaderHardwareInputPreferencesTest {
    private lateinit var preferences: ReaderHardwareInputPreferences
    private val sharedPreferences by lazy {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("hardware_input_test", Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        sharedPreferences.edit().clear().commit()
        preferences = ReaderHardwareInputPreferences(sharedPreferences)
    }

    @After
    fun tearDown() {
        sharedPreferences.edit().clear().commit()
    }

    @Test
    fun controlsUseRequestedDefaults() {
        assertEquals(ReaderHardwareAction.NEXT_PAGE, preferences.actionFor(ReaderHardwareControl.BUTTON_CLICK))
        assertEquals(ReaderHardwareAction.PREVIOUS_PAGE, preferences.actionFor(ReaderHardwareControl.BUTTON_DOUBLE_CLICK))
        assertEquals(ReaderHardwareAction.INCREASE_TEXT_SIZE, preferences.actionFor(ReaderHardwareControl.SWIPE_UP))
        assertEquals(ReaderHardwareAction.DECREASE_TEXT_SIZE, preferences.actionFor(ReaderHardwareControl.SWIPE_DOWN))
        assertEquals(ReaderHardwareAction.PREVIOUS_PAGE, preferences.actionFor(ReaderHardwareControl.SWIPE_LEFT))
        assertEquals(ReaderHardwareAction.NEXT_PAGE, preferences.actionFor(ReaderHardwareControl.SWIPE_RIGHT))
        assertEquals(ReaderHardwareAction.TOGGLE_READING_THEME, preferences.actionFor(ReaderHardwareControl.CIRCLE_COUNTERCLOCKWISE))
        assertEquals(ReaderHardwareAction.TOGGLE_BOOKMARK, preferences.actionFor(ReaderHardwareControl.CIRCLE_CLOCKWISE))
    }

    @Test
    fun restoreDefaultsRemovesCustomMappings() {
        preferences.setAction(ReaderHardwareControl.BUTTON_CLICK, ReaderHardwareAction.NONE)
        preferences.restoreDefaults()

        assertEquals(ReaderHardwareAction.NEXT_PAGE, preferences.actionFor(ReaderHardwareControl.BUTTON_CLICK))
    }
}
