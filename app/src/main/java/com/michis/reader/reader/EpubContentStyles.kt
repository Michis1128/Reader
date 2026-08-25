package com.michis.reader.reader

import com.michis.reader.settings.PageMarginMode

/** Reglas propias aplicadas sobre Readium CSS a cada recurso EPUB adaptable. */
internal object EpubContentStyles {
    const val STYLE_ELEMENT_IDENTIFIER = "michis-reader-content-overrides"

    fun stylesheet(
        marginMode: PageMarginMode,
        topMarginDp: Float,
        rightMarginDp: Float,
        bottomMarginDp: Float,
        leftMarginDp: Float
    ): String {
        val customPadding = if (marginMode == PageMarginMode.CUSTOM) {
            "padding: ${topMarginDp}px ${rightMarginDp}px ${bottomMarginDp}px ${leftMarginDp}px !important;"
        } else {
            ""
        }
        return COMMON_STYLESHEET + "\n" +
        """
        :root,
        body {
          justify-content: flex-start !important;
          align-content: start !important;
        }

        :root {
          --RS__maxLineLength: 1000rem !important;
        }

        body {
          width: 100% !important;
          max-width: 100% !important;
          box-sizing: border-box !important;
          $customPadding
        }
        """.trimIndent()
    }

    private val COMMON_STYLESHEET: String =
        """

        p,
        li,
        dt,
        dd,
        blockquote {
          widows: 1 !important;
          orphans: 1 !important;
          break-inside: auto !important;
          page-break-inside: auto !important;
          -webkit-column-break-inside: auto !important;
        }

        blockquote,
        [epub\:type~="z3998:letter"],
        [epub\:type~="z3998:poem"],
        [epub\:type~="z3998:song"],
        [epub\:type~="epigraph"],
        [epub\:type~="pullquote"],
        [role="doc-epigraph"],
        [class~="letter" i],
        [class~="carta" i],
        [class~="poem" i],
        [class~="poema" i],
        [class~="verse" i],
        [class~="verso" i],
        [class~="song" i],
        [class~="cancion" i],
        [class~="lyrics" i],
        [class~="epigraph" i],
        [class~="epigrafe" i],
        [class~="excerpt" i],
        [class~="extracto" i],
        [class~="fragment" i],
        [class~="fragmento" i],
        [class~="quote" i],
        [class~="cita" i] {
          box-sizing: border-box !important;
          margin-inline-start: 6% !important;
          margin-inline-end: 6% !important;
          font-size: 0.96em !important;
          font-style: italic !important;
        }

        blockquote *,
        [epub\:type~="z3998:letter"] *,
        [epub\:type~="z3998:poem"] *,
        [epub\:type~="z3998:song"] *,
        [epub\:type~="epigraph"] *,
        [epub\:type~="pullquote"] *,
        [role="doc-epigraph"] *,
        [class~="letter" i] *,
        [class~="carta" i] *,
        [class~="poem" i] *,
        [class~="poema" i] *,
        [class~="verse" i] *,
        [class~="verso" i] *,
        [class~="song" i] *,
        [class~="cancion" i] *,
        [class~="lyrics" i] *,
        [class~="epigraph" i] *,
        [class~="epigrafe" i] *,
        [class~="excerpt" i] *,
        [class~="extracto" i] *,
        [class~="fragment" i] *,
        [class~="fragmento" i] *,
        [class~="quote" i] *,
        [class~="cita" i] * {
          font-style: italic !important;
        }
        """.trimIndent()

    fun installationScript(
        marginMode: PageMarginMode,
        topMarginDp: Float,
        rightMarginDp: Float,
        bottomMarginDp: Float,
        leftMarginDp: Float
    ): String {
        val encodedStylesheet = stylesheet(
            marginMode,
            topMarginDp,
            rightMarginDp,
            bottomMarginDp,
            leftMarginDp
        ).toJavaScriptStringLiteral()
        return """
            (() => {
              const identifier = '$STYLE_ELEMENT_IDENTIFIER';
              let style = document.getElementById(identifier);
              if (!style) {
                style = document.createElement('style');
                style.id = identifier;
                (document.head || document.documentElement).appendChild(style);
              }
              style.textContent = $encodedStylesheet;
            })();
            """.trimIndent()
    }

    private fun String.toJavaScriptStringLiteral(): String = buildString(length + 2) {
        append('"')
        this@toJavaScriptStringLiteral.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> append(character)
            }
        }
        append('"')
    }
}
