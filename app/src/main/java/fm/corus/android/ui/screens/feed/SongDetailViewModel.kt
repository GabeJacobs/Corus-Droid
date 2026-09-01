package fm.corus.android.ui.screens.feed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.CommentDeletedEvent
import fm.corus.android.domain.CommentEditedEvent
import fm.corus.android.domain.FullSongPlayCoordinator
import fm.corus.android.domain.SongPlayRouting
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.QueuedTrack
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.R
import fm.corus.android.service.AnalyticsService
import fm.corus.android.ui.components.ToastManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongDetailViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val nowPlayingManager: NowPlayingManager,
    val analyticsService: AnalyticsService,
    private val commentEditedEvent: CommentEditedEvent,
    private val commentDeletedEvent: CommentDeletedEvent,
    private val cloudFunctions: fm.corus.android.data.remote.CloudFunctionsDataSource,
    private val remoteConfigService: fm.corus.android.service.RemoteConfigService,
    private val preferencesDataStore: PreferencesDataStore,
    private val playbackModePromptManager: fm.corus.android.domain.PlaybackModePromptManager,
    val musicServicePreference: fm.corus.android.domain.MusicServicePreference,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Prototype gate for the immersive (blurred-cover hero + frosted collapsing
     *  bar) song header. Shares the artist header's debug-on / RC-gated flag. */
    val immersiveHeaderEnabled: Boolean
        get() = remoteConfigService.immersiveArtistHeaderEnabled

    val prereleaseAlbumPagesEnabled: Boolean
        get() = remoteConfigService.prereleaseAlbumPagesEnabled

    /**
     * Resolve the link-out URL for a Spotify-source track in the viewer's
     * *preferred* service (Apple Music / TIDAL / Deezer). Returns null for
     * Spotify (caller opens the post's own URI) and on no-match / error.
     * Mirrors iOS `SongDetailView`'s service routing. See PostDetailViewModel.
     */
    suspend fun resolveServiceLinkUrl(track: fm.corus.android.data.model.CymbalTrack): String? =
        resolveLinkUrl(track, musicServicePreference.current.value)

    /**
     * Resolve the link-out URL for an explicit [service] — used by the
     * alternate-service button and Apple-only-track routing, which open a
     * service other than (or in addition to) the viewer's preference.
     */
    suspend fun resolveLinkUrl(
        track: fm.corus.android.data.model.CymbalTrack,
        service: fm.corus.android.data.model.MusicService,
    ): String? =
        fm.corus.android.domain.MusicServiceLinkOut.resolveLinkOutUrl(track, service, cloudFunctions)

    /** Resolve an Apple-sourced track to its Spotify open URL (or null on a
     *  confirmed miss / error). Backs the song-detail "Play in Spotify" tap under
     *  Apple-primary search — the detail-page twin of the mini-player resolve. */
    suspend fun resolveSpotifyFromApple(
        trackId: String,
        name: String,
        artist: String,
        isrc: String?,
    ): String? =
        fm.corus.android.domain.MusicServiceLinkOut.resolveSpotifyFromApple(trackId, name, artist, isrc, cloudFunctions)

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    init {
        viewModelScope.launch {
            commentEditedEvent.events.collect { payload ->
                _posts.value = applyCommentEditToPosts(_posts.value, payload)
            }
        }
        viewModelScope.launch {
            commentDeletedEvent.events.collect { payload ->
                _posts.value = applyCommentDeleteToPosts(_posts.value, payload)
            }
        }
    }

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
    private var isrc: String? = null
    private var trackName: String? = null
    private var artistName: String? = null

    fun loadSongPosts(
        trackId: String,
        spotifyURI: String? = null,
        isrc: String? = null,
        trackName: String? = null,
        artistName: String? = null,
        routeArtistId: String? = null,
    ) {
        this.currentTrackId = trackId
        this.spotifyURI = spotifyURI
        this.isrc = isrc
        this.trackName = trackName
        this.artistName = artistName
        viewModelScope.launch {
            _loadError.value = null
            _isLoading.value = true

            try {
                val page = postRepository.fetchSongPostsFromCloud(
                    trackId = trackId,
                    spotifyURI = spotifyURI,
                    isrc = isrc,
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

    // ── On-tap destination resolution (artist_pages_enabled) ──
    // "Go to Artist" / "Go to Album" always show (album except SoundCloud). When
    // the seed track reached us without a Spotify id, the tap resolves it via
    // resolveTrackDestinations — server-cached by ISRC, so the first tap on a
    // song resolves it for everyone. Both ids are cached here for instant repeat
    // taps. Mirrors iOS SongDetailView.resolveThenNavigate.

    private val _resolvedArtistId = MutableStateFlow<String?>(null)
    val resolvedArtistId: StateFlow<String?> = _resolvedArtistId.asStateFlow()

    private val _resolvedAlbumId = MutableStateFlow<String?>(null)
    val resolvedAlbumId: StateFlow<String?> = _resolvedAlbumId.asStateFlow()

    private val _isResolvingDestination = MutableStateFlow(false)
    val isResolvingDestination: StateFlow<Boolean> = _isResolvingDestination.asStateFlow()

    /** Resolve the track's Spotify destinations on demand (server-cached), cache
     *  both ids, and return them so the caller can navigate immediately (state
     *  emission is async). Empty on a miss. */
    suspend fun resolveDestinations(
        trackId: String,
        isrc: String?,
        name: String,
        artist: String,
        appleMusicId: String? = null,
        bandcampUrl: String? = null,
        bandcampArtistUrl: String? = null,
    ): fm.corus.android.data.remote.CloudFunctionsDataSource.TrackDestinations {
        _isResolvingDestination.value = true
        val dest = cloudFunctions.resolveTrackDestinations(
            trackId, isrc, name, artist, appleMusicId, bandcampUrl, bandcampArtistUrl,
        )
        _isResolvingDestination.value = false
        dest.artistIds.firstOrNull()?.let { _resolvedArtistId.value = it }
        dest.albumId?.takeIf { it.isNotBlank() }?.let { _resolvedAlbumId.value = it }
        return dest
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
                    isrc = isrc,
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
        albumArtLargeURL: String? = null,
        previewUrl: String?,
        spotifyURI: String?,
        spotifyWebURL: String?,
        isrc: String?,
        source: fm.corus.android.data.model.TrackSource = fm.corus.android.data.model.TrackSource.SPOTIFY,
        soundcloudId: String? = null,
        soundcloudPermalinkUrl: String? = null,
    ) {
        viewModelScope.launch {
            val track = CymbalTrack(
                id = trackId,
                name = trackName,
                artistName = artistName,
                albumName = "",
                albumArtURL = albumArtURL,
                albumArtLargeURL = albumArtLargeURL,
                previewUrl = previewUrl,
                spotifyURI = spotifyURI ?: "",
                spotifyWebURL = spotifyWebURL ?: "",
                isrc = isrc,
                source = source,
                soundcloudId = soundcloudId,
                soundcloudPermalinkUrl = soundcloudPermalinkUrl,
            )
            val preferFull = SongPlayRouting.preferFullPlaybackOnCatalog(preferencesDataStore)
            // Trending (and similar) stages the surrounding list before nav so
            // Next walks the chart after song-detail play — mirrors iOS
            // SongDetailView(queue:).
            val queue = nowPlayingManager.catalogQueueForPlayback(trackId)
            val outcome = FullSongPlayCoordinator.playTapOutcome(
                track = track,
                queue = queue,
                nowPlaying = nowPlayingManager,
                remoteConfig = remoteConfigService,
                musicService = musicServicePreference.current.value,
                playFullSongs = preferencesDataStore.effectivePlayFullSongsSync(),
                playbackModePromptManager = playbackModePromptManager,
                preferFullSong = preferFull,
            )
            FullSongPlayCoordinator.applyPlayTapOutcome(
                outcome = outcome,
                track = track,
                queue = queue,
                nowPlaying = nowPlayingManager,
                remoteConfig = remoteConfigService,
                musicService = musicServicePreference.current.value,
                playFullSongs = preferencesDataStore.effectivePlayFullSongsSync(),
                playbackModePromptManager = playbackModePromptManager,
                onPreview = {
                    if (queue.isEmpty()) {
                        nowPlayingManager.play(
                            trackId = trackId,
                            trackName = trackName,
                            artistName = artistName,
                            albumArtURL = albumArtURL,
                            albumArtLargeURL = albumArtLargeURL,
                            previewUrl = previewUrl,
                            spotifyURI = spotifyURI,
                            spotifyWebURL = spotifyWebURL,
                            isrc = isrc,
                            source = source,
                            soundcloudId = soundcloudId,
                            soundcloudPermalinkUrl = soundcloudPermalinkUrl,
                        )
                    } else {
                        val queued = queue.firstOrNull { it.trackId == trackId }
                            ?: QueuedTrack(
                                trackId = trackId,
                                trackName = trackName,
                                artistName = artistName,
                                albumArtURL = albumArtURL,
                                albumArtLargeURL = albumArtLargeURL,
                                previewUrl = previewUrl,
                                spotifyURI = spotifyURI,
                                spotifyWebURL = spotifyWebURL,
                                isrc = isrc,
                                sourcePostId = null,
                                source = source,
                                soundcloudId = soundcloudId,
                                soundcloudPermalinkUrl = soundcloudPermalinkUrl,
                            )
                        nowPlayingManager.play(track = queued, queue = queue)
                    }
                },
                scope = viewModelScope,
            )
        }
    }

    // ── Song share sheet ──
    // Mirrors the post share sheet's recipient picker (see FeedViewModel), but
    // shares a *track*: DMs send a `sharedTrack` message (deep-links to this
    // song page in-app). Recents are the most recent people you've DMed.

    private val _shareSearchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    val shareSearchResults: StateFlow<List<CymbalUser>> = _shareSearchResults.asStateFlow()

    private val _recentShareContacts = MutableStateFlow<List<CymbalUser>>(emptyList())
    val recentShareContacts: StateFlow<List<CymbalUser>> = _recentShareContacts.asStateFlow()

    private val _isShareSearching = MutableStateFlow(false)
    val isShareSearching: StateFlow<Boolean> = _isShareSearching.asStateFlow()

    private val _isLoadingShareContacts = MutableStateFlow(true)
    val isLoadingShareContacts: StateFlow<Boolean> = _isLoadingShareContacts.asStateFlow()

    private var shareSearchJob: Job? = null

    fun loadRecentShareContacts() {
        val userId = authRepository.currentUserId ?: return
        loadRecentDmShareContacts(
            userId = userId,
            messageRepository = messageRepository,
            currentContacts = _recentShareContacts.value,
            setContacts = { _recentShareContacts.value = it },
            setLoading = { _isLoadingShareContacts.value = it },
            scope = viewModelScope,
        )
    }

    fun searchShareUsers(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            shareSearchJob?.cancel()
            _shareSearchResults.value = emptyList()
            _isShareSearching.value = false
            return
        }

        shareSearchJob?.cancel()
        shareSearchJob = viewModelScope.launch {
            _isShareSearching.value = true
            delay(250)
            try {
                _shareSearchResults.value = userRepository.searchUsers(trimmed, includeFollowed = true)
            } catch (_: Exception) {
                _shareSearchResults.value = emptyList()
            }
            _isShareSearching.value = false
        }
    }

    fun sendTrackToUser(userId: String, track: CymbalTrack, message: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val threadId = messageRepository.getOrCreateThread(currentUserId, userId)
                messageRepository.sendSharedTrackMessage(
                    threadId = threadId,
                    fromUserId = currentUserId,
                    text = message.trim(),
                    track = track,
                )
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_send_post))
            }
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

}
