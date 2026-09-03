@file:Suppress("OPT_IN_USAGE")

package com.michis.reader.reader

import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.preferences.FontFamily

/** Catalogo unico de familias disponibles y sus archivos registrados en Readium. */
internal object EpubFontCatalog {
    private val minionPro = FontFamily("Minion Pro")

    private val entries = listOf(
        Entry("Sans Serif", FontFamily.SANS_SERIF),
        Entry("Serif", FontFamily.SERIF),
        Entry("Cursiva", FontFamily.CURSIVE),
        Entry("Monoespaciada", FontFamily.MONOSPACE),
        Entry("OpenDyslexic", FontFamily.OPEN_DYSLEXIC),
        Entry("Accessible DfA", FontFamily.ACCESSIBLE_DFA),
        Entry("iA Writer Duospace", FontFamily.IA_WRITER_DUOSPACE),
        Entry("Minion Pro", minionPro)
    )

    val names: Array<String>
        get() = entries.map(Entry::displayName).toTypedArray()

    fun family(index: Int): FontFamily = entries[index.coerceIn(entries.indices)].fontFamily

    fun configure(configuration: EpubNavigatorFragment.Configuration) {
        configuration.servedAssets += "$FONT_DIRECTORY/.*"
        configuration.addFontFamilyDeclaration(minionPro, alternates = listOf(FontFamily.SERIF)) {
            addFontFace {
                addSource("$FONT_DIRECTORY/MinionPro-Regular.otf", preload = true)
                setFontStyle(FontStyle.NORMAL)
                setFontWeight(FontWeight.NORMAL)
            }
            addFontFace {
                addSource("$FONT_DIRECTORY/MinionPro-Bold.otf", preload = true)
                setFontStyle(FontStyle.NORMAL)
                setFontWeight(FontWeight.BOLD)
            }
            addFontFace {
                addSource("$FONT_DIRECTORY/MinionPro-It.otf", preload = true)
                setFontStyle(FontStyle.ITALIC)
                setFontWeight(FontWeight.NORMAL)
            }
            addFontFace {
                addSource("$FONT_DIRECTORY/MinionPro-BoldIt.otf", preload = true)
                setFontStyle(FontStyle.ITALIC)
                setFontWeight(FontWeight.BOLD)
            }
        }
    }

    private data class Entry(val displayName: String, val fontFamily: FontFamily)

    private const val FONT_DIRECTORY = "readium/fonts/minion-pro"
}
