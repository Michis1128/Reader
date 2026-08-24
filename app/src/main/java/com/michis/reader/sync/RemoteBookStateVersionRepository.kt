package com.michis.reader.sync

import android.content.Context
import java.security.MessageDigest

/** Recuerda los estados JSON remotos ya conciliados sin depender de la fecha local del libro. */
internal class RemoteBookStateVersionRepository(context: Context, accountIdentifier: String) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val accountKey = MessageDigest.getInstance("SHA-256")
        .digest(accountIdentifier.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun wasApplied(documentKey: String, remoteVersion: Long): Boolean =
        preferences.getLong(key(documentKey), Long.MIN_VALUE) >= remoteVersion

    fun markApplied(documentKey: String, remoteVersion: Long) {
        preferences.edit().putLong(key(documentKey), remoteVersion).apply()
    }

    private fun key(documentKey: String) = "$accountKey:$documentKey"

    private companion object {
        const val PREFERENCES_NAME = "applied_remote_book_state_versions"
    }
}
