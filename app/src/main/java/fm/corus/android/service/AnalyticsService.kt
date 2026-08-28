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

    // Trending sections join the enum with unified search, where their
    // compact strips live on the same zero-state feed as the people sections.
    // Values match iOS/web.
    TrendingSongs("trending_songs"),
    TrendingFilms("trending_films"),
    TrendingHashtags("trending_hashtags"),
    TrendingArtists("trending_artists"),
    TrendingAlbums("trending_albums"),
    NewReleaseAlbums("new_release_albums"),
    ArtistsOnCorus("artists_on_corus"),
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
        isBot: Boolean = false,
    ) {
        analytics.setUserProperty("username", username)
        analytics.setUserProperty("is_club_member", if (isClubMember) "true" else "false")
        analytics.setUserProperty("is_verified", if (isVerified) "true" else "false")
        analytics.setUserProperty("post_count", postCount.toString())
        analytics.setUserProperty("follower_count", followerCount.toString())
        analytics.setUserProperty("following_count", followingCount.toString())
        analytics.setUserProperty("is_bot", if (isBot) "true" else "false")
    }

    fun clearUserProperties() {
        listOf(
            "username",
            "is_club_member",
            "is_verified",
            "post_count",
            "follower_count",
            "following_count",
            "is_bot",
            "spotify_full_playback_connected",
            "spotify_ftue_variant",
            "music_service",
        ).forEach { analytics.setUserProperty(it, null) }
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

    /**
     * `taste_onboarding` carries the A/B variant (taste-match onboarding flow
     * on/off) so BigQuery can compare signup completion rates across the two
     * flows. The same param ships on web (analyticsEvents.ts
     * trackOnboardingCompleted) and iOS.
     */
    fun logOnboardingCompleted(tasteOnboarding: Boolean) =
        logEvent("onboarding_completed", mapOf("taste_onboarding" to if (tasteOnboarding) "on" else "off"))
    fun logContactsSynced(matchCount: Int) = logEvent("contacts_synced", mapOf("match_count" to matchCount))
    fun logContactsSyncSkipped() = logEvent("contacts_sync_skipped")
    fun logSyncContactsTapped() = logEvent("sync_contacts_tapped")
    fun logFollowFriendsOnboardingCompleted(followedCount: Int) = logEvent("follow_friends_onboarding_completed", mapOf("followed_count" to followedCount))
    fun logOnboardingSeeAllTapped(section: String) = logEvent("onboarding_see_all_tapped", mapOf("section" to section))
    fun logOnboardingProfileSubmitted() = logEvent("onboarding_profile_submitted")
    fun logOnboardingAvatarNudge(action: String) = logEvent("onboarding_avatar_nudge", mapOf("action" to action))
    fun logNotificationPermissionResult(granted: Boolean) = logEvent("notification_permission_result", mapOf("granted" to granted))
    fun logNotificationPermissionPrimerShown() = logEvent("notification_permission_primer_shown")
    fun logNotificationPermissionPrimerTapped(action: String) =
        logEvent("notification_permission_primer_tapped", mapOf("action" to action))
    fun logNotificationPermissionReaskShown(source: String) =
        logEvent("notification_permission_reask_shown", mapOf("source" to source))
    fun logNotificationPermissionReaskTapped(source: String, action: String) =
        logEvent("notification_permission_reask_tapped", mapOf("source" to source, "action" to action))
    fun logBotPreviewPlayed(botUserId: String) = logEvent("bot_preview_played", mapOf("bot_user_id" to botUserId))

    // ── Taste-match onboarding (the flag-on flow) ──
    // Event names + param keys mirror web's analyticsEvents.ts exactly so GA4
    // reports the funnel cross-platform.

    fun logOnboardingTasteIntroShown() = logEvent("onboarding_taste_intro_shown")
    fun logOnboardingTasteQuizStarted() = logEvent("onboarding_taste_quiz_started")
    fun logOnboardingTastePickAdded(kind: String, total: Int) =
        logEvent("onboarding_taste_pick_added", mapOf("kind" to kind, "total" to total))
    fun logOnboardingTasteQuizCompleted(
        total: Int,
        songs: Int,
        films: Int,
        artists: Int,
        albums: Int,
        directors: Int,
    ) = logEvent(
        "onboarding_taste_quiz_completed",
        mapOf(
            "total" to total,
            "songs" to songs,
            "films" to films,
            "artists" to artists,
            "albums" to albums,
            "directors" to directors,
        ),
    )
    fun logOnboardingTasteMatchesShown(matchCount: Int, strongCount: Int) =
        logEvent("onboarding_taste_matches_shown", mapOf("match_count" to matchCount, "strong_count" to strongCount))
    fun logOnboardingTasteMakerShown() = logEvent("onboarding_taste_maker_shown")
    fun logOnboardingTasteMatchFollowed() = logEvent("onboarding_taste_match_followed")
    fun logOnboardingTastePicksPosted(count: Int) =
        logEvent("onboarding_taste_picks_posted", mapOf("count" to count))
    fun logOnboardingTasteSkipped(stage: String) =
        logEvent("onboarding_taste_skipped", mapOf("stage" to stage))

    /**
     * The user picked a preferred music service (onboarding or Settings). The
     * `service` value is the canonical `MusicService.value` ("spotify",
     * "appleMusic", "tidal", "deezer"). Mirrors iOS `music_service_selected`.
     */
    fun logMusicServiceSelected(service: String) = logEvent("music_service_selected", mapOf("service" to service))

    // MARK: - Post Events

    fun logPostSuccessOthersShown(otherCount: Int, visibleCount: Int, mediaType: String) =
        logEvent(
            "post_success_others_shown",
            mapOf(
                "other_count" to otherCount,
                "visible_count" to visibleCount,
                "media_type" to mediaType,
            ),
        )
    fun logPostSuccessOthersFollowed(targetUserId: String) =
        logEvent("post_success_others_followed", mapOf("target_user_id" to targetUserId))
    fun logPostSuccessOthersLiked(postId: String) =
        logEvent("post_success_others_liked", mapOf("post_id" to postId))
    fun logPostSuccessOthersProfileTapped(userId: String, mediaType: String) =
        logEvent("post_success_others_profile_tapped", mapOf("user_id" to userId, "media_type" to mediaType))
    fun logPostSuccessOthersSeeAllTapped(mediaType: String) =
        logEvent("post_success_others_see_all_tapped", mapOf("media_type" to mediaType))
    fun logPostSuccessOthersDismissed(method: String) =
        logEvent("post_success_others_dismissed", mapOf("method" to method))

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
    fun logArtistShared(artistId: String, method: String) = logEvent("artist_shared", mapOf("artist_id" to artistId, "share_method" to method))
    fun logAlbumShared(albumId: String, method: String) = logEvent("album_shared", mapOf("album_id" to albumId, "share_method" to method))
    fun logDirectorShared(directorId: String, method: String) = logEvent("director_shared", mapOf("director_id" to directorId, "share_method" to method))
    fun logFilmShared(filmId: String, method: String) = logEvent("film_shared", mapOf("film_id" to filmId, "share_method" to method))
    fun logBookShared(bookId: String, method: String) = logEvent("book_shared", mapOf("book_id" to bookId, "share_method" to method))
    fun logAuthorShared(authorSlug: String, method: String) = logEvent("author_shared", mapOf("author_slug" to authorSlug, "share_method" to method))
    fun logProfileShared(
        profileUserId: String,
        method: String,
        isOwnProfile: Boolean,
        cardTheme: String? = null,
    ) = logEvent(
        "profile_shared",
        buildMap {
            put("profile_user_id", profileUserId)
            put("share_method", method)
            put("is_own_profile", isOwnProfile)
            cardTheme?.let { put("card_theme", it) }
        },
    )
    fun logProfileShareSheetOpened(profileUserId: String, isOwnProfile: Boolean, entryPoint: String) =
        logEvent(
            "profile_share_sheet_opened",
            mapOf(
                "profile_user_id" to profileUserId,
                "is_own_profile" to isOwnProfile,
                "entry_point" to entryPoint,
            ),
        )
    fun logProfileShareThemeChanged(profileUserId: String, cardTheme: String) =
        logEvent("profile_share_theme_changed", mapOf("profile_user_id" to profileUserId, "card_theme" to cardTheme))
    fun logCaptionEdited(postId: String) = logEvent("caption_edited", mapOf("post_id" to postId))
    fun logLikesListViewed(postId: String) = logEvent("likes_list_viewed", mapOf("post_id" to postId))
    fun logVoiceNoteRecorded() = logEvent("voice_note_recorded")
    fun logVoiceNotePlayed() = logEvent("voice_note_played")
    fun logReposted(postId: String, mediaType: String) = logEvent("post_created", mapOf("post_id" to postId, "media_type" to mediaType, "is_repost" to true))
    fun logPostCreateError(error: String) = logEvent("post_create_error", mapOf("error" to error.take(100)))
    fun logFullPlayerDismissed(trackId: String?, method: String) = logEvent(
        "full_player_dismissed",
        buildMap {
            put("method", method)
            if (!trackId.isNullOrBlank()) put("track_id", trackId)
        },
    )

    // MARK: - Full player / queue / Spotify (iOS AnalyticsEvent parity)

    fun logFullPlayerOpened(trackId: String?, sourcePostId: String? = null) = logEvent(
        "full_player_opened",
        buildMap {
            if (!trackId.isNullOrBlank()) put("track_id", trackId)
            if (!sourcePostId.isNullOrBlank()) put("source_post_id", sourcePostId)
        },
    )

    fun logFullPlayerQueueOpened(trackId: String?) = logEvent(
        "full_player_queue_opened",
        buildMap {
            if (!trackId.isNullOrBlank()) put("track_id", trackId)
        },
    )

    fun logFullPlayerOpenInServiceTapped(service: String, trackId: String?) = logEvent(
        "full_player_open_in_service_tapped",
        buildMap {
            put("service", service)
            if (!trackId.isNullOrBlank()) put("track_id", trackId)
        },
    )

    fun logFullPlayerPlaybackModeToggled(toFull: Boolean, trackId: String?) = logEvent(
        "full_player_playback_mode_toggled",
        buildMap {
            put("to_full", toFull)
            if (!trackId.isNullOrBlank()) put("track_id", trackId)
        },
    )

    fun logFullPlayerComposeTapped(trackId: String) =
        logEvent("full_player_compose_tapped", mapOf("track_id" to trackId))

    fun logAddToQueueTapped(trackId: String, sourcePostId: String? = null) = logEvent(
        "add_to_queue_tapped",
        buildMap {
            put("track_id", trackId)
            if (!sourcePostId.isNullOrBlank()) put("source_post_id", sourcePostId)
        },
    )

    fun logQueueItemRemoved(trackId: String) =
        logEvent("queue_item_removed", mapOf("track_id" to trackId))

    fun logQueueItemReordered() = logEvent("queue_item_reordered")

    fun logSongPreviewPlayed(trackId: String) =
        logEvent("song_preview_played", mapOf("track_id" to trackId))

    fun logSpotifyAuthConnected(method: String) =
        logEvent("spotify_auth_connected", mapOf("method" to method))
    fun logSpotifyAuthConnectFailed(reason: String) =
        logEvent("spotify_auth_connect_failed", mapOf("reason" to reason.take(100)))
    fun logSpotifyAuthConnectCancelled() = logEvent("spotify_auth_connect_cancelled")
    fun logSpotifyAuthDisconnected() = logEvent("spotify_auth_disconnected")

    fun logSpotifyFullSongPlayStarted(trackId: String, trackSource: String) =
        logEvent(
            "spotify_full_song_play_started",
            mapOf("track_id" to trackId, "track_source" to trackSource),
        )

    fun logSpotifyFullSongPlayFailed(trackId: String, reason: String) =
        logEvent(
            "spotify_full_song_play_failed",
            mapOf("track_id" to trackId, "reason" to reason.take(100)),
        )

    fun setSpotifyFullPlaybackConnected(connected: Boolean) {
        analytics.setUserProperty(
            "spotify_full_playback_connected",
            if (connected) "true" else "false",
        )
    }

    fun setSpotifyFtueUserProperties(variant: String, musicService: String) {
        analytics.setUserProperty("spotify_ftue_variant", variant)
        analytics.setUserProperty("music_service", musicService)
    }

    fun logSpotifyAuthConnectPromptShown(trackId: String, variant: String, surface: String) =
        logEvent(
            "spotify_auth_connect_prompt_shown",
            mapOf("track_id" to trackId, "variant" to variant, "surface" to surface),
        )

    fun logSpotifyFtueAssigned(variant: String, spotifyInstalled: Boolean) =
        logEvent(
            "spotify_ftue_assigned",
            mapOf("variant" to variant, "spotify_installed" to spotifyInstalled),
        )

    fun logSpotifyFtuePromptChosen(variant: String, surface: String, choice: String) =
        logEvent(
            "spotify_ftue_prompt_chosen",
            mapOf("variant" to variant, "surface" to surface, "choice" to choice),
        )

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
    fun logFeedDecadeChanged(decade: String) = logEvent("feed_decade_changed", mapOf("decade" to decade))
    // Feed-switch hint discovery. `feed_switcher_opened` fires on every switcher
    // open regardless of the RC flag (baseline signal + permanent suppression);
    // `feed_switch_hint_shown` fires when the coachmark appears. Mirrors iOS/web.
    fun logFeedSwitcherOpened() = logEvent("feed_switcher_opened")
    fun logFeedSwitchHintShown() = logEvent("feed_switch_hint_shown")
    // Fires when the user taps the coachmark bubble to dismiss it (distinct from
    // discovering the switcher via feed_switcher_opened). Mirrors iOS/web.
    fun logFeedSwitchHintDismissed() = logEvent("feed_switch_hint_dismissed")
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
    fun logSearchSectionItemTapped(section: SearchSection, itemId: String) =
        logEvent("search_section_item_tapped", mapOf("section" to section.value, "item_id" to itemId))

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
    fun logProfileArtistLinkTapped(artistId: String, profileUserId: String) =
        logEvent(
            "profile_artist_link_tapped",
            mapOf("artist_id" to artistId, "profile_user_id" to profileUserId),
        )
    fun logAlbumPageViewed(albumId: String) =
        logEvent("album_page_viewed", mapOf("album_id" to albumId))
    fun logDirectorPageViewed(directorId: String) =
        logEvent("director_page_viewed", mapOf("director_id" to directorId))
    fun logAuthorPageViewed(authorSlug: String) =
        logEvent("author_page_viewed", mapOf("author_slug" to authorSlug))
    fun logBookPageViewed(bookId: String) =
        logEvent("book_page_viewed", mapOf("book_id" to bookId))
    fun logBookPreviewOpened(bookId: String, source: String) =
        logEvent("book_preview_opened", mapOf("book_id" to bookId, "source" to source))
    fun logAudiobookSamplePlayed(bookId: String) =
        logEvent("audiobook_sample_played", mapOf("book_id" to bookId))
    fun logArtistSongPreviewed(artistId: String, trackId: String) =
        logEvent("artist_song_previewed", mapOf("artist_id" to artistId, "track_id" to trackId))
    fun logAlbumTrackPreviewed(albumId: String, trackId: String) =
        logEvent("album_track_previewed", mapOf("album_id" to albumId, "track_id" to trackId))
    fun logPostFromArtistPage(artistId: String, trackId: String) =
        logEvent("post_from_artist_page", mapOf("artist_id" to artistId, "track_id" to trackId))
    fun logPostFromAlbum(albumId: String, trackId: String) =
        logEvent("post_from_album", mapOf("album_id" to albumId, "track_id" to trackId))
    fun logPostFromDirector(directorId: String, filmId: String) =
        logEvent("post_from_director", mapOf("director_id" to directorId, "film_id" to filmId))
    fun logMusicVideoPlayed(artistId: String, videoId: String) =
        logEvent("music_video_played", mapOf("artist_id" to artistId, "video_id" to videoId))
    fun logTrailerPlayed(directorId: String, videoId: String) =
        logEvent("trailer_played", mapOf("director_id" to directorId, "video_id" to videoId))

    // MARK: - Notification / Message Events

    fun logNotificationTapped(type: String, filter: String = "all") =
        logEvent("notification_tapped", mapOf("notification_type" to type, "filter" to filter))
    fun logNotificationFilterChanged(filter: String) =
        logEvent("notification_filter_changed", mapOf("filter" to filter))
    fun logNotificationFiltersShown(notificationCount: Int, typeCount: Int) =
        logEvent(
            "notification_filters_shown",
            mapOf("notification_count" to notificationCount, "type_count" to typeCount),
        )
    fun logTasteMatchPushOpened(subtype: String, fromUserId: String, appState: String) =
        logEvent("taste_match_push_opened", mapOf("subtype" to subtype, "from_user_id" to fromUserId, "app_state" to appState))
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

    // ── Free-trial in-feed banner (`taste_matches_free_trial` RC on) ──
    // Shown once per phase the banner is in ("preview" | "trial") and logged
    // again on tap, right before the tap opens the paywall with source
    // "taste_matches_banner". Event names + param keys match iOS/web.
    fun logTasteMatchesBannerShown(phase: String, daysRemaining: Int? = null, postCount: Int? = null) {
        val params = mutableMapOf<String, Any>("phase" to phase)
        if (daysRemaining != null) params["days_remaining"] = daysRemaining
        if (postCount != null) params["post_count"] = postCount
        logEvent("taste_matches_banner_shown", params)
    }
    fun logTasteMatchesBannerTapped(phase: String, daysRemaining: Int? = null) {
        val params = mutableMapOf<String, Any>("phase" to phase)
        if (daysRemaining != null) params["days_remaining"] = daysRemaining
        logEvent("taste_matches_banner_tapped", params)
    }
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
    fun logStyleChanged(changes: Map<String, String>) = logEvent("style_changed", changes)
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
