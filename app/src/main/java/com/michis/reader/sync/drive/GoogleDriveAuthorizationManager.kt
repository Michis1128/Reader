package com.michis.reader.sync.drive

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope

/** Autoriza Drive para sincronizar la carpeta de biblioteca elegida por el usuario. */
class GoogleDriveAuthorizationManager(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val authorizationClient get() = Identity.getAuthorizationClient(context)

    fun authorizationRequest(accountIdentifier: String): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setAccount(Account(accountIdentifier, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE))
            .setRequestedScopes(listOf(DRIVE_SCOPE))
            .build()

    fun isAuthorized(): Boolean = preferences.getBoolean(KEY_IS_AUTHORIZED, false)

    fun acceptAuthorizationResult(result: AuthorizationResult): Boolean {
        val granted = result.grantedScopes.contains(DRIVE_SCOPE.scopeUri) &&
            !result.accessToken.isNullOrBlank()
        preferences.edit().putBoolean(KEY_IS_AUTHORIZED, granted).apply()
        return granted
    }

    fun revokeRequest(accountIdentifier: String): RevokeAccessRequest =
        RevokeAccessRequest.builder()
            .setAccount(Account(accountIdentifier, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE))
            .setScopes(listOf(DRIVE_SCOPE))
            .build()

    fun clearLocalAuthorizationState() {
        preferences.edit().clear().apply()
    }

    companion object {
        const val DRIVE_SCOPE_URI = "https://www.googleapis.com/auth/drive"
        private val DRIVE_SCOPE = Scope(DRIVE_SCOPE_URI)
        private const val PREFERENCES_NAME = "google_drive_authorization"
        private const val KEY_IS_AUTHORIZED = "is_authorized"
    }
}
