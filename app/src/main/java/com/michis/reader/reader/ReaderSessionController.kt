package com.michis.reader.reader

import android.content.Context
import com.michis.reader.app.ReaderResumeState
import com.michis.reader.data.LibraryDocument
import com.michis.reader.data.ReaderDatabase
import com.michis.reader.sync.AutomaticDriveSyncScheduler
import org.readium.r2.shared.publication.Locator

/** Conserva la sesión del libro y programa una sola sincronización por salida o minimización. */
internal class ReaderSessionController(
    private val context: Context,
    private val database: ReaderDatabase,
    private val document: LibraryDocument
) {
    private var persistedSinceLastResume = false

    fun onResume() {
        persistedSinceLastResume = false
        ReaderResumeState.markReaderActive(context, document.identifier)
    }

    fun markReaderMenuOpened() {
        ReaderResumeState.markReaderExited(context)
    }

    fun onStop(locator: Locator?, changingConfigurations: Boolean) {
        if (!changingConfigurations) persistAndScheduleSync(locator)
    }

    fun onFinish(locator: Locator?) {
        persistAndScheduleSync(locator)
        ReaderResumeState.markReaderExited(context)
    }

    private fun persistAndScheduleSync(locator: Locator?) {
        if (persistedSinceLastResume || locator == null) return
        locator.locations.totalProgression?.let { progression ->
            database.updateProgress(
                document.identifier,
                locator.locations.position ?: 0,
                progression.toFloat()
            )
        }
        AutomaticDriveSyncScheduler(context).enqueueBookSync(document.identifier)
        persistedSinceLastResume = true
    }
}
