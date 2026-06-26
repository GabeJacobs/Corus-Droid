package fm.corus.android.ui.screens.search

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
 * came up empty. They now render "Member since X ago" from `clubMemberSince`,
 * mirroring ClubMembersCardRail.
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
    fun `club members show a member-since subtitle, not a blank`() {
        val twoHoursAgo = Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000)
        val subtitle = subtitleForRow(context, clubMember(twoHoursAgo), "clubMembers")
        assertTrue(
            "Club-member subtitle should read 'Member since … ago' but was: $subtitle",
            subtitle != null && subtitle.startsWith("Member since ") && subtitle.endsWith(" ago"),
        )
    }

    @Test
    fun `club member with no join date has no subtitle`() {
        assertNull(subtitleForRow(context, clubMember(null), "clubMembers"))
    }
}
