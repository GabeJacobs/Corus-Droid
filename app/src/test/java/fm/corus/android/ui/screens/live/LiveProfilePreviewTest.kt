package fm.corus.android.ui.screens.live

import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.*
import org.junit.Test

class LiveProfilePreviewTest {
    @Test fun showsKnownDJIdentityBeforeFetchingProfile() {
        val room = LiveRoom("room", mapOf("hostId" to "host", "djs" to listOf(
            mapOf("uid" to "dj", "username" to "snacks", "avatar" to "avatar-url")
        )))
        val route = liveProfilePreview("dj", LiveUiState(room = room), null)
        assertEquals("snacks", route.initialUsername)
        assertEquals("avatar-url", route.avatarURL)
        assertNull(route.initialDisplayName)
        assertNull(route.initialFollowerCount)
    }

    @Test fun passesCachedProfileDetailsWithoutInventingCounts() {
        val user = CymbalUser("dj", "snacks", "Matthew", avatarURL = "full", avatarThumbURL = "thumb", bio = "Music", isVerified = true)
        val route = liveProfilePreview("dj", LiveUiState(), LivePerson("dj", "snacks", "full", user))
        assertEquals("Matthew", route.initialDisplayName)
        assertEquals("thumb", route.avatarThumbURL)
        assertEquals("Music", route.initialBio)
        assertEquals(true, route.initialIsVerified)
        assertNull(route.initialFollowerCount)
    }

    @Test fun passesChatAuthorIdentity() {
        val message = LiveMessage("m", mapOf("uid" to "chat", "username" to "alice", "avatar" to "chat-avatar"))
        val route = liveProfilePreview("chat", LiveUiState(messages = listOf(message)), null)
        assertEquals("alice", route.initialUsername)
        assertEquals("chat-avatar", route.avatarURL)
    }
}
