package fm.corus.android.ui.components

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.domain.EngagementState
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for everything [PostMenuSheets] needs from a screen's ViewModel.
 * Implemented by Feed/ProfileFeed/PostDetail view models so the bottom-sheet
 * menu + share/edit/delete flows render identically everywhere.
 */
interface PostMenuActions {
    val remoteConfig: RemoteConfigService
    val analyticsService: AnalyticsService

    val shareSearchResults: StateFlow<List<CymbalUser>>
    val recentShareContacts: StateFlow<List<CymbalUser>>
    val isShareSearching: StateFlow<Boolean>
    val isLoadingShareContacts: StateFlow<Boolean>

    /** Live per-post engagement (like/save/counts) keyed by post id. The "…"
     *  menu reads [EngagementState.isSaved] to label its Save / Unsave row. */
    val engagementStates: StateFlow<Map<String, EngagementState>>

    fun isOwnPost(post: CymbalPost): Boolean
    fun loadRecentShareContacts()
    fun searchShareUsers(query: String)
    fun sendPostToUser(userId: String, post: CymbalPost, message: String)
    fun reportPost(postId: String, postUserId: String)
    fun blockUser(targetUserId: String)
    /** Toggles the viewer's save (bookmark) on the post — same action as the
     *  post card's bookmark button. Own posts show Edit Caption instead. */
    fun toggleSave(postId: String)
    fun deletePost(postId: String)
    suspend fun fetchBackCover(postId: String): String?

    /** Resolves the viewer's preferred-service catalog URL for [track] (Apple
     *  Music / TIDAL / Deezer); used by the post menu's "Open in service" row. */
    suspend fun resolveServiceLinkUrl(track: CymbalTrack): String?

    suspend fun resolveAlbumIdForTrack(track: CymbalTrack): String?

    /** Full resolveTrackDestinations payload for menu routing (album + Option A). */
    suspend fun resolveTrackDestinationsForTrack(track: CymbalTrack): fm.corus.android.data.remote.CloudFunctionsDataSource.TrackDestinations

    /** Resolves the first Spotify artist id for [track] when it reached the client
     *  without one (Apple-Music search posts land with `artistIds:[]`). Backs the
     *  "Go to Artist" row's resolve-on-tap; null on a miss. Sibling of
     *  [resolveAlbumIdForTrack] — one backend call serves both. */
    suspend fun resolveArtistIdForTrack(track: CymbalTrack): String?
}
