package com.michis.reader.reader

/** Reglas propias aplicadas sobre Readium CSS a cada recurso EPUB adaptable. */
internal object EpubContentStyles {
    const val STYLE_ELEMENT_IDENTIFIER = "michis-reader-content-overrides"

    val stylesheet: String =
        """
        :root,
        body {
          justify-content: flex-start !important;
          align-content: start !important;
        }

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
        }
        """.trimIndent()

    val installationScript: String
        get() =
            """
            (() => {
              const identifier = '$STYLE_ELEMENT_IDENTIFIER';
              let style = document.getElementById(identifier);
              if (!style) {
                style = document.createElement('style');
                style.id = identifier;
                (document.head || document.documentElement).appendChild(style);
              }
              style.textContent = `${stylesheet}`;
            })();
            """.trimIndent()
}
