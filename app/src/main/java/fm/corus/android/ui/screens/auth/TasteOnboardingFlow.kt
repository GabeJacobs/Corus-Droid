package fm.corus.android.ui.screens.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.MAX_QUIZ_PICKS
import fm.corus.android.data.model.QuizPick
import fm.corus.android.data.model.pickArt
import fm.corus.android.data.model.pickSubtitle
import fm.corus.android.data.model.pickTitle
import fm.corus.android.data.model.postablePicks
import fm.corus.android.ui.components.PopularUsersInfiniteGrid
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.components.TrophyCelebrationView
import fm.corus.android.ui.components.VennCollisionAnimation
import fm.corus.android.ui.components.VennDiagramIcon
import fm.corus.android.ui.components.rememberReducedMotion
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.PushNotificationPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The taste-match onboarding flow (onboarding_taste_match_enabled ON).
 * Step order mirrors web (app/onboarding/page.tsx): chores first, people last —
 * player choice → contacts → taste quiz → venn interstitial → taste-matched
 * suggestions → head-start posts → trophy chain → finish. Steps swap
 * INSTANTLY (no sliding layout animations — the product owner's design rule);
 * motion lives inside the screens (venn collision, slot springs), not between
 * them.
 */
private enum class TasteStep { MUSIC_SERVICE, SYNC_CONTACTS, TASTE_INTRO, QUIZ, SUGGESTIONS, HEADSTART }

@Composable
internal fun TasteOnboardingFlow(
    onFinished: () -> Unit,
    viewModel: SocialSetupViewModel,
) {
    var step by remember { mutableStateOf(TasteStep.MUSIC_SERVICE) }
    // Once the user has been INSIDE the picker, back-nav returns there — the
    // intro pitch is a one-time screen, not a gate to re-clear. Mirrors web's
    // quizReached.
    var quizReached by remember { mutableStateOf(false) }

    // The push-permission prompt fires at the flow's REAL finish (the legacy
    // flow fired it on the music-service step, which is now step #2).
    val context = LocalContext.current
    val pushPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.analyticsService.logNotificationPermissionResult(granted)
        viewModel.markPushPermissionRequested()
        onFinished()
    }
    val finishFlow: () -> Unit = {
        viewModel.logFollowFriendsOnboardingCompleted()
        if (PushNotificationPermission.shouldRequestPushPermission(context)) {
            pushPermissionLauncher.launch(PushNotificationPermission.permission)
        } else {
            viewModel.markPushPermissionRequested()
            onFinished()
        }
    }

    when (step) {
        TasteStep.MUSIC_SERVICE -> MusicServiceScreen(
            viewModel = viewModel,
            // Contacts sync is OUT of the flag-on chain (product decision
            // 07-15) — straight into the taste intro. SYNC_CONTACTS stays in
            // the enum so re-adding it (e.g. as an inline suggestions card)
            // is a one-line transition change; flag-off keeps today's
            // contacts step untouched.
            onFinished = { step = TasteStep.TASTE_INTRO },
            ctaLabelRes = R.string.onboarding_cta_continue,
            promptPushOnFinish = false,
        )
        // Quiz skippers gave no taste signal — grab the contacts signal
        // instead (product decision 07-16). All outcomes advance to
        // suggestions.
        TasteStep.SYNC_CONTACTS -> SyncContactsScreen(
            viewModel = viewModel,
            onContinue = { step = TasteStep.SUGGESTIONS },
            titleRes = R.string.onboarding_sync_contacts_title,
        )
        TasteStep.TASTE_INTRO -> {
            // Warm the trending caches while the venn intro plays so the
            // quiz's zero-state browse is ready before the first tap.
            LaunchedEffect(Unit) { viewModel.loadQuizBrowseIfNeeded() }
            TasteIntroScreen(
            viewModel = viewModel,
            onTakeQuiz = {
                viewModel.analyticsService.logOnboardingTasteQuizStarted()
                quizReached = true
                step = TasteStep.QUIZ
            },
            onSkip = {
                viewModel.analyticsService.logOnboardingTasteSkipped("intro")
                viewModel.discardQuizPicks()
                step = TasteStep.SYNC_CONTACTS
            },
        )
        }
        TasteStep.QUIZ -> TasteQuizScreen(
            viewModel = viewModel,
            onFindMatches = {
                viewModel.logQuizCompleted()
                step = TasteStep.SUGGESTIONS
            },
            onSkip = {
                viewModel.analyticsService.logOnboardingTasteSkipped("quiz")
                viewModel.discardQuizPicks()
                step = TasteStep.SYNC_CONTACTS
            },
        )
        TasteStep.SUGGESTIONS -> TasteSuggestionsScreen(
            viewModel = viewModel,
            onBack = { step = if (quizReached) TasteStep.QUIZ else TasteStep.TASTE_INTRO },
            onContinue = {
                if (viewModel.headstartPostables.isNotEmpty()) {
                    step = TasteStep.HEADSTART
                } else {
                    finishFlow()
                }
            },
        )
        TasteStep.HEADSTART -> HeadstartScreen(
            viewModel = viewModel,
            onDone = finishFlow,
        )
    }

    // Trophy chain: each head-start post that earned first-to-share queues a
    // celebration; play one at a time, dismiss advances, entrance replays per
    // trophy (the key() remount), and the feed handoff waits for the last one.
    val trophyQueue by viewModel.trophyQueue.collectAsState()
    val trophyIndex by viewModel.trophyIndex.collectAsState()
    if (trophyIndex < trophyQueue.size) {
        key(trophyIndex) {
            TrophyCelebrationView(
                post = trophyQueue[trophyIndex],
                visible = true,
                onDismiss = {
                    if (viewModel.advanceTrophy()) finishFlow()
                },
            )
        }
    }
}

