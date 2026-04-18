package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.repository.SpotifyRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
) {
    val viewModel: SongFilmPickerViewModel = hiltViewModel()
    val spotifyRepository = viewModel.spotifyRepository
    val tmdbRepository = viewModel.tmdbRepository

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
        searchJob = scope.launch {
            delay(300)
            isSearching = true
            try {
                if (currentMode == PickerMode.SONG) {
                    tracks = spotifyRepository.search(query)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CorusColors.Background,
        sheetMaxWidth = Int.MAX_VALUE.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = CorusSpacing.lg),
        ) {
            // Songs / Films segmented toggle
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

            // Search bar
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
                                text = if (mode == PickerMode.SONG) "Search for a song" else "Search for a film",
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
                        contentDescription = "Clear",
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

            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = CorusSpacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = CorusColors.Accent,
                        strokeWidth = 2.dp,
                    )
                }
            }

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

@Composable
private fun PickerSegmentedToggle(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf("Songs", "Films")
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
                            Modifier.background(Color.White, RoundedCornerShape(10.dp))
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

@HiltViewModel
class SongFilmPickerViewModel @Inject constructor(
    val spotifyRepository: SpotifyRepository,
    val tmdbRepository: TMDBRepository,
) : ViewModel()
