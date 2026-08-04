package com.michis.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderNavigationHistoryTest {
    @Test
    fun pendingJumpRecordsTheOriginalPageAfterReadiumConfirmsArrival() {
        val navigation = ReaderNavigationHistory()
        navigation.beginPending(PendingJumpSource.SEARCH, 4)

        assertFalse(navigation.confirmArrival(4))
        assertTrue(navigation.confirmArrival(21))
        assertEquals(4, navigation.previousPageIndex)
        assertEquals(4, navigation.moveBack())
        assertEquals(21, navigation.nextPageIndex)
    }

    @Test
    fun cancelledPendingJumpDoesNotCreateHistory() {
        val navigation = ReaderNavigationHistory()
        val token = navigation.beginPending(PendingJumpSource.CONTENTS, 7)

        navigation.cancelPending(PendingJumpSource.CONTENTS, token)

        assertFalse(navigation.confirmArrival(30))
        assertNull(navigation.previousPageIndex)
    }

    @Test
    fun aNewJumpAfterGoingBackDiscardsTheForwardBranch() {
        val navigation = ReaderNavigationHistory()
        navigation.record(1, 10)
        navigation.record(10, 20)
        assertEquals(10, navigation.moveBack())

        navigation.record(10, 15)

        assertNull(navigation.nextPageIndex)
        assertEquals(10, navigation.previousPageIndex)
    }
}
