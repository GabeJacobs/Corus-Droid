package fm.corus.android.ui.screens.search

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrendingArtist
import fm.corus.android.data.model.TrendingHashtag
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.model.TrendingWindow
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.navigation.FilmDetailRoute
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backing state for the full-screen trending lists ("See all" from the search
 * page's compact trending strips).
 *
 * This screen lives in its OWN backstack entry, so a
 * `hiltViewModel<SearchViewModel>()` here would be a FRESH, empty instance —
 * not the one the search page populated. It therefore loads its own data.
 *
 * The window selections are DataStore-backed (same keys SearchViewModel
 * reads), so this screen stays in sync with the search page's compact strips
 * for free.
 */
@HiltViewModel
class TrendingListViewModel @Inject constructor(
    private val exploreRepository: ExploreRepository,
    private val firestoreDataSource: FirestoreDataSource,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val preferencesDataStore: PreferencesDataStore,
    private val authRepository: AuthRepository,
    val nowPlayingManager: NowPlayingManager,
) : ViewModel() {

    private val _trendingSongs = MutableStateFlow<List<TrendingSong>>(emptyList())
    val trendingSongs: StateFlow<List<TrendingSong>> = _trendingSongs.asStateFlow()

    private val _trendingMovies = MutableStateFlow<List<TrendingMovie>>(emptyList())
    val trendingMovies: StateFlow<List<TrendingMovie>> = _trendingMovies.asStateFlow()

    private val _trendingHashtags = MutableStateFlow<List<TrendingHashtag>>(emptyList())
    val trendingHashtags: StateFlow<List<TrendingHashtag>> = _trendingHashtags.asStateFlow()

    private val _isSongsLoading = MutableStateFlow(true)
    val isSongsLoading: StateFlow<Boolean> = _isSongsLoading.asStateFlow()

    private val _isMoviesLoading = MutableStateFlow(true)
    val isMoviesLoading: StateFlow<Boolean> = _isMoviesLoading.asStateFlow()

    private val _isHashtagsLoading = MutableStateFlow(true)
    val isHashtagsLoading: StateFlow<Boolean> = _isHashtagsLoading.asStateFlow()

    private val _followedHashtagNames = MutableStateFlow<Set<String>>(emptySet())
    val followedHashtagNames: StateFlow<Set<String>> = _followedHashtagNames.asStateFlow()

    // Selected time window per kind. Persisted in DataStore (same keys as
    // SearchViewModel) so the choice survives restarts AND mirrors the search
    // page's strips. Mirrors SearchViewModel's window StateFlow pattern.
    val trendingSongsWindow: StateFlow<TrendingWindow> =
        preferencesDataStore.trendingSongsWindow
            .map { TrendingWindow.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TrendingWindow.DEFAULT)

    val trendingFilmsWindow: StateFlow<TrendingWindow> =
        preferencesDataStore.trendingFilmsWindow
            .map { TrendingWindow.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TrendingWindow.DEFAULT)

    val trendingHashtagsWindow: StateFlow<TrendingWindow> =
        preferencesDataStore.trendingHashtagsWindow
            .map { TrendingWindow.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TrendingWindow.DEFAULT)

    val trendingArtistsWindow: StateFlow<TrendingWindow> =
        preferencesDataStore.trendingArtistsWindow
            .map { TrendingWindow.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TrendingWindow.DEFAULT)

    private val _trendingArtists = MutableStateFlow<List<TrendingArtist>>(emptyList())
    val trendingArtists: StateFlow<List<TrendingArtist>> = _trendingArtists.asStateFlow()

    private val _isArtistsLoading = MutableStateFlow(true)
    val isArtistsLoading: StateFlow<Boolean> = _isArtistsLoading.asStateFlow()

    private val _isResolvingArtist = MutableStateFlow(false)
    val isResolvingArtist: StateFlow<Boolean> = _isResolvingArtist.asStateFlow()

    // The setters only persist; the screen collects the window StateFlow and
    // refetches on every emission, so persistence IS the refetch trigger.

    fun setTrendingSongsWindow(window: TrendingWindow) {
        viewModelScope.launch { preferencesDataStore.setTrendingSongsWindow(window.key) }
    }

    fun setTrendingFilmsWindow(window: TrendingWindow) {
        viewModelScope.launch { preferencesDataStore.setTrendingFilmsWindow(window.key) }
    }

    fun setTrendingHashtagsWindow(window: TrendingWindow) {
        viewModelScope.launch { preferencesDataStore.setTrendingHashtagsWindow(window.key) }
    }

    fun setTrendingArtistsWindow(window: TrendingWindow) {
        viewModelScope.launch { preferencesDataStore.setTrendingArtistsWindow(window.key) }
    }

    suspend fun loadSongs(window: TrendingWindow) {
        _isSongsLoading.value = true
        _trendingSongs.value = try {
            exploreRepository.fetchTrendingSongs(window)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load trending songs", e)
            emptyList()
        }
        _isSongsLoading.value = false
    }

    suspend fun loadMovies(window: TrendingWindow) {
        _isMoviesLoading.value = true
        _trendingMovies.value = try {
            exploreRepository.fetchTrendingMovies(window)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load trending films", e)
            emptyList()
        }
        _isMoviesLoading.value = false
    }

    suspend fun loadHashtags(window: TrendingWindow) {
        _isHashtagsLoading.value = true
        _trendingHashtags.value = try {
            firestoreDataSource.fetchTrendingHashtagsWindowed(window, limit = 20)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load trending hashtags", e)
            emptyList()
        }
        _isHashtagsLoading.value = false
    }

    suspend fun loadArtists(window: TrendingWindow) {
        _isArtistsLoading.value = true
        _trendingArtists.value = try {
            exploreRepository.fetchTrendingArtists(window)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load trending artists", e)
            emptyList()
        }
        _isArtistsLoading.value = false
        val names = _trendingArtists.value.map { it.artistName }
        if (names.isNotEmpty()) {
            viewModelScope.launch {
                cloudFunctions.prefetchArtistDestinations(names)
            }
        }
    }

    suspend fun resolveTrendingArtist(artist: TrendingArtist): fm.corus.android.ui.navigation.ArtistPageRoute? {
        cloudFunctions.cachedResolvedArtist(artist.artistName)?.let { cached ->
            return fm.corus.android.ui.navigation.ArtistPageRoute(
                artistId = cached.id,
                name = cached.name,
                imageUrl = cached.imageUrl,
            )
        }
        if (_isResolvingArtist.value) return null
        _isResolvingArtist.value = true
        return try {
            val resolved = cloudFunctions.resolveArtistByName(artist.artistName) ?: return null
            fm.corus.android.ui.navigation.ArtistPageRoute(
                artistId = resolved.id,
                name = resolved.name,
                imageUrl = resolved.imageUrl,
            )
        } finally {
            _isResolvingArtist.value = false
        }
    }

    /** Mirrors SearchViewModel.refreshFollowedHashtags. */
    fun refreshFollowedHashtags() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                _followedHashtagNames.value = firestoreDataSource.fetchFollowedHashtagNames(uid)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load followed hashtags", e)
            }
        }
    }

    /** Optimistic follow/unfollow with revert on failure — mirrors
     *  SearchViewModel.toggleHashtagFollowByName. */
    fun toggleHashtagFollowByName(name: String) {
        val uid = authRepository.currentUserId ?: return
        val key = name.lowercase()
        val current = _followedHashtagNames.value
        val wasFollowing = current.contains(key)
        // Optimistic update.
        _followedHashtagNames.value = if (wasFollowing) current - key else current + key
        viewModelScope.launch {
            try {
                if (wasFollowing) {
                    firestoreDataSource.unfollowHashtag(uid, name)
                } else {
                    firestoreDataSource.followHashtag(uid, name)
                }
            } catch (e: Exception) {
                Log.e(TAG, "toggleHashtagFollow failed for $key", e)
                // Revert.
                val rolled = _followedHashtagNames.value
                _followedHashtagNames.value =
                    if (wasFollowing) rolled + key else rolled - key
            }
        }
    }

    companion object {
        private const val TAG = "TrendingListVM"
    }
}

