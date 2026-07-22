package fm.corus.android.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MentionTextTest {

    // ── parseMentionQuery ──

    @Test
    fun `returns null for empty text`() {
        assertNull(parseMentionQuery(""))
    }

    @Test
    fun `returns null when no mention present`() {
        assertNull(parseMentionQuery("hello world"))
    }

    @Test
    fun `returns null for lone @ symbol`() {
        assertNull(parseMentionQuery("hello @"))
    }

    @Test
    fun `detects mention after space`() {
        assertEquals("ga", parseMentionQuery("hello @ga"))
    }

    @Test
    fun `detects mention at start of text`() {
        assertEquals("user", parseMentionQuery("@user"))
    }

    @Test
    fun `detects mention after newline`() {
        assertEquals("ga", parseMentionQuery("check this out\n@ga"))
    }

    @Test
    fun `detects mention after multiple newlines`() {
        assertEquals("user", parseMentionQuery("line one\n\n@user"))
    }

    @Test
    fun `detects mention after carriage return`() {
        assertEquals("test", parseMentionQuery("hello\r\n@test"))
    }

    @Test
    fun `returns null when mention is not the last word`() {
        assertNull(parseMentionQuery("hello @ga world"))
    }

    @Test
    fun `handles mention with dots in username`() {
        assertEquals("john.doe", parseMentionQuery("hey @john.doe"))
    }

    @Test
    fun `returns null for trailing whitespace after mention`() {
        assertNull(parseMentionQuery("hello @ga "))
    }

    // ── Caret-aware parseMentionQuery ──
    // Regression: composers passed only the full text, so editing an @mention
    // mid-sentence silently disabled the suggestion dropdown because the "last
    // word" wasn't the one the caret was in.

    @Test
    fun `detects mention when caret is in middle of text`() {
        // "hello @bry world" with caret right after "@bry" (position 10)
        assertEquals("bry", parseMentionQuery("hello @bry world", caret = 10))
    }

    @Test
    fun `ignores mention that does not contain the caret`() {
        // Caret at end — last word is "world", not a mention.
        assertNull(parseMentionQuery("hello @bry world", caret = 16))
    }

    @Test
    fun `detects mention at caret after newline mid-text`() {
        // "hey\n@alice\nbye" with caret right after "@alice" (position 10)
        assertEquals("alice", parseMentionQuery("hey\n@alice\nbye", caret = 10))
    }

    @Test
    fun `ignores mention when caret sits just past the trailing space`() {
        // "@bry | rest" — caret sits on the space right after @bry, so the
        // word at the caret is empty, not a mention.
        assertNull(parseMentionQuery("@bry rest", caret = 5))
    }

    @Test
    fun `detects partial mention during active typing mid-sentence`() {
        // User has "@ellagos @b Corus-wise" and the caret is right after "@b".
        assertEquals("b", parseMentionQuery("@ellagos @b Corus-wise", caret = 11))
    }

    // ── applyMention ──

    @Test
    fun `applyMention replaces partial mention with full username and trailing space`() {
        assertEquals("hello @gabejacobs ", applyMention("hello @gab", "gabejacobs"))
    }

    @Test
    fun `applyMention handles mention at start`() {
        assertEquals("@gabejacobs ", applyMention("@gab", "gabejacobs"))
    }

    @Test
    fun `applyMention preserves text with no mention in progress`() {
        assertEquals("hello world", applyMention("hello world", "gabejacobs"))
    }

    @Test
    fun `applyMention replaces only the last at-mention`() {
        assertEquals("hi @alice and @bob ", applyMention("hi @alice and @b", "bob"))
    }

    @Test
    fun `applyMention handles mention after newline`() {
        assertEquals("line one\n@gabe ", applyMention("line one\n@ga", "gabe"))
    }

    @Test
    fun `applyMention replaces bare @ with full username`() {
        assertEquals("@user ", applyMention("@", "user"))
    }

    // ── Caret-aware applyMention(TextFieldValue) ──
    // Regression: the old version called lastIndexOf('@') and truncated
    // everything after it, dropping user text. It also parked the cursor at
    // the end of the (truncated) string.

    @Test
    fun `applyMention preserves text after caret when inserting mid-sentence`() {
        // "Hey @bry whats up", caret right after "@bry" (position 8)
        val value = TextFieldValue("Hey @bry whats up", selection = TextRange(8))
        val result = applyMention(value, "brycef")
        assertEquals("Hey @brycef whats up", result.text)
        // Cursor lands after the existing space, before "whats"
        assertEquals(12, result.selection.start)
    }

    @Test
    fun `applyMention at end of text appends trailing space and parks cursor after it`() {
        val value = TextFieldValue("Hey @gab", selection = TextRange(8))
        val result = applyMention(value, "gabejacobs")
        assertEquals("Hey @gabejacobs ", result.text)
        assertEquals(16, result.selection.start)
    }

    @Test
    fun `applyMention is a no-op when caret is not on a mention token`() {
        val value = TextFieldValue("Hey world", selection = TextRange(9))
        val result = applyMention(value, "brycef")
        assertEquals("Hey world", result.text)
        assertEquals(9, result.selection.start)
    }

    @Test
    fun `applyMention does not double-space when existing trailing space is present`() {
        // "@bry rest" with caret right after "@bry"
        val value = TextFieldValue("@bry rest", selection = TextRange(4))
        val result = applyMention(value, "brycef")
        assertEquals("@brycef rest", result.text)
        assertEquals(8, result.selection.start)
    }

    // ── parseHashtagQuery ──

    @Test
    fun `hashtag returns null when no hashtag present`() {
        assertNull(parseHashtagQuery("hello world"))
    }

    @Test
    fun `hashtag returns empty string for a bare hash so trending can open`() {
        // Distinct from parseMentionQuery, which returns null for a lone "@".
        assertEquals("", parseHashtagQuery("hey #"))
    }

    @Test
    fun `hashtag detects query after space`() {
        assertEquals("jaz", parseHashtagQuery("love #jaz"))
    }

    @Test
    fun `hashtag detects query at start of text`() {
        assertEquals("music", parseHashtagQuery("#music"))
    }

    @Test
    fun `hashtag lowercases the query`() {
        assertEquals("jazztuesday", parseHashtagQuery("#JazzTuesday"))
    }

    @Test
    fun `hashtag only reads the word containing the caret`() {
        assertEquals("jazz", parseHashtagQuery("#jazz #film", caret = 5))
        assertEquals("film", parseHashtagQuery("#jazz #film", caret = 11))
    }

    @Test
    fun `hashtag closes once trailing punctuation is typed`() {
        assertNull(parseHashtagQuery("#jazz,", caret = 6))
    }

    @Test
    fun `hashtag ignores an at-mention token`() {
        assertNull(parseHashtagQuery("@gabe"))
    }

    // ── applyHashtag ──

    @Test
    fun `applyHashtag replaces partial tag with full tag and trailing space`() {
        assertEquals("love #jazztuesday ", applyHashtag("love #jaz", "jazztuesday"))
    }

    @Test
    fun `applyHashtag completes a bare hash into a tapped trending tag`() {
        assertEquals("hey #nowplaying ", applyHashtag("hey #", "nowplaying"))
    }

    @Test
    fun `applyHashtag preserves text with no tag in progress`() {
        assertEquals("hello world", applyHashtag("hello world", "jazz"))
    }

    @Test
    fun `applyHashtag(TextFieldValue) preserves text after caret and parks cursor after the space`() {
        // Caret sits right after "#jaz" (position 7), before the space.
        val value = TextFieldValue("go #jaz here", selection = TextRange(7))
        val result = applyHashtag(value, "jazz")
        assertEquals("go #jazz here", result.text)
        // Cursor lands after the reused space, before "here".
        assertEquals(9, result.selection.start)
    }

    @Test
    fun `applyHashtag(TextFieldValue) does not double-space when a trailing space is present`() {
        val value = TextFieldValue("#jaz rest", selection = TextRange(4))
        val result = applyHashtag(value, "jazz")
        assertEquals("#jazz rest", result.text)
        assertEquals(6, result.selection.start)
    }

    // ── buildMentionAnnotatedString ──
    // Regression coverage: comment bodies in SinglePostCommentsScreen and the inline
    // comment row in PostDetailScreen render through this builder. @mentions and
    // #hashtags must end up with tappable annotations or those screens go back to
    // plain text.

    @Test
    fun `buildMentionAnnotatedString tags @mention with mention annotation and strips the @`() {
        val result = buildMentionAnnotatedString("hey @gideon")
        val annotations = result.getStringAnnotations("mention", 0, result.length)
        assertEquals(1, annotations.size)
        assertEquals("gideon", annotations[0].item)
    }

    @Test
    fun `buildMentionAnnotatedString tags #hashtag with hashtag annotation and strips the hash`() {
        val result = buildMentionAnnotatedString("great #music today")
        val annotations = result.getStringAnnotations("hashtag", 0, result.length)
        assertEquals(1, annotations.size)
        assertEquals("music", annotations[0].item)
    }

    @Test
    fun `buildMentionAnnotatedString handles mention and hashtag in the same string`() {
        val result = buildMentionAnnotatedString("@alice check out #jazz")
        assertEquals("alice", result.getStringAnnotations("mention", 0, result.length).single().item)
        assertEquals("jazz", result.getStringAnnotations("hashtag", 0, result.length).single().item)
    }

    @Test
    fun `buildMentionAnnotatedString handles dotted usernames`() {
        val result = buildMentionAnnotatedString("cc @john.doe")
        assertEquals("john.doe", result.getStringAnnotations("mention", 0, result.length).single().item)
    }

    @Test
    fun `buildMentionAnnotatedString leaves plain text with no annotations`() {
        val result = buildMentionAnnotatedString("just a plain comment")
        assertEquals(0, result.getStringAnnotations("mention", 0, result.length).size)
        assertEquals(0, result.getStringAnnotations("hashtag", 0, result.length).size)
    }

    @Test
    fun `buildMentionAnnotatedString preserves full plain text alongside annotated tokens`() {
        val source = "hey @gideon about #music"
        val result = buildMentionAnnotatedString(source)
        assertEquals(source, result.text)
    }

    // ── bioTruncationCutoff ──
    // Longest prefix that still leaves room for "... more" inside the collapsed line budget.

    @Test
    fun `bio truncation fills the full three-line budget`() {
        // Hard-wrapped short lines: the old back-off-and-walk-to-a-space heuristic crossed a
        // newline and collapsed this to two lines ("The best / Of... more").
        val bio = "The best\nOf the\nBest\nof the best in the game"
        val cutoff = bioTruncationCutoff(bio) { candidate -> fitsInThreeLines(candidate) }
        val display = bioCollapsedDisplay(bio, cutoff)
        assertEquals(3, lineCount(display, charsPerLine = 20))
        assertEquals("The best\nOf the\nBest... more", display)
    }

    @Test
    fun `bio truncation packs the last line up to the more label`() {
        val bio = "dj and event producer published in brooklyn magazine every single month"
        val cutoff = bioTruncationCutoff(bio) { candidate -> fitsInThreeLines(candidate) }
        val display = bioCollapsedDisplay(bio, cutoff)
        assertEquals(3, lineCount(display, charsPerLine = 20))
        // One more character would have pushed "... more" onto a fourth line.
        assertEquals(4, lineCount(bioCollapsedDisplay(bio, cutoff + 1), charsPerLine = 20))
    }

    @Test
    fun `bio truncation returns zero when nothing fits`() {
        assertEquals(0, bioTruncationCutoff("some bio") { false })
    }

    @Test
    fun `bioCollapsedDisplay trims the trailing space before the ellipsis`() {
        assertEquals("hello... more", bioCollapsedDisplay("hello world", 6))
    }

    // ── buildLinkifiedBio ──

    @Test
    fun `buildLinkifiedBio tags emails and links without altering the text`() {
        val bio = "culture writer\narielle@corus.fm\nlinktr.ee/ariellenyc"
        val result = buildLinkifiedBio(bio, Color.Blue)
        assertEquals(bio, result.text)
        val targets = result
            .getStringAnnotations(tag = "link", start = 0, end = bio.length)
            .map { it.item }
        assertEquals(listOf("arielle@corus.fm", "linktr.ee/ariellenyc"), targets)
    }

    @Test
    fun `buildLinkifiedBio leaves plain prose alone`() {
        val bio = "brooklyn / culture writer / heads know"
        val result = buildLinkifiedBio(bio, Color.Blue)
        assertEquals(bio, result.text)
        assertEquals(0, result.getStringAnnotations(tag = "link", start = 0, end = bio.length).size)
    }

    @Test
    fun `buildLinkifiedBio drops sentence punctuation trailing a link`() {
        val bio = "find me at corus.fm."
        val result = buildLinkifiedBio(bio, Color.Blue)
        assertEquals(bio, result.text)
        assertEquals(
            listOf("corus.fm"),
            result.getStringAnnotations(tag = "link", start = 0, end = bio.length).map { it.item },
        )
    }

    // ── bioLinkUri ──

    @Test
    fun `bioLinkUri routes emails to mailto and bare domains to https`() {
        assertEquals("mailto:arielle@corus.fm", bioLinkUri("arielle@corus.fm"))
        assertEquals("https://linktr.ee/ariellenyc", bioLinkUri("linktr.ee/ariellenyc"))
        assertEquals("https://corus.fm/brand", bioLinkUri("https://corus.fm/brand"))
    }

    /** Deterministic stand-in for text layout: hard breaks plus fixed-width wrapping. */
    private fun lineCount(text: String, charsPerLine: Int): Int =
        text.split("\n").sumOf { segment ->
            maxOf(1, (segment.length + charsPerLine - 1) / charsPerLine)
        }

    private fun fitsInThreeLines(candidate: String): Boolean =
        lineCount(candidate, charsPerLine = 20) <= 3
}
