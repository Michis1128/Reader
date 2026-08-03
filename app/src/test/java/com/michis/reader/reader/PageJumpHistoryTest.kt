package com.michis.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageJumpHistoryTest {
    @Test
    fun navigatesBackwardAndForwardThroughLinkedNodes() {
        val history = PageJumpHistory()

        history.recordJump(4, 80)
        history.recordJump(80, 150)

        assertEquals(80, history.previousPageIndex)
        assertNull(history.nextPageIndex)
        assertEquals(80, history.moveBack())
        assertEquals(4, history.previousPageIndex)
        assertEquals(150, history.nextPageIndex)
        assertEquals(150, history.moveForward())
    }

    @Test
    fun newJumpAfterGoingBackDiscardsForwardBranch() {
        val history = PageJumpHistory()
        history.recordJump(2, 40)
        history.recordJump(40, 90)
        history.moveBack()

        history.recordJump(40, 65)

        assertEquals(40, history.previousPageIndex)
        assertNull(history.nextPageIndex)
        assertEquals(40, history.moveBack())
        assertEquals(65, history.nextPageIndex)
    }

    @Test
    fun clearRemovesBothDirections() {
        val history = PageJumpHistory()
        history.recordJump(10, 70)
        history.moveBack()
        assertTrue(history.hasNavigation)

        history.clear()

        assertFalse(history.hasNavigation)
        assertNull(history.previousPageIndex)
        assertNull(history.nextPageIndex)
        assertNull(history.moveBack())
        assertNull(history.moveForward())
    }
}
