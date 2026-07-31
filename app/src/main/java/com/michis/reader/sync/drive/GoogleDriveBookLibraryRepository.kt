package com.michis.reader.sync.drive

import com.michis.reader.data.ReaderDatabase

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class DriveLibraryFolder(val identifier: String, val name: String)
data class DriveLibrarySource(val identifier: String, val name: String, val isFolder: Boolean)
data class DriveLibrarySyncResult(val discoveredFiles: Int, val downloadedFiles: Int)

/** Descarga una carpeta elegida de Drive a una copia privada disponible sin conexión. */
class GoogleDriveBookLibraryRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun selectedFolder(accountIdentifier: String): DriveLibraryFolder? {
        val suffix = accountIdentifier.lowercase().hashCode()
        val identifier = preferences.getString("selected_folder_id_$suffix", null) ?: return null
        val name = preferences.getString("selected_folder_name_$suffix", null) ?: "Biblioteca de Drive"
        return DriveLibraryFolder(identifier, name)
    }

    fun saveSelectedFolder(accountIdentifier: String, folder: DriveLibraryFolder) {
        val suffix = accountIdentifier.lowercase().hashCode()
        preferences.edit()
            .putString("selected_folder_id_$suffix", folder.identifier)
            .putString("selected_folder_name_$suffix", folder.name)
            .apply()
    }

    fun selectedSources(accountIdentifier: String): List<DriveLibrarySource> {
        val suffix = accountIdentifier.lowercase().hashCode()
        val stored = preferences.getString("selected_sources_$suffix", null)
        if (stored != null) return runCatching {
            val array = JSONArray(stored)
            buildList {
                repeat(array.length()) {
                    val item = array.getJSONObject(it)
                    add(DriveLibrarySource(item.getString("id"), item.getString("name"), item.getBoolean("folder")))
                }
            }
        }.getOrDefault(emptyList())
        return selectedFolder(accountIdentifier)?.let { listOf(DriveLibrarySource(it.identifier, it.name, true)) }.orEmpty()
    }

    fun saveSelectedSources(accountIdentifier: String, sources: Collection<DriveLibrarySource>) {
        val suffix = accountIdentifier.lowercase().hashCode()
        val array = JSONArray()
        sources.distinctBy { it.identifier }.forEach { source ->
            array.put(JSONObject().put("id", source.identifier).put("name", source.name).put("folder", source.isFolder))
        }
        preferences.edit()
            .putString("selected_sources_$suffix", array.toString())
            .remove("changes_token_$suffix")
            .remove("known_folders_$suffix")
            .apply()
    }

    fun listSelectableSources(accessToken: String): List<DriveLibrarySource> = listFiles(
        accessToken,
        "trashed=false",
        "files(id,name,mimeType)"
    ).mapNotNull { item ->
        val mimeType = item.optString("mimeType")
        when {
            mimeType == FOLDER_MIME_TYPE -> DriveLibrarySource(item.getString("id"), item.optString("name", "Carpeta"), true)
            isEpub(item) -> DriveLibrarySource(item.getString("id"), item.optString("name", "Libro.epub"), false)
            else -> null
        }
    }.sortedWith(compareByDescending<DriveLibrarySource> { it.isFolder }.thenBy { it.name.lowercase() })

    fun listChildren(accessToken: String, parentIdentifier: String): List<DriveLibrarySource> = listFiles(
        accessToken,
        "'$parentIdentifier' in parents and trashed=false",
        "files(id,name,mimeType)"
    ).mapNotNull { item ->
        val mimeType = item.optString("mimeType")
        when {
            mimeType == FOLDER_MIME_TYPE -> DriveLibrarySource(item.getString("id"), item.optString("name", "Carpeta"), true)
            isEpub(item) -> DriveLibrarySource(item.getString("id"), item.optString("name", "Libro.epub"), false)
            else -> null
        }
    }.sortedWith(compareByDescending<DriveLibrarySource> { it.isFolder }.thenBy { it.name.lowercase() })

    fun listFolders(accessToken: String): List<DriveLibraryFolder> = listFiles(
        accessToken,
        "mimeType='$FOLDER_MIME_TYPE' and trashed=false",
        "files(id,name)"
    ).map { DriveLibraryFolder(it.getString("id"), it.optString("name", "Carpeta")) }
        .sortedBy { it.name.lowercase() }

    fun synchronizeSelectedFolder(accessToken: String, accountIdentifier: String): DriveLibrarySyncResult {
        val selectedSources = selectedSources(accountIdentifier)
        if (selectedSources.isEmpty()) return DriveLibrarySyncResult(0, 0)
        val accountSuffix = accountIdentifier.lowercase().hashCode()
        preferences.getString("changes_token_$accountSuffix", null)?.let { token ->
            return synchronizeDriveChanges(accessToken, accountIdentifier, selectedSources, token)
        }
        val initialChangesToken = getStartPageToken(accessToken)
        val database = ReaderDatabase.getInstance(context)
        val destinationRoot = File(context.filesDir, "drive-library/${accountIdentifier.lowercase().hashCode()}")
            .apply { mkdirs() }
        val pendingFolders = ArrayDeque<Pair<DriveLibrarySource, String?>>().apply {
            selectedSources.filter { it.isFolder }.forEach { add(it to null) }
        }
        val pendingFiles = ArrayDeque<Pair<JSONObject, String?>>().apply {
            selectedSources.filterNot { it.isFolder }.forEach { source ->
                add(fileMetadata(accessToken, source.identifier) to null)
            }
        }
        val visitedFolders = mutableSetOf<String>()
        val visitedFiles = mutableSetOf<String>()
        var discovered = 0
        var downloaded = 0

        while (pendingFolders.isNotEmpty()) {
            val (folder, parentFolderIdentifier) = pendingFolders.removeFirst()
            if (!visitedFolders.add(folder.identifier)) continue
            database.saveLibraryFolder(folder.identifier, parentFolderIdentifier, folder.name)
            val children = listFiles(accessToken, "'${folder.identifier}' in parents and trashed=false", "files(id,name,mimeType,modifiedTime,size)")
            children.forEach { item ->
                val mimeType = item.optString("mimeType")
                if (mimeType == FOLDER_MIME_TYPE) {
                    pendingFolders.add(DriveLibrarySource(item.getString("id"), item.optString("name", "Carpeta"), true) to folder.identifier)
                } else if (isEpub(item)) pendingFiles.add(item to folder.identifier)
            }
        }
        while (pendingFiles.isNotEmpty()) {
            val (item, parentFolderIdentifier) = pendingFiles.removeFirst()
            if (!isEpub(item)) continue
            val driveIdentifier = item.getString("id")
            if (visitedFiles.add(driveIdentifier)) {
                    discovered++
                    val fileName = safeFileName(item.optString("name", "libro.epub"))
                    val destinationDirectory = File(destinationRoot, driveIdentifier).apply { mkdirs() }
                    val destination = File(destinationDirectory, fileName)
                    val remoteVersion = item.optString("modifiedTime") + ":" + item.optLong("size", -1L)
                    val versionKey = "remote_version_${accountIdentifier.lowercase().hashCode()}_$driveIdentifier"
                    if (!destination.exists() || preferences.getString(versionKey, null) != remoteVersion) {
                        val temporary = File(destinationDirectory, "$fileName.download")
                        temporary.outputStream().buffered().use { output -> download(accessToken, driveIdentifier, output) }
                        check(temporary.renameTo(destination) || runCatching {
                            temporary.copyTo(destination, overwrite = true); temporary.delete(); true
                        }.getOrDefault(false)) { "No se pudo guardar $fileName" }
                        preferences.edit().putString(versionKey, remoteVersion).apply()
                        downloaded++
                    }
                    database.saveDocument(Uri.fromFile(destination).toString(), fileName, parentFolderIdentifier)
            }
        }
        preferences.edit()
            .putString("changes_token_$accountSuffix", initialChangesToken)
            .putString("known_folders_$accountSuffix", JSONArray(visitedFolders.toList()).toString())
            .apply()
        return DriveLibrarySyncResult(discovered, downloaded)
    }

    private fun synchronizeDriveChanges(
        accessToken: String,
        accountIdentifier: String,
        selectedSources: List<DriveLibrarySource>,
        savedToken: String
    ): DriveLibrarySyncResult {
        val suffix = accountIdentifier.lowercase().hashCode()
        val database = ReaderDatabase.getInstance(context)
        val destinationRoot = File(context.filesDir, "drive-library/$suffix").apply { mkdirs() }
        val knownFolders = mutableSetOf<String>().apply {
            val stored = runCatching { JSONArray(preferences.getString("known_folders_$suffix", "[]")) }.getOrDefault(JSONArray())
            repeat(stored.length()) { add(stored.optString(it)) }
            addAll(selectedSources.filter { it.isFolder }.map { it.identifier })
        }
        val selectedFiles = selectedSources.filterNot { it.isFolder }.map { it.identifier }.toSet()
        var pageToken = savedToken
        var finalToken = savedToken
        var discovered = 0
        var downloaded = 0
        do {
            val parameters = linkedMapOf(
                "pageToken" to pageToken,
                "pageSize" to "1000",
                "spaces" to "drive",
                "includeRemoved" to "true",
                "fields" to "nextPageToken,newStartPageToken,changes(fileId,removed,file(id,name,mimeType,modifiedTime,size,trashed,parents))"
            )
            val url = DRIVE_CHANGES_ENDPOINT + "?" + parameters.entries.joinToString("&") {
                URLEncoder.encode(it.key, StandardCharsets.UTF_8.name()) + "=" +
                    URLEncoder.encode(it.value, StandardCharsets.UTF_8.name())
            }
            val response = jsonRequest(url, accessToken)
            val changes = response.optJSONArray("changes") ?: JSONArray()
            repeat(changes.length()) { index ->
                val change = changes.optJSONObject(index) ?: return@repeat
                if (change.optBoolean("removed", false)) return@repeat
                val file = change.optJSONObject("file") ?: return@repeat
                if (file.optBoolean("trashed", false)) return@repeat
                val identifier = file.optString("id")
                val parents = file.optJSONArray("parents")?.let { array ->
                    buildList { repeat(array.length()) { add(array.optString(it)) } }
                }.orEmpty()
                val knownParent = parents.firstOrNull { it in knownFolders }
                if (file.optString("mimeType") == FOLDER_MIME_TYPE && knownParent != null) {
                    knownFolders += identifier
                    database.saveLibraryFolder(identifier, knownParent, file.optString("name", "Carpeta"))
                } else if (isEpub(file) && (identifier in selectedFiles || knownParent != null)) {
                    discovered++
                    if (downloadChangedEpub(accessToken, accountIdentifier, destinationRoot, file, knownParent)) downloaded++
                }
            }
            val next = response.optString("nextPageToken").takeIf { it.isNotBlank() }
            if (next != null) pageToken = next
            else {
                finalToken = response.optString("newStartPageToken").takeIf { it.isNotBlank() } ?: pageToken
                pageToken = ""
            }
        } while (pageToken.isNotBlank())
        preferences.edit()
            .putString("changes_token_$suffix", finalToken)
            .putString("known_folders_$suffix", JSONArray(knownFolders.toList()).toString())
            .apply()
        return DriveLibrarySyncResult(discovered, downloaded)
    }

    private fun downloadChangedEpub(
        accessToken: String,
        accountIdentifier: String,
        destinationRoot: File,
        item: JSONObject,
        parentFolderIdentifier: String?
    ): Boolean {
        val identifier = item.getString("id")
        val fileName = safeFileName(item.optString("name", "libro.epub"))
        val directory = File(destinationRoot, identifier).apply { mkdirs() }
        val destination = File(directory, fileName)
        val remoteVersion = item.optString("modifiedTime") + ":" + item.optLong("size", -1L)
        val versionKey = "remote_version_${accountIdentifier.lowercase().hashCode()}_$identifier"
        var downloaded = false
        if (!destination.exists() || preferences.getString(versionKey, null) != remoteVersion) {
            val temporary = File(directory, "$fileName.download")
            temporary.outputStream().buffered().use { output -> download(accessToken, identifier, output) }
            check(temporary.renameTo(destination) || runCatching {
                temporary.copyTo(destination, overwrite = true); temporary.delete(); true
            }.getOrDefault(false)) { "No se pudo guardar $fileName" }
            preferences.edit().putString(versionKey, remoteVersion).apply()
            downloaded = true
        }
        ReaderDatabase.getInstance(context).saveDocument(Uri.fromFile(destination).toString(), fileName, parentFolderIdentifier)
        return downloaded
    }

    private fun getStartPageToken(accessToken: String): String = jsonRequest(
        "$DRIVE_CHANGES_ENDPOINT/startPageToken",
        accessToken
    ).getString("startPageToken")

    private fun fileMetadata(accessToken: String, identifier: String): JSONObject =
        jsonRequest("$DRIVE_FILES_ENDPOINT/$identifier?fields=id,name,mimeType,modifiedTime,size,trashed", accessToken)

    private fun isEpub(item: JSONObject): Boolean =
        item.optString("mimeType").equals(EPUB_MIME_TYPE, ignoreCase = true) ||
            item.optString("name").endsWith(".epub", ignoreCase = true)

    private fun listFiles(accessToken: String, query: String, fields: String): List<JSONObject> {
        val results = mutableListOf<JSONObject>()
        var pageToken: String? = null
        do {
            val parameters = linkedMapOf(
                "q" to query, "spaces" to "drive", "pageSize" to "1000",
                "fields" to "nextPageToken,$fields"
            )
            pageToken?.let { parameters["pageToken"] = it }
            val url = DRIVE_FILES_ENDPOINT + "?" + parameters.entries.joinToString("&") {
                URLEncoder.encode(it.key, StandardCharsets.UTF_8.name()) + "=" +
                    URLEncoder.encode(it.value, StandardCharsets.UTF_8.name())
            }
            val response = jsonRequest(url, accessToken)
            val files = response.optJSONArray("files") ?: JSONArray()
            repeat(files.length()) { results += files.getJSONObject(it) }
            pageToken = response.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return results
    }

    private fun download(accessToken: String, identifier: String, output: java.io.OutputStream) {
        val connection = URL("$DRIVE_FILES_ENDPOINT/$identifier?alt=media").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000; connection.readTimeout = 120_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Drive respondió $status al descargar un libro EPUB")
            connection.inputStream.buffered().use { it.copyTo(output) }
        } finally { connection.disconnect() }
    }

    private fun jsonRequest(url: String, accessToken: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Drive respondió $status")
            return JSONObject(text)
        } finally { connection.disconnect() }
    }

    private fun safeFileName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "libro.epub" }

    companion object {
        private const val PREFERENCES_NAME = "google_drive_book_library"
        private const val DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_CHANGES_ENDPOINT = "https://www.googleapis.com/drive/v3/changes"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val EPUB_MIME_TYPE = "application/epub+zip"
    }
}
