package com.michis.reader.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderHardwareKeyMapperTest {
    @Test
    fun pageDownIdentifiesSwipeRight() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN)

        assertEquals(ReaderHardwareControl.SWIPE_RIGHT, ReaderHardwareKeyMapper.controlFor(event.keyCode, event))
    }

    @Test
    fun pageUpIdentifiesSwipeLeft() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_UP)

        assertEquals(ReaderHardwareControl.SWIPE_LEFT, ReaderHardwareKeyMapper.controlFor(event.keyCode, event))
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
}
