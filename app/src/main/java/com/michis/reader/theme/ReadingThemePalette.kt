package com.michis.reader.theme

object ReadingThemePalette {
    val names = arrayOf(
        "Día", "Noche", "Sepia", "Crepúsculo", "Consola", "Papel", "Arena", "Lavanda",
        "Bosque", "Océano", "Grafito", "Medianoche", "Rosa suave", "Menta"
    )
    private val colors = arrayOf(
        0xFFFFFFFF.toInt() to 0xFF1B1B1B.toInt(), 0xFF111318.toInt() to 0xFFE8EAF0.toInt(),
        0xFFF4ECD8.toInt() to 0xFF4A3B2A.toInt(), 0xFF2F2638.toInt() to 0xFFF1D5C9.toInt(),
        0xFF071A0D.toInt() to 0xFF78F58B.toInt(), 0xFFFFFCF2.toInt() to 0xFF302D28.toInt(),
        0xFFEAD9B8.toInt() to 0xFF40362B.toInt(), 0xFFEDE7F6.toInt() to 0xFF332B45.toInt(),
        0xFF183229.toInt() to 0xFFD9E9D9.toInt(), 0xFF102C3A.toInt() to 0xFFDCEFF5.toInt(),
        0xFF292B2F.toInt() to 0xFFE0E0E0.toInt(), 0xFF0B1020.toInt() to 0xFFD8E2FF.toInt(),
        0xFFFFEEF2.toInt() to 0xFF4A3038.toInt(), 0xFFE7F5EE.toInt() to 0xFF203B30.toInt()
    )
    fun colors(index: Int) = colors[index.coerceIn(colors.indices)]
    fun colors(name: String) = colors(names.indexOf(name).coerceAtLeast(0))
}
