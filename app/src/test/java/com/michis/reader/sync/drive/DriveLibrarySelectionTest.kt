package com.michis.reader.sync.drive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DriveLibrarySelectionTest {
    @Test
    fun persistsSeveralFoldersAndIndividualBooksWithoutDuplicates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = GoogleDriveBookLibraryRepository(context)
        val account = "drive-selection-test@example.com"
        repository.saveSelectedSources(account, listOf(
            DriveLibrarySource("folder-a", "Novelas", true),
            DriveLibrarySource("folder-b", "Escuela", true),
            DriveLibrarySource("book-a", "Libro.epub", false),
            DriveLibrarySource("book-a", "Libro.epub", false)
        ))

        val selected = repository.selectedSources(account)

        assertEquals(3, selected.size)
        assertEquals(2, selected.count { it.isFolder })
        assertEquals(1, selected.count { !it.isFolder })
    }
}
