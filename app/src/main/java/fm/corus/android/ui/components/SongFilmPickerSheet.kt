package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.R
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.ViewModel

enum class PickerMode { SONG, FILM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongFilmPickerSheet(
    initialMode: PickerMode,
    onSongSelected: (CymbalTrack) -> Unit,
    onFilmSelected: (CymbalMovie) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
) {
    val viewModel: SongFilmPickerViewModel = hiltViewModel()
    val musicSearchRepository = viewModel.musicSearchRepository
    val tmdbRepository = viewModel.tmdbRepository
    val trendingSongs by viewModel.trendingSongs.collectAsState()
    val trendingMovies by viewModel.trendingMovies.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()

    var mode by remember { mutableStateOf(initialMode) }
    var searchQuery by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<CymbalTrack>>(emptyList()) }
    var movies by remember { mutableStateOf<List<CymbalMovie>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun runSearch(query: String, currentMode: PickerMode) {
        searchJob?.cancel()
        if (query.isBlank()) {
            tracks = emptyList()
            movies = emptyList()
            isSearching = false
            return
        }
        // Show skeletons immediately rather than waiting through the debounce window.
        isSearching = true
        searchJob = scope.launch {
            delay(300)
            try {
                if (currentMode == PickerMode.SONG) {
                    tracks = musicSearchRepository.search(query).tracks
                    movies = emptyList()
                } else {
                    val initial = tmdbRepository.searchMovies(query)
                    movies = initial
                    tracks = emptyList()
                    try {
                        movies = tmdbRepository.prefetchDirectors(initial)
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) {
                tracks = emptyList()
                movies = emptyList()
            }
            isSearching = false
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val resolvedTitle = title ?: stringResource(R.string.comment_attachment_picker_title)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CorusColors.Background,
        sheetMaxWidth = Int.MAX_VALUE.dp,
    ) {
        CorusSystemBars()
        Column(modifier = Modifier.fillMaxSize()) {
            // Title bar — mirrors iOS NavigationStack title "Attach to comment".
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = CorusSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = resolvedTitle,
                    style = CorusFont.displayName,
                    color = CorusColors.Text,
                )
            }
            HorizontalDivider(color = CorusColors.Divider)

            Column(modifier = Modifier.padding(horizontal = CorusSpacing.lg)) {
                PickerSegmentedToggle(
                    selectedIndex = if (mode == PickerMode.SONG) 0 else 1,
                    onSelected = { idx ->
                        val newMode = if (idx == 0) PickerMode.SONG else PickerMode.FILM
                        if (newMode != mode) {
                            mode = newMode
                            tracks = emptyList()
                            movies = emptyList()
                            runSearch(searchQuery, newMode)
                        }
                    },
                    modifier = Modifier.padding(vertical = CorusSpacing.sm),
                )

                val keyboardController = LocalSoftwareKeyboardController.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CorusColors.CardBackground, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                        .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = CorusColors.Tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(CorusSpacing.sm))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            runSearch(it, mode)
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = CorusFont.body.copy(color = CorusColors.Text),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                        cursorBrush = SolidColor(CorusColors.Accent),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = if (mode == PickerMode.SONG) stringResource(R.string.song_film_picker_search_song) else stringResource(R.string.song_film_picker_search_film),
                                    style = CorusFont.body,
                                    color = CorusColors.Secondary,
                                )
                            }
                            innerTextField()
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.song_film_picker_cd_clear),
                            tint = CorusColors.Secondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    searchQuery = ""
                                    runSearch("", mode)
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.sm))
            }

            // Content area: trending (empty query) | skeletons (searching) | results.
            val showTrending = searchQuery.isBlank()
            when {
                showTrending && mode == PickerMode.SONG -> {
                    if (isLoadingTrending) {
                        SongRowSkeletons()
                    } else {
                        TrendingSongsSection(
                            songs = trendingSongs,
                            onClick = { song -> onSongSelected(song.track) },
                        )
                    }
                }
                showTrending && mode == PickerMode.FILM -> {
                    if (isLoadingTrending) {
                        FilmRowSkeletons()
                    } else {
                        TrendingFilmsSection(
                            movies = trendingMovies,
                            onClick = { movie -> onFilmSelected(movie.asCymbalMovie()) },
                        )
                    }
                }
                isSearching -> {
                    if (mode == PickerMode.SONG) SongRowSkeletons() else FilmRowSkeletons()
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (mode == PickerMode.SONG) {
                            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                                SongPickerRow(track = track, onClick = { onSongSelected(track) })
                                if (index < tracks.lastIndex) {
                                    HorizontalDivider(
                                        color = CorusColors.Divider,
                                        modifier = Modifier.padding(start = 72.dp),
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(movies, key = { _, m -> m.id }) { index, movie ->
                                FilmSearchResultRow(movie = movie, onClick = { onFilmSelected(movie) })
                                if (index < movies.lastIndex) {
                                    HorizontalDivider(
                                        color = CorusColors.Divider,
                                        modifier = Modifier.padding(start = 72.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerSegmentedToggle(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(stringResource(R.string.song_film_picker_songs), stringResource(R.string.song_film_picker_films))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CorusColors.CardBackground, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .padding(CorusSpacing.xxs),
    ) {
        options.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (index == selectedIndex) {
                            Modifier.background(CorusColors.SegmentedSelected, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelected(index) }
                    .padding(vertical = CorusSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = CorusFont.bodyMedium,
                    color = if (index == selectedIndex) CorusColors.Text else CorusColors.Secondary,
                )
            }
        }
    }
}

@Composable
private fun SongPickerRow(track: CymbalTrack, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.albumArtURL,
            contentDescription = track.name,
            modifier = Modifier
                .size(CorusSpacing.albumArtSearch)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = CorusFont.body,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Trending sections (mirrors iOS SongSearchView) ──

@Composable
private fun TrendingHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .padding(top = CorusSpacing.sm, bottom = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.TrendingUp,
            contentDescription = null,
            tint = CorusColors.Accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = CorusFont.sectionHeader,
            color = CorusColors.Secondary,
        )
    }
}

@Composable
private fun TrendingSongsSection(
    songs: List<TrendingSong>,
    onClick: (TrendingSong) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { TrendingHeader(stringResource(R.string.compose_trending_songs)) }
        itemsIndexed(songs, key = { _, s -> s.track.id }) { index, song ->
            TrendingSongRow(song = song, onClick = { onClick(song) })
            if (index < songs.lastIndex) {
                HorizontalDivider(
                    color = CorusColors.Divider,
                    modifier = Modifier.padding(start = 72.dp),
                )
            }
        }
    }
}

@Composable
private fun TrendingFilmsSection(
    movies: List<TrendingMovie>,
    onClick: (TrendingMovie) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { TrendingHeader(stringResource(R.string.compose_trending_films)) }
        itemsIndexed(movies, key = { _, m -> m.id }) { index, movie ->
            TrendingMovieRow(movie = movie, onClick = { onClick(movie) })
            if (index < movies.lastIndex) {
                HorizontalDivider(
                    color = CorusColors.Divider,
                    modifier = Modifier.padding(start = 72.dp),
                )
            }
        }
    }
}

@Composable
private fun TrendingSongRow(song: TrendingSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .defaultMinSize(minHeight = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${song.rank}",
            style = CorusFont.engagementCount,
            color = CorusColors.Secondary,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        AsyncImage(
            model = song.track.albumArtLargeURL ?: song.track.albumArtURL,
            contentDescription = song.track.name,
            modifier = Modifier
                .size(CorusSpacing.albumArtSearch)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.track.name,
                style = CorusFont.body,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.track.artistName,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${song.cymbalCount}",
            style = CorusFont.captionMedium,
            color = CorusColors.Secondary,
        )
    }
}

@Composable
private fun TrendingMovieRow(movie: TrendingMovie, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .defaultMinSize(minHeight = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${movie.rank}",
            style = CorusFont.engagementCount,
            color = CorusColors.Secondary,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        AsyncImage(
            model = movie.posterURL,
            contentDescription = movie.movieTitle,
            modifier = Modifier
                .width(40.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movie.movieTitle,
                style = CorusFont.body,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs)) {
                Text(
                    text = movie.directorName,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (movie.releaseYear.isNotEmpty()) {
                    Text(
                        text = "(${movie.releaseYear})",
                        style = CorusFont.caption,
                        color = CorusColors.Tertiary,
                    )
                }
            }
        }
        Text(
            text = "${movie.cymbalCount}",
            style = CorusFont.captionMedium,
            color = CorusColors.Secondary,
        )
    }
}

@Composable
private fun SongRowSkeletons() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(8) { index ->
            SkeletonSongRow()
            if (index < 7) {
                HorizontalDivider(
                    color = CorusColors.Divider,
                    modifier = Modifier.padding(start = 72.dp),
                )
            }
        }
    }
}

@Composable
private fun FilmRowSkeletons() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(8) { index ->
            SkeletonFilmRow()
            if (index < 7) {
                HorizontalDivider(
                    color = CorusColors.Divider,
                    modifier = Modifier.padding(start = 72.dp),
                )
            }
        }
    }
}

@HiltViewModel
class SongFilmPickerViewModel @Inject constructor(
    val musicSearchRepository: MusicSearchRepository,
    val tmdbRepository: TMDBRepository,
    private val exploreRepository: ExploreRepository,
) : ViewModel() {

    private val _trendingSongs = MutableStateFlow<List<TrendingSong>>(emptyList())
    val trendingSongs: StateFlow<List<TrendingSong>> = _trendingSongs.asStateFlow()

    private val _trendingMovies = MutableStateFlow<List<TrendingMovie>>(emptyList())
    val trendingMovies: StateFlow<List<TrendingMovie>> = _trendingMovies.asStateFlow()

    private val _isLoadingTrending = MutableStateFlow(true)
    val isLoadingTrending: StateFlow<Boolean> = _isLoadingTrending.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _trendingSongs.value = exploreRepository.fetchTrendingSongs()
            } catch (_: Exception) { }
            try {
                _trendingMovies.value = exploreRepository.fetchTrendingMovies()
            } catch (_: Exception) { }
            _isLoadingTrending.value = false
        }
    }
}
