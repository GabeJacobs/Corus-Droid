package fm.corus.android.ui.screens.messaging

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageThreadLinkifyTest {

    @Test
    fun `urls stay annotated and underlined`() {
        val attributed = buildLinkifiedText("see https://corus.fm/hello and more", isFromCurrentUser = false)
        val url = attributed.getStringAnnotations("URL", 0, attributed.length).firstOrNull()
        assertEquals("https://corus.fm/hello", url?.item)
    }

    @Test
    fun `mentions are annotated and extra bold`() {
        val attributed = buildLinkifiedText("hey @gabe check this", isFromCurrentUser = false)
        val mention = attributed.getStringAnnotations("mention", 0, attributed.length).single()
        assertEquals("gabe", mention.item)
        val span = attributed.spanStyles.single { it.start == mention.start && it.end == mention.end }
        assertEquals(FontWeight.ExtraBold, span.item.fontWeight)
    }

    @Test
    fun `hashtags are annotated and extra bold like mentions`() {
        val attributed = buildLinkifiedText("love #tampopo tonight", isFromCurrentUser = false)
        val hashtag = attributed.getStringAnnotations("hashtag", 0, attributed.length).single()
        assertEquals("tampopo", hashtag.item)
        val span = attributed.spanStyles.single { it.start == hashtag.start && it.end == hashtag.end }
        assertEquals(FontWeight.ExtraBold, span.item.fontWeight)
    }

    @Test
    fun `own-bubble hashtags stay white and extra bold`() {
        val attributed = buildLinkifiedText("#jazztuesday", isFromCurrentUser = true)
        val hashtag = attributed.getStringAnnotations("hashtag", 0, attributed.length).single()
        val span = attributed.spanStyles.single { it.start == hashtag.start }
        assertEquals(Color.White, span.item.color)
        assertEquals(FontWeight.ExtraBold, span.item.fontWeight)
    }

    @Test
    fun `url fragment is not treated as a hashtag`() {
        val attributed = buildLinkifiedText("see https://corus.fm/hello#feed", isFromCurrentUser = false)
        assertTrue(attributed.getStringAnnotations("URL", 0, attributed.length).isNotEmpty())
        assertTrue(attributed.getStringAnnotations("hashtag", 0, attributed.length).isEmpty())
    }

    @Test
    fun `own-bubble mentions stay white`() {
        val attributed = buildLinkifiedText("hi @gabe", isFromCurrentUser = true)
        val mention = attributed.getStringAnnotations("mention", 0, attributed.length).single()
        val color = attributed.spanStyles.single { it.start == mention.start }.item.color
        assertEquals(Color.White, color)
    }

    @Test
    fun `plain text has no annotations`() {
        val attributed = buildLinkifiedText("just a message", isFromCurrentUser = false)
        assertTrue(attributed.getStringAnnotations("URL", 0, attributed.length).isEmpty())
        assertTrue(attributed.getStringAnnotations("mention", 0, attributed.length).isEmpty())
        assertTrue(attributed.getStringAnnotations("hashtag", 0, attributed.length).isEmpty())
        assertFalse(attributed.text.isEmpty())
    }
}
