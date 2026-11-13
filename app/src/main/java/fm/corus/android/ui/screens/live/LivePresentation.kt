package fm.corus.android.ui.screens.live

import androidx.compose.ui.res.stringResource
import fm.corus.android.localization.CorusStrings
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.domain.SpotifyPlaybackService
import fm.corus.android.ui.components.ComposePlusButton
import fm.corus.android.ui.components.blockTouchPassthrough
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
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rooms, key = { it.id }) { room ->
                Row(
                    Modifier.widthIn(max = 264.dp).heightIn(min = 47.dp).clip(CircleShape)
                        .background(CorusColors.Accent.copy(alpha = .12f))
                        .combinedClickable(
                            onClick = { live.viewModel.event("discovery_opened", source = source); live.viewModel.join(room.id) },
                            onLongClick = { hide = room },
                        ).padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    LiveAvatar(room.host, 28.dp, ring = true)
                    Column(Modifier.widthIn(max = 153.dp)) {
                        LiveText(room.title, CorusFont.username, lines = 1)
                        room.current?.let {
                            LiveText("${it.song.name} · ${it.song.artistName}", CorusFont.caption, CorusColors.Secondary, lines = 1)
                        }
                    }
                    LiveText(stringResource(CorusStrings.corus_live_live), CorusFont.sectionHeader, LiveRed)
                    live.state.discoveryCounts[room.id]?.let { LiveText(it.toString(), CorusFont.caption, CorusColors.Secondary) }
                }
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

@Composable
internal fun LiveMiniPlayer(state: LiveUiState, vm: LiveViewModel, modifier: Modifier = Modifier) {
    val room = state.room ?: return
    var hostLabel by remember(room.id) { mutableStateOf(false) }
    LaunchedEffect(room.id) {
        while (true) { delay(3_500); hostLabel = !hostLabel }
    }
    Column(modifier.fillMaxWidth().background(CorusColors.Background)) {
        Row(
            Modifier.fillMaxWidth().height(62.dp).clickable(onClick = vm::expand).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveAvatar(room.host, 36.dp, ring = true)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Sensors, null, tint = CorusColors.Accent, modifier = Modifier.size(15.dp))
                    Crossfade(if (hostLabel) "@${room.host.username.removePrefix("@")}" else room.title, label = "Live room label") {
                        LiveText(it, CorusFont.username, lines = 1)
                    }
                }
                val song = room.current
                LiveText(
                    when {
                        room.voiceMode == "talk" -> "Listening live"
                        !state.audioReady -> stringResource(CorusStrings.corus_live_connect_to_hear_the_music)
                        song == null -> stringResource(CorusStrings.corus_live_waiting_for_the_next_song)
                        else -> "${song.song.name} · ${song.song.artistName}"
                    },
                    CorusFont.caption, CorusColors.Secondary, lines = 1,
                )
            }
            LiveIconButton(if (state.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                if (state.muted) stringResource(CorusStrings.corus_live_unmute_audio) else stringResource(CorusStrings.corus_live_mute_audio), vm::toggleMute)
            LiveIconButton(
                if (state.membership.number("likedRevision") == room.revision) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                stringResource(CorusStrings.corus_live_like_song), vm::heart, tint = LiveRed, enabled = room.current != null,
            )
            if (state.hosting) {
                var end by remember { mutableStateOf(false) }
                LiveIconButton(Icons.Default.Close, stringResource(CorusStrings.corus_live_end_live), { end = true })
                if (end) LiveConfirmation(stringResource(CorusStrings.corus_live_end_live), stringResource(CorusStrings.corus_live_this_will_end_the_room_for_everyone), stringResource(CorusStrings.corus_live_end_live),
                    { end = false }, { end = false; vm.end() })
            } else LiveIconButton(Icons.Default.Close, stringResource(CorusStrings.corus_live_leave_room), vm::leave)
        }
        room.current?.let {
            LinearProgressIndicator(
                progress = { (state.positionMs.toFloat() / it.duration.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp), color = CorusColors.Accent,
                trackColor = CorusColors.Secondary.copy(alpha = .15f),
            )
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
    val setupReady = state.setup != null && !state.checkingSetupAccess && !state.hostSpotifyRequiredForSetup
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
    if ((state.setup == null || state.hostSpotifyRequiredForSetup) && state.progress != null) {
        Box(Modifier.fillMaxSize().zIndex(10f).background(CorusColors.Background)) { LiveLoading(state.progress) }
    }
    state.ended?.let { ended ->
        LiveEndedSheets(ended, vm, openSong, vm::clearEnded)
    }
    state.openSavedSetlist?.let { savedRoom ->
        LiveEndedSheets(savedRoom, vm, openSong, vm::closeSavedSetlist, startInSetlist = true)
    }
    state.openSavedTrack?.let { saved ->
        LiveSheet(saved.track.song.name, vm::closeSavedTrack) {
            LiveTrackRow(saved.track)
            saved.pickedBy?.let { LiveText("@${it.username}", CorusFont.caption, CorusColors.Secondary) }
            LiveRow(stringResource(CorusStrings.native_84b771e51b43), Icons.Default.MusicNote) {
                vm.closeSavedTrack()
                openSong(saved.track.song)
            }
            LiveRow(stringResource(CorusStrings.corus_live_view_setlist), Icons.Default.QueueMusic, saved.roomTitle) {
                vm.showSavedSetlist(saved)
            }
            LiveRow("Remove from saves", Icons.Default.BookmarkRemove) { vm.removeSavedTrack(saved) }
        }
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
    if (state.hostSpotifyPrompt) {
        val installed = SpotifyPlaybackService.isSpotifyAppInstalled(context)
        AlertDialog(
            onDismissRequest = vm::dismissHostSpotifyPrompt,
            title = { LiveText("Spotify Premium required", CorusFont.songTitle) },
            text = { LiveText(state.hostSpotifyHint ?: "To go live on Android, connect Spotify Premium. Corus will switch your music service to Spotify.") },
            confirmButton = {
                TextButton(onClick = {
                    if (installed) vm.connectSpotifyFromHostPrompt()
                    else try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.spotify.music"))) }
                    catch (_: ActivityNotFoundException) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.spotify.music")))
                    }
                }) { LiveText(if (installed) "Connect Spotify" else "Install Spotify") }
            },
            dismissButton = { TextButton(onClick = vm::dismissHostSpotifyPrompt) { LiveText(stringResource(CorusStrings.common_cancel)) } },
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
                LiveTrackRow(track, onTrack = { close(); openSong(track.song) })
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