// ═══════════════════════════════════════════════
// TASTE INTRO — the venn brand moment
// ═══════════════════════════════════════════════

@Composable
private fun TasteIntroScreen(
    viewModel: SocialSetupViewModel,
    onTakeQuiz: () -> Unit,
    onSkip: () -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.analyticsService.logOnboardingTasteIntroShown()
        viewModel.loadVennAvatarsIfNeeded()
    }
    val avatars by viewModel.vennAvatars.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = CorusSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        Text(
            stringResource(R.string.onboarding_taste_intro_title),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
        )

        // Floating centered cluster, biased slightly above geometric center
        // (optical center) — the asymmetric weights are the web's pb-16 bias.
        Spacer(modifier = Modifier.weight(0.85f))

        VennCollisionAnimation(avatars = avatars, startDelayMs = 300L)

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            stringResource(R.string.onboarding_taste_intro_body),
            style = CorusFont.body,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            stringResource(R.string.onboarding_taste_intro_duration),
            style = CorusFont.caption,
            color = CorusColors.Tertiary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1.15f))

        Button(
            onClick = onTakeQuiz,
            modifier = Modifier
                .fillMaxWidth()
                .height(CorusSpacing.touchTarget),
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
        ) {
            Text(
                stringResource(R.string.onboarding_taste_intro_cta),
                style = CorusFont.button,
                color = Color.White,
            )
        }
        TextButton(onClick = onSkip) {
            Text(
                stringResource(R.string.onboarding_taste_do_it_later),
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xxxl))
    }
}

// ═══════════════════════════════════════════════
// VENN INTERSTITIAL — "Finding Your Taste Matches…"
// ═══════════════════════════════════════════════

