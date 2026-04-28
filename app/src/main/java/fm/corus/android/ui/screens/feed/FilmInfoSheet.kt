package fm.corus.android.ui.screens.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.remote.TMDBMovieDetails
import fm.corus.android.ui.components.SkeletonFilmInfoSheet
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmInfoSheet(
    post: CymbalPost,
    onDismiss: () -> Unit,
    fetchMovieDetails: suspend (Int) -> TMDBMovieDetails?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var isLoading by remember { mutableStateOf(true) }
    var movieDetails by remember { mutableStateOf<TMDBMovieDetails?>(null) }

    LaunchedEffect(post.movieId) {
        val idString = post.movieId
        if (idString != null) {
            val id = idString.toIntOrNull()
            if (id != null) {
                movieDetails = fetchMovieDetails(id)
            }
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CorusColors.Background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "filmInfoContent",
        ) { loading ->
            if (loading) {
                SkeletonFilmInfoSheet()
            } else {
                FilmInfoLoadedContent(post = post, details = movieDetails)
            }
        }
    }
}

@Composable
private fun FilmInfoLoadedContent(
    post: CymbalPost,
    details: TMDBMovieDetails?,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
    ) {
        // ── Header: poster + title + year/runtime + genre chips ──
        Row(
            modifier = Modifier
                .padding(horizontal = CorusSpacing.lg)
                .padding(top = CorusSpacing.xl, bottom = CorusSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(post.displayImageLargeURL ?: post.displayImageURL)
                    .crossfade(true)
                    .build(),
                contentDescription = post.movieTitle,
                modifier = Modifier
                    .width(60.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
            ) {
                post.movieTitle?.let { title ->
                    Text(
                        text = title,
                        style = CorusFont.songTitleLarge,
                        color = CorusColors.Text,
                    )
                }

                // Year + runtime
                val yearRuntime = buildList {
                    post.releaseYear?.takeIf { it.isNotEmpty() }?.let { add(it) }
                    details?.runtimeText?.let { add(it) }
                }
                if (yearRuntime.isNotEmpty()) {
                    Text(
                        text = yearRuntime.joinToString(" \u00B7 "),
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                    )
                }

                // Genre chips
                val genres = details?.genreNames ?: emptyList()
                if (genres.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs)) {
                        genres.forEach { genre ->
                            Text(
                                text = genre,
                                style = CorusFont.captionMedium,
                                color = CorusColors.Text,
                                modifier = Modifier
                                    .background(
                                        CorusColors.Secondary.copy(alpha = 0.22f),
                                        RoundedCornerShape(50),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = CorusColors.Divider)

        // ── Details: country, language, status ──
        val detailRows = buildList {
            details?.countryName?.let { add("COUNTRY" to it) }
            details?.languageDisplayName?.let { add("LANGUAGE" to it) }
            details?.status?.takeIf { it.isNotBlank() }?.let { add("STATUS" to it) }
        }
        if (detailRows.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                detailRows.forEach { (label, value) ->
                    InfoRow(label = label, value = value)
                }
            }
            HorizontalDivider(color = CorusColors.Divider)
        }

        // ── Director + Screenplay ──
        val directorName = post.directorName?.takeIf { it.isNotEmpty() }
        val writers = details?.writers ?: emptyList()
        if (directorName != null || writers.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                directorName?.let { InfoRow(label = "DIRECTOR", value = it) }
                if (writers.isNotEmpty()) {
                    InfoRow(label = "SCREENPLAY", value = writers.joinToString(", "))
                }
            }
            HorizontalDivider(color = CorusColors.Divider)
        }

        // ── Synopsis ──
        val overview = post.movieOverview?.takeIf { it.isNotEmpty() }
        if (overview != null) {
            InfoBlock(label = "SYNOPSIS", content = overview)
            HorizontalDivider(color = CorusColors.Divider)
        }

        // ── Cast ──
        val castWithChars = details?.castWithCharacters ?: emptyList()
        val fallbackCast = post.movieCast ?: emptyList()
        if (castWithChars.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
            ) {
                Text(
                    text = "CAST",
                    style = CorusFont.sectionHeader,
                    color = CorusColors.Secondary,
                )
                castWithChars.forEach { entry ->
                    Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs)) {
                        Text(
                            text = entry.name,
                            style = CorusFont.bodyMedium,
                            color = CorusColors.Text,
                        )
                        Text(
                            text = "as ${entry.character}",
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                        )
                    }
                }
            }
            HorizontalDivider(color = CorusColors.Divider)
        } else if (fallbackCast.isNotEmpty()) {
            InfoBlock(label = "CAST", content = fallbackCast.joinToString(", "))
            HorizontalDivider(color = CorusColors.Divider)
        }

        // ── Link buttons ──
        val linkButtons = buildList {
            details?.imdbURL?.let { add(Triple("IMDb", "film", it)) }
            details?.homepageURL?.let { add(Triple("Official Site", "globe", it)) }
            val title = post.movieTitle
            if (!title.isNullOrEmpty()) {
                val encoded = URLEncoder.encode(title, "UTF-8")
                add(Triple("Where to Watch", "tv", "https://www.justwatch.com/us/search?q=$encoded"))
            }
        }
        if (linkButtons.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
            ) {
                linkButtons.forEach { (label, _, url) ->
                    Button(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CorusColors.Accent,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = CorusSpacing.sm, vertical = 6.dp),
                    ) {
                        Text(
                            text = label,
                            style = CorusFont.buttonSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Bottom padding for safe area
        Spacer(modifier = Modifier.height(CorusSpacing.lg))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        Text(
            text = label,
            style = CorusFont.sectionHeader,
            color = CorusColors.Secondary,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = CorusFont.body,
            color = CorusColors.Text,
        )
    }
}

@Composable
private fun InfoBlock(label: String, content: String) {
    Column(
        modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
    ) {
        Text(
            text = label,
            style = CorusFont.sectionHeader,
            color = CorusColors.Secondary,
        )
        Text(
            text = content,
            style = CorusFont.body,
            color = CorusColors.Text,
        )
    }
}
