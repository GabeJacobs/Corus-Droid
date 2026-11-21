package fm.corus.android.ui.screens.live

import com.google.firebase.functions.FirebaseFunctionsException
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fm.corus.android.localization.CorusStrings
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import fm.corus.android.R
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicService
import fm.corus.android.domain.AppleMusicPlayback
import fm.corus.android.domain.SpotifyPlaybackService
import fm.corus.android.service.DeepLinkDestination
import fm.corus.android.ui.components.ComposePlusButton
import fm.corus.android.ui.components.MiniPlayerProgressHeight
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.components.rememberReducedMotion
import fm.corus.android.ui.components.parityCopy
import fm.corus.android.data.model.FlairStyle
import fm.corus.android.ui.components.blockTouchPassthrough
import fm.corus.android.ui.player.PlayerExpansionState
import fm.corus.android.ui.player.rememberPlayerExpansionState
import fm.corus.android.ui.player.PlayerSheetValue
import fm.corus.android.ui.player.miniOpacity
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusContentTheme
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class LiveNavigation(
    val state: LiveNavigationState,
    val viewModel: LiveViewModel,
    val post: (Boolean) -> Unit,
    val postBook: (() -> Unit)? = null,
)

internal data class LiveNavigationState(
    val access: LiveAccess?,
    val roomCoversFeed: Boolean,
    val discovery: List<LiveRoom>,
    val discoveryListenerCounts: Map<String, Int>,
    val trendingRoom: LiveRoom?,
    val showGoLive: Boolean,
    val canOpenSetup: Boolean,
    val mixedSaves: List<LiveSaveItem>?,
    val mixedSavesLoading: Boolean,
    val mixedSavesHasMore: Boolean,
    val mixedSavesFailed: Boolean,
)

internal val LocalLiveNavigation = compositionLocalOf<LiveNavigation?> { null }

@Composable
internal fun rememberLiveRoomExpansion(state: LiveUiState): PlayerExpansionState = key(state.room?.id) {
    // The entrance already covers the feed. Replace it with a full room on the
    // first frame, rather than exposing the feed while a collapsed panel opens.
    // Only a different room resets this state; reopening a minimized room still animates.
    rememberPlayerExpansionState(initiallyExpanded = state.room != null && !state.minimized)
}