@Composable
private fun VennSearchingScreen(
    picks: List<QuizPick>,
    avatars: List<String>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CorusSpacing.xxl)
            .padding(bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The SAME collision loop as the intro, but the covers colliding are
        // the user's actual picks. Community avatars still stream in — until
        // they do, pulsing skeleton circles hold their spots (the "still
        // searching" read).
        VennCollisionAnimation(
            art = picks.mapNotNull { it.pickArt() }.take(3),
            avatars = avatars,
            shimmerPlaceholders = true,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.xxl))
        Text(
            stringResource(R.string.onboarding_taste_searching_title),
            style = CorusFont.bodyMedium,
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.xs))
        Text(
            stringResource(R.string.onboarding_taste_searching_body),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

// ═══════════════════════════════════════════════
// QUIZ — universal picker (songs, films, artists)
// ═══════════════════════════════════════════════

private enum class QuizFilter { ALL, MUSIC, FILM }

@Composable
private fun TasteQuizScreen(
    viewModel: SocialSetupViewModel,
    onFindMatches: () -> Unit,
    onSkip: () -> Unit,
) {
    val query by viewModel.quizQuery.collectAsState()
    val results by viewModel.quizResults.collectAsState()
    val isSearching by viewModel.isQuizSearching.collectAsState()
    val picks by viewModel.quizPicks.collectAsState()
    val addingFilmId by viewModel.addingFilmId.collectAsState()
    var filter by remember { mutableStateOf(QuizFilter.ALL) }

    val searchFocus = remember { FocusRequester() }
    // Tracks raw field focus: the question cluster hides the moment the
    // keyboard comes up (not just once text arrives) so it never gets
    // squeezed into truncation.
    var searchFocused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusSearch: () -> Unit = {
        searchFocus.requestFocus()
        keyboard?.show()
    }

    val searching = query.trim().isNotEmpty()
    val atMax = picks.size >= MAX_QUIZ_PICKS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Text(
            stringResource(R.string.onboarding_taste_intro_title),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xxl),
        )

        Column(modifier = Modifier.weight(1f)) {
            // Subtitle + search + slots read as ONE centered cluster while
            // idle; focusing the search animates the question away and the
            // cluster up (product call: focus transitions glide on mobile;
            // typing-driven swaps stay instant).
            val questionVisible = !searching && !searchFocused
            val topSpacerWeight by animateFloatAsState(
                targetValue = if (questionVisible) 0.85f else 0.0001f,
                animationSpec = tween(250),
                label = "quiz-top-spacer",
            )
            Spacer(modifier = Modifier.weight(topSpacerWeight))
            AnimatedVisibility(
                visible = questionVisible,
                enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(250)),
            ) {
                Column {
                    Text(
                        stringResource(R.string.onboarding_taste_quiz_question),
                        style = CorusFont.screenTitle,
                        color = CorusColors.Text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.xxl),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    Text(
                        stringResource(R.string.onboarding_taste_quiz_instruction),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.xxl),
                    )
                }
            }

            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            OnboardingSearchBar(
                query = query,
                onQueryChange = { viewModel.quizSearch(it) },
                onSearch = { keyboard?.hide() },
                modifier = Modifier
                    .padding(horizontal = CorusSpacing.xxl)
                    .focusRequester(searchFocus)
                    .onFocusChanged { searchFocused = it.isFocused },
                placeholderRes = R.string.onboarding_taste_search_placeholder,
            )

            val browsing = searchFocused && !searching
            if (searching || browsing) {
                Spacer(modifier = Modifier.height(CorusSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm, Alignment.CenterHorizontally),
                ) {
                    QuizFilterChip(stringResource(R.string.onboarding_taste_chip_all), filter == QuizFilter.ALL) { filter = QuizFilter.ALL }
                    QuizFilterChip(stringResource(R.string.onboarding_taste_chip_music), filter == QuizFilter.MUSIC) { filter = QuizFilter.MUSIC }
                    QuizFilterChip(stringResource(R.string.onboarding_taste_chip_film), filter == QuizFilter.FILM) { filter = QuizFilter.FILM }
                }
                Spacer(modifier = Modifier.height(CorusSpacing.md))
            }
            if (searching) {
                QuizResultsList(
                    viewModel = viewModel,
                    results = results,
                    isSearching = isSearching,
                    filter = filter,
                    picks = picks,
                    atMax = atMax,
                    addingFilmId = addingFilmId,
                    modifier = Modifier.weight(1f),
                )
            } else if (browsing) {
                LaunchedEffect(Unit) { viewModel.loadQuizBrowseIfNeeded() }
                QuizBrowseList(
                    viewModel = viewModel,
                    filter = filter,
                    picks = picks,
                    atMax = atMax,
                    addingFilmId = addingFilmId,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.height(40.dp))
                QuizPicksTray(
                    picks = picks,
                    atMax = atMax,
                    onRemove = { viewModel.removeQuizPick(it) },
                    onSlotTap = focusSearch,
                )
                Spacer(modifier = Modifier.weight(1.15f))
            }
        }

        // Bottom CTA cluster — yields to the keyboard: while the search field
        // is focused the bottom third belongs to results/slots, and FIND MY
        // MATCHES can't be tapped mid-typing anyway. Fades/shrinks in step
        // with the question cluster above.
        AnimatedVisibility(
            visible = !searchFocused,
            enter = fadeIn(tween(200)) + expandVertically(tween(250)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(250)),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onFindMatches,
                enabled = picks.size >= 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CorusSpacing.touchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CorusColors.Accent,
                    disabledContainerColor = CorusColors.Accent.copy(alpha = 0.4f),
                ),
            ) {
                Text(
                    stringResource(R.string.onboarding_taste_find_matches_cta),
                    style = CorusFont.button,
                    color = Color.White,
                )
            }
            TextButton(onClick = onSkip) {
                Text(
                    stringResource(R.string.onboarding_taste_do_it_later),
                    style = CorusFont.caption,
                    color = CorusColors.Tertiary,
                )
            }
            Spacer(modifier = Modifier.height(CorusSpacing.lg))
        }
        }
    }
}

@Composable
private fun QuizFilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) CorusColors.Accent else CorusColors.CardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = 6.dp),
    ) {
        Text(
            label,
            style = CorusFont.captionMedium,
            color = if (active) Color.White else CorusColors.Secondary,
        )
    }
}

