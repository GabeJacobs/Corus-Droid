package fm.corus.android.ui.components

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
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

    fun isOwnPost(post: CymbalPost): Boolean
    fun loadRecentShareContacts()
    fun searchShareUsers(query: String)
    fun sendPostToUser(userId: String, post: CymbalPost, message: String)
    fun reportPost(postId: String, postUserId: String)
    fun blockUser(targetUserId: String)
    fun deletePost(postId: String)
    suspend fun fetchBackCover(postId: String): String?

    /** Resolves the viewer's preferred-service catalog URL for [track] (Apple
     *  Music / TIDAL / Deezer); used by the post menu's "Open in service" row. */
    suspend fun resolveServiceLinkUrl(track: CymbalTrack): String?
}
