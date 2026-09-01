package fm.corus.android.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTabPreferencesHoldEmptyStateTest {

    @Test
    fun `holds skeleton when media backfill has not confirmed empty`() {
        // Film-primary profile, mixed page has no movies, fetch not started:
        // keep the skeleton so first load can't flash "No films yet".
        assertTrue(
            ProfileTabPreferences.shouldHoldEmptyState(
                isLoading = false,
                hasFetchedPage = false,
                itemCount = 0,
                totalCount = 12,
            )
        )
        // Unknown counter — same hold, matching songsPending's `?: 1`.
        assertTrue(
            ProfileTabPreferences.shouldHoldEmptyState(
                isLoading = false,
                hasFetchedPage = false,
                itemCount = 0,
                totalCount = null,
            )
        )
        // In-flight load/backfill keeps the skeleton even after the fetch flag flips.
        assertTrue(
            ProfileTabPreferences.shouldHoldEmptyState(
                isLoading = true,
                hasFetchedPage = true,
                itemCount = 0,
                totalCount = 12,
            )
        )
    }

    @Test
    fun `initial load holds empty even when trackCount is zero`() {
        // Own-profile tab is composed off-screen with an auth-seeded profile
        // and empty posts. isLoading starts true so the music empty prompt
        // cannot paint on first tap even if the denormalized counter is 0.
        assertTrue(
            ProfileTabPreferences.shouldHoldEmptyState(
                isLoading = true,
                hasFetchedPage = false,
                itemCount = 0,
                totalCount = 0,
            )
        )
    }

    @Test
    fun `releases hold once empty is confirmed`() {
        assertFalse(
            ProfileTabPreferences.shouldHoldEmptyState(
                isLoading = false,
                hasFetchedPage = false,
                itemCount = 0,
                totalCount = 0,
            )
        )
        assertFalse(
            ProfileTabPreferences.shouldHoldEmptyState(
                isLoading = false,
                hasFetchedPage = true,
                itemCount = 0,
                totalCount = 12,
            )
        )
        assertFalse(
            ProfileTabPreferences.shouldHoldEmptyState(
                isLoading = false,
                hasFetchedPage = false,
                itemCount = 3,
                totalCount = 12,
            )
        )
    }
}