@Composable
private fun QuizResultsList(
    viewModel: SocialSetupViewModel,
    results: SocialSetupViewModel.QuizSearchResults,
    isSearching: Boolean,
    filter: QuizFilter,
    picks: List<QuizPick>,
    atMax: Boolean,
    addingFilmId: String?,
    modifier: Modifier = Modifier,
) {
    val pickIds = remember(picks) { picks.map { it.id }.toSet() }
    // Every add returns to the idle tray (keyboard down) so the pick visibly
    // lands in its slot — tapping the next slot re-opens search.
    val focusManager = LocalFocusManager.current
    val clearAfterAdd: () -> Unit = {
        viewModel.quizSearch("")
        focusManager.clearFocus()
    }

    if (isSearching) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        ) {
            repeat(4) { SkeletonQuizRow() }
        }
        return
    }

    // Chips only FILTER what's shown — all requests already ran.
    val showMusic = filter != QuizFilter.FILM
    val showFilm = filter != QuizFilter.MUSIC
    val artists = if (showMusic) results.artists else emptyList()
    val albums = if (showMusic) results.albums else emptyList()
    val songs = if (showMusic) results.songs.take(if (filter == QuizFilter.MUSIC) 12 else 5) else emptyList()
    val films = if (showFilm) results.films.take(if (filter == QuizFilter.FILM) 12 else 4) else emptyList()
    val directors = if (showFilm) results.directors else emptyList()

    val nothingVisible = artists.isEmpty() && albums.isEmpty() && songs.isEmpty() &&
        films.isEmpty() && directors.isEmpty()
    if (nothingVisible) {
        val messageRes = when {
            !results.isEmpty && filter == QuizFilter.MUSIC -> R.string.onboarding_taste_no_results_music
            !results.isEmpty && filter == QuizFilter.FILM -> R.string.onboarding_taste_no_results_film
            else -> R.string.onboarding_taste_no_results
        }
        Text(
            stringResource(messageRes),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            modifier = modifier.padding(horizontal = CorusSpacing.xxl),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = CorusSpacing.xxl),
    ) {
        if (artists.isNotEmpty()) {
            item(key = "header-artists") { QuizSectionLabel(stringResource(R.string.onboarding_taste_section_artists)) }
            items(artists.size, key = { "artist-${artists[it].id}" }) { i ->
                val artist = artists[i]
                QuizResultRow(
                    imageUrl = artist.imageUrl,
                    circleImage = true,
                    fallbackInitial = artist.name,
                    title = artist.name,
                    subtitle = stringResource(R.string.onboarding_taste_row_artist),
                    added = "artist:${artist.id}" in pickIds,
                    enabled = !atMax,
                    onAdd = {
                        viewModel.addQuizPick(QuizPick.Artist(artist.id, artist.name, artist.imageUrl))
                        clearAfterAdd()
                    },
                )
            }
        }
        if (albums.isNotEmpty()) {
            item(key = "header-albums") { QuizSectionLabel(stringResource(R.string.onboarding_taste_section_albums)) }
            items(albums.size, key = { "album-${albums[it].id}" }) { i ->
                val album = albums[i]
                QuizResultRow(
                    imageUrl = album.coverUrl,
                    circleImage = false,
                    fallbackInitial = album.title,
                    title = album.title,
                    subtitle = album.artistName + (album.year?.let { " · $it" } ?: ""),
                    added = "album:${album.id}" in pickIds,
                    enabled = !atMax,
                    onAdd = {
                        viewModel.addQuizPick(
                            QuizPick.Album(album.id, album.title, album.artistName, album.coverUrl),
                        )
                        clearAfterAdd()
                    },
                )
            }
        }
        if (songs.isNotEmpty()) {
            item(key = "header-songs") { QuizSectionLabel(stringResource(R.string.onboarding_taste_section_songs)) }
            items(songs.size, key = { "song-${songs[it].id}" }) { i ->
                val song = songs[i]
                QuizResultRow(
                    imageUrl = song.albumArtURL,
                    circleImage = false,
                    fallbackInitial = song.name,
                    title = song.name,
                    subtitle = song.artistName,
                    added = song.id in pickIds,
                    enabled = !atMax,
                    onAdd = {
                        viewModel.addQuizPick(QuizPick.Song(song))
                        clearAfterAdd()
                    },
                )
            }
        }
        if (films.isNotEmpty()) {
            item(key = "header-films") { QuizSectionLabel(stringResource(R.string.onboarding_taste_section_films)) }
            items(films.size, key = { "film-${films[it].id}" }) { i ->
                val film = films[i]
                QuizResultRow(
                    imageUrl = film.posterURL,
                    circleImage = false,
                    posterAspect = true,
                    fallbackInitial = film.title,
                    title = film.title,
                    subtitle = film.year,
                    added = film.id in pickIds,
                    enabled = !atMax && addingFilmId == null,
                    loading = addingFilmId == film.id,
                    // Films resolve director details async — dismiss to the
                    // idle tray AFTER the pick lands (every other row type
                    // calls clearAfterAdd synchronously on tap).
                    onAdd = { viewModel.addFilmPick(film) { clearAfterAdd() } },
                )
            }
        }
        if (directors.isNotEmpty()) {
            item(key = "header-directors") { QuizSectionLabel(stringResource(R.string.onboarding_taste_section_directors)) }
            items(directors.size, key = { "director-${directors[it].id}" }) { i ->
                val director = directors[i]
                QuizResultRow(
                    imageUrl = director.imageUrl,
                    circleImage = true,
                    fallbackInitial = director.name,
                    title = director.name,
                    subtitle = stringResource(R.string.onboarding_taste_row_director),
                    added = "director:${director.id}" in pickIds,
                    enabled = !atMax,
                    onAdd = {
                        viewModel.addQuizPick(
                            QuizPick.Director(director.id, director.name, director.imageUrl),
                        )
                        clearAfterAdd()
                    },
                )
            }
        }
    }
}

@Composable
private fun QuizSectionLabel(label: String) {
    Text(
        label.uppercase(),
        style = CorusFont.sectionHeader,
        color = CorusColors.Tertiary,
        modifier = Modifier.padding(
            start = CorusSpacing.xxl,
            end = CorusSpacing.xxl,
            top = CorusSpacing.md,
            bottom = CorusSpacing.xs,
        ),
    )
}

