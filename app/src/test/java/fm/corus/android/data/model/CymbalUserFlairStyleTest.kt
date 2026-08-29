package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Free users always resolve to None. Club/verified keep their saved pick;
 * a missing stored value still parses as checkmark so a newly granted
 * member shows the badge without waiting on a second write.
 */
class CymbalUserFlairStyleTest {

    @Test
    fun `free user effective flair is None even when stored value is checkmark`() {
        val user = user(isClubMember = false, isVerified = false, profileFlair = "checkmark")
        assertEquals(FlairStyle.NONE, user.flairStyle)
        assertEquals(FlairStyle.CHECKMARK, user.rawFlairStyle)
    }

    @Test
    fun `free user effective flair is None when the field is absent`() {
        val user = CymbalUser.fromMap(
            "u1",
            mapOf("username" to "hopk", "displayName" to "hopk"),
        )
        assertEquals(FlairStyle.NONE, user.flairStyle)
    }

    @Test
    fun `club member with no stored flair defaults to Checkmark`() {
        val user = CymbalUser.fromMap(
            "u1",
            mapOf(
                "username" to "hopk",
                "displayName" to "hopk",
                "isClubMember" to true,
            ),
        )
        assertEquals(FlairStyle.CHECKMARK, user.flairStyle)
    }

    @Test
    fun `club member can keep None`() {
        val user = user(isClubMember = true, isVerified = false, profileFlair = "none")
        assertEquals(FlairStyle.NONE, user.flairStyle)
    }

    @Test
    fun `verified member can keep a custom flair`() {
        val user = user(isClubMember = false, isVerified = true, profileFlair = "vinyl")
        assertEquals(FlairStyle.VINYL, user.flairStyle)
    }

    private fun user(
        isClubMember: Boolean,
        isVerified: Boolean,
        profileFlair: String,
    ) = CymbalUser(
        id = "u1",
        username = "hopk",
        displayName = "hopk",
        isVerified = isVerified,
        isClubMember = isClubMember,
        profileFlair = profileFlair,
    )
}
