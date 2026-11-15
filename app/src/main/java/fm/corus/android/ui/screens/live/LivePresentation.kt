package fm.corus.android.ui.screens.live

import androidx.compose.ui.res.stringResource
import fm.corus.android.localization.CorusStrings
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import fm.corus.android.R
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicService
import fm.corus.android.domain.AppleMusicPlayback
import fm.corus.android.domain.SpotifyPlaybackService
import fm.corus.android.ui.components.ComposePlusButton
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.components.rememberReducedMotion
import fm.corus.android.ui.components.parityCopy
import fm.corus.android.data.model.FlairStyle
import fm.corus.android.ui.components.blockTouchPassthrough
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class LiveNavigation(
    val state: LiveNavigationState,
    val viewModel: LiveViewModel,
    val post: (Boolean) -> Unit,
    val postBook: (() -> Unit)? = null,
)

internal data class LiveNavigationState(
    val access: LiveAccess?,
    val discovery: List<LiveRoom>,
    val discoveryCounts: Map<String, Int>,
    val invites: List<LiveFields>,
    val showGoLive: Boolean,
    val canOpenSetup: Boolean,
    val mixedSaves: List<LiveSaveItem>?,
    val mixedSavesLoading: Boolean,
    val mixedSavesHasMore: Boolean,
    val mixedSavesFailed: Boolean,
)