/** Zero-state browse (search focused, empty query): tappable all-time
 *  popular artists + films on Corus, filtered by the same chips. */
@Composable
private fun QuizBrowseList(
    viewModel: SocialSetupViewModel,
    filter: QuizFilter,
    picks: List<QuizPick>,
    atMax: Boolean,
    addingFilmId: String?,
    modifier: Modifier = Modifier,
) {
    val artists by viewModel.popularArtists.collectAsState()
    val films by viewModel.popularFilms.collectAsState()
    val loading by viewModel.quizBrowseLoading.collectAsState()
    val pickIds = picks.map { it.id }.toSet()
    // Adds return to the idle tray (keyboard down) so the pick visibly lands.
    val focusManager = LocalFocusManager.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = CorusSpacing.xxl),
    ) {
        if (loading && artists.isEmpty() && films.isEmpty()) {
            // Trending cache still loading (the intro prefetch usually beats
            // the first tap; this covers cold caches / slow networks).
            items(6, key = { "browse-skeleton-$it" }) {
                SkeletonUserRow()
            }
        }
        if (filter != QuizFilter.FILM && artists.isNotEmpty()) {
            item(key = "header-popular-artists") {
                QuizSectionLabel(stringResource(R.string.onboarding_taste_popular_artists))
            }
            items(artists.size, key = { "popular-artist-${artists[it].id}" }) { i ->
                val artist = artists[i]
                QuizResultRow(
                    imageUrl = artist.albumArtURL,
                    circleImage = true,
                    fallbackInitial = artist.artistName,
                    title = artist.artistName,
                    subtitle = stringResource(R.string.onboarding_taste_row_artist),
                    added = "artist:${artist.id}" in pickIds,
                    enabled = !atMax,
                    onAdd = {
                        // Name-only pick: trending artists carry no Spotify id.
                        viewModel.addQuizPick(QuizPick.Artist("", artist.artistName, artist.albumArtLargeURL ?: artist.albumArtURL))
                        focusManager.clearFocus()
                    },
                )
            }
        }
        if (filter != QuizFilter.MUSIC && films.isNotEmpty()) {
            item(key = "header-popular-films") {
                QuizSectionLabel(stringResource(R.string.onboarding_taste_popular_films))
            }
            items(films.size, key = { "popular-film-${films[it].movieId}" }) { i ->
                val movie = films[i]
                QuizResultRow(
                    imageUrl = movie.posterURL,
                    circleImage = false,
                    posterAspect = true,
                    fallbackInitial = movie.movieTitle,
                    title = movie.movieTitle,
                    subtitle = movie.releaseYear,
                    added = movie.movieId in pickIds,
                    enabled = !atMax,
                    loading = addingFilmId == movie.movieId,
                    onAdd = {
                        viewModel.addFilmPick(movie.asCymbalMovie())
                        focusManager.clearFocus()
                    },
                )
            }
        }
    }
}

@Composable
private fun QuizResultRow(
    imageUrl: String?,
    circleImage: Boolean,
    fallbackInitial: String,
    title: String,
    subtitle: String,
    added: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit,
    posterAspect: Boolean = false,
    loading: Boolean = false,
) {
    val shape = if (circleImage) CircleShape else RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !added && !loading, onClick = onAdd)
            .padding(horizontal = CorusSpacing.xxl, vertical = CorusSpacing.sm)
            .alpha(if (added || !enabled) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = if (posterAspect) 40.dp else 44.dp, height = if (posterAspect) 56.dp else 44.dp)
                .clip(shape)
                .background(CorusColors.Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    fallbackInitial.take(1).uppercase(),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Accent,
                )
            }
        }
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = CorusColors.Accent,
                strokeWidth = 2.dp,
            )
            added -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = CorusColors.Accent,
                modifier = Modifier.size(20.dp),
            )
            else -> Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = CorusColors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SkeletonQuizRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(CorusColors.Skeleton),
    )
}

// ═══════════════════════════════════════════════
// PICKS TRAY — 3 seed slots + optional extras
// ═══════════════════════════════════════════════

private val SLOT_SIZE = 96.dp
private val SLOT_SHAPE = RoundedCornerShape(16.dp)

