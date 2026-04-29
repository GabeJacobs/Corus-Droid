package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.valentinilk.shimmer.shimmer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import fm.corus.android.R
import fm.corus.android.data.model.KlipyGif
import fm.corus.android.data.repository.GifRepository
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifPickerSheet(
    gifRepository: GifRepository = hiltGifRepository(),
    onGifSelected: (KlipyGif) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var gifs by remember { mutableStateOf<List<KlipyGif>>(emptyList()) }
    var currentPage by remember { mutableStateOf(1) }
    var hasNext by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Load trending on first open
    LaunchedEffect(Unit) {
        try {
            val result = gifRepository.getTrendingGifs()
            gifs = result.gifs
            currentPage = result.currentPage
            hasNext = result.hasNext
        } catch (_: Exception) { }
        isLoading = false
    }

    // Debounced search
    fun doSearch(query: String) {
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(300)
            isLoading = true
            try {
                val result = if (query.isBlank()) {
                    gifRepository.getTrendingGifs()
                } else {
                    gifRepository.searchGifs(query)
                }
                gifs = result.gifs
                currentPage = result.currentPage
                hasNext = result.hasNext
            } catch (_: Exception) { }
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CorusColors.Background,
        sheetMaxWidth = Int.MAX_VALUE.dp,
    ) {
        CorusSystemBars()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .padding(horizontal = CorusSpacing.lg),
        ) {
            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    doSearch(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.gif_picker_search_placeholder), style = CorusFont.body, color = CorusColors.Tertiary) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.gif_picker_cd_search), tint = CorusColors.Secondary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.gif_picker_cd_clear),
                            modifier = Modifier.clickable {
                                searchQuery = ""
                                doSearch("")
                            },
                            tint = CorusColors.Secondary,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch(searchQuery) }),
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CorusColors.CardBackground,
                    unfocusedContainerColor = CorusColors.CardBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = CorusColors.Accent,
                ),
                textStyle = CorusFont.body.copy(color = CorusColors.Text),
            )

            Spacer(modifier = Modifier.height(CorusSpacing.sm))

            // GIF grid or skeleton
            if (isLoading && gifs.isEmpty()) {
                // Skeleton grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    items(8) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    items(gifs, key = { it.id }) { gif ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(gif.thumbnailURL)
                                .build(),
                            contentDescription = stringResource(R.string.gif_picker_cd_gif),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onGifSelected(gif) },
                            contentScale = ContentScale.Crop,
                        )
                    }

                    // Load more
                    if (hasNext) {
                        item {
                            val nextPage = currentPage + 1
                            LaunchedEffect(nextPage) {
                                try {
                                    val result = if (searchQuery.isBlank()) {
                                        gifRepository.getTrendingGifs(page = nextPage)
                                    } else {
                                        gifRepository.searchGifs(searchQuery, page = nextPage)
                                    }
                                    gifs = gifs + result.gifs
                                    currentPage = result.currentPage
                                    hasNext = result.hasNext
                                } catch (_: Exception) { }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(CorusSpacing.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = CorusColors.Accent,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }

            // Attribution
            Text(
                text = stringResource(R.string.gif_picker_powered_by),
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = CorusSpacing.sm),
            )
        }
    }
}

@Composable
private fun ShimmerBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shimmer()
            .background(CorusColors.Skeleton),
    )
}

@Composable
private fun hiltGifRepository(): GifRepository {
    return androidx.hilt.navigation.compose.hiltViewModel<GifPickerViewModel>().gifRepository
}

@dagger.hilt.android.lifecycle.HiltViewModel
class GifPickerViewModel @javax.inject.Inject constructor(
    val gifRepository: GifRepository,
) : androidx.lifecycle.ViewModel()
