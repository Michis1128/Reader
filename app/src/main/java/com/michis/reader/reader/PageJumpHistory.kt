package com.michis.reader.reader

/**
 * Historial navegable de saltos lejanos dentro de un EPUB.
 *
 * Cada posición conoce explícitamente su nodo anterior y siguiente. Al registrar
 * un salto nuevo después de haber retrocedido, la rama que estaba por delante se
 * descarta, igual que ocurre en el historial de un navegador.
 */
class PageJumpHistory {
    private data class PageNode(
        val pageIndex: Int,
        var previous: PageNode? = null,
        var next: PageNode? = null
    )

    private var currentNode: PageNode? = null

    val previousPageIndex: Int?
        get() = currentNode?.previous?.pageIndex

    val nextPageIndex: Int?
        get() = currentNode?.next?.pageIndex

    val hasNavigation: Boolean
        get() = previousPageIndex != null || nextPageIndex != null

    fun recordJump(originPageIndex: Int, destinationPageIndex: Int) {
        if (originPageIndex == destinationPageIndex) return

        val originNode = currentNode?.let { current ->
            if (current.pageIndex == originPageIndex) {
                current
            } else {
                current.next = null
                PageNode(originPageIndex, previous = current).also { current.next = it }
            }
        } ?: PageNode(originPageIndex)

        originNode.next = null
        currentNode = PageNode(destinationPageIndex, previous = originNode).also { originNode.next = it }
    }

    fun moveBack(): Int? {
        val destination = currentNode?.previous ?: return null
        currentNode = destination
        return destination.pageIndex
    }

    fun moveForward(): Int? {
        val destination = currentNode?.next ?: return null
        currentNode = destination
        return destination.pageIndex
    }

    fun clear() {
        currentNode?.previous?.next = null
        currentNode?.next?.previous = null
        currentNode = null
    }
}
