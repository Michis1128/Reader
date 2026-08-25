package com.michis.reader.reader

import com.michis.reader.settings.PageMarginMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubContentStylesTest {
    @Test
    fun paginationRulesAllowSingleLinesAtColumnBoundaries() {
        val stylesheet = stylesheet()

        assertTrue(stylesheet.contains("widows: 1 !important"))
        assertTrue(stylesheet.contains("orphans: 1 !important"))
        assertTrue(stylesheet.contains("break-inside: auto !important"))
    }

    @Test
    fun specialTextRulesRecognizeSemanticAndCommonBookMarkup() {
        val stylesheet = stylesheet()

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
        val script = EpubContentStyles.installationScript(PageMarginMode.REDUCED, 0f, 0f, 0f, 0f)

        assertTrue(script.contains(EpubContentStyles.STYLE_ELEMENT_IDENTIFIER))
        assertTrue(script.contains("document.getElementById"))
        assertTrue(script.contains("[epub\\\\:type"))
        assertFalse(script.contains("style.textContent = `"))
        assertFalse(script.contains("document.body.innerHTML"))
    }

    @Test
    fun customMarginsArePartOfTheStylesheetBeforeLayout() {
        val stylesheet = EpubContentStyles.stylesheet(PageMarginMode.CUSTOM, 8f, 12f, 16f, 20f)

        assertTrue(stylesheet.contains("padding: 8.0px 12.0px 16.0px 20.0px !important"))
        assertTrue(stylesheet.contains("--RS__maxLineLength: 1000rem !important"))
    }

    @Test
    fun stylesheetIsInjectedInsideHeadBeforeReadiumLoadsTheResource() {
        val original = "<?xml version=\"1.0\"?><html><head><title>Capitulo</title></head><body>Texto</body></html>"

        val decorated = EpubResourceStyleInjector.injectStyleElement(original, "body { width: 100%; }")

        assertTrue(decorated.indexOf(EpubContentStyles.STYLE_ELEMENT_IDENTIFIER) < decorated.indexOf("<title>"))
        assertTrue(decorated.startsWith("<?xml version=\"1.0\"?>"))
    }

    private fun stylesheet() =
        EpubContentStyles.stylesheet(PageMarginMode.REDUCED, 0f, 0f, 0f, 0f)
}
