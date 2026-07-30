package com.michis.reader.library

import com.michis.reader.data.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

object BookCoverLoader {
    private val executor = Executors.newFixedThreadPool(2)

    fun load(context: Context, document: LibraryDocument, target: ImageView) {
        val expectedUri = document.uri
        target.tag = expectedUri
        executor.execute {
            val bitmap = runCatching { epubCover(context, Uri.parse(document.uri)) }.getOrNull()
            target.post { if (target.tag == expectedUri && bitmap != null) target.setImageBitmap(bitmap) }
        }
    }

    private fun epubCover(context: Context, uri: Uri): Bitmap? {
        var firstImage: ByteArray? = null
        var namedCover: ByteArray? = null
        context.contentResolver.openInputStream(uri)?.use { stream -> ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.lowercase()
                if (!entry.isDirectory && name.substringAfterLast('.', "") in setOf("jpg", "jpeg", "png", "webp")) {
                    val bytes = ByteArrayOutputStream().also { zip.copyTo(it) }.toByteArray()
                    if (firstImage == null) firstImage = bytes
                    if ("cover" in name || "portada" in name) { namedCover = bytes; break }
                }
            }
        } }
        return (namedCover ?: firstImage)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
}
