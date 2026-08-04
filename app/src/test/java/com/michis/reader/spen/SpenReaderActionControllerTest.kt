package com.michis.reader.spen

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.michis.reader.settings.ReaderSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpenReaderActionControllerTest {
    private lateinit var settings: ReaderSettingsRepository

    @Before
    fun prepareSettings() {
        settings = ReaderSettingsRepository.get(ApplicationProvider.getApplicationContext<Context>())
        settings.preferences.edit().clear().commit()
    }

    @After
    fun clearSettings() {
        settings.preferences.edit().clear().commit()
    }

    @Test
    fun nativeKeyMappingAndCustomizedActionAreKeptOutsideTheActivity() {
        var bookmarks = 0
        var interactions = 0
        settings.preferences.edit()
            .putString(SpenControlPreferences.click.preferenceKey, SpenControlPreferences.BOOKMARK)
            .commit()
        val controller = SpenReaderActionController(
            settings,
            SpenReaderActionController.Actions(
                nextPage = {}, previousPage = {}, toggleControls = {},
                toggleBookmark = { bookmarks++ }, increaseText = {}, decreaseText = {},
                toggleQuickTheme = {}, addSelectionToDictionary = {}, addSelectionAsQuote = {},
                interactionCompleted = { interactions++ }
            )
        )

        assertEquals(SpenControlPreferences.click, controller.gestureForKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertNull(controller.gestureForKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
        controller.execute(SpenControlPreferences.click)

        assertEquals(1, bookmarks)
        assertEquals(1, interactions)
    }
}
