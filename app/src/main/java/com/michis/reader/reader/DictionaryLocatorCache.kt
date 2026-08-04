@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.michis.reader.data.LibraryDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import java.io.File
import java.security.MessageDigest

/** Caché descartable de resultados Readium; nunca sustituye los datos persistentes del usuario. */
internal class DictionaryLocatorCache(private val context: Context) {
    suspend fun load(document: LibraryDocument): MutableMap<String, List<Locator>> = withContext(Dispatchers.IO) {
        val root = runCatching { JSONObject(cacheFile(document).readText()) }.getOrNull() ?: return@withContext mutableMapOf()
        if (root.optInt("schemaVersion") != SCHEMA_VERSION ||
            root.optString("publicationFingerprint") != publicationFingerprint(document)
        ) return@withContext mutableMapOf()
        val cachedTerms = root.optJSONObject("terms") ?: return@withContext mutableMapOf()
        buildMap {
            cachedTerms.keys().forEach { key -> put(key, Locator.fromJSONArray(cachedTerms.optJSONArray(key))) }
        }.toMutableMap()
    }

    suspend fun save(document: LibraryDocument, locatorsByTerm: Map<String, List<Locator>>) = withContext(Dispatchers.IO) {
        val terms = JSONObject()
        locatorsByTerm.forEach { (term, locators) ->
            terms.put(term, JSONArray().apply { locators.forEach { put(it.toJSON()) } })
        }
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("publicationFingerprint", publicationFingerprint(document))
            .put("terms", terms)
        val destination = cacheFile(document)
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.writeText(root.toString())
        check(temporary.renameTo(destination) || runCatching {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
            true
        }.getOrDefault(false)) { "No se pudo actualizar la caché de términos" }
    }

    private fun cacheFile(document: LibraryDocument): File = File(
        File(context.cacheDir, CACHE_DIRECTORY),
        "${sha256(document.uri)}.json"
    )

    private fun publicationFingerprint(document: LibraryDocument): String {
        val uri = Uri.parse(document.uri)
        if (uri.scheme == "file") {
            val file = uri.path?.let(::File)
            if (file != null) return "${document.uri}|${file.length()}|${file.lastModified()}"
        }
        val assetLength = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        val providerMetadata = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use ""
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else assetLength
                val modified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else -1L
                "$size|$modified"
            }
        }.getOrNull().orEmpty()
        return "${document.uri}|$assetLength|$providerMetadata"
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val CACHE_DIRECTORY = "dictionary-locators"
    }
}
