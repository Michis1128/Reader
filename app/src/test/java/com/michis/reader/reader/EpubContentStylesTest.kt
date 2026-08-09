package com.michis.reader.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubContentStylesTest {
    @Test
    fun paginationRulesAllowSingleLinesAtColumnBoundaries() {
        val stylesheet = EpubContentStyles.stylesheet

        assertTrue(stylesheet.contains("widows: 1 !important"))
        assertTrue(stylesheet.contains("orphans: 1 !important"))
        assertTrue(stylesheet.contains("break-inside: auto !important"))
    }

    @Test
    fun specialTextRulesRecognizeSemanticAndCommonBookMarkup() {
        val stylesheet = EpubContentStyles.stylesheet

        assertTrue(stylesheet.contains("blockquote"))
        assertTrue(stylesheet.contains("z3998:letter"))
        assertTrue(stylesheet.contains("z3998:poem"))
        assertTrue(stylesheet.contains("class~=\"carta\""))
        assertTrue(stylesheet.contains("class~=\"cancion\""))
        assertTrue(stylesheet.contains("margin-inline-start: 6%"))
        assertTrue(stylesheet.contains("font-style: italic !important"))
        assertTrue(stylesheet.contains("blockquote *"))
        assertTrue(stylesheet.contains("[epub\\:type~=\"z3998:letter\"] *"))
    }

    @Test
    fun installationScriptReusesOneStyleElement() {
        val script = EpubContentStyles.installationScript

        assertTrue(script.contains(EpubContentStyles.STYLE_ELEMENT_IDENTIFIER))
        assertTrue(script.contains("document.getElementById"))
        assertTrue(script.contains("[epub\\\\:type"))
        assertFalse(script.contains("style.textContent = `"))
        assertFalse(script.contains("document.body.innerHTML"))
    }
}
