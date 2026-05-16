package fm.corus.android.domain

import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RecentLikersTest {

    private fun user(id: String) = CymbalUser(id = id, username = id, displayName = id)

    private val me = user("me")
    private val alice = user("alice")
    private val bob = user("bob")

    @Test fun `returns input unchanged when current user is unknown`() {
        val likers = listOf(alice, bob)
        val result = reconcileRecentLikers(likers, isLikedByCurrentUser = true, currentUser = null)
        assertSame("must not allocate when there's nothing to reconcile", likers, result)
    }

    @Test fun `prepends current user when liked and missing from list`() {
        val result = reconcileRecentLikers(listOf(alice, bob), isLikedByCurrentUser = true, currentUser = me)
        assertEquals(listOf(me, alice, bob), result)
    }

    @Test fun `moves current user to front when liked and present further down`() {
        // Server preview can return the current user not-first (e.g. ordered by like time).
        val result = reconcileRecentLikers(listOf(alice, me, bob), isLikedByCurrentUser = true, currentUser = me)
        assertEquals(listOf(me, alice, bob), result)
    }

    @Test fun `removes current user when not liked but present in stale list`() {
        // Stale server preview after the user unliked from another device.
        val result = reconcileRecentLikers(listOf(me, alice), isLikedByCurrentUser = false, currentUser = me)
        assertEquals(listOf(alice), result)
    }

    @Test fun `leaves list alone when not liked and user not present`() {
        val likers = listOf(alice, bob)
        val result = reconcileRecentLikers(likers, isLikedByCurrentUser = false, currentUser = me)
        assertEquals(listOf(alice, bob), result)
    }

    @Test fun `produces single-entry list when liked with empty preview`() {
        // Covers the bug scenario seed path: the post had likers stripped or
        // never carried any, but the user has liked it on another device.
        val result = reconcileRecentLikers(emptyList(), isLikedByCurrentUser = true, currentUser = me)
        assertEquals(listOf(me), result)
    }

    @Test fun `preserves order of other likers`() {
        val result = reconcileRecentLikers(listOf(alice, bob, me), isLikedByCurrentUser = true, currentUser = me)
        assertEquals("alice and bob keep their relative order", listOf(me, alice, bob), result)
    }

    // freshenIfSelf: prevents the signed-in user from seeing their own stale
    // displayName/username in server-supplied liker lists after a profile edit.

    @Test fun `freshenIfSelf swaps stale self entry for live current user`() {
        val staleMe = CymbalUser(id = "me", username = "old_handle", displayName = "Old Name")
        val liveMe = CymbalUser(id = "me", username = "new_handle", displayName = "New Name")
        val result = freshenIfSelf(staleMe, currentUser = liveMe)
        assertSame(liveMe, result)
    }

    @Test fun `freshenIfSelf leaves other users untouched`() {
        val result = freshenIfSelf(alice, currentUser = me)
        assertSame(alice, result)
    }

    @Test fun `freshenIfSelf is a no-op when current user is unknown`() {
        val result = freshenIfSelf(alice, currentUser = null)
        assertSame(alice, result)
    }
}
