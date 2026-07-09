package fm.corus.android.ui.navigation

import kotlinx.serialization.Serializable

// ── Top-Level Routes ──

@Serializable object AuthRoute
@Serializable object OnboardingRoute
@Serializable object MainRoute

// ── Tab Routes ──

@Serializable object FeedTabRoute
@Serializable object SearchTabRoute
@Serializable object NotificationsTabRoute
@Serializable object ProfileTabRoute

// ── Nested Routes ──

@Serializable data class PostDetailRoute(val postId: String)
@Serializable data class ProfileFeedRoute(
    val userId: String,
    val username: String,
    val segment: Int,
    val initialPostId: String,
    /** Set when [segment] == 4 (hashtag feed reuses this route). The lowercased
     *  hashtag name without the leading `#`. */
    val hashtag: String = "",
)
@Serializable data class SongDetailRoute(
    val trackId: String,
    val albumArtURL: String? = null,
    val albumArtLargeURL: String? = null,
    val songName: String? = null,
    val artistName: String? = null,
    val spotifyURI: String? = null,
    val spotifyWebURL: String? = null,
    val previewUrl: String? = null,
    val source: String? = null,
    val soundcloudId: String? = null,
    val soundcloudPermalinkUrl: String? = null,
    /** The recording's ISRC, when the source track carries it. Lets the song
     *  page's "posted by" match the SAME recording across store IDs — e.g. an
     *  Apple-catalog "Popular" row (`am:` id, no spotifyURI) whose posts were
     *  made from Spotify. Without it the page wrongly reads "no one has posted." */
    val isrc: String? = null,
    /** First credited artist's Spotify id, when the caller has it (search
     *  results / catalog rows). Makes the artist line tappable before the
     *  first post (which also carries artistIds) loads. */
    val artistId: String? = null,
    /** How many artist ids the source track carried — drives the
     *  primaryNameHint split for joined credit strings. 0 = unknown. */
    val artistIdCount: Int = 0,
    /** Spotify album id from search/catalog tracks, when the caller has it.
     *  Makes the song page's album line tappable (artist_pages_enabled).
     *  Null (Apple-sourced/older tracks) = plain text. */
    val albumId: String? = null,
    /** Release date + precision when the caller has them (search/catalog/trending
     *  tracks all carry them). Lets the song page paint the NEW RELEASE tag on the
     *  first frame instead of waiting for its posts to load. Null = fall back to
     *  the loaded posts. */
    val releaseDate: String? = null,
    val releaseDatePrecision: String? = null,
)
@Serializable data class FilmDetailRoute(
    val movieId: String,
    val movieTitle: String? = null,
    val directorName: String? = null,
    val releaseYear: String? = null,
    val posterURL: String? = null,
    val posterLargeURL: String? = null,
    val trailerURL: String? = null,
    /** Full YYYY-MM-DD release date when the caller has it (trending / catalog /
     *  director-page films). Lets the film page paint the NEW RELEASE tag on the
     *  first frame instead of waiting for posts. Null = fall back to posts. */
    val movieReleaseDate: String? = null,
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
    val initialIsFollowing: Boolean? = null,
)
@Serializable data class ProfileByUsernameRoute(val username: String)
@Serializable data class FollowListRoute(
    val userId: String,
    val isFollowers: Boolean,
    val username: String,
    val followerCount: Int,
    val followingCount: Int,
)
@Serializable data class HashtagFeedRoute(val hashtag: String)
@Serializable data class HashtagPeopleRoute(val hashtag: String, val isFollowers: Boolean)
@Serializable data class EditProfileRoute(val userId: String)
@Serializable object SearchRoute
/** Full-screen trending list ("See all" from the search page's trending
 *  strips). [kind] is "songs" | "films" | "hashtags". */
@Serializable data class TrendingListRoute(val kind: String)
@Serializable data class EditCaptionRoute(val postId: String, val initialCaption: String, val albumArtURL: String? = null)
@Serializable object SettingsRoute
@Serializable data object NotificationSettingsRoute
@Serializable object BlockedUsersRoute
@Serializable data object MutedUsersRoute
@Serializable object ChangeUsernameRoute
@Serializable object ChangePhoneNumberRoute
@Serializable object FeedbackFormRoute
@Serializable data class CymbalClubOfferRoute(val source: String = "DEFAULT")
@Serializable object ThreadListRoute
@Serializable data class MessageThreadRoute(val threadId: String, val otherUserId: String)
@Serializable data class BotListRoute(val botType: String? = null)
@Serializable data class SinglePostCommentsRoute(val postId: String, val commentId: String? = null)
@Serializable data class CommentLikesRoute(val postId: String, val commentId: String)
@Serializable data class SuggestedUsersListRoute(
    val title: String = "Taste Matches",
    val useRowLayout: Boolean = false,
    val source: String = "tasteMatches",
)
@Serializable data object ContactFriendsListRoute
@Serializable data object SyncContactsSettingsRoute

// ── Artist / Album / Director destination pages (artist_pages_enabled) ──
// name/image/title hints let each page paint its header instantly while the
// catalog callable resolves — mirrors SongDetailRoute's hint pattern.

@Serializable data class ArtistPageRoute(
    val artistId: String,
    val name: String? = null,
    val imageUrl: String? = null,
)
@Serializable data class AlbumPageRoute(
    /** Spotify album id, or `am:{appleAlbumId}` for Apple-resolved albums —
     *  passed through to getAlbumCatalog untouched. */
    val albumId: String,
    val title: String? = null,
    val artist: String? = null,
    val coverUrl: String? = null,
    /** Release year when the source row already knows it — paints the header
     *  meta line before the catalog loads. */
    val year: Int? = null,
)
@Serializable data class DirectorPageRoute(
    val directorId: String,
    val name: String? = null,
    val imageUrl: String? = null,
)
@Serializable data class ArtistDiscographyRoute(
    val artistId: String,
    val name: String? = null,
)
/** "Music videos" see-all — the artist page's full newest-first video grid. */
@Serializable data class ArtistMusicVideosRoute(
    val artistId: String,
    val name: String? = null,
)
/** "Trailers" see-all — the director page's full newest-first trailer grid. */
@Serializable data class DirectorTrailersRoute(
    val directorId: String,
    val name: String? = null,
)
/** "Who shared {name}" — the artist page's paginated posts see-all. */
@Serializable data class ArtistPostsRoute(
    val artistId: String,
    val name: String? = null,
)
@Serializable data class DirectorFilmographyRoute(
    val directorId: String,
    val name: String? = null,
)
/** "Who shared {name}" — the director page's paginated posts see-all. */
@Serializable data class DirectorPostsRoute(
    val directorId: String,
    val name: String? = null,
)
