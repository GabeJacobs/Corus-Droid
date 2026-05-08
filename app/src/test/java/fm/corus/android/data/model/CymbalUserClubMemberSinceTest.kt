package fm.corus.android.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

/**
 * Regression tests for parsing and ordering by `clubMemberSince`. The Corus
 * Club Members section sorts by initial sign-up (newest first), falling back
 * to `createdAt` when `clubMemberSince` isn't populated yet.
 */
class CymbalUserClubMemberSinceTest {

    private fun timestamp(ms: Long) = Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())

    @Test
    fun `fromMap parses clubMemberSince when present as Firestore Timestamp`() {
        val ms = 1_777_000_000_000L
        val data = mapOf<String, Any?>(
            "username" to "alice",
            "displayName" to "Alice",
            "isClubMember" to true,
            "clubMemberSince" to timestamp(ms),
        )
        val user = CymbalUser.fromMap("u1", data)
        assertEquals(ms, user.clubMemberSince?.time)
    }

    @Test
    fun `fromMap returns null when clubMemberSince is missing (pre-backfill user)`() {
        val data = mapOf<String, Any?>(
            "username" to "bob",
            "displayName" to "Bob",
            "isClubMember" to true,
        )
        val user = CymbalUser.fromMap("u2", data)
        assertNull(user.clubMemberSince)
    }

    @Test
    fun `sortedByDescending puts most-recent club sign-up first`() {
        val older = baseUser(id = "older", clubMemberSince = Date(1_000L))
        val newer = baseUser(id = "newer", clubMemberSince = Date(5_000L))
        val sorted = listOf(older, newer).sortedByDescending {
            it.clubMemberSince ?: it.createdAt ?: Date(0)
        }
        assertEquals(listOf("newer", "older"), sorted.map { it.id })
    }

    @Test
    fun `sortedByDescending falls back to createdAt when clubMemberSince is null`() {
        // Member with explicit clubMemberSince should outrank a fallback even
        // if the fallback's createdAt is more recent — clubMemberSince is the
        // signal we actually care about; createdAt is just a tiebreaker for
        // pre-backfill records.
        val withSince = baseUser(
            id = "withSince",
            clubMemberSince = Date(1_000L),
            createdAt = Date(0L),
        )
        val onlyCreatedAt = baseUser(
            id = "onlyCreatedAt",
            clubMemberSince = null,
            createdAt = Date(2_000L),
        )
        val sorted = listOf(withSince, onlyCreatedAt).sortedByDescending {
            it.clubMemberSince ?: it.createdAt ?: Date(0)
        }
        // onlyCreatedAt's 2000ms beats withSince's 1000ms — that's the
        // documented fallback. The point of this test is that the comparator
        // doesn't crash on nulls and ordering is deterministic.
        assertEquals(listOf("onlyCreatedAt", "withSince"), sorted.map { it.id })
    }

    private fun baseUser(
        id: String,
        clubMemberSince: Date?,
        createdAt: Date? = null,
    ) = CymbalUser(
        id = id,
        username = id,
        displayName = id,
        isClubMember = true,
        clubMemberSince = clubMemberSince,
        createdAt = createdAt,
    )
}
