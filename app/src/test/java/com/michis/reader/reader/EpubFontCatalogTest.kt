package com.michis.reader.reader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.navigator.preferences.FontFamily

class EpubFontCatalogTest {
    @Test
    fun minionProIsAppendedWithoutChangingStoredFontIndexes() {
        assertArrayEquals(
            arrayOf(
                "Sans Serif",
                "Serif",
                "Cursiva",
                "Monoespaciada",
                "OpenDyslexic",
                "Accessible DfA",
                "iA Writer Duospace",
                "Minion Pro"
            ),
            EpubFontCatalog.names
        )
        assertEquals(FontFamily.SANS_SERIF, EpubFontCatalog.family(0))
        assertEquals(FontFamily("Minion Pro"), EpubFontCatalog.family(7))
    }
}
