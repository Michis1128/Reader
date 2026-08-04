package com.michis.reader.library

import com.michis.reader.data.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

object BookCoverLoader {
    private val executor = Executors.newFixedThreadPool(2)
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSizeKilobytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val pendingTargets = mutableMapOf<String, MutableList<WeakReference<ImageView>>>()

    fun load(context: Context, document: LibraryDocument, target: ImageView) {
        val uri = Uri.parse(document.uri)
        val cacheKey = coverCacheKey(uri)
        target.tag = cacheKey
        synchronized(memoryCache) { memoryCache.get(cacheKey) }?.let { cached ->
            target.setImageBitmap(cached)
            return
        }
        val shouldStartRequest = synchronized(pendingTargets) {
            val targets = pendingTargets.getOrPut(cacheKey) { mutableListOf() }
            targets += WeakReference(target)
            targets.size == 1
        }
        if (!shouldStartRequest) return
        executor.execute {
            val bitmap = runCatching { epubCover(context.applicationContext, uri) }.getOrNull()
            if (bitmap != null) synchronized(memoryCache) { memoryCache.put(cacheKey, bitmap) }
            val targets = synchronized(pendingTargets) { pendingTargets.remove(cacheKey).orEmpty() }
            targets.forEach { reference ->
                reference.get()?.let { imageView ->
                    imageView.post {
                        if (imageView.tag == cacheKey && bitmap != null) imageView.setImageBitmap(bitmap)
                    }
                }
            }
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
        return (namedCover ?: firstImage)?.let(::decodeSampledCover)
    }

    private fun decodeSampledCover(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAXIMUM_COVER_WIDTH_PIXELS * 2 ||
            bounds.outHeight / sampleSize > MAXIMUM_COVER_HEIGHT_PIXELS * 2
        ) sampleSize *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        })
    }

    private fun coverCacheKey(uri: Uri): String {
        if (uri.scheme != "file") return uri.toString()
        val file = uri.path?.let(::File) ?: return uri.toString()
        return "${uri}|${file.lastModified()}|${file.length()}"
    }

    private fun cacheSizeKilobytes(): Int =
        (Runtime.getRuntime().maxMemory() / 1024L / 16L).coerceIn(4_096L, 32_768L).toInt()

    private const val MAXIMUM_COVER_WIDTH_PIXELS = 720
    private const val MAXIMUM_COVER_HEIGHT_PIXELS = 1_080
}