internal val LocalLiveNavigation = compositionLocalOf<LiveNavigation?> { null }

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
internal fun LiveDiscovery(source: String, following: Set<String>? = null, inSearch: Boolean = false) {
    val live = LocalLiveNavigation.current ?: return
    val rooms = live.state.discovery.filter { following == null || it.host.uid in following }
    if (rooms.isEmpty() && live.state.invites.isEmpty()) return
    var hide by remember { mutableStateOf<LiveRoom?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (inSearch && rooms.isNotEmpty()) {
            LiveText(stringResource(CorusStrings.corus_live_live_now), CorusFont.sectionHeader, CorusColors.Secondary, Modifier.padding(horizontal = 16.dp))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = if (inSearch) 16.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rooms, key = { it.id }) { room ->
                val count = if (inSearch) 0 else live.state.discoveryCounts[room.id] ?: 0
                LiveDiscoveryPill(
                    room, count,
                    open = { live.viewModel.event("discovery_opened", source = source); live.viewModel.join(room.id) },
                    hide = { hide = room },
                )
            }
        }
        live.state.invites.forEach { invite ->
            val host = invite.text("hostName")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
                    .background(CorusColors.CardBackground).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { live.viewModel.presentInvite(invite) }, modifier = Modifier.weight(1f)) {
                    LiveText(if (host.isBlank()) "DJ invite" else "@$host invited you to DJ", CorusFont.username)
                }
                LiveIconButton(Icons.Default.Close, "Decline DJ invite", { live.viewModel.inviteDecision(invite, false) })
            }
        }
    }
    hide?.let { room ->
        LiveSheet("Live room", { hide = null }) {
            LiveRow("Hide room", Icons.Default.VisibilityOff) {
                live.viewModel.event("discovery_hidden", source = "room_pill")
                live.viewModel.dismissDiscoveryRoom(room.id)
                hide = null
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveDiscoveryPill(room: LiveRoom, count: Int, open: () -> Unit, hide: () -> Unit) {
    val djs = remember(room.djs) { room.djs.map(LivePerson::parse).filter { it.uid.isNotBlank() } }
    val customTitle = room.customTitle.trim()
    val hasNamedLineup = customTitle.isNotEmpty() && djs.isNotEmpty()
    val handles = (listOf(room.host) + djs).map { "@${it.username.removePrefix("@")}" }
    val number = remember(count) { java.text.NumberFormat.getIntegerInstance().format(count) }
    val liveLabel = stringResource(CorusStrings.corus_live_live)
    val stateLabel = if (count > 0) parityCopy("Live, listeners: $number") else liveLabel
    val fill = CorusColors.Accent.copy(alpha = if (LocalCorusDarkTheme.current) .16f else .07f)
    Row(
        Modifier.widthIn(max = 264.dp).heightIn(min = 48.dp).clip(CircleShape).background(fill)
            .combinedClickable(onClick = open, onLongClick = hide)
            .semantics { stateDescription = stateLabel }
            .padding(start = 8.dp, end = if (count > 0) 12.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        LiveBadgedArtwork {
            LiveMiniAvatarStack(room.host, djs)
        }
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (djs.isEmpty() || hasNamedLineup) {
                LiveText(customTitle.ifEmpty { handles.first() }, CorusFont.username, lines = 1)
            }
            if (djs.isNotEmpty()) {
                val style = if (hasNamedLineup) CorusFont.caption else CorusFont.username
                val color = if (hasNamedLineup) CorusColors.Secondary else CorusColors.Text
                LiveDiscoveryDJNames(handles, style, color)
            }
            if (!hasNamedLineup) room.current?.let { track ->
                val subtitle = listOf(track.song.name, track.song.artistName).filter { it.isNotBlank() }.joinToString(" · ")
                LiveText(subtitle, CorusFont.caption, CorusColors.Secondary, lines = 1)
            }
        }
        if (count > 0) {
            Row(
                Modifier.clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.People, null, tint = CorusColors.Secondary, modifier = Modifier.size(14.dp))
                LiveText(number, CorusFont.caption, CorusColors.Secondary)
            }
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
internal fun LiveProfileAvatar(uid: String, avatar: @Composable () -> Unit) {
    val live = LocalLiveNavigation.current
    val room = live?.state?.discovery?.firstOrNull { it.host.uid == uid || it.djs.any { dj -> dj.text("uid") == uid } }
    if (room == null || live == null) {
        avatar()
        return
    }
    Box {
        avatar()
        Box(Modifier.matchParentSize().clip(CircleShape).clickable {
            live.viewModel.event("discovery_opened", source = "profile_avatar")
            live.viewModel.join(room.id)
        })
        LiveText(stringResource(CorusStrings.corus_live_live), CorusFont.sectionHeader, LiveRed,
            Modifier.align(Alignment.BottomCenter).clip(CircleShape).background(CorusColors.Background).padding(horizontal = 5.dp))
    }
}

internal val LiveMiniPlayerHeight = 56.dp

@Composable
internal fun LiveMiniPlayer(state: LiveUiState, vm: LiveViewModel, modifier: Modifier = Modifier) {
    val room = state.miniPlayerRoom ?: return
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
    val canSkip = mounted && track != null && state.canSkipTrack(room)
    val reducedMotion = rememberReducedMotion()
    val artwork = track?.song?.takeIf { room.pausedAt == null }?.liveDisplayArtwork

    Box(modifier.fillMaxWidth().height(LiveMiniPlayerHeight).blockTouchPassthrough()) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clickable(onClick = vm::openMiniPlayer),
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
                            room.hostWaitStartsAt?.let { now >= it } == true -> parityCopy("Waiting for host")
                            room.status == "waiting" && room.pausedAt != null -> parityCopy("Talk mode")
                            track == null -> stringResource(CorusStrings.corus_live_waiting_for_the_next_song)
                            !ready -> parityCopy("Connect a streaming provider")
                            else -> "${track.song.name} · ${track.song.artistName}"
                        },
                        CorusFont.caption, CorusColors.Secondary, lines = 1,
                    )
                }
            }
            if (canLike) {
                val liked = state.membership.number("likedRevision") == room.revision
                LiveIconButton(
                    if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    if (liked) parityCopy("Song liked") else stringResource(CorusStrings.corus_live_like_song),
                    { vm.heartFromMiniPlayer(room.id, room.revision) },
                    tint = if (liked) CorusColors.Like else CorusColors.Text,
                    touchSize = 44.dp, glyphSize = 20.dp,
                )
            }
            if (mounted && !isHost) {
                LiveIconButton(Icons.Default.Close, stringResource(CorusStrings.corus_live_leave_room), vm::leave,
                    tint = CorusColors.Text, touchSize = 44.dp, glyphSize = 20.dp)
            }
            if (canSkip) {
                LiveIconButton(Icons.Default.SkipNext, stringResource(CorusStrings.corus_live_skip_song),
                    { vm.skipFromMiniPlayer(room.id, room.revision) },
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
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.5.dp).background(CorusColors.Divider)
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
                ) {
                    Box(Modifier.fillMaxWidth(displayed.coerceIn(0f, 1f)).fillMaxHeight().background(CorusColors.Secondary))
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.LivePresentation(
    state: LiveUiState,
    vm: LiveViewModel,
    openProfile: (String) -> Unit,
    openSong: (CymbalTrack) -> Unit,
) {
    val profile by vm.profile.collectAsState()
    val context = LocalContext.current
    val room = state.room
    if (room != null && !state.minimized) {
        BackHandler(onBack = vm::minimize)
        Box(Modifier.fillMaxSize().zIndex(9f).background(CorusColors.Background).blockTouchPassthrough()) {
            LiveRoomScreen(room, state, vm, openProfile, openSong)
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
                visible = latest != null, modifier = Modifier.blockTouchPassthrough(),
            )
        }
    }
    if ((state.setup == null || state.hostProviderRequiredForSetup) && state.progress != null) {
        Box(Modifier.fillMaxSize().zIndex(10f).background(CorusColors.Background)) { LiveLoading(state.progress) }
    }
    state.ended?.let { ended ->
        LiveEndedSheets(ended, vm, openSong, vm::clearEnded)
    }
    state.openSavedSetlist?.let { savedRoom ->
        LiveEndedSheets(savedRoom, vm, openSong, vm::closeSavedSetlist, startInSetlist = true)
    }
    if (state.confirmPlaybackExit) {
        LiveConfirmation(
            if (state.hosting) stringResource(CorusStrings.corus_live_end_live) else stringResource(CorusStrings.corus_live_leave_live_room),
            if (state.hosting) stringResource(CorusStrings.corus_live_this_will_end_the_room_for_everyone) else stringResource(CorusStrings.corus_live_leave_the_room_to_play_this_song),
            if (state.hosting) "End and play" else stringResource(CorusStrings.corus_live_leave_and_play),
            { vm.confirmPlaybackExit(false) }, { vm.confirmPlaybackExit(true) },
        )
    }
    state.shownInvite?.let { invite ->
        LiveSheet("DJ invite", vm::closeInvite) {
            LiveText("@${invite.text("hostName")} invited you to DJ.")
            LivePrimary("Accept") { vm.inviteDecision(invite, true) }
            TextButton(onClick = { vm.inviteDecision(invite, false) }) { LiveText("Decline") }
        }
    }
    if (state.linkUpcoming) {
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { LiveText("This room hasn’t started yet", CorusFont.songTitle) },
            confirmButton = { TextButton(onClick = vm::clearError) { LiveText(stringResource(CorusStrings.common_ok)) } },
        )
    }
    if (state.hostProviderPrompt) {
        val installed = SpotifyPlaybackService.isSpotifyAppInstalled(context)
        val connecting = state.connectingProvider
        val appleAvailable = AppleMusicPlayback.isAvailable()
        AlertDialog(
            onDismissRequest = vm::dismissHostProviderPrompt,
            title = { LiveText(if (appleAvailable) "Connect a streaming provider" else "Spotify Premium required", CorusFont.songTitle) },
            text = {
                LiveText(state.hostProviderHint ?: if (appleAvailable)
                    "Go Live requires Apple Music or Spotify Premium. Connecting a provider also switches your music service in Corus."
                else "To go live on Android, connect Spotify Premium. Corus will switch your music service to Spotify.")
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (appleAvailable) {
                        LiveProviderButton("Connect Apple Music", MusicService.APPLE_MUSIC, connecting) {
                            vm.connectHostProvider(MusicService.APPLE_MUSIC)
                        }
                    }
                    LiveProviderButton(if (installed) "Connect Spotify" else "Install Spotify", MusicService.SPOTIFY, connecting) {
                        if (installed) vm.connectHostProvider(MusicService.SPOTIFY)
                        else try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.spotify.music"))) }
                        catch (_: ActivityNotFoundException) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.spotify.music")))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissHostProviderPrompt, enabled = connecting == null) {
                    LiveText(stringResource(CorusStrings.common_cancel))
                }
            },
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
    room: LiveRoom, vm: LiveViewModel, openSong: (CymbalTrack) -> Unit, close: () -> Unit,
    startInSetlist: Boolean = false,
) {
    var showSetlist by remember(room.id, startInSetlist) { mutableStateOf(startInSetlist) }
    var exporting by remember(room.id) { mutableStateOf(false) }
    var error by remember(room.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tracks = room.setlist
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
                if (tracks.isEmpty()) stringResource(CorusStrings.corus_live_no_songs_played_yet) else "${tracks.size} songs played",
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
        LivePrimary(if (exporting) "Exporting…" else stringResource(CorusStrings.corus_live_export_playlist), !exporting) {
            scope.launch {
                exporting = true
                try {
                    val url = vm.exportSetlist(room.id)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.localizedMessage ?: "Couldn’t export playlist."
                } finally {
                    exporting = false
                }
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(tracks) { track ->
                LiveTrackRow(track, onTrack = { close(); openSong(track.song.withLiveDisplayArtwork()) })
            }
        }
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { LiveText("Couldn’t export playlist", CorusFont.songTitle) },
            text = { LiveText(message) },
            confirmButton = { TextButton(onClick = { error = null }) { LiveText(stringResource(CorusStrings.common_ok)) } },
        )
    }
}


@Composable
internal fun LiveProviderButton(
    label: String,
    provider: MusicService,
    connecting: MusicService?,
    onClick: () -> Unit,
) {
    TextButton(enabled = connecting == null, onClick = onClick) {
        if (connecting == provider) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = CorusColors.Accent,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            LiveText(stringResource(R.string.live_connecting_spotify))
        } else {
            LiveText(label)
        }
    }
}
