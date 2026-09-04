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
    fun pageDownStartsNextPageAction() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN)

        assertEquals(ReaderHardwareAction.NEXT_PAGE, ReaderHardwareKeyMapper.actionFor(event.keyCode, event))
    }

    @Test
    fun pageUpStartsPreviousPageAction() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_UP)

        assertEquals(ReaderHardwareAction.PREVIOUS_PAGE, ReaderHardwareKeyMapper.actionFor(event.keyCode, event))
    }

    @Test
    fun repeatedAndReleasedEventsAreIgnored() {
        val repeated = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, 1)
        val released = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN)

        assertNull(ReaderHardwareKeyMapper.actionFor(repeated.keyCode, repeated))
        assertNull(ReaderHardwareKeyMapper.actionFor(released.keyCode, released))
    }

    @Test
    fun unrelatedKeysAreIgnored() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)

        assertNull(ReaderHardwareKeyMapper.actionFor(event.keyCode, event))
    }
}
