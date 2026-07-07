package fm.corus.android.service

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canonical identifier for each section on the People search page.
 * Kept in sync with iOS (`SearchSection` enum) and Web (`SearchSection` union)
 * so cross-platform analytics queries can compare sections directly.
 */
enum class SearchSection(val value: String) {
    TasteMatches("taste_matches"),
    MutualConnections("mutual_connections"),
    FriendsOnCorus("friends_on_corus"),
    Popular("popular"),
    ClubMembers("club_members"),
    NewOnCorus("new_on_corus"),
}

@Singleton
class AnalyticsService @Inject constructor(
    private val analytics: FirebaseAnalytics,
) {
    init {
        // Mirrors iOS (`app_platform=ios`) and the web app so GA4 reports
        // can split web vs. iOS vs. Android cleanly.
        analytics.setUserProperty("app_platform", "android")
    }

    // MARK: - User Identity

    fun setUserId(userId: String?) {
        analytics.setUserId(userId)
    }

    fun setUserProperties(
        userId: String,
        username: String,
        isClubMember: Boolean,
        isVerified: Boolean,
        postCount: Int,
        followerCount: Int,
        followingCount: Int,
    ) {
        analytics.setUserProperty("username", username)
        analytics.setUserProperty("is_club_member", if (isClubMember) "true" else "false")
        analytics.setUserProperty("is_verified", if (isVerified) "true" else "false")
        analytics.setUserProperty("post_count", postCount.toString())
        analytics.setUserProperty("follower_count", followerCount.toString())
        analytics.setUserProperty("following_count", followingCount.toString())
    }

    fun clearUserProperties() {
        listOf("username", "is_club_member", "is_verified", "post_count", "follower_count", "following_count")
            .forEach { analytics.setUserProperty(it, null) }
    }

    // MARK: - Core Logging

    fun logEvent(name: String, params: Map<String, Any> = emptyMap()) {
        val bundle = Bundle().apply {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Boolean -> putBoolean(key, value)
                    else -> putString(key, value.toString())
                }
            }
        }
        analytics.logEvent(name, bundle)
    }

    // MARK: - Screen Tracking

    fun logScreenView(screen: String) = logEvent("screen_view", mapOf("screen_name" to screen))

    // MARK: - Auth Events

    fun logSignUp(method: String) = logEvent("sign_up", mapOf("method" to method))
    fun logSignIn(method: String) = logEvent("login", mapOf("method" to method))
    fun logSignOut() = logEvent("sign_out")
    fun logEmailVerificationSent() = logEvent("email_verification_sent")
    fun logEmailVerificationCompleted() = logEvent("email_verification_completed")
    fun logDeleteAccount() = logEvent("delete_account")
    fun logAuthError(method: String, error: String) = logEvent("auth_error", mapOf("method" to method, "error" to error.take(100)))

    // MARK: - Onboarding Events

    fun logOnboardingCompleted() = logEvent("onboarding_completed")
    fun logContactsSynced(matchCount: Int) = logEvent("contacts_synced", mapOf("match_count" to matchCount))
    fun logContactsSyncSkipped() = logEvent("contacts_sync_skipped")
    fun logSyncContactsTapped() = logEvent("sync_contacts_tapped")
    fun logFollowFriendsOnboardingCompleted(followedCount: Int) = logEvent("follow_friends_onboarding_completed", mapOf("followed_count" to followedCount))
    fun logOnboardingSeeAllTapped(section: String) = logEvent("onboarding_see_all_tapped", mapOf("section" to section))
    fun logOnboardingProfileSubmitted() = logEvent("onboarding_profile_submitted")
    fun logOnboardingAvatarNudge(action: String) = logEvent("onboarding_avatar_nudge", mapOf("action" to action))
    fun logNotificationPermissionResult(granted: Boolean) = logEvent("notification_permission_result", mapOf("granted" to granted))
    fun logBotPreviewPlayed(botUserId: String) = logEvent("bot_preview_played", mapOf("bot_user_id" to botUserId))

    /**
     * The user picked a preferred music service (onboarding or Settings). The
     * `service` value is the canonical `MusicService.value` ("spotify",
     * "appleMusic", "tidal", "deezer"). Mirrors iOS `music_service_selected`.
     */
    fun logMusicServiceSelected(service: String) = logEvent("music_service_selected", mapOf("service" to service))

    // MARK: - Post Events

    fun logPostCreated(
        mediaType: String,
        trackId: String = "",
        hasVoiceNote: Boolean = false,
        hasHashtags: Boolean = false,
        hasMentions: Boolean = false,
        isFirstPoster: Boolean = false,
        isRepost: Boolean = false,
    ) = logEvent("post_created", mapOf(
        "track_id" to trackId,
        "has_voice_note" to hasVoiceNote,
        "has_hashtags" to hasHashtags,
        "has_mentions" to hasMentions,
        "is_first_poster" to isFirstPoster,
        "media_type" to mediaType,
        "is_repost" to isRepost,
    ))

    fun logPostLiked(postId: String, mediaType: String) = logEvent("post_liked", mapOf("post_id" to postId, "media_type" to mediaType))
    fun logPostUnliked(postId: String, mediaType: String) = logEvent("post_unliked", mapOf("post_id" to postId, "media_type" to mediaType))
    fun logPostSaved(postId: String, mediaType: String) = logEvent("post_saved", mapOf("post_id" to postId, "media_type" to mediaType))
    fun logPostUnsaved(postId: String, mediaType: String) = logEvent("post_unsaved", mapOf("post_id" to postId, "media_type" to mediaType))
    fun logPostDeleted(postId: String, mediaType: String) = logEvent("post_deleted", mapOf("post_id" to postId, "media_type" to mediaType))
    fun logPostShared(postId: String, mediaType: String, method: String) = logEvent("post_shared", mapOf("post_id" to postId, "media_type" to mediaType, "share_method" to method))
    fun logSongShared(trackId: String, method: String) = logEvent("song_shared", mapOf("track_id" to trackId, "share_method" to method))
    fun logCaptionEdited(postId: String) = logEvent("caption_edited", mapOf("post_id" to postId))
    fun logLikesListViewed(postId: String) = logEvent("likes_list_viewed", mapOf("post_id" to postId))
    fun logVoiceNoteRecorded() = logEvent("voice_note_recorded")
    fun logVoiceNotePlayed() = logEvent("voice_note_played")
    fun logReposted(postId: String, mediaType: String) = logEvent("post_created", mapOf("post_id" to postId, "media_type" to mediaType, "is_repost" to true))
    fun logPostCreateError(error: String) = logEvent("post_create_error", mapOf("error" to error.take(100)))

    // MARK: - Comment Events

    fun logCommentAdded(postId: String, mediaType: String) = logEvent("comment_added", mapOf("post_id" to postId, "media_type" to mediaType))
    fun logCommentLiked(postId: String, commentId: String, mediaType: String) = logEvent("comment_liked", mapOf("post_id" to postId, "comment_id" to commentId, "media_type" to mediaType))
    fun logCommentDeleted(postId: String, commentId: String, mediaType: String) = logEvent("comment_deleted", mapOf("post_id" to postId, "comment_id" to commentId, "media_type" to mediaType))
    fun logCommentError(action: String, error: String) = logEvent("comment_error", mapOf("action" to action, "error" to error.take(100)))

    // MARK: - Search / Discovery Events

    fun logUserSearched(query: String) = logEvent("user_searched", mapOf("query" to query))
    fun logMusicMatchTapped(userId: String, similarityScore: Double) = logEvent("music_match_tapped", mapOf("user_id" to userId, "similarity_score" to similarityScore))
    fun logTrendingSongTapped(trackId: String, rank: Int) = logEvent("trending_song_tapped", mapOf("track_id" to trackId, "rank" to rank))
    fun logTrendingHashtagTapped(hashtagName: String) = logEvent("trending_hashtag_tapped", mapOf("hashtag_name" to hashtagName))
    fun logSearchFilterChanged(filter: String) = logEvent("search_filter_changed", mapOf("filter" to filter))
    fun logFeedFilterChanged(filter: String) = logEvent("feed_filter_changed", mapOf("filter" to filter))
    fun logFeedModeChanged(mode: String) = logEvent("feed_mode_changed", mapOf("mode" to mode))
    fun logDeepLinkOpened(linkType: String) = logEvent("deep_link_opened", mapOf("link_type" to linkType))

    // Cross-section search-page events. Pair with `logMusicMatchTapped` for Taste Matches
    // (both fire on that section so similarity_score stays available).
    fun logSearchSectionUserTapped(section: SearchSection, userId: String) =
        logEvent("search_section_user_tapped", mapOf("section" to section.value, "user_id" to userId))
    fun logSearchSectionUserFollowed(section: SearchSection, targetUserId: String) =
        logEvent("search_section_user_followed", mapOf("section" to section.value, "target_user_id" to targetUserId))
    fun logSearchSectionUserUnfollowed(section: SearchSection, targetUserId: String) =
        logEvent("search_section_user_unfollowed", mapOf("section" to section.value, "target_user_id" to targetUserId))
    fun logSearchSectionSeeAllTapped(section: SearchSection) =
        logEvent("search_section_see_all_tapped", mapOf("section" to section.value))

    // MARK: - Profile Events

    fun logProfileViewed(userId: String, isOwnProfile: Boolean) = logEvent("profile_viewed", mapOf("user_id" to userId, "is_own_profile" to isOwnProfile))
    fun logFollowUser(targetUserId: String) = logEvent("follow_user", mapOf("target_user_id" to targetUserId))
    fun logUnfollowUser(targetUserId: String) = logEvent("unfollow_user", mapOf("target_user_id" to targetUserId))
    fun logBlockUser(targetUserId: String) = logEvent("block_user", mapOf("target_user_id" to targetUserId))
    fun logUnblockUser(targetUserId: String) = logEvent("unblock_user", mapOf("target_user_id" to targetUserId))
    fun logMuteUser(targetUserId: String) = logEvent("mute_user", mapOf("target_user_id" to targetUserId))
    fun logUnmuteUser(targetUserId: String) = logEvent("unmute_user", mapOf("target_user_id" to targetUserId))
    fun logFavoriteAdded(targetUserId: String) = logEvent("favorite_added", mapOf("target_user_id" to targetUserId))
    fun logFavoriteRemoved(targetUserId: String) = logEvent("favorite_removed", mapOf("target_user_id" to targetUserId))
    fun logReportUser(targetUserId: String) = logEvent("report_user", mapOf("target_user_id" to targetUserId))
    fun logReportPost(postId: String, reason: String) = logEvent("report_post", mapOf("post_id" to postId, "reason" to reason))
    fun logReportComment(commentId: String, reason: String) = logEvent("report_comment", mapOf("comment_id" to commentId, "reason" to reason))
    fun logReportMessage(messageId: String, reason: String) = logEvent("report_message", mapOf("message_id" to messageId, "reason" to reason))
    fun logEditProfileSaved() = logEvent("edit_profile_saved")
    fun logAvatarChanged() = logEvent("avatar_changed")
    fun logProfileSegmentChanged(segment: String) = logEvent("profile_segment_changed", mapOf("segment" to segment))
    fun logFeedPlaylistTapped() = logEvent("feed_playlist_tapped")
    fun logProfilePlaylistTapped(userId: String) = logEvent("profile_playlist_tapped", mapOf("user_id" to userId))
    fun logHashtagPlaylistTapped(hashtag: String) = logEvent("hashtag_playlist_tapped", mapOf("hashtag" to hashtag))
    fun logFollowError(targetUserId: String, error: String) = logEvent("follow_error", mapOf("target_user_id" to targetUserId, "error" to error.take(100)))
    fun logProfileUpdateError(error: String) = logEvent("profile_update_error", mapOf("error" to error.take(100)))

    // MARK: - Song / Film Events

    fun logSongDetailViewed(trackId: String) = logEvent("song_detail_viewed", mapOf("track_id" to trackId))
    fun logFilmDetailViewed(filmId: String) = logEvent("film_detail_viewed", mapOf("film_id" to filmId))
    fun logPostThisSongTapped(trackId: String) = logEvent("post_this_song_tapped", mapOf("track_id" to trackId))
    fun logSpotifyLinkTapped(trackId: String) = logEvent("spotify_link_tapped", mapOf("track_id" to trackId))

    /**
     * Two events on the song/film detail "Posted by" list so the two tap
     * destinations are comparable: the row opens that user's post, the
     * avatar/name opens their profile. `mediaType` is "song" or "film";
     * `contentId` is the track or movie id. Mirrored on Web and iOS.
     */
    fun logPostedByPostTapped(mediaType: String, contentId: String, postId: String) =
        logEvent("posted_by_post_tapped", mapOf("media_type" to mediaType, "content_id" to contentId, "post_id" to postId))
    fun logPostedByProfileTapped(mediaType: String, contentId: String, userId: String) =
        logEvent("posted_by_profile_tapped", mapOf("media_type" to mediaType, "content_id" to contentId, "user_id" to userId))

    /**
     * Link-out tap for a non-Spotify service (TIDAL, Deezer, Apple Music).
     * Spotify keeps the established `spotify_link_tapped`; every other service
     * routes here with a `service` param (the canonical `MusicService.value`)
     * so the three are comparable in one report. Mirrors iOS
     * `AnalyticsService.logMusicServiceLinkTapped`.
     */
    fun logMusicServiceLinkTapped(service: String, trackId: String) =
        logEvent("music_service_link_tapped", mapOf("service" to service, "track_id" to trackId))

    /**
     * Convenience mirror of iOS `logMusicServiceLinkTapped(service:)`: routes
     * Spotify to the established `spotify_link_tapped` and every other service to
     * `music_service_link_tapped`, so call sites don't have to branch.
     */
    fun logMusicServiceLinkTapped(service: fm.corus.android.data.model.MusicService, trackId: String) {
        if (service == fm.corus.android.data.model.MusicService.SPOTIFY) {
            logSpotifyLinkTapped(trackId)
        } else {
            logMusicServiceLinkTapped(service.value, trackId)
        }
    }
    fun logTrailerLinkTapped(filmId: String) = logEvent("trailer_link_tapped", mapOf("film_id" to filmId))

    // MARK: - Artist / Album destination pages
    // Names + params match web/iOS exactly (artist_page_viewed etc.) so GA4
    // reports compare the pages cross-platform. These six are the complete set.

    fun logArtistPageViewed(artistId: String) =
        logEvent("artist_page_viewed", mapOf("artist_id" to artistId))
    fun logAlbumPageViewed(albumId: String) =
        logEvent("album_page_viewed", mapOf("album_id" to albumId))
    fun logArtistSongPreviewed(artistId: String, trackId: String) =
        logEvent("artist_song_previewed", mapOf("artist_id" to artistId, "track_id" to trackId))
    fun logAlbumTrackPreviewed(albumId: String, trackId: String) =
        logEvent("album_track_previewed", mapOf("album_id" to albumId, "track_id" to trackId))
    fun logPostFromArtistPage(artistId: String, trackId: String) =
        logEvent("post_from_artist_page", mapOf("artist_id" to artistId, "track_id" to trackId))
    fun logPostFromAlbum(albumId: String, trackId: String) =
        logEvent("post_from_album", mapOf("album_id" to albumId, "track_id" to trackId))

    // MARK: - Notification / Message Events

    fun logNotificationTapped(type: String) = logEvent("notification_tapped", mapOf("notification_type" to type))
    fun logTasteMatchPushOpened(subtype: String, fromUserId: String, appState: String) =
        logEvent("taste_match_push_opened", mapOf("subtype" to subtype, "from_user_id" to fromUserId, "app_state" to appState))
    fun logTasteMatchFeedRowViewed(subtype: String, fromUserId: String) =
        logEvent("taste_match_feed_row_viewed", mapOf("subtype" to subtype, "from_user_id" to fromUserId))
    fun logTasteMatchFeedRowTapped(subtype: String, fromUserId: String) =
        logEvent("taste_match_feed_row_tapped", mapOf("subtype" to subtype, "from_user_id" to fromUserId))
    fun logTasteMatchSettingsToggled(enabled: Boolean) =
        logEvent("taste_match_settings_toggled", mapOf("enabled" to enabled))

    // ── Taste Matches feed funnel (selection → cold-start → served feed) ──
    // Event names + param keys are kept identical across iOS / Web / Android so
    // GA4 can report the funnel cross-platform.
    fun logTasteMatchesSelected(hasAccess: Boolean) =
        logEvent("taste_matches_selected", mapOf("has_access" to hasAccess))
    fun logTasteMatchesColdstartShown() = logEvent("taste_matches_coldstart_shown")
    fun logTasteMatchesColdstartPostTapped() = logEvent("taste_matches_coldstart_post_tapped")
    fun logTasteMatchesFeedViewed(postCount: Int) =
        logEvent("taste_matches_feed_viewed", mapOf("post_count" to postCount))
    fun logMessageThreadOpened(threadId: String) = logEvent("message_thread_opened", mapOf("thread_id" to threadId))
    fun logMessageSent(threadId: String, type: String) = logEvent("message_sent", mapOf("thread_id" to threadId, "message_type" to type))
    fun logMessageError(threadId: String, error: String) = logEvent("message_error", mapOf("thread_id" to threadId, "error" to error.take(100)))

    // MARK: - Group Messaging Events
    fun logGroupCreated(memberCount: Int, hasName: Boolean) =
        logEvent("group_created", mapOf("member_count" to memberCount, "has_name" to hasName))
    fun logGroupMembersAdded(threadId: String, addedCount: Int) =
        logEvent("group_members_added", mapOf("thread_id" to threadId, "added_count" to addedCount))
    fun logGroupMemberRemoved(threadId: String) = logEvent("group_member_removed", mapOf("thread_id" to threadId))
    fun logGroupLeft(threadId: String) = logEvent("group_left", mapOf("thread_id" to threadId))
    fun logGroupRenamed(threadId: String) = logEvent("group_renamed", mapOf("thread_id" to threadId))
    fun logGroupPhotoChanged(threadId: String) = logEvent("group_photo_changed", mapOf("thread_id" to threadId))

    // MARK: - Settings Events

    fun logSettingToggled(setting: String, enabled: Boolean) = logEvent("setting_toggled", mapOf("setting" to setting, "enabled" to enabled))
    fun logFeedbackSubmitted(type: String) = logEvent("feedback_submitted", mapOf("feedback_type" to type))

    // MARK: - Subscription / Paywall Events

    fun logPaywallShown(source: String, defaultPlan: String = "monthly") = logEvent("paywall_shown", mapOf("source" to source, "default_plan" to defaultPlan))
    fun logPaywallDismissed() = logEvent("paywall_dismissed")
    fun logPurchaseStarted(plan: String, source: String) = logEvent("purchase_started", mapOf("plan" to plan, "source" to source))
    fun logPurchaseCompleted(plan: String, source: String) = logEvent("purchase_completed", mapOf("plan" to plan, "source" to source))
    fun logPurchaseFailed(plan: String, error: String) = logEvent("purchase_failed", mapOf("plan" to plan, "error" to error.take(100)))
    fun logPurchaseRestored() = logEvent("purchase_restored")
    fun logPurchaseRestoreFailed(error: String) = logEvent("purchase_restore_failed", mapOf("error" to error.take(100)))
    fun logSubscriptionExpired() = logEvent("subscription_expired")
    fun logManageSubscriptionTapped() = logEvent("manage_subscription_tapped")
    fun logPostLimitReached(todayCount: Int) = logEvent("post_limit_reached", mapOf("today_count" to todayCount))
    fun logSaveCapReached(savesCount: Int) = logEvent("save_cap_reached", mapOf("saves_count" to savesCount))
    fun logFavoritePeopleCapReached(favoritesCount: Int) = logEvent("favorite_people_cap_reached", mapOf("favorites_count" to favoritesCount))
    fun logSaveWarningToastShown(savesRemaining: Int) = logEvent("save_warning_toast_shown", mapOf("saves_remaining" to savesRemaining))
    fun logSaveWarningToastTapped(savesRemaining: Int) = logEvent("save_warning_toast_tapped", mapOf("saves_remaining" to savesRemaining))

    // MARK: - Error Events

    fun logLikeError(postId: String, error: String) = logEvent("like_error", mapOf("post_id" to postId, "error" to error.take(100)))
    fun logSaveError(postId: String, error: String) = logEvent("save_error", mapOf("post_id" to postId, "error" to error.take(100)))
}
