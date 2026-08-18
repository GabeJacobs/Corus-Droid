package fm.corus.android.ui.screens.search

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Regression test for the Club Members see-all cards showing their subtitle.
 *
 * The bug: Club Members rendered no subtitle at all — [subtitleForRow] sent them
 * down the mutual-followers branch, which club members don't carry, so the line
 * came up empty. They now render the user's display name, matching the rail.
 *
 * Robolectric is needed for the real string resource + relative-time formatting.
 * Like the repo's other Robolectric tests (e.g. TasteMatchCardLayoutTest), this
 * runs in Android Studio / CI — not from a JDK-25 CLI, where Robolectric's ASM
 * can't read the class files. The line-count split is covered by the pure
 * SubtitleLinesForSourceTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SuggestedUsersListSubtitleTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private fun clubMember(since: Date?) = SuggestedUserMatch(
        user = CymbalUser(id = "u1", username = "alice", displayName = "Alice", clubMemberSince = since),
        matchData = null,
        suggestionReason = null,
    )

    @Test
    fun `club members show their display name`() {
        val twoHoursAgo = Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000)
        assertEquals("Alice", subtitleForRow(context, clubMember(twoHoursAgo), "clubMembers"))
    }

    @Test
    fun `club member with a blank display name has no subtitle`() {
        val match = SuggestedUserMatch(
            user = CymbalUser(id = "u1", username = "alice", displayName = "  ", clubMemberSince = Date()),
            matchData = null,
            suggestionReason = null,
        )
        assertNull(subtitleForRow(context, match, "clubMembers"))
    }
}
