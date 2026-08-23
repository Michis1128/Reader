package com.michis.reader.settings

import com.michis.reader.data.LibraryDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class ResetBooksPresentationTest {
    private val documents = listOf(
        document(1, "Zeta", "Ana", openedAt = 10),
        document(2, "Árbol", "", openedAt = 30),
        document(3, "Beta", "Carlos", openedAt = 20)
    )

    @Test
    fun searchMatchesTitleAuthorFormatAndFileNameIgnoringCase() {
        assertEquals(listOf(1L), identifiers(filterAndSortResetDocuments(documents, "zEtA", ResetBooksSortMode.TITLE)))
        assertEquals(listOf(3L), identifiers(filterAndSortResetDocuments(documents, "CARLOS", ResetBooksSortMode.TITLE)))
        assertEquals(listOf(2L), identifiers(filterAndSortResetDocuments(documents, "ÁRBOL.EPUB", ResetBooksSortMode.TITLE)))
        assertEquals(listOf(3L, 1L, 2L), identifiers(filterAndSortResetDocuments(documents, "epub", ResetBooksSortMode.TITLE)))
    }

    @Test
    fun sortModesPreserveThePreviousScreenRules() {
        assertEquals(listOf(3L, 1L, 2L), identifiers(filterAndSortResetDocuments(documents, "", ResetBooksSortMode.TITLE)))
        assertEquals(listOf(1L, 3L, 2L), identifiers(filterAndSortResetDocuments(documents, "", ResetBooksSortMode.AUTHOR)))
        assertEquals(listOf(2L, 3L, 1L), identifiers(filterAndSortResetDocuments(documents, "", ResetBooksSortMode.RECENTLY_OPENED)))
    }

    private fun identifiers(values: List<LibraryDocument>) = values.map(LibraryDocument::identifier)

    private fun document(identifier: Long, title: String, author: String, openedAt: Long) = LibraryDocument(
        identifier = identifier,
        uri = "content://books/$identifier",
        fileName = "${title.lowercase()}.epub",
        title = title,
        author = author,
        format = "EPUB",
        progress = 0f,
        lastOpenedAt = openedAt
    )
}
