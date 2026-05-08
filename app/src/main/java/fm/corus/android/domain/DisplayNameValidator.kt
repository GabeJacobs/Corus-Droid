package fm.corus.android.domain

object DisplayNameValidator {

    fun hasVisibleCharacter(input: String): Boolean =
        input.any { !isInvisible(it) }

    private fun isInvisible(c: Char): Boolean {
        if (c.isWhitespace()) return true
        val code = c.code
        return when (code) {
            0x200B, 0x200C, 0x200D,            // zero-width space / non-joiner / joiner
            0x200E, 0x200F,                    // LTR / RTL marks
            0x2060,                            // word joiner
            0xFEFF -> true                     // BOM / zero-width no-break space
            in 0x202A..0x202E -> true          // bidi embeddings / overrides
            in 0x2066..0x2069 -> true          // bidi isolates
            else -> false
        }
    }
}