private fun Modifier.dashedRoundedBorder(color: Color, cornerRadius: Dp, width: Dp = 2.dp): Modifier =
    drawBehind {
        drawRoundRect(
            color = color,
            style = Stroke(
                width = width.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
            ),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
        )
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizPicksTray(
    picks: List<QuizPick>,
    atMax: Boolean,
    onRemove: (String) -> Unit,
    onSlotTap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Past 6 picks the tray compacts: first 5 tiles + a "+x" overflow
            // tile, so a big pick set never buries the CTA. Hidden picks still
            // count (and post/match).
            val visiblePicks = if (picks.size > 6) picks.take(5) else picks
            visiblePicks.forEach { pick ->
                key(pick.id) {
                    FilledPickSlot(pick = pick, onRemove = { onRemove(pick.id) })
                }
            }
            if (picks.size > 6) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CorusColors.CardBackground)
                        .clickable(onClick = onSlotTap),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+${picks.size - 5}",
                        style = CorusFont.screenTitle,
                        color = CorusColors.Secondary,
                    )
                }
            }
            // Empty slots look tappable (they are the visual ask), so make them
            // BE tappable: a tap hands focus to the search — keyboard up.
            if (picks.size < 3) {
                repeat(3 - picks.size) { i ->
                    if (i == 0) {
                        ActiveEmptySlot(onTap = onSlotTap)
                    } else {
                        IdleEmptySlot(onTap = onSlotTap)
                    }
                }
            }
            // Past the 3 minimum, adding more is OPTIONAL — a small chip, not a
            // fourth slot-sized tile (which reads as "one more required").
            if (picks.size >= 3 && !atMax) {
                val dashColor = CorusColors.Tertiary.copy(alpha = 0.5f)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onSlotTap)
                        .drawBehind {
                            drawCircle(
                                color = dashColor,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                                ),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.onboarding_taste_cd_add_another),
                        tint = CorusColors.Tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Progress copy — re-keyed per count so the roll-in replays each tick
        // (mirrors web corus-tm-count-in / iOS numericText transition).
        if (picks.isNotEmpty()) {
            key(picks.size) {
                CountRollIn {
                    Text(
                        if (picks.size >= 3) {
                            stringResource(R.string.onboarding_taste_nice_taste)
                        } else {
                            pluralStringResource(
                                R.plurals.onboarding_taste_pick_more,
                                3 - picks.size,
                                3 - picks.size,
                            )
                        },
                        style = CorusFont.caption,
                        color = CorusColors.Tertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Filled seed slot: art springs in (scale 0.7→1 + fade — the web
 *  corus-tm-art-in / iOS spring(response 0.45, damping 0.7)). */
@Composable
private fun FilledPickSlot(pick: QuizPick, onRemove: () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val scale = remember { Animatable(if (reducedMotion) 1f else 0.7f) }
    val alphaAnim = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            launch {
                scale.animateTo(
                    1f,
                    spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                )
            }
            launch { alphaAnim.animateTo(1f, tween(220)) }
        }
    }
    Box {
        Box(
            modifier = Modifier
                .size(SLOT_SIZE)
                .scale(scale.value)
                .alpha(alphaAnim.value)
                .clip(SLOT_SHAPE)
                .background(if (pick.pickArt() != null) CorusColors.CardBackground else CorusColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            val art = pick.pickArt()
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = pick.pickTitle(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    pick.pickTitle().take(1).uppercase(),
                    style = CorusFont.appTitle,
                    color = Color.White,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(CorusColors.Text)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.onboarding_taste_cd_remove, pick.pickTitle()),
                tint = CorusColors.Background,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

/** The NEXT slot: dashed accent border + the venn pulsing inside (the visual
 *  ask). Pulse mirrors iOS easeInOut(1.1s).repeatForever: scale 0.9↔1.08 +
 *  opacity 0.5↔1.0 (web corus-tm-pulse). */
@Composable
private fun ActiveEmptySlot(onTap: () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val pulse = if (reducedMotion) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "slot-pulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "slot-pulse-t",
        )
    }
    val t = pulse?.value ?: 1f
    Box(
        modifier = Modifier
            .size(SLOT_SIZE)
            .clip(SLOT_SHAPE)
            .background(CorusColors.Accent.copy(alpha = 0.1f))
            .dashedRoundedBorder(CorusColors.Accent, 16.dp)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .scale(0.9f + 0.18f * t)
                .alpha(0.5f + 0.5f * t),
        ) {
            VennDiagramIcon(
                size = 40.dp,
                color = CorusColors.Accent,
                shadedIntersection = true,
            )
        }
    }
}

@Composable
private fun IdleEmptySlot(onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(SLOT_SIZE)
            .clip(SLOT_SHAPE)
            .dashedRoundedBorder(CorusColors.Tertiary.copy(alpha = 0.4f), 16.dp)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = stringResource(R.string.onboarding_taste_cd_slot_search),
            tint = CorusColors.Tertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Fade + 6dp rise for the progress copy (web corus-tm-count-in, 0.3s ease-out). */
@Composable
private fun CountRollIn(content: @Composable () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val t = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) t.animateTo(1f, tween(300))
    }
    Box(
        modifier = Modifier
            .alpha(t.value)
            .offset(y = (6 * (1f - t.value)).dp),
    ) {
        content()
    }
}

// ═══════════════════════════════════════════════
// SUGGESTIONS — "Curate Your Feed" with taste matches
// ═══════════════════════════════════════════════

private const val TASTE_GRID_MAX_VISIBLE = 8 // 4 rows of the 2-col grid

@Composable
private fun TasteSuggestionsScreen(
    viewModel: SocialSetupViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val picks by viewModel.quizPicks.collectAsState()
    val matches by viewModel.tasteMatches.collectAsState()
    val vennAvatars by viewModel.vennAvatars.collectAsState()
    val contactMatches by viewModel.contactMatches.collectAsState()
    val contactsSynced by viewModel.contactsSynced.collectAsState()
    val followedIds by viewModel.followedIds.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val previewSheetUser by viewModel.previewSheetUser.collectAsState()
    val previewSheetPosts by viewModel.previewSheetPosts.collectAsState()
    val previewSheetIsLoading by viewModel.previewSheetIsLoading.collectAsState()
    val previewSheetIsLoadingMore by viewModel.previewSheetIsLoadingMore.collectAsState()
    val previewSheetHasMore by viewModel.previewSheetHasMore.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.loadVennAvatarsIfNeeded()
        viewModel.loadTasteMatchesIfNeeded()
    }

    // Keep the venn "searching" moment on screen long enough to read even when
    // the matcher returns fast — the animation IS the reveal's drumroll.
    var dwellDone by remember { mutableStateOf(picks.isEmpty()) }
    LaunchedEffect(Unit) {
        if (picks.isNotEmpty()) {
            // Long enough for the collision choreography's payoff — circles
            // collide ~2.4s, lens glow ~3.5s — so the reveal lands on the beat.
            delay(3600)
            dwellDone = true
        }
    }
    if (picks.isNotEmpty() && (!dwellDone || matches == null)) {
        VennSearchingScreen(picks = picks, avatars = vennAvatars)
        return
    }

    var showAllMatches by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(start = CorusSpacing.sm),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.onboarding_taste_cd_back),
                tint = CorusColors.Secondary,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.social_setup_curate_title),
                style = CorusFont.appTitle,
                color = CorusColors.Text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(CorusSpacing.sm))
            Text(
                stringResource(R.string.onboarding_suggestions_subtitle),
                style = CorusFont.body,
                color = CorusColors.Secondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xl))

        OnboardingSearchBar(
            query = searchQuery,
            onQueryChange = { viewModel.searchUsers(it) },
            onSearch = { keyboardController?.hide() },
            modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        if (searchQuery.length >= 2) {
            // Friend search swaps the sections for people results — someone who
            // joined because a friend told them to can find that friend here.
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = CorusSpacing.lg),
            ) {
                if (isSearching) {
                    items(4) { SkeletonUserRow() }
                } else if (searchResults.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(CorusSpacing.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                stringResource(R.string.search_no_users_found),
                                style = CorusFont.bodyMedium,
                                color = CorusColors.Secondary,
                            )
                        }
                    }
                } else {
                    items(searchResults.size, key = { searchResults[it].id }) { i ->
                        val user = searchResults[i]
                        OnboardingUserRow(
                            user = user,
                            isFollowed = followedIds.contains(user.id),
                            onFollow = { viewModel.toggleFollow(user.id) },
                            onTap = { viewModel.openUserPreview(user) },
                        )
                    }
                }
            }
        } else {
            val hasFriendsSection = contactsSynced && contactMatches.isNotEmpty()
            val hasTasteSection = picks.isNotEmpty()
            PopularUsersInfiniteGrid(
                excludeIds = emptySet(),
                followedIds = followedIds,
                onUserTap = { user -> viewModel.openUserPreview(user) },
                onFollowTap = { user -> viewModel.toggleFollow(user.id) },
                modifier = Modifier.weight(1f),
                headerVerticalPadding = 0.dp,
                topContent = if (!hasFriendsSection && !hasTasteSection) null else {
                    {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (hasFriendsSection) {
                                OnboardingSectionHeader(
                                    title = stringResource(R.string.social_setup_section_friends),
                                )
                                contactMatches.take(5).forEach { user ->
                                    OnboardingUserRow(
                                        user = user,
                                        subtitle = stringResource(R.string.search_subtitle_from_contacts),
                                        isFollowed = followedIds.contains(user.id),
                                        onFollow = { viewModel.toggleFollow(user.id) },
                                        onTap = { viewModel.openUserPreview(user) },
                                    )
                                }
                                Spacer(modifier = Modifier.height(CorusSpacing.lg))
                            }
                            if (hasTasteSection) {
                                TasteMatchesSection(
                                    viewModel = viewModel,
                                    followedIds = followedIds,
                                    showAll = showAllMatches,
                                    onShowAll = { showAllMatches = true },
                                )
                            }
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xxl)
                .padding(bottom = CorusSpacing.xxxl)
                .height(CorusSpacing.touchTarget),
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
        ) {
            Text(
                stringResource(R.string.onboarding_cta_continue),
                style = CorusFont.button,
                color = Color.White,
            )
        }
    }

    previewSheetUser?.let { sheetUser ->
        UserPreviewSheet(
            user = sheetUser,
            posts = previewSheetPosts,
            isLoading = previewSheetIsLoading,
            isLoadingMore = previewSheetIsLoadingMore,
            hasMore = previewSheetHasMore,
            isFollowed = followedIds.contains(sheetUser.id),
            nowPlaying = viewModel.nowPlayingManagerInstance,
            onFollow = { viewModel.toggleFollow(sheetUser.id) },
            onLoadMore = { viewModel.loadMorePreviewPosts() },
            onDismiss = { viewModel.closeUserPreview() },
        )
    }
}

