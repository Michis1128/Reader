package com.michis.reader.sync

import com.michis.reader.sync.drive.GoogleDriveAuthorizationManager

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit

class AutomaticDriveSyncScheduler(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isEnabled() = preferences.getBoolean(KEY_ENABLED, false)
    fun intervalMinutes() = preferences.getLong(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
    fun wifiOnly() = preferences.getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnly(wifiOnly: Boolean) {
        preferences.edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
        if (isEnabled()) schedule()
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) schedule() else WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun setIntervalMinutes(minutes: Long) {
        preferences.edit().putLong(KEY_INTERVAL_MINUTES, minutes.coerceAtLeast(15)).apply()
        if (isEnabled()) schedule()
    }

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<GoogleDriveSyncWorker>(intervalMinutes(), TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun enqueueImmediateSync(direction: SyncDirection = SyncDirection.BIDIRECTIONAL): UUID {
        val request = OneTimeWorkRequestBuilder<GoogleDriveSyncWorker>()
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(
                GoogleDriveSyncWorker.KEY_MANUAL_EXECUTION to true,
                GoogleDriveSyncWorker.KEY_SYNC_DIRECTION to direction.storageValue
            ))
            .build()
        preferences.edit().putString(KEY_LAST_IMMEDIATE_WORK_ID, request.id.toString()).apply()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, request
        )
        return request.id
    }

    fun immediateSyncWorkInfos() = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkLiveData(IMMEDIATE_WORK_NAME)

    fun latestImmediateWorkInfo(workInfos: List<WorkInfo>): WorkInfo? {
        val identifier = preferences.getString(KEY_LAST_IMMEDIATE_WORK_ID, null)
            ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
        return workInfos.firstOrNull { it.id == identifier }
    }

    fun enqueueBookSync(documentIdentifier: Long) {
        if (documentIdentifier < 0) return
        val request = OneTimeWorkRequestBuilder<GoogleDriveSyncWorker>()
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(
                GoogleDriveSyncWorker.KEY_MANUAL_EXECUTION to true,
                GoogleDriveSyncWorker.KEY_DOCUMENT_IDENTIFIER to documentIdentifier
            ))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BOOK_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request
        )
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly()) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    fun saveStatus(status: String) {
        preferences.edit().putString(KEY_LAST_STATUS, status).putLong(KEY_LAST_ATTEMPT_AT, System.currentTimeMillis()).apply()
    }

    fun lastStatus(): String = preferences.getString(KEY_LAST_STATUS, "Todavía no se ha ejecutado") ?: "Todavía no se ha ejecutado"

    companion object {
        private const val PREFERENCES_NAME = "automatic_drive_sync"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private const val KEY_LAST_STATUS = "last_status"
        private const val KEY_LAST_ATTEMPT_AT = "last_attempt_at"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_LAST_IMMEDIATE_WORK_ID = "last_immediate_work_id"
        private const val DEFAULT_INTERVAL_MINUTES = 60L
        private const val UNIQUE_WORK_NAME = "michis_reader_periodic_drive_sync"
        private const val IMMEDIATE_WORK_NAME = "michis_reader_immediate_drive_sync"
        private const val BOOK_WORK_NAME = "michis_reader_last_book_sync"
    }
}
