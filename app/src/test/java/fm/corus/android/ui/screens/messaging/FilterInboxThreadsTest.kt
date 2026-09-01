package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterInboxThreadsTest {

    private fun user(id: String, username: String, displayName: String) = CymbalUser(
        id = id,
        username = username,
        displayName = displayName,
    )

    private fun thread(id: String, user: CymbalUser, last: String) = CymbalThread(
        id = id,
        otherUser = user,
        otherUserId = user.id,
        lastMessageText = last,
        lastMessageFromUserId = user.id,
    )

    private val alex = thread("t1", user("u1", "alexx", "Alex Stone"), "Hey just wanted to say welcome")
    private val cam = thread("t2", user("u2", "cam", "Cameron"), "Long time no see")
    private val johnny = thread("t3", user("u3", "johnnyapple", "Johnny Apple"), "This is a test")
    private val all = listOf(alex, cam, johnny)

    @Test
    fun emptyQueryReturnsAll() {
        assertEquals(all, filterInboxThreads(all, ""))
        assertEquals(all, filterInboxThreads(all, "   "))
    }

    @Test
    fun matchesByUsername() {
        assertEquals(listOf(alex), filterInboxThreads(all, "alex"))
    }

    @Test
    fun matchesByDisplayName() {
        assertEquals(listOf(cam), filterInboxThreads(all, "Cameron"))
    }

    @Test
    fun matchesByLastMessagePreview() {
        assertEquals(listOf(johnny), filterInboxThreads(all, "test"))
    }

    @Test
    fun matchIsCaseInsensitive() {
        assertEquals(listOf(alex), filterInboxThreads(all, "ALEX"))
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertEquals(emptyList<CymbalThread>(), filterInboxThreads(all, "zzzzzz"))
    }

    @Test
    fun chatsSectionExcludesLastMessageOnlyHits() {
        assertEquals(emptyList<CymbalThread>(), filterInboxChats(all, "test"))
        assertEquals(listOf(cam), filterInboxChats(all, "Cameron"))
    }

    @Test
    fun messagesSectionExcludesNameOnlyHits() {
        assertEquals(emptyList<CymbalThread>(), filterInboxMessagePreviews(all, "Cameron"))
        assertEquals(listOf(johnny), filterInboxMessagePreviews(all, "test"))
    }
}
