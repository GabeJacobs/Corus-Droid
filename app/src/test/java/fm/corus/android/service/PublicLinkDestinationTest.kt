package fm.corus.android.service

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * What a tapped corus.fm link asks the app to open.
 *
 * The public link shapes are owned by
 * `Corus-Web/app/lib/seo/public-routes.ts`; every segment listed there that the
 * app has a screen for has to land somewhere here.
 */
class PublicLinkDestinationTest {

    private fun fakeUri(scheme: String, host: String, segments: List<String>): Uri = mock {
        on { this.scheme } doReturn scheme
        on { this.host } doReturn host
        on { this.pathSegments } doReturn segments
    }

    private fun public(vararg segments: String) =
        DeepLinkHandler.parse(fakeUri("https", "corus.fm", segments.toList()))

    private fun scheme(host: String, vararg segments: String) =
        DeepLinkHandler.parse(fakeUri("corus", host, segments.toList()))

    @Test
    fun `profile and post links keep opening their screens`() {
        assertEquals(DeepLinkDestination.ProfileByUsername("gabe"), public("u", "gabe"))
        assertEquals(DeepLinkDestination.Post("abc123"), public("post", "abc123"))
    }

    /**
     * THE clean-URL regression. Android claimed neither the path nor the parse
     * for catalog links, so a slug URL opened the browser instead of the app.
     */
    @Test
    fun `clean catalog urls carry their key through`() {
        assertEquals(
            DeepLinkDestination.Entity(EntitySegment.ARTIST, "radiohead"),
            public("artist", "radiohead"),
        )
        assertEquals(
            DeepLinkDestination.Entity(EntitySegment.ALBUM, "burial-burial"),
            public("album", "burial-burial"),
        )
        assertEquals(
            DeepLinkDestination.Entity(EntitySegment.SONG, "burial-archangel"),
            public("song", "burial-archangel"),
        )
        assertEquals(
            DeepLinkDestination.Entity(EntitySegment.FILM, "the-dark-knight"),
            public("film", "the-dark-knight"),
        )
        assertEquals(
            DeepLinkDestination.Entity(EntitySegment.DIRECTOR, "christopher-nolan"),
            public("director", "christopher-nolan"),
        )
    }

    @Test
    fun `id catalog urls still carry their key through`() {
        assertEquals(
            DeepLinkDestination.Entity(EntitySegment.ARTIST, "4Z8W4fKeB5YxbusRsdQVPb"),
            public("artist", "4Z8W4fKeB5YxbusRsdQVPb"),
        )
        assertEquals(DeepLinkDestination.Entity(EntitySegment.FILM, "27205"), public("film", "27205"))
        assertEquals(
            DeepLinkDestination.Entity(EntitySegment.SONG, "am:1440857781"),
            public("song", "am:1440857781"),
        )
    }

    /**
     * A segment the parser silently dropped would be a link that opens the app
     * and then does nothing at all.
     */
    @Test
    fun `every catalog segment reaches a destination`() {
        for (segment in EntitySegment.entries) {
            assertNotNull(segment.segment, public(segment.segment, "anything-here"))
        }
    }

    @Test
    fun `unknown segments fall through to the browser`() {
        assertNull(public("privacy"))
        assertNull(public("hashtag", "jazz"))
        assertNull(public("artist"))
        assertNull(public())
    }

    /**
     * app.corus.fm IS the web app. Only the Club paywall path is claimed; every
     * other path there must stay in the browser rather than be answered by a
     * second copy of the same screen.
     */
    @Test
    fun `only the club path is claimed on the app host`() {
        val app = { segments: List<String> ->
            DeepLinkHandler.parse(fakeUri("https", "app.corus.fm", segments))
        }
        assertEquals(DeepLinkDestination.Club, app(listOf("settings", "club")))
        assertNull(app(listOf("feed")))
        assertNull(app(listOf("artist", "radiohead")))
        assertNull(app(listOf("u", "gabe")))
        assertNull(app(listOf("post", "abc123")))
        assertNull(app(listOf("settings", "notifications")))
        assertNull(app(listOf("settings", "club", "extra")))
    }

    @Test
    fun `corus scheme carries catalog keys through too`() {
        assertEquals(
            DeepLinkDestination.Entity(EntitySegment.ARTIST, "radiohead"),
            scheme("artist", "radiohead"),
        )
        assertEquals(
            DeepLinkDestination.Entity(EntitySegment.DIRECTOR, "christopher-nolan"),
            scheme("director", "christopher-nolan"),
        )
        assertEquals(DeepLinkDestination.Club, scheme("club"))
        assertNull(scheme("nonsense", "x"))
    }
}
