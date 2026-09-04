package com.michis.reader.input

import org.junit.Assert.assertEquals
import org.junit.Test

class HardwareInputDispatcherTest {
    @Test
    fun dispatchDeliversActionSynchronously() {
        var receivedAction: ReaderHardwareAction? = null
        val dispatcher = HardwareInputDispatcher { receivedAction = it }

        dispatcher.dispatch(ReaderHardwareAction.NEXT_PAGE)

        assertEquals(ReaderHardwareAction.NEXT_PAGE, receivedAction)
    }
}
