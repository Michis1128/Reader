package com.michis.reader.app

import android.content.Context

object ReaderResumeState {
    private const val PREFERENCES = "reader_resume_state"
    private const val KEY_ACTIVE = "reader_was_active"
    private const val KEY_DOCUMENT = "last_document_identifier"

    fun markReaderActive(context: Context, documentIdentifier: Long) {
        if (documentIdentifier < 0) return
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_DOCUMENT, documentIdentifier)
            .apply()
    }

    fun markReaderExited(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, false)
            .apply()
    }

    fun lastDocumentIdentifier(context: Context): Long =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getLong(KEY_DOCUMENT, -1L)

    fun shouldResumeReader(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)
}
