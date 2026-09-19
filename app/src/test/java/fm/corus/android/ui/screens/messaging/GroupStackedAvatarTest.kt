package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupStackedAvatarTest {
    private fun user(id: String) = CymbalUser(id = id, username = id, displayName = id)

    @Test
    fun prefersLastWritersAndSkipsViewer() {
        val members = listOf(user("a"), user("b"), user("c"), user("me")).associateBy { it.id }
        val shown = stackedAvatarMembers(
            membersById = members,
            currentUserId = "me",
            lastWriterIds = listOf("me", "c", "a"),
            memberIds = listOf("a", "b", "c", "me"),
        )
        assertEquals(listOf("c", "a"), shown.map { it.id })
    }

    @Test
    fun fallsBackToLastSenderThenMembers() {
        val members = listOf(user("a"), user("b"), user("c")).associateBy { it.id }
        val shown = stackedAvatarMembers(
            membersById = members,
            currentUserId = "me",
            lastWriterIds = emptyList(),
            memberIds = listOf("a", "b", "c"),
            lastMessageFromUserId = "b",
        )
        assertEquals(listOf("b", "a"), shown.map { it.id })
    }

    @Test
    fun ignoresSystemActorsAsWriters() {
        val members = listOf(user("a"), user("b")).associateBy { it.id }
        val shown = stackedAvatarMembers(
            membersById = members,
            currentUserId = "me",
            lastWriterIds = emptyList(),
            memberIds = listOf("a", "b"),
            lastMessageFromUserId = "c",
            lastMessageIsSystem = true,
        )
        assertEquals(listOf("a", "b"), shown.map { it.id })
    }
}
