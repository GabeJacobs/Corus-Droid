package fm.corus.android.data.model
import org.junit.Assert.assertEquals
import org.junit.Test
class PostGiftSummaryTest {
    private val gabe = GiftSender("1", "gabe")
    private val clifton = GiftSender("2", "clifton")
    @Test fun uniqueSendersAreNotGiftCounts() {
        assertEquals("gabe sent 2 gifts", PostGiftSummary(2, 1, listOf(gabe)).attribution())
        assertEquals("3 gifts from gabe and clifton", PostGiftSummary(3, 2, listOf(gabe, clifton)).attribution())
        assertEquals("5 gifts from gabe and 2 others", PostGiftSummary(5, 3, listOf(gabe, clifton)).attribution())
        assertEquals("5 gifts · latest from gabe", PostGiftSummary(5, null, listOf(gabe)).attribution())
    }
}
