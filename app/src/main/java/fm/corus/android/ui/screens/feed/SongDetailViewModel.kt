package fm.corus.android.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class SongDetailViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val nowPlayingManager: NowPlayingManager,
    val analyticsService: AnalyticsService,
) : ViewModel() {

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _uniquePosterCount = MutableStateFlow<Int?>(null)
    val uniquePosterCount: StateFlow<Int?> = _uniquePosterCount.asStateFlow()

    val nowPlayingState = nowPlayingManager.state
    val previewLoadingTrackId = nowPlayingManager.loadingTrackId

    private var firstPosterId: String? = null
    private var paginationCursor: Long? = null
    private val pageSize = 15

    // Track metadata for passing to Cloud Function
    private var currentTrackId: String = ""
    private var spotifyURI: String? = null
    private var trackName: String? = null
    private var artistName: String? = null

    fun loadSongPosts(
        trackId: String,
        spotifyURI: String? = null,
        trackName: String? = null,
        artistName: String? = null,
    ) {
        this.currentTrackId = trackId
        this.spotifyURI = spotifyURI
        this.trackName = trackName
        this.artistName = artistName
        viewModelScope.launch {
            _loadError.value = null
            _isLoading.value = true

            if (SCREENSHOT_MODE) {
                _posts.value = buildFakePosts(trackId, trackName, artistName, spotifyURI)
                _uniquePosterCount.value = 847
                _hasMore.value = false
                _isLoading.value = false
                return@launch
            }

            try {
                val page = postRepository.fetchSongPostsFromCloud(
                    trackId = trackId,
                    spotifyURI = spotifyURI,
                    trackName = trackName,
                    artistName = artistName,
                    pageSize = pageSize,
                )
                firstPosterId = page.firstPosterId
                _uniquePosterCount.value = page.uniquePosterCount
                paginationCursor = page.posts.lastOrNull()?.timestamp?.time

                val unique = deduplicateByUser(page.posts)
                _posts.value = moveFirstPosterToTop(unique, page.firstPosterId)
                _hasMore.value = page.posts.size >= pageSize
            } catch (e: Exception) {
                _loadError.value = "Couldn't load posts for this song."
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        val cursor = paginationCursor ?: return
        if (_isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = postRepository.fetchSongPostsFromCloud(
                    trackId = currentTrackId,
                    spotifyURI = spotifyURI,
                    trackName = trackName,
                    artistName = artistName,
                    pageSize = pageSize,
                    beforeMs = cursor,
                )
                paginationCursor = page.posts.lastOrNull()?.timestamp?.time

                val existingUserIds = _posts.value.map { it.user.id }.toSet()
                val newPosts = page.posts.filter { it.user.id !in existingUserIds }
                _posts.value = _posts.value + newPosts
                _hasMore.value = page.posts.size >= pageSize
            } catch (_: Exception) { }
            _isLoadingMore.value = false
        }
    }

    fun togglePreview(
        trackId: String,
        trackName: String,
        artistName: String,
        albumArtURL: String?,
        previewUrl: String?,
        spotifyURI: String?,
        spotifyWebURL: String?,
        isrc: String?,
    ) {
        viewModelScope.launch {
            nowPlayingManager.play(
                trackId = trackId,
                trackName = trackName,
                artistName = artistName,
                albumArtURL = albumArtURL,
                previewUrl = previewUrl,
                spotifyURI = spotifyURI,
                spotifyWebURL = spotifyWebURL,
                isrc = isrc,
            )
        }
    }

    private fun deduplicateByUser(posts: List<CymbalPost>): List<CymbalPost> {
        val seen = mutableSetOf<String>()
        return posts.filter { seen.add(it.user.id) }
    }

    private fun moveFirstPosterToTop(posts: List<CymbalPost>, firstPosterId: String?): List<CymbalPost> {
        // Partition: non-bots first, bots last, preserving relative order
        val nonBots = posts.filter { !it.user.isBot }
        val bots = posts.filter { it.user.isBot }
        val sorted = (nonBots + bots).toMutableList()

        if (firstPosterId != null) {
            val idx = sorted.indexOfFirst { it.user.id == firstPosterId }
            if (idx > 0) {
                val first = sorted.removeAt(idx)
                sorted.add(0, first)
            }
        }
        return sorted
    }

    private fun buildFakePosts(
        trackId: String,
        trackName: String?,
        artistName: String?,
        spotifyURI: String?,
    ): List<CymbalPost> {
        // (displayName, username, isVerified, isBot, isFirstPoster)
        val fakeUsers = listOf(
            FakeUser("NINA SIMONE", "ninasimone", false, true, true),
            FakeUser("Alex Rivera", "alexrivera", false, false, false),
            FakeUser("Mia Chen", "miachen", true, false, false),
            FakeUser("Jordan Blake", "jordanblake", false, false, false),
            FakeUser("Samira Osei", "samiraosei", false, false, false),
            FakeUser("Liam Donovan", "liamdonovan", false, false, false),
            FakeUser("Priya Sharma", "priyasharma", false, false, false),
            FakeUser("Marcus Hall", "marcushall", true, false, false),
            FakeUser("Chloe Dupont", "chloedupont", false, false, false),
            FakeUser("Kenji Tanaka", "kenjitanaka", false, false, false),
            FakeUser("Ava Moretti", "avamoretti", false, false, false),
            FakeUser("Diego Santos", "diegosantos", false, false, false),
        )
        val timeOffsetsMs = longArrayOf(
            -3_600_000L, -7_200_000L, -10_800_000L, -14_400_000L, -28_800_000L,
            -43_200_000L, -86_400_000L, -172_800_000L, -259_200_000L, -345_600_000L,
            -432_000_000L, -518_400_000L,
        )
        val now = System.currentTimeMillis()
        val track = CymbalTrack(
            id = trackId,
            name = trackName ?: "",
            artistName = artistName ?: "",
            albumName = "",
            albumArtURL = null,
            albumArtLargeURL = null,
            spotifyURI = spotifyURI ?: "",
            spotifyWebURL = "",
            durationMs = 0,
            previewUrl = null,
            isrc = null,
            albumArtBackURL = null,
        )
        return fakeUsers.mapIndexed { index, info ->
            val user = CymbalUser(
                id = "fake_$index",
                username = info.username,
                displayName = info.displayName,
                avatarURL = "https://i.pravatar.cc/150?img=${index + 1}",
                isVerified = info.isVerified,
                isBot = info.isBot,
                botType = if (info.isBot) "music" else null,
            )
            CymbalPost(
                id = "fake_post_$index",
                user = user,
                track = track,
                timestamp = Date(now + timeOffsetsMs[index]),
                isFirstPoster = info.isFirstPoster,
            )
        }
    }

    private data class FakeUser(
        val displayName: String,
        val username: String,
        val isVerified: Boolean,
        val isBot: Boolean,
        val isFirstPoster: Boolean,
    )

    companion object {
        // SCREENSHOT MODE: set to true to show fake users, false for real data
        private const val SCREENSHOT_MODE = true
    }
}