/**
 * Full-screen trending list — songs, films, or hashtags depending on [kind]
 * ("songs" | "films" | "hashtags" | "artists"). Reuses SearchScreen's
 * trending content composables so rows/headers/skeletons render identically
 * to the search tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingListScreen(
    kind: String,
    viewModel: TrendingListViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (FilmDetailRoute) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
    onNavigateToArtist: (fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val trendingSongs by viewModel.trendingSongs.collectAsState()
    val trendingMovies by viewModel.trendingMovies.collectAsState()
    val trendingHashtags by viewModel.trendingHashtags.collectAsState()
    val trendingArtists by viewModel.trendingArtists.collectAsState()
    val isSongsLoading by viewModel.isSongsLoading.collectAsState()
    val isMoviesLoading by viewModel.isMoviesLoading.collectAsState()
    val isHashtagsLoading by viewModel.isHashtagsLoading.collectAsState()
    val isArtistsLoading by viewModel.isArtistsLoading.collectAsState()
    val isResolvingArtist by viewModel.isResolvingArtist.collectAsState()
    val songsWindow by viewModel.trendingSongsWindow.collectAsState()
    val filmsWindow by viewModel.trendingFilmsWindow.collectAsState()
    val hashtagsWindow by viewModel.trendingHashtagsWindow.collectAsState()
    val artistsWindow by viewModel.trendingArtistsWindow.collectAsState()
    val followedHashtagNames by viewModel.followedHashtagNames.collectAsState()

    // Only fetch the kind being shown. The window StateFlows are DataStore
    // backed, so collecting them both performs the initial load AND refetches
    // whenever the user picks a different window (the setter just persists).
    LaunchedEffect(kind) {
        when (kind) {
            KIND_SONGS -> viewModel.trendingSongsWindow.collect { viewModel.loadSongs(it) }
            KIND_FILMS -> viewModel.trendingFilmsWindow.collect { viewModel.loadMovies(it) }
            KIND_HASHTAGS -> {
                viewModel.refreshFollowedHashtags()
                viewModel.trendingHashtagsWindow.collect { viewModel.loadHashtags(it) }
            }
            KIND_ARTISTS -> viewModel.trendingArtistsWindow.collect { viewModel.loadArtists(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (kind) {
                                KIND_FILMS -> fm.corus.android.R.string.search_trending_films_title
                                KIND_HASHTAGS -> fm.corus.android.R.string.search_trending_hashtags_title
                                KIND_ARTISTS -> fm.corus.android.R.string.search_trending_artists_title
                                else -> fm.corus.android.R.string.search_trending_songs_title
                            },
                        ),
                        style = CorusFont.screenTitle,
                        color = CorusColors.Text,
                    )
                },
                navigationIcon = {
                    CorusHeaderIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(fm.corus.android.R.string.common_back),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (kind) {
                KIND_FILMS -> TrendingFilmsContent(
                    listState = listState,
                    movies = trendingMovies,
                    isLoading = isMoviesLoading,
                    window = filmsWindow,
                    onWindowChange = viewModel::setTrendingFilmsWindow,
                    onFilmTap = onNavigateToFilm,
                )
                KIND_HASHTAGS -> TrendingHashtagsContent(
                    listState = listState,
                    hashtags = trendingHashtags,
                    isLoading = isHashtagsLoading,
                    followedHashtagNames = followedHashtagNames,
                    window = hashtagsWindow,
                    onWindowChange = viewModel::setTrendingHashtagsWindow,
                    onHashtagTap = { tag -> onNavigateToHashtag(tag.name) },
                    onToggleFollow = { tag -> viewModel.toggleHashtagFollowByName(tag.name) },
                )
                KIND_ARTISTS -> TrendingArtistsContent(
                    listState = listState,
                    artists = trendingArtists,
                    isLoading = isArtistsLoading,
                    window = artistsWindow,
                    onWindowChange = viewModel::setTrendingArtistsWindow,
                    onArtistTap = { artist ->
                        scope.launch {
                            val route = viewModel.resolveTrendingArtist(artist)
                            if (route != null) onNavigateToArtist(route)
                            else android.widget.Toast.makeText(
                                context,
                                context.getString(fm.corus.android.R.string.song_detail_artist_not_found),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
                else -> TrendingSongsContent(
                    listState = listState,
                    songs = trendingSongs,
                    isLoading = isSongsLoading,
                    window = songsWindow,
                    onWindowChange = viewModel::setTrendingSongsWindow,
                    onSongTap = onNavigateToSong,
                    nowPlaying = viewModel.nowPlayingManager,
                )
            }
            if (isResolvingArtist) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CorusColors.Accent)
                }
            }
        }
    }
}

private const val KIND_SONGS = "songs"
private const val KIND_FILMS = "films"
private const val KIND_HASHTAGS = "hashtags"
private const val KIND_ARTISTS = "artists"
