package com.michis.reader.sync.drive

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class GoogleDriveFolder(val identifier: String, val name: String)
/** Operaciones mínimas y no destructivas para preparar la raíz de sincronización. */
class GoogleDriveFolderRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val httpClient = GoogleDriveHttpClient()

    fun savedFolder(accountIdentifier: String): GoogleDriveFolder? {
        val identifier = preferences.getString(folderKey(accountIdentifier), null)?.takeIf { it.isNotBlank() } ?: return null
        return GoogleDriveFolder(identifier, SYNC_FOLDER_NAME)
    }

    fun ensureSyncFolder(accessToken: String, accountIdentifier: String): GoogleDriveFolder {
        val saved = savedFolder(accountIdentifier)
        if (saved != null && validateFolder(accessToken, saved.identifier)) return saved

        val existing = findAppFolder(accessToken)
        val folder = existing ?: createFolder(accessToken)
        preferences.edit().putString(folderKey(accountIdentifier), folder.identifier).apply()
        return folder
    }

    fun downloadLibrarySnapshot(
        accessToken: String,
        accountIdentifier: String,
        folderIdentifier: String
    ): ByteArray {
        val savedIdentifier = preferences.getString(libraryStateKey(accountIdentifier), null)
        val fileIdentifier = if (savedIdentifier != null && validateNamedFile(
                accessToken, savedIdentifier, folderIdentifier, LIBRARY_STATE_FILE_NAME
            )) savedIdentifier else findNamedFile(accessToken, folderIdentifier, LIBRARY_STATE_FILE_NAME)
        require(fileIdentifier != null) { "No existe library-state.json en la carpeta vinculada" }
        return downloadFile(accessToken, fileIdentifier)
    }

    fun downloadLibrarySnapshotOrNull(
        accessToken: String,
        accountIdentifier: String,
        folderIdentifier: String
    ): ByteArray? = runCatching {
        downloadLibrarySnapshot(accessToken, accountIdentifier, folderIdentifier)
    }.getOrElse { error ->
        if (error.message?.contains("No existe library-state.json") == true) null else throw error
    }

    fun downloadNamedJsonOrNull(accessToken: String, folderIdentifier: String, fileName: String): ByteArray? {
        val identifier = findNamedFile(accessToken, folderIdentifier, fileName) ?: return null
        return downloadFile(accessToken, identifier)
    }

    fun uploadNamedJson(accessToken: String, folderIdentifier: String, fileName: String, bytes: ByteArray): String {
        val existing = findNamedFile(accessToken, folderIdentifier, fileName)
        if (existing != null) {
            updateManifest(accessToken, existing, bytes)
            return existing
        }
        return createJsonFile(accessToken, folderIdentifier, fileName, "michisReaderIncrementalState", bytes)
    }

    private fun validateFolder(accessToken: String, identifier: String): Boolean {
        val url = "$DRIVE_FILES_ENDPOINT/$identifier?fields=id,name,mimeType,trashed"
        return runCatching {
            val response = httpClient.json(url, accessToken)
            response.optString("mimeType") == FOLDER_MIME_TYPE && !response.optBoolean("trashed", false)
        }.getOrDefault(false)
    }

    private fun findAppFolder(accessToken: String): GoogleDriveFolder? {
        val query = "mimeType='$FOLDER_MIME_TYPE' and trashed=false and appProperties has { key='$APP_PROPERTY_KEY' and value='$APP_PROPERTY_VALUE' }"
        val url = Uri.parse(DRIVE_FILES_ENDPOINT).buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("spaces", "drive")
            .appendQueryParameter("fields", "files(id,name)")
            .appendQueryParameter("pageSize", "10")
            .build().toString()
        val files = httpClient.json(url, accessToken).optJSONArray("files") ?: JSONArray()
        if (files.length() == 0) return null
        val file = files.getJSONObject(0)
        return GoogleDriveFolder(file.getString("id"), file.optString("name", SYNC_FOLDER_NAME))
    }

    private fun createFolder(accessToken: String): GoogleDriveFolder {
        val body = JSONObject()
            .put("name", SYNC_FOLDER_NAME)
            .put("mimeType", FOLDER_MIME_TYPE)
            .put("appProperties", JSONObject().put(APP_PROPERTY_KEY, APP_PROPERTY_VALUE))
        val url = "$DRIVE_FILES_ENDPOINT?fields=id,name"
        val response = httpClient.json(url, accessToken, "POST", body.toString())
        return GoogleDriveFolder(response.getString("id"), response.optString("name", SYNC_FOLDER_NAME))
    }

    private fun validateNamedFile(
        accessToken: String,
        identifier: String,
        folderIdentifier: String,
        expectedName: String
    ): Boolean = runCatching {
        val response = httpClient.json(
            "$DRIVE_FILES_ENDPOINT/$identifier?fields=id,name,mimeType,trashed,parents",
            accessToken
        )
        response.optString("name") == expectedName &&
            !response.optBoolean("trashed", false) &&
            response.optJSONArray("parents")?.let { parents ->
                (0 until parents.length()).any { parents.getString(it) == folderIdentifier }
            } == true
    }.getOrDefault(false)

    private fun createJsonFile(
        accessToken: String,
        folderIdentifier: String,
        fileName: String,
        appPropertyName: String,
        bytes: ByteArray
    ): String {
        val boundary = "MichisReaderBoundary${UUID.randomUUID()}"
        val metadata = JSONObject()
            .put("name", fileName)
            .put("mimeType", "application/json")
            .put("parents", JSONArray().put(folderIdentifier))
            .put("appProperties", JSONObject().put(appPropertyName, "true"))
        val header = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
        val footer = "\r\n--$boundary--\r\n"
        val requestBytes = header.toByteArray(Charsets.UTF_8) + bytes + footer.toByteArray(Charsets.UTF_8)
        val response = httpClient.bytes(
            "$DRIVE_UPLOAD_ENDPOINT?uploadType=multipart&fields=id",
            accessToken,
            "POST",
            "multipart/related; boundary=$boundary",
            requestBytes
        )
        return JSONObject(response.toString(Charsets.UTF_8)).getString("id")
    }

    private fun findNamedFile(accessToken: String, folderIdentifier: String, fileName: String): String? {
        val escapedName = fileName.replace("'", "\\'")
        val query = "'$folderIdentifier' in parents and name='$escapedName' and trashed=false"
        val url = Uri.parse(DRIVE_FILES_ENDPOINT).buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("spaces", "drive")
            .appendQueryParameter("fields", "files(id,name)")
            .appendQueryParameter("pageSize", "10")
            .build().toString()
        val files = httpClient.json(url, accessToken).optJSONArray("files") ?: JSONArray()
        return if (files.length() == 0) null else files.getJSONObject(0).getString("id")
    }

    private fun updateManifest(accessToken: String, fileIdentifier: String, bytes: ByteArray) {
        httpClient.bytes(
            "$DRIVE_UPLOAD_ENDPOINT/$fileIdentifier?uploadType=media",
            accessToken,
            "PATCH",
            "application/json; charset=UTF-8",
            bytes
        )
    }

    private fun downloadFile(accessToken: String, fileIdentifier: String): ByteArray =
        httpClient.bytes("$DRIVE_FILES_ENDPOINT/$fileIdentifier?alt=media", accessToken)

    private fun folderKey(accountIdentifier: String) = "folder_${accountIdentifier.lowercase().hashCode()}"
    private fun libraryStateKey(accountIdentifier: String) = "library_state_${accountIdentifier.lowercase().hashCode()}"

    companion object {
        const val SYNC_FOLDER_NAME = "Michis Reader"
        private const val PREFERENCES_NAME = "google_drive_folders"
        private const val DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val LIBRARY_STATE_FILE_NAME = "library-state.json"
        private const val APP_PROPERTY_KEY = "michisReaderSyncRoot"
        private const val APP_PROPERTY_VALUE = "true"
    }
}
