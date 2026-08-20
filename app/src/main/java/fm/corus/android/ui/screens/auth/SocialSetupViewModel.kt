package fm.corus.android.ui.screens.auth

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.local.readContactPhoneNumbers
import fm.corus.android.data.model.AlbumSearchSummary
import fm.corus.android.data.model.ArtistSummary
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.QuizPick
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.postablePicks
import fm.corus.android.data.model.quizPicksToTastePicks
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.remote.OnboardingTasteMatchesResult
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.model.TrendingWindow
import fm.corus.android.data.model.TrendingArtist
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.MusicServicePreference
import fm.corus.android.domain.SpotifyFtueExperiment
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.NowPlayingState
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.FeedSwitchHintManager
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

@HiltViewModel
class SocialSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val firestoreDataSource: FirestoreDataSource,
    private val nowPlayingManager: NowPlayingManager,
    private val musicServicePreference: MusicServicePreference,
    private val preferencesDataStore: PreferencesDataStore,
    private val remoteConfigService: RemoteConfigService,
    private val musicSearchRepository: MusicSearchRepository,
    private val tmdbRepository: TMDBRepository,
    private val exploreRepository: ExploreRepository,
    private val feedSwitchHintManager: FeedSwitchHintManager,
    val analyticsService: AnalyticsService,
) : ViewModel() {

    /** Whether the taste-match onboarding flow replaces the legacy three-step
     *  social setup. Read once per composition by SocialSetupFlow — flag OFF
     *  keeps the existing flow byte-identical. */
    val onboardingTasteMatchEnabled: Boolean
        get() = remoteConfigService.onboardingTasteMatchEnabled

    /** Whether the TIDAL option should appear in the music-service picker. */
    val tidalEnabled: Boolean
        get() = remoteConfigService.tidalEnabled

    /** Whether the YouTube Music option should appear in the music-service picker. */
    val youtubeMusicEnabled: Boolean
        get() = remoteConfigService.youtubeMusicEnabled

    /** Whether the Deezer option should appear in the music-service picker. */
    val deezerEnabled: Boolean
        get() = remoteConfigService.deezerEnabled

    // ── Contact Sync ──

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _contactsSynced = MutableStateFlow(false)
    val contactsSynced: StateFlow<Boolean> = _contactsSynced.asStateFlow()

    private val _contactMatches = MutableStateFlow<List<CymbalUser>>(emptyList())
    val contactMatches: StateFlow<List<CymbalUser>> = _contactMatches.asStateFlow()

    // ── Follow Friends ──

    private val _isLoadingSuggestions = MutableStateFlow(false)
    val isLoadingSuggestions: StateFlow<Boolean> = _isLoadingSuggestions.asStateFlow()

    private val _followedIds = MutableStateFlow<Set<String>>(emptySet())
    val followedIds: StateFlow<Set<String>> = _followedIds.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    val searchResults: StateFlow<List<CymbalUser>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isFinishing = MutableStateFlow(false)
    val isFinishing: StateFlow<Boolean> = _isFinishing.asStateFlow()

    // ── User Preview Sheet ──

    /** The user being previewed in the half-sheet, or null when closed. */
    private val _previewSheetUser = MutableStateFlow<CymbalUser?>(null)
    val previewSheetUser: StateFlow<CymbalUser?> = _previewSheetUser.asStateFlow()

    private val _previewSheetPosts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val previewSheetPosts: StateFlow<List<CymbalPost>> = _previewSheetPosts.asStateFlow()

    private val _previewSheetIsLoading = MutableStateFlow(false)
    val previewSheetIsLoading: StateFlow<Boolean> = _previewSheetIsLoading.asStateFlow()

    private val _previewSheetIsLoadingMore = MutableStateFlow(false)
    val previewSheetIsLoadingMore: StateFlow<Boolean> = _previewSheetIsLoadingMore.asStateFlow()

    private val _previewSheetHasMore = MutableStateFlow(true)
    val previewSheetHasMore: StateFlow<Boolean> = _previewSheetHasMore.asStateFlow()

    val nowPlayingManagerInstance: NowPlayingManager get() = nowPlayingManager

    val nowPlayingState: StateFlow<NowPlayingState> = nowPlayingManager.state

    val currentUserId: String? get() = authRepository.currentUserId

    fun syncContacts(contentResolver: ContentResolver) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val phoneNumbers = readContactPhoneNumbers(contentResolver)
                if (phoneNumbers.isNotEmpty()) {
                    // Store synced contacts and find matches in parallel.
                    // supervisorScope + per-await runCatching is required: these
                    // are `async` children, and a callable failure (e.g.
                    // findContactMatches / notifyContactsOnSync returning
                    // UNAUTHENTICATED before the auth/App Check token is ready
                    // right after signup) would otherwise propagate to
                    // viewModelScope and CRASH onboarding — the outer try/catch
                    // can't stop `async` exceptions from reaching the parent.
                    supervisorScope {
                        val storeJob = async { firestoreDataSource.storeSyncedContacts(userId, phoneNumbers) }
                        val matchesJob = async {
                            val ids = cloudFunctions.findContactMatches(phoneNumbers).filter { it != userId }
                            userRepository.fetchUsersByIdsBatched(ids)
                        }
                        val notifyJob = async { cloudFunctions.notifyContactsOnSync() }

                        runCatching { storeJob.await() }
                        _contactMatches.value = runCatching { matchesJob.await() }.getOrDefault(emptyList())
                        runCatching { notifyJob.await() }
                    }
                }
            } catch (_: Exception) { }
            preferencesDataStore.setContactsSyncStatus("synced")
            _contactsSynced.value = true
            _isSyncing.value = false
            analyticsService.logContactsSynced(_contactMatches.value.size)
        }
    }

    /** Kept as a no-op so existing call sites (notably SocialSetupFlow.kt) don't
     *  break — popular users now load via the embedded HorizontalPopularUsersRail
     *  composable, and bots are no longer surfaced in onboarding. */
    fun loadSuggestions() {
        // intentionally empty
    }

    fun searchUsers(query: String) {
        _searchQuery.value = query
        if (query.length < 2) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                _searchResults.value = userRepository.searchUsers(query, limit = 10)
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            }
            _isSearching.value = false
        }
    }

    fun toggleFollow(userId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        val isFollowed = _followedIds.value.contains(userId)

        // Optimistic update
        _followedIds.value = if (isFollowed) _followedIds.value - userId else _followedIds.value + userId

        viewModelScope.launch {
            try {
                if (isFollowed) {
                    userRepository.unfollowUser(currentUserId, userId)
                } else {
                    userRepository.followUser(currentUserId, userId)
                }
            } catch (_: Exception) {
                // Revert
                _followedIds.value = if (isFollowed) _followedIds.value + userId else _followedIds.value - userId
            }
        }
    }

    fun saveMusicService(service: MusicService, spotifyInstalled: Boolean) {
        analyticsService.logMusicServiceSelected(service.value)
        viewModelScope.launch {
            musicServicePreference.syncToFirestore(service)
            // Experiment value is delivered on fetch. Stamp only after that
            // so a fast onboarding tap does not stick `off` forever.
            remoteConfigService.awaitInitialFetch()
            SpotifyFtueExperiment.applyOnboardingDefaults(
                uid = authRepository.currentUserId,
                service = service,
                spotifyInstalled = spotifyInstalled,
                rcVariant = remoteConfigService.spotifyFtueVariant,
                preferences = preferencesDataStore,
                analytics = analyticsService,
            )
        }
    }

    /** Open the user-preview half-sheet for [user] and start fetching their posts. */
    fun openUserPreview(user: CymbalUser) {
        val viewerId = authRepository.currentUserId ?: return
        _previewSheetUser.value = user
        _previewSheetPosts.value = emptyList()
        _previewSheetHasMore.value = true
        _previewSheetIsLoading.value = true
        viewModelScope.launch {
            try {
                val posts = postRepository.getProfilePosts(
                    userId = user.id,
                    viewerId = viewerId,
                    limit = PREVIEW_PAGE_SIZE,
                )
                _previewSheetPosts.value = posts
                _previewSheetHasMore.value = posts.size == PREVIEW_PAGE_SIZE
            } catch (_: Exception) {
                _previewSheetHasMore.value = false
            }
            _previewSheetIsLoading.value = false
        }
    }

    /** Fetch the next page of preview posts for the open sheet. */
    fun loadMorePreviewPosts() {
        val user = _previewSheetUser.value ?: return
        val viewerId = authRepository.currentUserId ?: return
        if (_previewSheetIsLoadingMore.value || !_previewSheetHasMore.value) return
        val cursor = _previewSheetPosts.value.lastOrNull()?.timestamp?.time ?: return
        _previewSheetIsLoadingMore.value = true
        viewModelScope.launch {
            try {
                val next = postRepository.getProfilePosts(
                    userId = user.id,
                    viewerId = viewerId,
                    limit = PREVIEW_PAGE_SIZE,
                    lastTimestamp = cursor,
                )
                val existingIds = _previewSheetPosts.value.map { it.id }.toSet()
                val deduped = next.filter { it.id !in existingIds }
                _previewSheetPosts.value = _previewSheetPosts.value + deduped
                _previewSheetHasMore.value = next.size == PREVIEW_PAGE_SIZE
            } catch (_: Exception) {
                _previewSheetHasMore.value = false
            }
            _previewSheetIsLoadingMore.value = false
        }
    }

    fun closeUserPreview() {
        _previewSheetUser.value = null
        _previewSheetPosts.value = emptyList()
        _previewSheetIsLoading.value = false
        _previewSheetIsLoadingMore.value = false
        _previewSheetHasMore.value = true
        nowPlayingManager.stop()
    }

    private companion object {
        private const val PREVIEW_PAGE_SIZE = 15
        private const val QUIZ_SEARCH_DEBOUNCE_MS = 250L

        /** Popular accounts whose avatars shouldn't front the venn animation
         *  (product owner's call — mirrors web VENN_AVATAR_EXCLUDE). */
        private val VENN_AVATAR_EXCLUDE = setOf("arielle", "jakeport461")
    }

    /** Remembers that the primer finished (Allow or Not now) so the MainTab
     * fallback doesn't fire the system dialog on feed entry. */
    fun markPushPermissionRequested() {
        viewModelScope.launch {
            preferencesDataStore.setHasRequestedPushPermission()
        }
    }

    fun logFollowFriendsOnboardingCompleted() {
        analyticsService.logFollowFriendsOnboardingCompleted(_followedIds.value.size)
    }

    /**
     * Zero-follow signups land on Trending (not an empty Following feed).
     * Marked programmatic so the feed-switch coachmark can still show.
     * Call just before finishing onboarding, while [followedIds] is still known.
     */
    fun applyPostOnboardingFeedDefault() {
        feedSwitchHintManager.applyPostOnboardingFeedDefault(_followedIds.value.size)
    }

    // ═══════════════════════════════════════════════
    // TASTE-MATCH ONBOARDING (flag-on flow)
    // ═══════════════════════════════════════════════

    // ── Quiz picks ──

    private val _quizPicks = MutableStateFlow<List<QuizPick>>(emptyList())
    val quizPicks: StateFlow<List<QuizPick>> = _quizPicks.asStateFlow()

    fun addQuizPick(pick: QuizPick) {
        val current = _quizPicks.value
        if (current.any { it.id == pick.id }) return
        if (current.size >= fm.corus.android.data.model.MAX_QUIZ_PICKS) return
        analyticsService.logOnboardingTastePickAdded(pick.kind, current.size + 1)
        _quizPicks.value = current + pick
    }

    fun removeQuizPick(id: String) {
        _quizPicks.value = _quizPicks.value.filter { it.id != id }
    }

    /** "Do it later" discards the quiz session — the suggestions step must
     *  treat the user as a non-quiz user (no venn interstitial, no matcher run
     *  on picks they chose not to submit). Mirrors web onSkip. */
    fun discardQuizPicks() {
        _quizPicks.value = emptyList()
        quizMatchesKey = null
        _tasteMatches.value = null
    }

    fun logQuizCompleted() {
        val picks = _quizPicks.value
        analyticsService.logOnboardingTasteQuizCompleted(
            total = picks.size,
            songs = picks.count { it is QuizPick.Song },
            films = picks.count { it is QuizPick.Film },
            artists = picks.count { it is QuizPick.Artist },
            albums = picks.count { it is QuizPick.Album },
            directors = picks.count { it is QuizPick.Director },
        )
    }

    // ── Quiz universal search ──

    data class QuizSearchResults(
        val artists: List<ArtistSummary> = emptyList(),
        val albums: List<AlbumSearchSummary> = emptyList(),
        val songs: List<CymbalTrack> = emptyList(),
        val films: List<CymbalMovie> = emptyList(),
        val directors: List<ArtistSummary> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = artists.isEmpty() && albums.isEmpty() && songs.isEmpty() &&
                films.isEmpty() && directors.isEmpty()
    }

    private val _quizQuery = MutableStateFlow("")
    val quizQuery: StateFlow<String> = _quizQuery.asStateFlow()

    private val _quizResults = MutableStateFlow(QuizSearchResults())
    val quizResults: StateFlow<QuizSearchResults> = _quizResults.asStateFlow()

    private val _isQuizSearching = MutableStateFlow(false)
    val isQuizSearching: StateFlow<Boolean> = _isQuizSearching.asStateFlow()

    private var quizSearchJob: Job? = null

    /** Monotonic query generation: a stale (cancelled) search's finally block
     *  must not clear the loading flag the newer search just set. */
    private var quizSearchGeneration = 0

    /**
     * One query fans out to songs+artists+albums (a single searchSongs call —
     * includeArtists/includeAlbums ride along), films, and directors, in
     * parallel. Debounced ~250ms like web (un-debounced, each keystroke cost
     * 3 Spotify calls server-side and the burst starved the artist/album
     * lookups past their soft timeout). The UI's chips only FILTER what's
     * shown; all requests always run so switching chips is instant. The
     * debounce gap counts as loading — without that, the empty state flashes
     * before the first results land.
     */
    fun quizSearch(query: String) {
        _quizQuery.value = query
        val generation = ++quizSearchGeneration
        quizSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _quizResults.value = QuizSearchResults()
            _isQuizSearching.value = false
            return
        }
        _isQuizSearching.value = true
        quizSearchJob = viewModelScope.launch {
            delay(QUIZ_SEARCH_DEBOUNCE_MS)
            try {
                coroutineScope {
                    val songsJob = async {
                        runCatching {
                            musicSearchRepository.search(
                                query = trimmed,
                                limit = 12,
                                includeSoundCloud = true,
                                includeArtists = true,
                                includeAlbums = true,
                            )
                        }.getOrDefault(MusicSearchRepository.Page(emptyList(), false))
                    }
                    val filmsJob = async {
                        runCatching { tmdbRepository.searchMovies(trimmed) }
                            .getOrDefault(emptyList())
                    }
                    val directorsJob = async {
                        runCatching { tmdbRepository.searchDirectors(trimmed) }
                            .getOrDefault(emptyList())
                    }
                    val page = songsJob.await()
                    if (generation == quizSearchGeneration) {
                        _quizResults.value = QuizSearchResults(
                            artists = page.artists.take(4),
                            albums = page.albums.take(4),
                            songs = page.tracks,
                            films = filmsJob.await(),
                            directors = directorsJob.await().take(4),
                        )
                    }
                }
            } finally {
                if (generation == quizSearchGeneration) _isQuizSearching.value = false
            }
        }
    }

    /** The film row whose director lookup is in flight (spinner on that row). */
    private val _addingFilmId = MutableStateFlow<String?>(null)
    val addingFilmId: StateFlow<String?> = _addingFilmId.asStateFlow()

    /**
     * Film search results carry no director (TMDB's search endpoint omits
     * credits), so resolve it via the detail call on pick — the matcher's film
     * mode matches on director name. The pick stays usable if resolution fails
     * (it still posts and shows in the tray; it just contributes nothing to
     * matching). Mirrors web addFilm.
     */
    // ── Zero-state browse (quiz search focused, empty query): all-time
    // popular artists (YEAR trending songs deduped by artist, id-bearing
    // rows only) + YEAR trending films. No new backend endpoint. ──

    private val _popularArtists = MutableStateFlow<List<TrendingArtist>>(emptyList())
    val popularArtists: StateFlow<List<TrendingArtist>> = _popularArtists.asStateFlow()

    private val _popularFilms = MutableStateFlow<List<TrendingMovie>>(emptyList())
    val popularFilms: StateFlow<List<TrendingMovie>> = _popularFilms.asStateFlow()

    private val _quizBrowseLoading = MutableStateFlow(false)
    val quizBrowseLoading: StateFlow<Boolean> = _quizBrowseLoading.asStateFlow()

    private var quizBrowseLoadStarted = false

    /** Prefetched by the intro step so browse is usually ready before the
     *  first search-bar tap; the repository memoizes for TRENDING_TTL_MS. */
    fun loadQuizBrowseIfNeeded() {
        if (quizBrowseLoadStarted) return
        quizBrowseLoadStarted = true
        _quizBrowseLoading.value = true
        viewModelScope.launch {
            runCatching { exploreRepository.fetchTrendingArtists(TrendingWindow.YEAR) }
                .getOrNull()?.let { _popularArtists.value = it.take(5) }
            runCatching { exploreRepository.fetchTrendingMovies(TrendingWindow.YEAR) }
                .getOrNull()?.let { _popularFilms.value = it.take(5) }
            _quizBrowseLoading.value = false
        }
    }

    /**
     * [onAdded] runs after the pick lands (post director-detail resolve) so the
     * caller can return the UI to the idle slot tray — films resolve async, so
     * unlike the other row types the composable can't just dismiss on tap
     * (the row's loading spinner would vanish mid-fetch).
     */
    fun addFilmPick(movie: CymbalMovie, onAdded: (() -> Unit)? = null) {
        if (_addingFilmId.value != null) return
        _addingFilmId.value = movie.id
        viewModelScope.launch {
            try {
                val resolved = if (movie.directorName.isBlank()) {
                    runCatching {
                        movie.id.toIntOrNull()?.let { tmdbRepository.getMovieDetails(it) }
                    }.getOrNull() ?: movie
                } else {
                    movie
                }
                addQuizPick(QuizPick.Film(resolved))
                quizSearch("")
                onAdded?.invoke()
            } finally {
                _addingFilmId.value = null
            }
        }
    }

    // ── Taste matches (venn interstitial + suggestions) ──

    private val _tasteMatches = MutableStateFlow<OnboardingTasteMatchesResult?>(null)
    val tasteMatches: StateFlow<OnboardingTasteMatchesResult?> = _tasteMatches.asStateFlow()

    private val _isLoadingTasteMatches = MutableStateFlow(false)
    val isLoadingTasteMatches: StateFlow<Boolean> = _isLoadingTasteMatches.asStateFlow()

    /** The picks key the current match result was computed (and its shown-event
     *  fired) for — one matcher run + one shown-event per reveal, not per
     *  recomposition. Mirrors web's queryKey + shownForKey refs. */
    private var quizMatchesKey: String? = null

    fun loadTasteMatchesIfNeeded() {
        val picks = _quizPicks.value
        if (picks.isEmpty()) return
        val key = picks.joinToString(",") { it.id }
        if (quizMatchesKey == key) return
        quizMatchesKey = key
        _isLoadingTasteMatches.value = true
        _tasteMatches.value = null
        viewModelScope.launch {
            val result = runCatching {
                cloudFunctions.getOnboardingTasteMatches(quizPicksToTastePicks(picks))
            }.getOrDefault(OnboardingTasteMatchesResult())
            _tasteMatches.value = result
            _isLoadingTasteMatches.value = false
            analyticsService.logOnboardingTasteMatchesShown(
                matchCount = result.users.size,
                strongCount = result.strongCount,
            )
            // The callable just stamped users_v2.tasteSeedCount server-side.
            // Unlike iOS (live snapshot listener on the profile doc), Android's
            // userProfile is one-shot-fetched BEFORE onboarding — without a
            // refresh the search rail's post-count pre-gate never learns about
            // the seed and keeps showing "as you post more" to quiz-takers.
            runCatching { authRepository.refreshUserProfile() }
        }
    }

    // ── Venn animation avatars (intro + interstitial community circle) ──

    private val _vennAvatars = MutableStateFlow<List<String>>(emptyList())
    val vennAvatars: StateFlow<List<String>> = _vennAvatars.asStateFlow()

    private var vennAvatarsLoaded = false

    /** Popular-user avatar thumbs that swirl/settle in the venn animation.
     *  Mirrors web's onboarding-venn-avatars query: popular pool, exclusions
     *  applied, first 3 swirl and the next 3 settle in the overlap. */
    fun loadVennAvatarsIfNeeded() {
        if (vennAvatarsLoaded) return
        vennAvatarsLoaded = true
        viewModelScope.launch {
            val users = runCatching {
                userRepository.fetchPopularUsersPaginated(
                    limit = 12,
                    excludeIds = setOfNotNull(authRepository.currentUserId),
                )
            }.getOrDefault(emptyList())
            _vennAvatars.value = users
                .filter { it.username.lowercase() !in VENN_AVATAR_EXCLUDE }
                .mapNotNull { user ->
                    (user.avatarThumbURL ?: user.avatarURL)?.takeIf { it.isNotBlank() }
                }
                .take(6)
        }
    }

    // ── Head-start (post your picks) ──

    /** Removal on the head-start screen is presentation-only: it trims what
     *  gets POSTED, never the taste seed (the quiz pick still counts toward
     *  matching). Mirrors web removedIds. */
    private val _headstartRemovedIds = MutableStateFlow<Set<String>>(emptySet())
    val headstartRemovedIds: StateFlow<Set<String>> = _headstartRemovedIds.asStateFlow()

    fun removeHeadstartPick(id: String) {
        _headstartRemovedIds.value = _headstartRemovedIds.value + id
    }

    val headstartPostables: List<QuizPick>
        // Offer caps at the FIRST 5 postable picks: the server floors a new
        // account's daily allowance at 5, and these posts count toward it.
        get() = postablePicks(_quizPicks.value).take(5).filter { it.id !in _headstartRemovedIds.value }

    private val _isPostingPicks = MutableStateFlow(false)
    val isPostingPicks: StateFlow<Boolean> = _isPostingPicks.asStateFlow()

    /** First-to-share trophies earned by the head-start posts, played one at a
     *  time BEFORE the flow hands off to the feed — same celebration a regular
     *  compose shows. */
    private val _trophyQueue = MutableStateFlow<List<CymbalPost>>(emptyList())
    val trophyQueue: StateFlow<List<CymbalPost>> = _trophyQueue.asStateFlow()

    private val _trophyIndex = MutableStateFlow(0)
    val trophyIndex: StateFlow<Int> = _trophyIndex.asStateFlow()

    /** Dismissing a trophy advances the chain; returns true when the chain is
     *  exhausted and the flow should finish. */
    fun advanceTrophy(): Boolean {
        val next = _trophyIndex.value + 1
        _trophyIndex.value = next
        return next >= _trophyQueue.value.size
    }

    /**
     * Post every remaining postable pick via the createPost callable (empty
     * caption, same payload shape as compose). Failures are per-pick and
     * silent — a failed post simply earns nothing, matching web's allSettled.
     * Calls [onDone] when there are no trophies to celebrate; otherwise the
     * trophy chain plays and the UI finishes after the last dismissal.
     */
    fun postHeadstartPicks(onDone: () -> Unit) {
        if (_isPostingPicks.value) return
        val userId = authRepository.currentUserId ?: return
        val postables = headstartPostables
        if (postables.isEmpty()) {
            onDone()
            return
        }
        _isPostingPicks.value = true
        viewModelScope.launch {
            // One slot per pick so the trophy queue keeps tile order regardless
            // of network completion order; null = no trophy earned / post failed.
            val trophies = arrayOfNulls<CymbalPost?>(postables.size)
            coroutineScope {
                postables.mapIndexed { index, pick ->
                    async {
                        val result = runCatching {
                            postRepository.createPost(headstartPostPayload(pick))
                        }.getOrNull() ?: return@async
                        analyticsService.logPostCreated(
                            if (pick is QuizPick.Film) MediaType.MOVIE.value else MediaType.TRACK.value,
                        )
                        if (result.isFirstPoster) {
                            trophies[index] = trophyPostStub(userId, pick)
                        }
                    }
                }.awaitAll()
            }
            analyticsService.logOnboardingTastePicksPosted(postables.size)
            _isPostingPicks.value = false
            val queue = trophies.filterNotNull()
            if (queue.isNotEmpty()) {
                _trophyIndex.value = 0
                _trophyQueue.value = queue
            } else {
                onDone()
            }
        }
    }

    /** createPost payload for a head-start pick — same keys the compose screen
     *  sends (ComposeViewModel.createPost) with an empty caption. */
    private fun headstartPostPayload(pick: QuizPick): Map<String, Any?> {
        val payload = mutableMapOf<String, Any?>(
            "caption" to "",
            "hashtags" to emptyList<String>(),
        )
        when (pick) {
            is QuizPick.Song -> {
                val track = pick.track
                payload["mediaType"] = MediaType.TRACK.value
                val trackMap = mutableMapOf<String, Any?>(
                    "trackId" to track.id,
                    "trackName" to track.name,
                    "artistName" to track.artistName,
                    "artistIds" to track.artistIds,
                    "albumName" to track.albumName,
                    "albumArtURL" to (track.albumArtURL ?: ""),
                    "albumArtLargeURL" to (track.albumArtLargeURL ?: ""),
                    "durationMs" to track.durationMs,
                    "trackSource" to track.source.raw,
                )
                track.isrc?.let { trackMap["isrc"] = it }
                track.releaseDate?.let { trackMap["trackReleaseDate"] = it }
                track.releaseDatePrecision?.let { trackMap["trackReleaseDatePrecision"] = it }
                track.previewUrl?.let { trackMap["previewUrl"] = it }
                if (track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD) {
                    trackMap["soundcloudId"] = track.soundcloudId ?: ""
                    trackMap["soundcloudPermalinkUrl"] = track.soundcloudPermalinkUrl ?: ""
                }
                if (track.source == fm.corus.android.data.model.TrackSource.AUDIOMACK) {
                    trackMap["audiomackId"] = track.audiomackId ?: ""
                    trackMap["audiomackUrl"] = track.audiomackUrl ?: ""
                    // Carry the artist/album page URLs so the posted card's "…"
                    // menu can link out to Audiomack (no Corus artist/album page).
                    trackMap["audiomackArtistUrl"] = track.audiomackArtistUrl ?: ""
                    trackMap["audiomackAlbumUrl"] = track.audiomackAlbumUrl ?: ""
                }
                payload["track"] = trackMap
            }
            is QuizPick.Film -> {
                val movie = pick.movie
                payload["mediaType"] = MediaType.MOVIE.value
                val movieMap = mutableMapOf<String, Any?>(
                    "movieId" to movie.id,
                    "movieTitle" to movie.title,
                    "directorName" to movie.directorName,
                    "directorIds" to movie.directorIds,
                    "releaseYear" to movie.year,
                    "posterURL" to (movie.posterURL ?: ""),
                    "posterLargeURL" to (movie.posterLargeURL ?: ""),
                    "movieOverview" to movie.overview,
                    "movieRating" to movie.rating,
                    "movieCast" to movie.cast,
                    "tmdbWebURL" to movie.tmdbWebURL,
                )
                movie.releaseDate?.let { movieMap["movieReleaseDate"] = it }
                movie.trailerURL?.let { movieMap["trailerURL"] = it }
                payload["movie"] = movieMap
            }
            else -> error("headstartPostPayload: pick $pick is not postable")
        }
        return payload
    }

    /** A minimal CymbalPost for TrophyCelebrationView — same stub compose
     *  builds after a first-poster createPost. */
    private fun trophyPostStub(userId: String, pick: QuizPick): CymbalPost? = when (pick) {
        is QuizPick.Song -> CymbalPost(
            id = "",
            user = CymbalUser(id = userId, username = "", displayName = ""),
            track = pick.track,
            mediaType = MediaType.TRACK,
            isFirstPoster = true,
        )
        is QuizPick.Film -> CymbalPost(
            id = "",
            user = CymbalUser(id = userId, username = "", displayName = ""),
            track = CymbalTrack(id = "", name = "", artistName = "", albumName = ""),
            mediaType = MediaType.MOVIE,
            movieId = pick.movie.id,
            movieTitle = pick.movie.title,
            directorName = pick.movie.directorName,
            posterURL = pick.movie.posterURL,
            posterLargeURL = pick.movie.posterLargeURL,
            movieReleaseDate = pick.movie.releaseDate,
            isFirstPoster = true,
        )
        else -> null
    }
}
