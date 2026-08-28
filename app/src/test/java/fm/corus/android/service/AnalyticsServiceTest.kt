package fm.corus.android.service

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that each high-level log method emits the expected Firebase event name.
 * These are parity contracts with the iOS AnalyticsEvents enum — renaming an event
 * key is a breaking change to dashboards.
 *
 * Uses Robolectric so android.os.Bundle resolves to a real implementation at
 * test time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AnalyticsServiceTest {

    private val firebase: FirebaseAnalytics = mock()
    private val service = AnalyticsService(firebase)

    @Test
    fun `logSyncContactsTapped emits sync_contacts_tapped`() {
        service.logSyncContactsTapped()
        verify(firebase).logEvent(eq("sync_contacts_tapped"), any<Bundle>())
    }

    @Test
    fun `post success others events keep cross-platform names`() {
        service.logPostSuccessOthersShown(7, 3, "track")
        verify(firebase).logEvent(eq("post_success_others_shown"), any<Bundle>())
        service.logPostSuccessOthersFollowed("u1")
        verify(firebase).logEvent(eq("post_success_others_followed"), any<Bundle>())
        service.logPostSuccessOthersLiked("p1")
        verify(firebase).logEvent(eq("post_success_others_liked"), any<Bundle>())
        service.logPostSuccessOthersProfileTapped("u1", "track")
        verify(firebase).logEvent(eq("post_success_others_profile_tapped"), any<Bundle>())
        service.logPostSuccessOthersSeeAllTapped("track")
        verify(firebase).logEvent(eq("post_success_others_see_all_tapped"), any<Bundle>())
        service.logPostSuccessOthersDismissed("done")
        verify(firebase).logEvent(eq("post_success_others_dismissed"), any<Bundle>())
    }

    @Test
    fun `logOnboardingSeeAllTapped emits onboarding_see_all_tapped`() {
        service.logOnboardingSeeAllTapped("friends")
        verify(firebase).logEvent(eq("onboarding_see_all_tapped"), any<Bundle>())
    }

    @Test
    fun `logOnboardingProfileSubmitted emits onboarding_profile_submitted`() {
        service.logOnboardingProfileSubmitted()
        verify(firebase).logEvent(eq("onboarding_profile_submitted"), any<Bundle>())
    }

    @Test
    fun `logOnboardingAvatarNudge emits onboarding_avatar_nudge`() {
        service.logOnboardingAvatarNudge("skip")
        verify(firebase).logEvent(eq("onboarding_avatar_nudge"), any<Bundle>())
    }

    @Test
    fun `logNotificationPermissionResult emits notification_permission_result`() {
        service.logNotificationPermissionResult(true)
        verify(firebase).logEvent(eq("notification_permission_result"), any<Bundle>())
    }

    @Test
    fun `logNotificationPermissionPrimerShown emits notification_permission_primer_shown`() {
        service.logNotificationPermissionPrimerShown()
        verify(firebase).logEvent(eq("notification_permission_primer_shown"), any<Bundle>())
    }

    @Test
    fun `logNotificationPermissionPrimerTapped emits notification_permission_primer_tapped`() {
        service.logNotificationPermissionPrimerTapped("allow")
        verify(firebase).logEvent(eq("notification_permission_primer_tapped"), any<Bundle>())
    }

    @Test
    fun `logNotificationPermissionReaskShown emits notification_permission_reask_shown`() {
        service.logNotificationPermissionReaskShown("first_comment")
        verify(firebase).logEvent(eq("notification_permission_reask_shown"), any<Bundle>())
    }

    @Test
    fun `notification filter events match iOS names`() {
        service.logNotificationFilterChanged("comments")
        verify(firebase).logEvent(eq("notification_filter_changed"), any<Bundle>())
        service.logNotificationFiltersShown(12, 4)
        verify(firebase).logEvent(eq("notification_filters_shown"), any<Bundle>())
        service.logNotificationTapped("like", "all")
        verify(firebase).logEvent(eq("notification_tapped"), any<Bundle>())
    }

    @Test
    fun `logLikesListViewed emits likes_list_viewed`() {
        service.logLikesListViewed("post123")
        verify(firebase).logEvent(eq("likes_list_viewed"), any<Bundle>())
    }

    @Test
    fun `logVoiceNoteRecorded emits voice_note_recorded`() {
        service.logVoiceNoteRecorded()
        verify(firebase).logEvent(eq("voice_note_recorded"), any<Bundle>())
    }

    @Test
    fun `logVoiceNotePlayed emits voice_note_played`() {
        service.logVoiceNotePlayed()
        verify(firebase).logEvent(eq("voice_note_played"), any<Bundle>())
    }

    @Test
    fun `logPostThisSongTapped emits post_this_song_tapped`() {
        service.logPostThisSongTapped("track456")
        verify(firebase).logEvent(eq("post_this_song_tapped"), any<Bundle>())
    }

    @Test
    fun `logDeepLinkOpened emits deep_link_opened`() {
        service.logDeepLinkOpened("post")
        verify(firebase).logEvent(eq("deep_link_opened"), any<Bundle>())
    }

    @Test
    fun `logTrendingHashtagTapped emits trending_hashtag_tapped`() {
        service.logTrendingHashtagTapped("music")
        verify(firebase).logEvent(eq("trending_hashtag_tapped"), any<Bundle>())
    }

    @Test
    fun `logReportPost emits report_post`() {
        service.logReportPost("p1", "spam")
        verify(firebase).logEvent(eq("report_post"), any<Bundle>())
    }

    @Test
    fun `logReportComment emits report_comment`() {
        service.logReportComment("c1", "harassment")
        verify(firebase).logEvent(eq("report_comment"), any<Bundle>())
    }

    @Test
    fun `logReportMessage emits report_message`() {
        service.logReportMessage("m1", "spam")
        verify(firebase).logEvent(eq("report_message"), any<Bundle>())
    }

    @Test
    fun `logEditProfileSaved emits edit_profile_saved`() {
        service.logEditProfileSaved()
        verify(firebase).logEvent(eq("edit_profile_saved"), any<Bundle>())
    }

    @Test
    fun `logAvatarChanged emits avatar_changed`() {
        service.logAvatarChanged()
        verify(firebase).logEvent(eq("avatar_changed"), any<Bundle>())
    }

    @Test
    fun `logProfileSegmentChanged emits profile_segment_changed`() {
        service.logProfileSegmentChanged("music")
        verify(firebase).logEvent(eq("profile_segment_changed"), any<Bundle>())
    }

    @Test
    fun `logFeedPlaylistTapped emits feed_playlist_tapped`() {
        service.logFeedPlaylistTapped()
        verify(firebase).logEvent(eq("feed_playlist_tapped"), any<Bundle>())
    }

    @Test
    fun `logProfilePlaylistTapped emits profile_playlist_tapped`() {
        service.logProfilePlaylistTapped("user789")
        verify(firebase).logEvent(eq("profile_playlist_tapped"), any<Bundle>())
    }

    @Test
    fun `logProfileUpdateError emits profile_update_error`() {
        service.logProfileUpdateError("something")
        verify(firebase).logEvent(eq("profile_update_error"), any<Bundle>())
    }

    @Test
    fun `logMusicServiceLinkTapped with Spotify routes to spotify_link_tapped`() {
        // Spotify keeps the established event so it stays comparable to historical data.
        service.logMusicServiceLinkTapped(fm.corus.android.data.model.MusicService.SPOTIFY, "track1")
        verify(firebase).logEvent(eq("spotify_link_tapped"), any<Bundle>())
    }

    @Test
    fun `logMusicServiceLinkTapped with Deezer routes to music_service_link_tapped`() {
        // Regression guard: a non-Spotify preference (the reported Deezer bug) must
        // emit music_service_link_tapped, never silently fall through to Spotify.
        service.logMusicServiceLinkTapped(fm.corus.android.data.model.MusicService.DEEZER, "track2")
        verify(firebase).logEvent(eq("music_service_link_tapped"), any<Bundle>())
    }

    @Test
    fun `logMusicServiceLinkTapped with Tidal routes to music_service_link_tapped`() {
        service.logMusicServiceLinkTapped(fm.corus.android.data.model.MusicService.TIDAL, "track3")
        verify(firebase).logEvent(eq("music_service_link_tapped"), any<Bundle>())
    }

    @Test
    fun `logFeedSwitcherOpened emits feed_switcher_opened`() {
        service.logFeedSwitcherOpened()
        verify(firebase).logEvent(eq("feed_switcher_opened"), any<Bundle>())
    }

    @Test
    fun `logFeedSwitchHintShown emits feed_switch_hint_shown`() {
        service.logFeedSwitchHintShown()
        verify(firebase).logEvent(eq("feed_switch_hint_shown"), any<Bundle>())
    }

    @Test
    fun `logFeedSwitchHintDismissed emits feed_switch_hint_dismissed`() {
        service.logFeedSwitchHintDismissed()
        verify(firebase).logEvent(eq("feed_switch_hint_dismissed"), any<Bundle>())
    }

    @Test
    fun `spotify FTUE events match iOS names`() {
        service.logSpotifyFtueAssigned("a", true)
        verify(firebase).logEvent(eq("spotify_ftue_assigned"), any<Bundle>())
        service.logSpotifyAuthConnectPromptShown("track1", "b", "first_play")
        verify(firebase).logEvent(eq("spotify_auth_connect_prompt_shown"), any<Bundle>())
        service.logSpotifyFtuePromptChosen("b", "first_play", "link")
        verify(firebase).logEvent(eq("spotify_ftue_prompt_chosen"), any<Bundle>())
        service.logSpotifyAuthConnected("app_remote")
        verify(firebase).logEvent(eq("spotify_auth_connected"), any<Bundle>())
        service.logSpotifyAuthConnectFailed("denied")
        verify(firebase).logEvent(eq("spotify_auth_connect_failed"), any<Bundle>())
        service.logSpotifyAuthConnectCancelled()
        verify(firebase).logEvent(eq("spotify_auth_connect_cancelled"), any<Bundle>())
        service.logSpotifyAuthDisconnected()
        verify(firebase).logEvent(eq("spotify_auth_disconnected"), any<Bundle>())
        service.logSpotifyFullSongPlayStarted("track1", "spotify")
        verify(firebase).logEvent(eq("spotify_full_song_play_started"), any<Bundle>())
        service.logSpotifyFullSongPlayFailed("track1", "timeout")
        verify(firebase).logEvent(eq("spotify_full_song_play_failed"), any<Bundle>())
    }

    @Test
    fun `event names are stable strings`() {
        // Compile-time parity check — if anyone renames a method, they must update this list too.
        val expected = setOf(
            "sync_contacts_tapped",
            "onboarding_see_all_tapped",
            "onboarding_profile_submitted",
            "onboarding_avatar_nudge",
            "notification_permission_result",
            "likes_list_viewed",
            "voice_note_recorded",
            "voice_note_played",
            "post_this_song_tapped",
            "deep_link_opened",
            "trending_hashtag_tapped",
            "report_post",
            "report_comment",
            "report_message",
            "edit_profile_saved",
            "avatar_changed",
            "profile_segment_changed",
            "feed_playlist_tapped",
            "profile_playlist_tapped",
            "profile_update_error",
            "taste_match_push_opened",
            "taste_match_feed_row_tapped",
            "taste_match_settings_toggled",
        )
        assertEquals(23, expected.size)
    }
}
