package com.michis.reader.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderHardwareKeyMapperTest {
    @Test
    fun pageDownIdentifiesButtonClick() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN)

        assertEquals(ReaderHardwareControl.BUTTON_CLICK, ReaderHardwareKeyMapper.controlFor(event.keyCode, event))
    }

    @Test
    fun pageUpIdentifiesButtonDoubleClick() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_UP)

        assertEquals(ReaderHardwareControl.BUTTON_DOUBLE_CLICK, ReaderHardwareKeyMapper.controlFor(event.keyCode, event))
    }

    @Test
    fun repeatedAndReleasedEventsAreIgnored() {
        val repeated = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, 1)
        val released = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN)

        assertNull(ReaderHardwareKeyMapper.controlFor(repeated.keyCode, repeated))
        assertNull(ReaderHardwareKeyMapper.controlFor(released.keyCode, released))
    }

    @Test
    fun unrelatedKeysAreIgnored() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)

        assertNull(ReaderHardwareKeyMapper.controlFor(event.keyCode, event))
    }

    @Test
    fun supportedKeysAreConsumedEvenWhenEventMustNotExecute() {
        assertTrue(ReaderHardwareKeyMapper.supports(KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(ReaderHardwareKeyMapper.supports(KeyEvent.KEYCODE_PAGE_DOWN))
        assertTrue(ReaderHardwareKeyMapper.supports(KeyEvent.KEYCODE_PLUS))
        assertFalse(ReaderHardwareKeyMapper.supports(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun cachedSamsungButtonKeysRemainCompatible() {
        val singleClick = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val doubleClick = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B)

        assertEquals(
            ReaderHardwareControl.BUTTON_CLICK,
            ReaderHardwareKeyMapper.controlFor(singleClick.keyCode, singleClick)
        )
        assertEquals(
            ReaderHardwareControl.BUTTON_DOUBLE_CLICK,
            ReaderHardwareKeyMapper.controlFor(doubleClick.keyCode, doubleClick)
        )
    }
}
