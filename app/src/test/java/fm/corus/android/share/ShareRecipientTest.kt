package fm.corus.android.share

import fm.corus.android.data.model.*
import fm.corus.android.ui.screens.messaging.visibleInboxRows
import fm.corus.android.ui.screens.messaging.applyLiveThreadUpdates
import java.util.Date
import org.junit.Assert.*
import org.junit.Test

class ShareRecipientTest {
    private val person = CymbalUser(id = "friend", username = "friend", displayName = "Friend")
    private fun direct() = CymbalThread(id = "dm", otherUserId = person.id, otherUser = person,
        lastMessageFromUserId = person.id, lastMessageAt = Date(100))
    private fun group() = CymbalThread(id = "grp", isGroup = true, groupName = "Ocean Vibes",
        members = listOf(person), memberIds = listOf(person.id), lastMessageFromUserId = person.id, lastMessageAt = Date(200))

    @Test fun recentGroupLeadsEvenWhenAnOlderDirectChatIsPinned() {
        val result = recentShareRecipients(listOf(direct().copy(isPinned = true), group()))
        assertEquals(listOf("group:grp", "friend"), result.map { it.id })
        assertEquals("grp", result.first().group?.id)
    }

    @Test fun groupsMatchNamesAndMembersButNotMessageBodies() {
        assertTrue(groupMatchesShareQuery(group(), " OCEAN "))
        assertTrue(groupMatchesShareQuery(group(), "friend"))
        assertFalse(groupMatchesShareQuery(group().copy(lastMessageText = "secret"), "secret"))
    }

    @Test fun pinLeadsInboxAndUnpinRestoresRecency() {
        val old = direct().copy(isPinned = true)
        assertEquals(listOf("dm", "grp"), visibleInboxRows(listOf(group(), old), emptySet()) { false }.map { it.id })
        assertEquals(listOf("grp", "dm"), visibleInboxRows(listOf(old.copy(isPinned = false), group()), emptySet()) { false }.map { it.id })
    }

    @Test fun oldPinsDoNotPrunePreviouslyLoadedUnpinnedHistory() {
        val recent = direct().copy(updatedAt = Date(100))
        val paged = direct().copy(id = "paged", lastMessageAt = Date(50), updatedAt = Date(50))
        val pin = group().copy(lastMessageAt = Date(1), updatedAt = Date(1), isPinned = true, isOutsideRecentWindow = true)
        val result = applyLiveThreadUpdates(listOf(recent, paged, pin), listOf(recent, pin), 1)
        assertEquals(setOf("dm", "paged", "grp"), result.merged.map { it.id }.toSet())
    }

    @Test fun olderPinnedGroupCanEnterTheLiveWindow() {
        val old = group().copy(lastMessageAt = Date(1), isPinned = true)
        val merged = applyLiveThreadUpdates(listOf(direct()), listOf(old), 30)
        assertEquals(listOf("grp"), merged.newThreads.map { it.id })
    }
}
