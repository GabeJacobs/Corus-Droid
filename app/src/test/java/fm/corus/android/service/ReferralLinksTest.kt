package fm.corus.android.service
import org.junit.Assert.*
import org.junit.Test
class ReferralLinksTest {
    private val code = "24ec0ab270a094ab59368c0f"
    @Test fun `branded codes preserve case and round trip canonical bytes`() {
        assertEquals(code, ReferralLinks.parse("https://invite.corus.fm/JOwKsnCglKtZNowP"))
        assertEquals("https://invite.corus.fm/JOwKsnCglKtZNowP", ReferralLinks.brandedURL(code))
        assertEquals("7K4N8X2P", ReferralLinks.parse("7k4n8x2p"))
    }
    @Test fun `attribution rejects duplicate and contradictory inviter destinations`() {
        val url = "https://corus.onelink.me/fgUi/?deep_link_value=invite&deep_link_sub1=$code"
        assertEquals(code, ReferralLinks.parse(url))
        assertNull(ReferralLinks.parse("$url&deep_link_sub1=$code"))
        assertNull(ReferralLinks.attribution("profile", code))
        assertNull(ReferralLinks.attribution("invite", code, "corus://invite/" + "a".repeat(24)))
        assertNull(ReferralLinks.parse("https://evil.test/invite/$code"))
        assertNull(ReferralLinks.parse("http://invite.corus.fm/JOwKsnCglKtZNowP"))
    }
}
