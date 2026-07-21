package fm.corus.android.share

/**
 * Cross-catalog match validation for the share resolver: the subset of the
 * backend's `appleMusicMatch.js` needed to validate one candidate
 * (`isValidISRC`, `namesAlign`, `artistMatches`).
 *
 * KEEP IN SYNC with the backend and with the iOS ports
 * (Corus-iOS/Cymbal-Remake/Services/TrackMatchHelpers.swift and the
 * extension's ShareTrackMatch) — same regexes, same algorithms. These gates
 * exist so a missing/malformed ISRC can never silently substitute a wrong
 * song (the "always Sonny Rollins" bug): the ISRC query is pre-flight gated,
 * and the text-search fallback must align on BOTH name and artist.
 */
object ShareTrackMatch {

    /**
     * ISRC format: 2-letter country, 3-char alphanumeric registrant, 7-digit
     * numeric body — 12 chars total.
     */
    fun isValidISRC(s: String?): Boolean {
        val trimmed = s?.trim() ?: return false
        if (trimmed.length != 12) return false
        return ISRC_REGEX.matches(trimmed)
    }

    private val ISRC_REGEX = Regex("^[A-Za-z]{2}[A-Za-z0-9]{3}[0-9]{7}$")

    // Optional leading year, one of several variant words, up to two trailing
    // qualifiers. Captures "Remastered 2009", "2022 Mix", "Mono Mix Version".
    // Distinguishing tokens (Live/Take/Demo/Acoustic) are deliberately NOT
    // here — those name a different recording and must survive normalization.
    private const val VARIANT_BODY =
        "(?:(?:19|20)\\d{2}\\s+)?" +
            "(?:" +
            "remaster(?:ed)?|" +
            "re-?master(?:ed)?|" +
            "remix(?:ed)?|" +
            "stereo(?:\\s+mix)?|" +
            "mono(?:\\s+mix)?(?:\\s+version)?|" +
            "single\\s+version|" +
            "album\\s+version|" +
            "radio\\s+edit|" +
            "original\\s+mix|" +
            "\\d{4}\\s+mix|" +
            "mix" +
            ")" +
            "(?:\\s+(?:mix|version|remaster(?:ed)?|remix(?:ed)?|rm\\s*\\d+|(?:19|20)\\d{2})){0,2}"

    private val TRAILING_DASH_VARIANT =
        Regex("\\s*[-–—]\\s*$VARIANT_BODY\\s*$", RegexOption.IGNORE_CASE)
    private val TRAILING_PAREN_VARIANT =
        Regex("\\s*[(\\[]\\s*$VARIANT_BODY\\s*[)\\]]\\s*$", RegexOption.IGNORE_CASE)

    /**
     * Iteratively strip trailing variant segments — handles
     * "X - Mono - Remastered" or "X (2009 Remaster) [Mono Mix]". Capped at 4
     * passes to match the backend's defensive bound.
     */
    private fun stripVariantSuffix(name: String): String {
        var n = name.trim()
        repeat(4) {
            val before = n
            n = TRAILING_PAREN_VARIANT.replace(n, "").trim()
            n = TRAILING_DASH_VARIANT.replace(n, "").trim()
            if (n == before) return n
        }
        return n
    }

    /**
     * Punctuation-stripped alignment key: lowercase, variant-stripped, only
     * a-z0-9 kept, runs collapsed to single spaces.
     */
    private fun alignKey(name: String): String {
        val normalized = stripVariantSuffix(name).lowercase().trim()
        val sb = StringBuilder(normalized.length)
        for (c in normalized) {
            if (c in 'a'..'z' || c in '0'..'9') sb.append(c) else sb.append(' ')
        }
        return sb.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * True iff `candidate` could reasonably be the same track as `query` once
     * variant suffixes are stripped. Substring both directions tolerates one
     * side carrying extra edition info.
     */
    fun namesAlign(candidate: String, query: String): Boolean {
        val c = alignKey(candidate)
        val q = alignKey(query)
        if (c.isEmpty() || q.isEmpty()) return false
        return c == q || c.contains(q) || q.contains(c)
    }

    /** Lowercase, drop dots, split on non-alphanumeric, keep tokens len > 1. */
    private fun tokenize(s: String): Set<String> {
        val cleaned = s.lowercase().replace(".", "")
        val tokens = mutableSetOf<String>()
        val current = StringBuilder()
        for (c in cleaned) {
            if (c in 'a'..'z' || c in '0'..'9') {
                current.append(c)
            } else {
                if (current.length > 1) tokens.add(current.toString())
                current.setLength(0)
            }
        }
        if (current.length > 1) tokens.add(current.toString())
        return tokens
    }

    /** Alnum-only lowercase fingerprint ("Kinoko Teikoku" == "Kinokoteikoku"). */
    private fun alnumLower(s: String): String {
        val cleaned = s.lowercase().replace(".", "")
        val sb = StringBuilder(cleaned.length)
        for (c in cleaned) if (c in 'a'..'z' || c in '0'..'9') sb.append(c)
        return sb.toString()
    }

    /**
     * True iff `candidate` is a plausible counterpart of `expected`. Token
     * overlap is the primary signal; alnum-collapse substring is the fallback
     * for spacing differences across catalogs. Substring runs both ways so
     * "The Beatles" doesn't absorb an unrelated short-named candidate.
     */
    fun artistMatches(expected: String, candidate: String): Boolean {
        val a = tokenize(expected)
        val b = tokenize(candidate)
        val nE = alnumLower(expected)
        val nC = alnumLower(candidate)
        if (a.isEmpty() || b.isEmpty()) {
            if (nE.isEmpty() || nC.isEmpty()) return true
            return nC.contains(nE) || nE.contains(nC)
        }
        if (a.intersect(b).isNotEmpty()) return true
        if (nE.isEmpty() || nC.isEmpty()) return false
        return nE == nC || nC.contains(nE) || nE.contains(nC)
    }
}