/** Taste Matches section: quiet uppercase header with the venn mark + a 2-col
 *  card grid capped at 8 with a "See all N" expander; the taste-maker card
 *  when the quiz was taken but nobody matched. */
@Composable
private fun TasteMatchesSection(
    viewModel: SocialSetupViewModel,
    followedIds: Set<String>,
    showAll: Boolean,
    onShowAll: () -> Unit,
) {
    val result = viewModel.tasteMatches.collectAsState().value ?: return
    Column(modifier = Modifier.fillMaxWidth()) {
        // Same header format as "Popular on Corus" below — icon + quiet
        // uppercase label, not a bold section title.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VennDiagramIcon(
                size = 16.dp,
                color = CorusColors.Accent,
                shadedIntersection = true,
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            Text(
                stringResource(R.string.onboarding_taste_matches_header).uppercase(),
                style = CorusFont.sectionHeader,
                color = CorusColors.Secondary,
            )
        }

        if (result.users.isEmpty()) {
            TasteMakerCard(viewModel)
        } else {
            val visible = if (showAll) result.users else result.users.take(TASTE_GRID_MAX_VISIBLE)
            visible.chunked(2).forEach { rowMatches ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = CorusSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    rowMatches.forEach { match ->
                        TasteMatchCard(
                            match = match,
                            isFollowing = match.user.id in followedIds,
                            onUserTap = { viewModel.openUserPreview(match.user) },
                            onFollowTap = {
                                if (match.user.id !in followedIds) {
                                    viewModel.analyticsService.logOnboardingTasteMatchFollowed()
                                }
                                viewModel.toggleFollow(match.user.id)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowMatches.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            if (!showAll && result.users.size > TASTE_GRID_MAX_VISIBLE) {
                OutlinedButton(
                    onClick = onShowAll,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        stringResource(R.string.onboarding_taste_see_all_n, result.users.size),
                        style = CorusFont.captionMedium,
                        color = CorusColors.Text,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(CorusSpacing.lg))
    }
}

/** The quiz was taken but no member shares the picks yet — flip the empty
 *  state into a compliment. Mirrors web TasteMakerState (no icon). */
@Composable
private fun TasteMakerCard(viewModel: SocialSetupViewModel) {
    LaunchedEffect(Unit) {
        viewModel.analyticsService.logOnboardingTasteMakerShown()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(CorusColors.CardBackground)
            .padding(CorusSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.onboarding_taste_maker_title),
            style = CorusFont.bodyMedium,
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.xs))
        Text(
            stringResource(R.string.onboarding_taste_maker_body),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )
    }
}

// ═══════════════════════════════════════════════
// HEAD-START — post your picks
// ═══════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeadstartScreen(
    viewModel: SocialSetupViewModel,
    onDone: () -> Unit,
) {
    val picks by viewModel.quizPicks.collectAsState()
    val removedIds by viewModel.headstartRemovedIds.collectAsState()
    val isPosting by viewModel.isPostingPicks.collectAsState()
    val postables = remember(picks, removedIds) {
        postablePicks(picks).take(5).filter { it.id !in removedIds }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = CorusSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Floating cluster biased slightly above geometric center (optical
        // center), matching the intro step's layout.
        Spacer(modifier = Modifier.weight(0.85f))

        Text(
            stringResource(R.string.onboarding_headstart_title),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            stringResource(R.string.onboarding_headstart_subtitle),
            style = CorusFont.body,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            postables.forEach { pick ->
                key(pick.id) {
                    HeadstartTile(
                        pick = pick,
                        enabled = !isPosting,
                        onRemove = { viewModel.removeHeadstartPick(pick.id) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1.15f))

        // Hide the POST button entirely at 0 remaining — "Start fresh" is the
        // only path then.
        if (postables.isNotEmpty()) {
            Button(
                onClick = { viewModel.postHeadstartPicks(onDone = onDone) },
                enabled = !isPosting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CorusSpacing.touchTarget),
                colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        pluralStringResource(
                            R.plurals.onboarding_headstart_post_cta,
                            postables.size,
                            postables.size,
                        ),
                        style = CorusFont.button,
                        color = Color.White,
                    )
                }
            }
        }
        TextButton(onClick = onDone, enabled = !isPosting) {
            Text(
                stringResource(R.string.onboarding_headstart_start_fresh),
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xxxl))
    }
}

/** 120dp art tile with title/subtitle and a ✕ badge. Removing only trims what
 *  posts — the pick still counts toward the taste seed. */
@Composable
private fun HeadstartTile(
    pick: QuizPick,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier.width(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                    .background(CorusColors.CardBackground),
            ) {
                val art = pick.pickArt()
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = pick.pickTitle(),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Text)
                    .clickable(enabled = enabled, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(
                        R.string.onboarding_headstart_cd_dont_post,
                        pick.pickTitle(),
                    ),
                    tint = CorusColors.Background,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            pick.pickTitle(),
            style = CorusFont.captionMedium,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            pick.pickSubtitle(),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
