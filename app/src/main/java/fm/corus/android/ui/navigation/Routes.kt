package fm.corus.android.ui.navigation

import kotlinx.serialization.Serializable

// ── Top-Level Routes ──

@Serializable object AuthRoute
@Serializable object OnboardingRoute
@Serializable object MainRoute

// ── Tab Routes ──

@Serializable object FeedTabRoute
@Serializable object ExploreTabRoute
@Serializable object NotificationsTabRoute
@Serializable object ProfileTabRoute

// ── Nested Routes ──

@Serializable data class PostDetailRoute(val postId: String)
@Serializable data class SongDetailRoute(
    val trackId: String,
    val albumArtURL: String? = null,
    val albumArtLargeURL: String? = null,
    val songName: String? = null,
    val artistName: String? = null,
    val spotifyURI: String? = null,
    val spotifyWebURL: String? = null,
    val previewUrl: String? = null,
)
@Serializable data class FilmDetailRoute(
    val movieId: String,
    val movieTitle: String? = null,
    val directorName: String? = null,
    val releaseYear: String? = null,
    val posterURL: String? = null,
    val posterLargeURL: String? = null,
    val trailerURL: String? = null,
)
@Serializable data class CommentsRoute(val postId: String)
@Serializable data class OtherProfileRoute(
    val userId: String,
    val avatarURL: String? = null,
    val avatarThumbURL: String? = null,
    val initialDisplayName: String? = null,
    val initialUsername: String? = null,
    val initialBio: String? = null,
    val initialCymbalCount: Int? = null,
    val initialFollowerCount: Int? = null,
    val initialFollowingCount: Int? = null,
    val initialIsVerified: Boolean? = null,
    val initialIsClubMember: Boolean? = null,
)
@Serializable data class ProfileByUsernameRoute(val username: String)
@Serializable data class FollowListRoute(val userId: String, val isFollowers: Boolean)
@Serializable data class HashtagFeedRoute(val hashtag: String)
@Serializable data class EditProfileRoute(val userId: String)
@Serializable object FindPeopleRoute
@Serializable data class EditCaptionRoute(val postId: String, val initialCaption: String, val albumArtURL: String? = null)
@Serializable object SettingsRoute
@Serializable data object AppearanceSettingsRoute
@Serializable data object NotificationSettingsRoute
@Serializable object BlockedUsersRoute
@Serializable data object MutedUsersRoute
@Serializable object ChangeUsernameRoute
@Serializable object ChangePhoneNumberRoute
@Serializable object FeedbackFormRoute
@Serializable object CymbalClubOfferRoute
@Serializable object ThreadListRoute
@Serializable data class MessageThreadRoute(val threadId: String, val otherUserId: String)
@Serializable data class BotListRoute(val botType: String? = null)
@Serializable data class SinglePostCommentsRoute(val postId: String, val commentId: String? = null)
@Serializable data class SuggestedUsersListRoute(val title: String = "Taste Matches", val useRowLayout: Boolean = false)
@Serializable data object ContactFriendsListRoute
