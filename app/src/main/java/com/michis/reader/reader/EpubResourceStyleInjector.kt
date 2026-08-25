package com.michis.reader.reader

import com.michis.reader.settings.ReaderSettingsRepository
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.TransformingContainer
import org.readium.r2.shared.util.resource.map

/** Inserta los estilos del lector antes del primer calculo de maquetacion de cada XHTML. */
internal object EpubResourceStyleInjector {
    fun decorate(
        container: Container<Resource>,
        manifest: Manifest,
        settings: ReaderSettingsRepository
    ): Container<Resource> {
        val contentUrls = manifest.readingOrder.mapTo(mutableSetOf()) { normalizedUrl(it.href.toString()) }
        return TransformingContainer(container) { url, resource ->
            if (normalizedUrl(url.toString()) !in contentUrls) return@TransformingContainer resource
            resource.map { bytes ->
                Try.success(injectStyles(bytes, settings))
            }
        }
    }

    internal fun injectStyles(bytes: ByteArray, settings: ReaderSettingsRepository): ByteArray {
        val charset = detectCharset(bytes)
        val document = bytes.toString(charset)
        val stylesheet = EpubContentStyles.stylesheet(
            settings.pageMarginMode,
            settings.customPageMarginTopDp,
            settings.customPageMarginRightDp,
            settings.customPageMarginBottomDp,
            settings.customPageMarginLeftDp
        )
        val decoratedDocument = injectStyleElement(document, stylesheet)
        return decoratedDocument.toByteArray(charset)
    }

    internal fun injectStyleElement(document: String, stylesheet: String): String {
        val styleElement = "<style id=\"${EpubContentStyles.STYLE_ELEMENT_IDENTIFIER}\">$stylesheet</style>"
        val headStart = document.indexOf("<head", ignoreCase = true)
        if (headStart >= 0) {
            val openingTagEnd = document.indexOf('>', startIndex = headStart)
            if (openingTagEnd >= 0) {
                return document.substring(0, openingTagEnd + 1) + styleElement + document.substring(openingTagEnd + 1)
            }
        }
        val bodyStart = document.indexOf("<body", ignoreCase = true)
        if (bodyStart >= 0) {
            return document.substring(0, bodyStart) + "<head>$styleElement</head>" + document.substring(bodyStart)
        }
        return document
    }

    private fun normalizedUrl(value: String) = value.substringBefore('#').substringBefore('?')

    private fun detectCharset(bytes: ByteArray): Charset = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> StandardCharsets.UTF_16LE
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> StandardCharsets.UTF_16BE
        else -> StandardCharsets.UTF_8
    }
}
