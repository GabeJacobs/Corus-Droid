package fm.corus.android.domain

import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.PostRepository
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Mirrors iOS PostCard / PostDetailView / FeaturedCymbalView / FeaturedMoviePosterView /
 * PostContextMenu haptic behavior: a LIGHT impact fires as soon as the user
 * taps the heart/bookmark, before the network round-trip.
 *
 * Guards the injection wiring: if a refactor drops HapticManager from the
 * constructor, this test fails loudly — otherwise every like/save on Android
 * would silently stop vibrating while iOS keeps buzzing.
 */
class PostEngagementManagerHapticsTest {

    private fun newManager(haptic: HapticManager): PostEngagementManager = PostEngagementManager(
        postRepository = mock<PostRepository>(),
        firestoreDataSource = mock<FirestoreDataSource>(),
        hapticManager = haptic,
        subscriptionRepository = mock(),
        remoteConfig = mock(),
        analyticsService = mock(),
        saveChangedEvent = mock(),
    )

    @Test
    fun `toggleLike fires a LIGHT impact haptic`() {
        val haptic = mock<HapticManager>()
        val manager = newManager(haptic)
        manager.initState("p1", likeCount = 0, commentCount = 0, repostCount = 0, isLiked = false, isSaved = false)

        manager.toggleLike(postId = "p1", userId = "u1")

        verify(haptic).impact(HapticManager.ImpactStyle.LIGHT)
    }

    @Test
    fun `toggleSave fires a LIGHT impact haptic`() {
        val haptic = mock<HapticManager>()
        val manager = newManager(haptic)
        manager.initState("p1", likeCount = 0, commentCount = 0, repostCount = 0, isLiked = false, isSaved = false)

        manager.toggleSave(postId = "p1", userId = "u1")

        verify(haptic).impact(HapticManager.ImpactStyle.LIGHT)
    }
}
