package com.michis.reader.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DriveSyncWarningPreferencesTest {
    private lateinit var context: Context
    private lateinit var preferences: DriveSyncWarningPreferences

    @Before
    fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("drive_sync_confirmations", Context.MODE_PRIVATE).edit().clear().commit()
        preferences = DriveSyncWarningPreferences(context)
    }

    @Test
    fun warningsAreIndependentAndCanBeRestored() {
        assertTrue(preferences.shouldShow(SyncDirection.UPLOAD))
        assertTrue(preferences.shouldShow(SyncDirection.DOWNLOAD))

        preferences.hide(SyncDirection.UPLOAD)

        assertFalse(preferences.shouldShow(SyncDirection.UPLOAD))
        assertTrue(preferences.shouldShow(SyncDirection.DOWNLOAD))

        preferences.restoreAll()

        assertTrue(preferences.shouldShow(SyncDirection.UPLOAD))
        assertTrue(preferences.shouldShow(SyncDirection.DOWNLOAD))
    }
}