@Composable
internal fun LiveCreateButton(onDefault: () -> Unit, modifier: Modifier = Modifier) {
    val live = LocalLiveNavigation.current
    if (live == null) {
        ComposePlusButton(onClick = onDefault, modifier = modifier)
        return
    }
    var menu by remember { mutableStateOf(false) }
    var handingOffToLive by remember { mutableStateOf(false) }
    Box(modifier) {
        ComposePlusButton(onClick = {
            if (live.state.showGoLive && live.state.canOpenSetup) live.viewModel.preloadFirstSong()
            handingOffToLive = false
            menu = true
        })
        if (!handingOffToLive) DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(
                { LiveText(stringResource(CorusStrings.destination_song_label)) }, { menu = false; live.post(false) },
                leadingIcon = { Icon(Icons.Default.MusicNote, null) },
            )
            DropdownMenuItem(
                { LiveText(stringResource(CorusStrings.corus_live_film)) }, { menu = false; live.post(true) },
                leadingIcon = { Icon(Icons.Default.Movie, null) },
            )
            live.postBook?.let { postBook ->
                DropdownMenuItem(
                    { LiveText(stringResource(CorusStrings.native_909cb81127c5)) }, { menu = false; postBook() },
                    leadingIcon = { Icon(Icons.Default.Book, null) },
                )
            }
            if (live.state.showGoLive) DropdownMenuItem(
                { LiveText(stringResource(CorusStrings.corus_live_go_live)) },
                { handingOffToLive = true; menu = false; live.viewModel.openSetup() },
                enabled = live.state.canOpenSetup,
                leadingIcon = { Icon(Icons.Default.Sensors, null) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiveDiscovery(
    source: String,
    following: Set<String>? = null,
    inSearch: Boolean = false,
    isActive: Boolean = true,
) {
    val live = LocalLiveNavigation.current ?: return
    val candidates = if (source == "trending") listOfNotNull(live.state.trendingRoom)
    else live.state.discovery.filter { it.matchesFollowing(following) }
    val rooms = roomsWithDiscoveryCounts(candidates, live.state.discoveryListenerCounts)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val artworkUrls = if (lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !live.state.roomCoversFeed) {
        rooms.take(3).mapNotNull { it.current?.loadingArtworkUrl }.distinct()
    } else emptyList()
    val context = LocalContext.current
    val artworkSize = with(LocalDensity.current) { LiveLoadingArtworkSize.roundToPx() }
    LaunchedEffect(artworkUrls, artworkSize) {
        val loader = SingletonImageLoader.get(context)
        artworkUrls.forEach { url ->
            loader.enqueue(ImageRequest.Builder(context).data(url).size(artworkSize).build())
        }
    }
    val reduceMotion = rememberReducedMotion()
    val duration = if (inSearch || reduceMotion || !isActive) 0 else 250
    // Seed each feed activation with its current visibility. Only changes
    // after activation should expand/collapse; switching tabs is immediate.
    val visibility = remember(source, isActive, following) {
        MutableTransitionState(rooms.isNotEmpty())
    }
    visibility.targetState = rooms.isNotEmpty()
    // Keep the last contents mounted during exit; an empty LazyRow would
    // lose its natural height before AnimatedVisibility can collapse it.
    var lastRooms by remember(source) { mutableStateOf(rooms) }
    SideEffect { if (rooms.isNotEmpty()) lastRooms = rooms }
    val displayedRooms = rooms.ifEmpty { lastRooms }
    var hide by remember { mutableStateOf<LiveRoom?>(null) }
    AnimatedVisibility(
        visibleState = visibility,
        enter = expandVertically(tween(duration), expandFrom = Alignment.Top) + fadeIn(tween(duration)),
        exit = shrinkVertically(tween(duration), shrinkTowards = Alignment.Top) + fadeOut(tween(duration)),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (inSearch) {
                LiveText(stringResource(CorusStrings.corus_live_live_now), CorusFont.sectionHeader, CorusColors.Secondary, Modifier.padding(horizontal = 16.dp))
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = if (inSearch) 16.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayedRooms, key = { it.id }) { room ->
                    LiveDiscoveryPill(
                        room,
                        listenerCount = live.state.discoveryListenerCounts[room.id],
                        open = { live.viewModel.event("discovery_opened", source = source); live.viewModel.join(room.id) },
                        hide = { hide = room },
                    )
                }
            }
        }
    }
    hide?.let { room ->
        LiveSheet(stringResource(R.string.live_live_room), { hide = null }) {
            LiveRow(stringResource(R.string.live_hide_room), Icons.Default.VisibilityOff) {
                live.viewModel.event("discovery_hidden", source = "room_pill")
                live.viewModel.dismissDiscoveryRoom(room.id)
                hide = null
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveDiscoveryPill(room: LiveRoom, listenerCount: Int?, open: () -> Unit, hide: () -> Unit) {
    val djs = remember(room.djs) { room.djs.map(LivePerson::parse).filter { it.uid.isNotBlank() } }
    val customTitle = room.customTitle.trim()
    val hasNamedLineup = customTitle.isNotEmpty() && djs.isNotEmpty()
    val handles = (listOf(room.host) + djs).map { "@${it.username.removePrefix("@")}" }
    val liveLabel = stringResource(CorusStrings.corus_live_live)
    val fill = CorusColors.Accent.copy(alpha = if (LocalCorusDarkTheme.current) .16f else .07f)
    Row(
        Modifier.widthIn(max = 264.dp).heightIn(min = 48.dp).clip(CircleShape).background(fill)
            .combinedClickable(onClick = open, onLongClick = hide)
            .semantics { stateDescription = liveLabel }
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        LiveBadgedArtwork {
            LiveMiniAvatarStack(room.host, djs)
        }
        Row(
            Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (djs.isEmpty() || hasNamedLineup) {
                    LiveText(customTitle.ifEmpty { handles.first() }, CorusFont.username, lines = 1)
                }
                if (djs.isNotEmpty()) {
                    LiveDiscoveryDJNames(
                        handles,
                        if (hasNamedLineup) CorusFont.caption else CorusFont.username,
                        if (hasNamedLineup) CorusColors.Secondary else CorusColors.Text,
                    )
                }
                if (!hasNamedLineup) room.current?.let { track ->
                    val subtitle = listOf(track.song.name, track.song.artistName).filter { it.isNotBlank() }.joinToString(" · ")
                    LiveText(subtitle, CorusFont.caption, CorusColors.Secondary, lines = 1)
                }
            }
            LiveDiscoveryPeopleCount(listenerCount?.let { it + 1 + djs.size })
        }
    }
}

@Composable
private fun LiveDiscoveryPeopleCount(people: Int?) {
    people?.let {
        val count = java.text.NumberFormat.getIntegerInstance().format(it)
        val peopleLabel = stringResource(R.string.search_trending_people)
        val dark = LocalCorusDarkTheme.current
        val foreground = if (dark) Color(0xFFB0B0B6) else Color(0xFF74747C)
        Row(
            Modifier.background(CorusColors.Accent.copy(alpha = if (dark) .16f else .09f), CircleShape)
                .padding(horizontal = 5.dp, vertical = 2.dp)
                .clearAndSetSemantics { contentDescription = "$count $peopleLabel" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(Icons.Default.Person, null, Modifier.size(14.dp), tint = foreground)
            LiveText(count, CorusFont.caption.copy(fontWeight = FontWeight.Medium), foreground, lines = 1)
        }
    }
}

@Composable
private fun LiveDiscoveryDJNames(
    handles: List<String>,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
) {
    val full = handles.joinToString(" · ")
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.ui.layout.SubcomposeLayout(
        Modifier.clearAndSetSemantics { contentDescription = full },
    ) { constraints ->
        val short = if (handles.size > 2) "${handles.take(2).joinToString(" · ")} +${handles.size - 2}" else full
        fun fits(text: String) = measurer.measure(text, style = style, maxLines = 1).size.width <= constraints.maxWidth
        val label = when {
            fits(full) -> full
            fits(short) -> short
            else -> null
        }
        val child = subcompose(Unit) {
            if (label != null) LiveText(label, style, color, lines = 1)
            else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LiveText(handles.first(), style, color, Modifier.weight(1f, fill = false), lines = 1)
                LiveText("+${handles.size - 1}", style, color, lines = 1)
            }
        }.single().measure(constraints)
        layout(child.width, child.height) { child.placeRelative(0, 0) }
    }
}

@Composable
private fun LiveBadgedArtwork(content: @Composable () -> Unit) {
    Box(Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.BottomCenter) {
        content()
        LiveText(
            stringResource(CorusStrings.corus_live_live).uppercase(),
            CorusFont.custom(weight = 900, size = 8).copy(letterSpacing = 0.3.sp), Color.White,
            Modifier.offset(y = 4.dp).clip(CircleShape).background(Color(0xFF4774CA))
                .border(1.dp, CorusColors.Background, CircleShape).padding(horizontal = 4.dp, vertical = 1.dp),
            lines = 1,
        )
    }
}

@Composable
private fun LiveMiniAvatarStack(host: LivePerson, djs: List<LivePerson>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        (listOf(host) + djs).forEachIndexed { index, person ->
            Box(Modifier.zIndex(if (index == 0) 1f else 0f)) {
                LiveAvatar(person, 28.dp, ring = true, ringPadding = 2.dp, ringWidth = 1.5.dp)
            }
        }
    }
}

@Composable
internal fun LiveProfileAvatar(uid: String, showPhoto: () -> Unit, avatar: @Composable () -> Unit) {
    val live = LocalLiveNavigation.current
    val room = live?.state?.discovery?.firstOrNull { it.host.uid == uid || it.djs.any { dj -> dj.text("uid") == uid } }
    if (room == null || live == null) {
        Box(Modifier.clickable(onClick = showPhoto)) { avatar() }
        return
    }
    Box(
        Modifier.drawBehind {
            // iOS strokes an 8-point larger circle inside its bounds, without
            // changing the profile photo's size or the header's layout.
            val stroke = 2.5.dp.toPx()
            drawCircle(CorusColors.Accent, radius = size.minDimension / 2 + 4.dp.toPx() - stroke / 2,
                style = Stroke(stroke))
        }.clickable(role = Role.Button, onClickLabel = stringResource(R.string.live_join_room)) {
            live.viewModel.event("discovery_opened", source = "profile_avatar")
            live.viewModel.join(room.id)
        },
    ) {
        avatar()
        Box(Modifier.align(Alignment.BottomCenter).offset(y = 4.dp).width(58.dp), contentAlignment = Alignment.Center) {
            LiveText(
                stringResource(CorusStrings.corus_live_live).uppercase(),
                CorusFont.custom(weight = 900, size = 10).copy(letterSpacing = 0.3.sp), Color.White,
                Modifier.clearAndSetSemantics {}.clip(CircleShape).background(Color(0xFF4774CA))
                    .border(1.dp, CorusColors.Background, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                lines = 1,
            )
        }
    }
}

internal val LiveMiniPlayerHeight = 56.dp

@Composable
internal fun LiveMiniPlayer(
    state: LiveUiState,
    vm: LiveViewModel,
    modifier: Modifier = Modifier,
    room: LiveRoom? = state.miniPlayerRoom,
) {
    if (room == null) return
    val mounted = state.room?.id == room.id
    val isHost = room.host.uid == state.uid
    val localProfile by vm.profile.collectAsState()
    val hostProfile by produceState(localProfile?.takeIf { it.id == room.host.uid }, room.host.uid, state.uid) {
        if (!isHost) {
            value = try {
                vm.miniPlayerHostProfile(room.host.uid)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
    }
    val profile = localProfile?.takeIf { it.id == room.host.uid } ?: hostProfile?.takeIf { it.id == room.host.uid }
    val now by produceState(vm.serverTime(), room.id) {
        while (true) {
            value = vm.serverTime()
            delay(500)
        }
    }
    val track = room.miniPlayerTrack(now)
    val djs = remember(room.djs) { room.djs.map(LivePerson::parse).filter { it.uid.isNotBlank() } }
    val ready = state.audioReady || isHost
    val canLike = mounted && state.audioReady && room.canLikeFromMiniPlayer(state.uid, now)
    val transitioning = room.pendingSkip != null
    val hasNextSong = room.nextTrack != null
    val canMute = track != null || transitioning || hasNextSong
    val canAdvance = transitioning || hasNextSong
    val showsNext = mounted && (isHost || room.currentDJ?.uid == state.uid)
    val reducedMotion = rememberReducedMotion()
    val artwork = track?.song?.takeIf { room.pausedAt == null }?.liveDisplayArtwork

    Box(modifier.fillMaxWidth().height(LiveMiniPlayerHeight).blockTouchPassthrough()) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = vm::openMiniPlayer,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LiveBadgedArtwork {
                    if (djs.isEmpty()) {
                        var showArtwork by remember(room.id, artwork, reducedMotion) { mutableStateOf(false) }
                        LaunchedEffect(room.id, artwork, reducedMotion) {
                            if (artwork == null || reducedMotion) return@LaunchedEffect
                            while (true) {
                                delay(4_000)
                                showArtwork = !showArtwork
                            }
                        }
                        val artworkAlpha by animateFloatAsState(
                            if (showArtwork && artwork != null) 1f else 0f,
                            tween(if (reducedMotion) 0 else 250),
                            label = "Live artwork",
                        )
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            LiveAvatar(room.host, 30.dp, ring = true, ringPadding = 3.dp,
                                modifier = Modifier.alpha(1f - artworkAlpha))
                            if (artwork != null) LiveArtwork(artwork, 34.dp, Modifier.alpha(artworkAlpha))
                        }
                    } else LiveMiniAvatarStack(room.host, djs)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        val nameStyle = CorusFont.custom(weight = 800, size = 12)
                        UsernameWithFlair(
                            username = (profile?.username?.takeIf { it.isNotBlank() } ?: room.host.username).removePrefix("@"),
                            isVerified = profile?.isVerified == true,
                            isClubMember = profile?.isClubMember == true,
                            isBot = profile?.isBot == true,
                            flairStyle = profile?.flairStyle ?: FlairStyle.NONE,
                            showAtPrefix = true,
                            style = nameStyle,
                            flairIconSize = 10.dp,
                            flairAssetSize = 14.dp,
                        )
                        if (djs.isNotEmpty()) {
                            LiveText(djs.joinToString(separator = " · ", prefix = "· ") { "@${it.username.removePrefix("@")}" },
                                nameStyle, lines = 1)
                        }
                    }
                    LiveText(
                        when {
                            room.hostWaitStartsAt?.let { now >= it } == true -> parityCopy(stringResource(R.string.live_waiting_for_host))
                            room.status == "waiting" && room.pausedAt != null -> parityCopy(stringResource(R.string.live_talk_mode))
                            track == null -> stringResource(CorusStrings.corus_live_waiting_for_the_next_song)
                            !ready -> parityCopy(stringResource(R.string.live_connect_a_streaming_provider))
                            else -> "${track.song.name} · ${track.song.artistName}"
                        },
                        CorusFont.caption, CorusColors.Secondary, lines = 1,
                    )
                }
            }
            if (canLike) {
                val liked = state.membership.number("likedRevision") == room.revision
                LiveLikeButton(liked, glyphSize = 20.dp) { vm.heartFromMiniPlayer(room.id, room.revision) }
            }
            if (mounted && !isHost) {
                LiveIconButton(Icons.Default.Close, stringResource(CorusStrings.corus_live_leave_room), vm::leave,
                    tint = CorusColors.Text, touchSize = 44.dp, glyphSize = 20.dp)
            }
            if (mounted && isHost) {
                LiveIconButton(
                    if (state.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    if (state.muted) stringResource(CorusStrings.corus_live_unmute_audio)
                        else stringResource(CorusStrings.corus_live_mute_audio),
                    { if (!transitioning) vm.toggleMute() },
                    enabled = canMute,
                    tint = CorusColors.Text, touchSize = 44.dp, glyphSize = 20.dp,
                )
            }
            if (showsNext) {
                LiveIconButton(Icons.Default.SkipNext, stringResource(CorusStrings.corus_live_skip_song),
                    { vm.skipFromMiniPlayer(room.id, room.revision) },
                    enabled = canAdvance,
                    tint = CorusColors.Text, touchSize = 44.dp, glyphSize = 24.dp)
            }
        }
        if (track != null && ready && room.startedAt > 0L) {
            key(room.id, room.revision) {
                val fraction = (((room.pausedAt ?: now) - room.startedAt).toFloat() / track.duration).coerceIn(0f, 1f)
                val displayed by animateFloatAsState(
                    fraction, tween(if (reducedMotion) 0 else 500, easing = LinearEasing),
                    label = "Live playback progress",
                )
                Box(
                    // Straddle the surface edge like the regular player's scrubber,
                    // covering the pixel seam where the sheet's position is rounded.
                    Modifier.align(Alignment.TopCenter).offset(y = -MiniPlayerProgressHeight / 2)
                        .fillMaxWidth().height(MiniPlayerProgressHeight).background(CorusColors.Divider)
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
                ) {
                    Box(Modifier.fillMaxWidth(displayed.coerceIn(0f, 1f)).fillMaxHeight().background(CorusColors.Secondary))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveRoomSheet(
    room: LiveRoom,
    state: LiveUiState,
    vm: LiveViewModel,
    expansion: PlayerExpansionState,
    parkInsetPx: Float,
    showMini: Boolean,
    openProfile: (String) -> Unit,
    openSong: (CymbalTrack) -> Unit,
    openNowPlaying: (CymbalTrack) -> Unit,
    showingNowPlaying: Boolean,
) {
    val dragState = expansion.draggableState
    val scope = rememberCoroutineScope()
    BackHandler(enabled = !showingNowPlaying && dragState.targetValue == PlayerSheetValue.Expanded) {
        scope.launch { expansion.collapse() }
    }

    // Park under the tab bar and app sheets, like the regular miniplayer.
    // The room moves above app content as soon as it starts expanding.
    val roomLayer = if (expansion.expansionFraction() > 0f) 9f else 1f
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(roomLayer)) {
        val miniHeightPx = with(LocalDensity.current) { LiveMiniPlayerHeight.toPx() }
        val travel = (constraints.maxHeight.toFloat() - parkInsetPx - miniHeightPx).coerceAtLeast(1f)
        LaunchedEffect(travel, state.minimized) {
            expansion.updateTravel(travel)
            if (state.minimized) expansion.collapse() else expansion.expand()
        }
        LaunchedEffect(dragState, vm) {
            // Keep the room mounted through a drag and publish its mode once it reaches an anchor.
            snapshotFlow { dragState.settledValue }.drop(1).collect { value ->
                if (value == PlayerSheetValue.Collapsed) vm.minimize() else vm.expand()
            }
        }

        val fraction = expansion.expansionFraction(travel)
        val settled = dragState.settledValue
        val miniAlpha = miniOpacity(fraction, travel)
        val showFullRoom = fraction > 0f || settled == PlayerSheetValue.Expanded
        Box(
            Modifier.fillMaxSize().offset {
                val offset = dragState.offset
                val initialOffset = if (dragState.currentValue == PlayerSheetValue.Expanded) 0f else travel
                IntOffset(0, (if (offset.isNaN()) initialOffset else offset.coerceIn(0f, travel)).roundToInt())
            },
        ) {
            if (showMini && !showFullRoom) {
                Box(Modifier.fillMaxWidth().height(LiveMiniPlayerHeight).background(CorusColors.Background))
            }
            // The surface where the drag started must stay mounted until release at either anchor.
            if (showMini && (fraction < 1f || settled == PlayerSheetValue.Collapsed)) {
                LiveMiniPlayer(
                    state, vm,
                    Modifier.alpha(miniAlpha)
                        .zIndex(if (miniAlpha > 0f) 1f else 0f)
                        .anchoredDraggable(dragState, Orientation.Vertical, startDragImmediately = false),
                    room = room,
                )
            }
            if (showFullRoom) {
                CorusContentTheme(LocalCorusDarkTheme.current || state.usesDarkRoomAppearance) {
                    Box(Modifier.fillMaxSize().blockTouchPassthrough()) {
                        LiveRoomScreen(
                            room, state, vm, openProfile, openSong, openNowPlaying, showingNowPlaying,
                            headerAlpha = 1f - miniAlpha,
                            // Chat and its horizontal pager sit outside this drag target.
                            minimizeDrag = Modifier.anchoredDraggable(
                                state = dragState,
                                orientation = Orientation.Vertical,
                                enabled = !showingNowPlaying,
                                startDragImmediately = false,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoxScope.LivePresentation(
    state: LiveUiState,
    vm: LiveViewModel,
    openProfile: (String) -> Unit,
    openSong: (CymbalTrack) -> Unit,
    setlistNavController: NavHostController? = null,
    nowPlayingDetails: @Composable (CymbalTrack, () -> Unit) -> Unit,
    roomExpansion: PlayerExpansionState,
    parkInsetPx: Float,
    showMini: Boolean = true,
) {
    val profile by vm.profile.collectAsState()
    val context = LocalContext.current
    val visibleRoom = state.presentedRoom
    val expandedRoom = visibleRoom?.takeUnless { state.minimized }
    var nowPlayingSong by remember(state.uid) { mutableStateOf<CymbalTrack?>(null) }
    visibleRoom?.let { room ->
        key(room.id) {
            LiveRoomSheet(
                room, state, vm, roomExpansion, parkInsetPx, showMini,
                openProfile, openSong, { nowPlayingSong = it }, nowPlayingSong != null,
            )
        }
    }
    val setupTransition = remember { MutableTransitionState(false) }
    val setupReady = state.setup != null && !state.checkingSetupAccess && !state.hostProviderRequiredForSetup
    setupTransition.targetState = setupReady
    AnimatedVisibility(
        visibleState = setupTransition,
        modifier = Modifier.fillMaxSize().zIndex(10f),
        enter = slideInVertically { it },
        exit = if (state.hosting) ExitTransition.None else slideOutVertically { it },
    ) {
        val latest = state.setup.takeIf { setupReady }
        var exitingSetup by remember { mutableStateOf(latest) }
        SideEffect { if (latest != null) exitingSetup = latest }
        (latest ?: exitingSetup)?.let {
            LiveHostSetup(
                it, profile, vm, state.progress, state.checkingSetupAccess, roomOpened = state.hosting,
                visible = latest != null, privateRoomsAvailable = state.access?.privateRooms == true, modifier = Modifier.blockTouchPassthrough(),
            )
        }
    }
    if (expandedRoom == null && (state.setup == null || state.hostProviderRequiredForSetup) && state.progress != null) {
        Box(Modifier.fillMaxSize().zIndex(10f).background(CorusColors.Background).blockTouchPassthrough()) {
            LiveLoading(state.progress, state.joiningRoom?.current, state.joiningRoom?.title, state.joinReady)
        }
    }
    nowPlayingSong?.let { track ->
        nowPlayingDetails(track) { nowPlayingSong = null }
    }
    state.ended?.takeIf { nowPlayingSong == null }?.let { ended ->
        key(ended.id) { LiveEndedSheets(ended, vm, setlistNavController, vm::clearEnded) }
    }
    state.openSavedSetlist?.let { savedRoom ->
        key(savedRoom.id) {
            LiveEndedSheets(savedRoom, vm, setlistNavController, vm::closeSavedSetlist, startInSetlist = true)
        }
    }
    state.playbackExitEndsShow?.let { endsRoom ->
        LiveConfirmation(
            if (endsRoom) stringResource(CorusStrings.corus_live_end_live) else stringResource(CorusStrings.corus_live_leave_live_room),
            if (state.roomSwitchConfirmation) stringResource(CorusStrings.corus_live_switch_room_ends_show)
            else if (endsRoom) stringResource(CorusStrings.corus_live_this_will_end_the_room_for_everyone) else stringResource(CorusStrings.corus_live_leave_the_room_to_play_this_song),
            if (state.roomSwitchConfirmation) stringResource(CorusStrings.corus_live_end_and_join)
            else if (endsRoom) stringResource(R.string.live_end_and_play) else stringResource(CorusStrings.corus_live_leave_and_play),
            { vm.confirmPlaybackExit(false) }, { vm.confirmPlaybackExit(true) },
        )
    }
    LiveDJInvitation(state, vm)
    state.roomJoinPrompt?.takeIf {
        state.shownInvite == null && state.progress == null && state.setup == null &&
            state.ended == null && state.openSavedSetlist == null
    }?.let { prompt ->
        val discoveredRoom = state.discovery.firstOrNull { it.id == prompt.roomId }
        val room by produceState(discoveredRoom, prompt.roomId, discoveredRoom) {
            value = discoveredRoom ?: try {
                vm.roomJoinPreview(prompt.roomId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
        LiveRoomJoinSheet(
            prompt, room?.takeIf { it.id == prompt.roomId },
            close = { vm.dismissRoomJoinPrompt(prompt) },
            join = { vm.confirmRoomJoin(prompt) },
        )
    }
    if (state.linkUpcoming) {
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { LiveText(stringResource(R.string.live_this_room_hasnt_started_yet), CorusFont.songTitle) },
            confirmButton = { TextButton(onClick = vm::clearError) { LiveText(stringResource(CorusStrings.common_ok)) } },
        )
    }
    if (state.hostProviderPrompt) {
        LiveHostProviderSheet(
            connecting = state.connectingProvider,
            hint = state.hostProviderHint,
            close = vm::dismissHostProviderPrompt,
            connect = vm::connectHostProvider,
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { LiveText(if (state.spotifyInstallNeeded) stringResource(CorusStrings.corus_live_spotify_is_required) else stringResource(CorusStrings.corus_live_live), CorusFont.songTitle) },
            text = { LiveText(error) },
            confirmButton = {
                TextButton(onClick = {
                    if (state.spotifyInstallNeeded) {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.spotify.music"))) }
                        catch (_: ActivityNotFoundException) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.spotify.music")))
                        }
                    }
                    vm.clearError()
                }) { LiveText(if (state.spotifyInstallNeeded) stringResource(CorusStrings.corus_live_install_spotify) else stringResource(CorusStrings.common_ok)) }
            },
            dismissButton = if (state.spotifyInstallNeeded) {
                { TextButton(onClick = vm::clearError) { LiveText(stringResource(CorusStrings.common_cancel)) } }
            } else null,
        )
    }
}

@Composable
private fun LiveConfirmation(title: String, message: String, confirm: String, cancel: () -> Unit, accept: () -> Unit) {
    LiveSheet(title, cancel) {
        LiveText(message, color = CorusColors.Secondary, modifier = Modifier.align(Alignment.CenterHorizontally))
        LivePrimary(confirm, onClick = accept)
        TextButton(onClick = cancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { LiveText(stringResource(CorusStrings.common_cancel)) }
    }
}

@Composable
private fun LiveEndedSheets(
    room: LiveRoom,
    vm: LiveViewModel,
    navController: NavHostController?,
    close: () -> Unit,
    startInSetlist: Boolean = false,
) {
    var showSetlist by remember(room.id, startInSetlist) { mutableStateOf(startInSetlist) }
    var exporting by remember(room.id) { mutableStateOf(false) }
    var error by remember(room.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tracks = room.setlist
    val listState = rememberLazyListState()
    var returnEntry by remember { mutableStateOf<NavBackStackEntry?>(null) }
    val origin = returnEntry
    val currentEntry = key(navController) { navController?.currentBackStackEntryAsState()?.value }

    if (showSetlist) {
        LaunchedEffect(room.id) { vm.event("setlist_viewed", source = "setlist", roomId = room.id) }
    }
    if (origin != null) {
        val originLifecycle by origin.lifecycle.currentStateAsState()
        LaunchedEffect(originLifecycle) {
            if (originLifecycle == Lifecycle.State.DESTROYED) close()
        }
        // Keep the setlist state while the song (or a page opened from it) is above its origin.
        if (currentEntry != origin) return
    }

    if (!showSetlist) {
        LiveSheet(stringResource(CorusStrings.corus_live_live_room_ended), close) {
            if (tracks.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(94.dp), contentAlignment = Alignment.Center) {
                    tracks.take(3).forEachIndexed { index, track ->
                        val position = index - (minOf(3, tracks.size) - 1) / 2f
                        LiveArtwork(track.song.albumArtURL, 76.dp, Modifier.offset(x = (45 * position).dp).rotate(position * 9))
                    }
                }
            }
            LiveText(room.title, CorusFont.username, modifier = Modifier.align(Alignment.CenterHorizontally))
            LiveText(
                if (tracks.isEmpty()) stringResource(CorusStrings.corus_live_no_songs_played_yet) else pluralStringResource(R.plurals.live_songs_played, tracks.size, tracks.size),
                CorusFont.caption, CorusColors.Secondary, Modifier.align(Alignment.CenterHorizontally),
            )
            if (tracks.isNotEmpty()) LivePrimary(stringResource(CorusStrings.corus_live_view_setlist)) { showSetlist = true }
        }
    } else LiveSheet(stringResource(CorusStrings.corus_live_live_setlist), close, large = true) {
        LiveText(room.title, CorusFont.songTitleLarge)
        val djs = tracks.mapNotNull { it.playedBy }.distinctBy { it.uid }
        if (djs.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                djs.forEach { dj -> LiveAvatar(dj, 28.dp) }
            }
        }
        LiveText(java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(
            java.util.Date(room.endedAt.takeIf { it > 0 } ?: System.currentTimeMillis()),
        ), CorusFont.caption, CorusColors.Secondary)
        if (room.recent.size == 30) LiveText(stringResource(CorusStrings.corus_live_showing_the_most_recent_songs), CorusFont.caption, CorusColors.Secondary)
        LivePrimary(
            title = stringResource(if (exporting) R.string.live_exporting else CorusStrings.corus_live_export_playlist),
            enabled = !exporting,
            leadingIcon = if (exporting) null else Icons.AutoMirrored.Filled.QueueMusic,
        ) {
            scope.launch {
                exporting = true
                try {
                    val url = vm.exportSetlist(room.id)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val reason = (failure as? FirebaseFunctionsException)?.details.liveFields()?.text("reason")
                    error = context.getString(if (reason == "no_spotify_tracks") R.string.live_no_spotify_tracks
                        else R.string.live_couldnt_export_playlist)
                } finally {
                    exporting = false
                }
            }
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(tracks) { track ->
                LiveTrackRow(track, showLikes = true, saveCount = room.savesFor(track.playRevision), onTrack = {
                    val entry = navController?.currentBackStackEntry
                    if (entry != null && entry == currentEntry) {
                        returnEntry = entry
                        navController.navigate(track.song.withLiveDisplayArtwork().toSongDetailRoute())
                    }
                })
            }
        }
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { LiveText(stringResource(R.string.live_couldnt_export_playlist), CorusFont.songTitle) },
            text = { LiveText(message) },
            confirmButton = { TextButton(onClick = { error = null }) { LiveText(stringResource(CorusStrings.common_ok)) } },
        )
    }
}


@Composable
private fun LiveHostProviderSheet(
    connecting: MusicService?,
    hint: String?,
    close: () -> Unit,
    connect: (MusicService) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val spotifyInstalled = remember(context, lifecycleState) { SpotifyPlaybackService.isSpotifyAppInstalled(context) }
    val appleAvailable = AppleMusicPlayback.isAvailable()
    LiveSheet(
        title = stringResource(R.string.live_host_provider_title),
        close = close,
        showCloseButton = true,
        dismissEnabled = connecting == null,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiveText(
                hint ?: stringResource(if (appleAvailable) R.string.live_host_provider_body else R.string.live_host_provider_spotify_body),
                color = CorusColors.Secondary,
            )
            HostProviderRow(
                label = stringResource(if (spotifyInstalled) CorusStrings.music_service_spotify_sign_in else CorusStrings.corus_live_install_spotify),
                logo = R.drawable.spotify_logo,
                enabled = connecting == null,
                busy = connecting == MusicService.SPOTIFY,
            ) {
                if (spotifyInstalled) {
                    connect(MusicService.SPOTIFY)
                } else {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.spotify.music")))
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.spotify.music")))
                    }
                }
            }
            if (appleAvailable) {
                HostProviderRow(
                    label = stringResource(R.string.settings_connect_apple_music),
                    logo = R.drawable.apple_music_logo,
                    enabled = connecting == null,
                    busy = connecting == MusicService.APPLE_MUSIC,
                ) { connect(MusicService.APPLE_MUSIC) }
            }
        }
    }
}

@Composable
private fun HostProviderRow(label: String, logo: Int, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .alpha(if (enabled || busy) 1f else .55f)
            .clip(RoundedCornerShape(14.dp))
            .background(CorusColors.SheetControlBackground)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(logo), contentDescription = null, modifier = Modifier.size(26.dp))
        LiveText(
            if (busy) stringResource(R.string.live_connecting_spotify) else label,
            CorusFont.body.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), color = CorusColors.Accent, strokeWidth = 2.dp)
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = CorusColors.Secondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun LiveProviderButton(
    label: String,
    logo: Int,
    modifier: Modifier,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier.alpha(if (enabled || busy) 1f else .55f)
            .clip(CircleShape)
            .background(CorusColors.Background)
            .border(.5.dp, CorusColors.Divider, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 50.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(23.dp), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = CorusColors.Accent, strokeWidth = 2.dp)
            else Image(painterResource(logo), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        LiveText(label, CorusFont.bodyMedium)
    }
}

@Composable
internal fun LiveRoomJoinSheet(
    prompt: DeepLinkDestination.LiveRoom,
    room: LiveRoom?,
    close: () -> Unit,
    join: () -> Unit,
) {
    val host = room?.host ?: LivePerson("", prompt.hostName.orEmpty(), null)
    val hostName = room?.hostHandle ?: host.username
    LiveSheet(
        title = stringResource(R.string.live_join_room_prompt),
        close = close,
        showCloseButton = true,
        horizontalContentPadding = 16.dp,
    ) {
        val avatarModifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)
        if (room != null) {
            LiveAvatar(
                host.copy(username = host.username.removePrefix("@")),
                56.dp,
                modifier = avatarModifier,
            )
        } else {
            // A share link can arrive before discovery has the host metadata.
            // Match LiveAvatar's image-loading circle until the room is known.
            Box(avatarModifier.size(56.dp).clip(CircleShape)
                .background(CorusColors.Text.copy(alpha = 0.16f)))
        }
        if (hostName.isNotBlank() && room?.status != "ended") {
            LiveInviteMessage(stringResource(R.string.live_notification_preview_title, hostName), hostName)
        }
        LivePrimary(stringResource(R.string.live_join_room), onClick = join)
        TextButton(onClick = close, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            LiveText(stringResource(CorusStrings.common_cancel), CorusFont.body.copy(fontWeight = FontWeight.Bold),
                CorusColors.Secondary)
        }
    }
}

@Composable
private fun LiveInviteMessage(message: String, name: String) {
    val nameColor = CorusColors.Text
    Text(
        buildAnnotatedString {
            append(message)
            val start = message.indexOf(name)
            if (name.isNotBlank() && start >= 0) {
                addStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold), start, start + name.length)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        style = CorusFont.body,
        color = CorusColors.Secondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun LiveDJInvitation(state: LiveUiState, vm: LiveViewModel) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val push = state.pendingInvitePush
    val candidate = if (push == null && state.shownInvite == null) {
        state.invites.firstOrNull {
            (state.room == null || state.room.id == it.text("roomId")) &&
                it.number("expiresAt") > vm.serverTime() && it.inviteKey() !in state.seenInviteKeys
        }
    } else null
    val canPresent = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && state.access?.collaboration == true &&
        state.progress == null && !state.joiningInvite && state.roomJoinPrompt == null
    LaunchedEffect(vm, state.uid, canPresent, push, candidate) {
        if (canPresent && (push != null || candidate != null)) vm.presentPendingInvite(push, candidate)
    }

    val invite = state.shownInvite ?: return
    val joining = state.joiningInvite
    LaunchedEffect(invite.inviteKey(), joining) {
        if (!joining) {
            delay((invite.number("expiresAt") - vm.serverTime()).coerceAtLeast(0))
            vm.closeInvite()
        }
    }

    val fromCoDJ = invite.text("inviterId").isNotBlank()
    val senderId = invite.text("inviterId").ifBlank { invite.text("hostId") }
    val senderName = invite.text("inviterName").ifBlank { invite.text("hostName") }.removePrefix("@")
    val senderAvatar = invite.text(if (fromCoDJ) "inviterAvatar" else "hostAvatar")
    val avatar by produceState(senderAvatar, senderId, senderAvatar) {
        value = senderAvatar
        if (senderAvatar.isBlank() && senderId.isNotBlank()) {
            val profile = try {
                vm.miniPlayerHostProfile(senderId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            value = profile?.avatarThumbURL ?: profile?.avatarURL.orEmpty()
        }
    }
    val inAnotherRoom = state.room?.id?.let { it != invite.text("roomId") } == true
    LiveSheet(
        title = stringResource(R.string.live_dj_invitation_title),
        close = vm::closeInvite,
        dismissEnabled = !joining,
        horizontalContentPadding = 16.dp,
    ) {
        LiveAvatar(
            LivePerson(senderId, senderName, avatar.ifBlank { null }),
            56.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
        )
        val message = stringResource(
            if (fromCoDJ) R.string.live_dj_invitation_from_codj else R.string.live_dj_invitation_from_host,
            senderName.ifBlank { stringResource(R.string.live_dj_invitation_host_fallback) },
        )
        LiveInviteMessage(message, senderName)
        val help = state.inviteError ?: R.string.live_dj_invitation_other_room.takeIf { inAnotherRoom }
        help?.let {
            Text(
                stringResource(it),
                modifier = Modifier.fillMaxWidth(),
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                textAlign = TextAlign.Center,
            )
        }
        LivePrimary(
            stringResource(if (joining) R.string.live_dj_invitation_joining else R.string.live_dj_invitation_join),
            enabled = !joining && !inAnotherRoom,
        ) { vm.inviteDecision(invite, true) }
        TextButton(
            onClick = { vm.inviteDecision(invite, false) },
            enabled = !joining,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        ) {
            LiveText(
                stringResource(R.string.live_dj_invitation_decline),
                CorusFont.body.copy(fontWeight = FontWeight.Bold),
                LiveRed.copy(alpha = if (joining) .4f else 1f),
            )
        }
    }
}
