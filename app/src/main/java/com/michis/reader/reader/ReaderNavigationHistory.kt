package com.michis.reader.reader

internal enum class PendingJumpSource { CONTENTS, SEARCH }

/** Une el historial bidireccional con los saltos que Readium confirma de forma asíncrona. */
internal class ReaderNavigationHistory {
    private val history = PageJumpHistory()
    private val pendingJumps = mutableMapOf<PendingJumpSource, PendingJump>()
    private var nextToken = 0

    val previousPageIndex: Int? get() = history.previousPageIndex
    val nextPageIndex: Int? get() = history.nextPageIndex
    val hasNavigation: Boolean get() = history.hasNavigation

    fun record(originPage: Int, destinationPage: Int) = history.recordJump(originPage, destinationPage)

    fun beginPending(source: PendingJumpSource, originPage: Int): Int {
        val token = ++nextToken
        pendingJumps[source] = PendingJump(originPage, token)
        return token
    }

    fun cancelPending(source: PendingJumpSource, token: Int) {
        if (pendingJumps[source]?.token == token) pendingJumps.remove(source)
    }

    fun confirmArrival(pageIndex: Int): Boolean {
        var recorded = false
        pendingJumps.entries.toList().forEach { (source, pending) ->
            if (pageIndex != pending.originPage) {
                history.recordJump(pending.originPage, pageIndex)
                pendingJumps.remove(source)
                recorded = true
            }
        }
        return recorded
    }

    fun moveBack(): Int? = history.moveBack()
    fun moveForward(): Int? = history.moveForward()

    fun clear() {
        pendingJumps.clear()
        history.clear()
    }

    private data class PendingJump(val originPage: Int, val token: Int)
}
