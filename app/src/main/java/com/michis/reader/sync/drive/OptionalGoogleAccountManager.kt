package com.michis.reader.sync.drive

import com.michis.reader.R
import com.michis.reader.sync.AutomaticDriveSyncScheduler

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

data class GoogleAccountSession(
    val accountIdentifier: String,
    val displayName: String,
    val profilePictureUri: String
)

/** Gestiona solo la identidad opcional; no solicita Drive ni conserva el ID token. */
class OptionalGoogleAccountManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun currentSession(): GoogleAccountSession? {
        val identifier = preferences.getString(KEY_ACCOUNT_IDENTIFIER, null)?.takeIf { it.isNotBlank() } ?: return null
        return GoogleAccountSession(
            accountIdentifier = identifier,
            displayName = preferences.getString(KEY_DISPLAY_NAME, "").orEmpty(),
            profilePictureUri = preferences.getString(KEY_PROFILE_PICTURE_URI, "").orEmpty()
        )
    }

    suspend fun signIn(activity: ComponentActivity): GoogleAccountSession {
        val googleOption = GetSignInWithGoogleOption.Builder(
            serverClientId = context.getString(R.string.google_web_client_id)
        ).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
        val credential = credentialManager.getCredential(activity, request).credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "Google devolvió un tipo de credencial no compatible" }

        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val session = GoogleAccountSession(
            accountIdentifier = googleCredential.id,
            displayName = googleCredential.displayName.orEmpty(),
            profilePictureUri = googleCredential.profilePictureUri?.toString().orEmpty()
        )
        preferences.edit()
            .putString(KEY_ACCOUNT_IDENTIFIER, session.accountIdentifier)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .putString(KEY_PROFILE_PICTURE_URI, session.profilePictureUri)
            .apply()
        return session
    }

    suspend fun signOut() {
        AutomaticDriveSyncScheduler(context).setEnabled(false)
        preferences.edit().clear().apply()
        GoogleDriveAuthorizationManager(context).clearLocalAuthorizationState()
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    companion object {
        private const val PREFERENCES_NAME = "optional_google_account"
        private const val KEY_ACCOUNT_IDENTIFIER = "account_identifier"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PROFILE_PICTURE_URI = "profile_picture_uri"
    }
}
