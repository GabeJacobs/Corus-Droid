package fm.corus.android.ui.screens.live

import androidx.compose.ui.res.stringResource
import fm.corus.android.localization.CorusStrings
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import fm.corus.android.R
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.DiscoIntensity
import fm.corus.android.data.model.RainIntensity
import fm.corus.android.data.model.SnowIntensity
import fm.corus.android.ui.components.DiscoEffectView
import fm.corus.android.ui.components.DiscoScrim
import fm.corus.android.ui.components.RainEffectView
import fm.corus.android.ui.components.SnowEffectView
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.Locale

private enum class RoomPanel { HISTORY, LISTENERS, OPTIONS, INVITE, LINEUP, PICK_DJS, EDIT_TITLE, QUEUE, REQUEST_SONG, GIF, REPORT, END }

private data class QueuePlaySelection(val track: LiveTrack, val index: Int, val version: Long, val revision: Long)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiveRoomScreen(
    room: LiveRoom,
    state: LiveUiState,
    vm: LiveViewModel,
    openProfile: (String) -> Unit,
    openSong: (CymbalTrack) -> Unit,
) {
    var panel by remember(room.id) { mutableStateOf<RoomPanel?>(null) }
    var chatFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboardVisible = WindowInsets.isImeVisible
    var keyboardWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardVisible) {
        if (keyboardWasVisible && !keyboardVisible && chatFocused) focus.clearFocus()
        keyboardWasVisible = keyboardVisible
    }
    val close = { panel = null }
    LaunchedEffect(room.allowSongRequests) {
        if (!room.allowSongRequests && panel == RoomPanel.REQUEST_SONG) close()
    }
    val canControl = state.hosting || state.dj != null
    LaunchedEffect(vm, state.uid, canControl) {
        if (canControl) vm.preloadSongSuggestions()
    }
    val openDJSpots = (LIVE_MAX_GUEST_DJS - room.djs.size -
        state.pendingDJs.count { it.number("expiresAt") > vm.serverTime() }).coerceAtLeast(0)
    val pager = rememberPagerState { 2 }
    val listenRoster = remember { mutableStateOf<List<LiveFields>?>(null) }
    var selectedMessage by remember { mutableStateOf<LiveMessage?>(null) }
    var selectedSong by remember { mutableStateOf<LiveTrack?>(null) }
    var text by remember { mutableStateOf("") }
    var gif by remember { mutableStateOf<String?>(null) }
    var welcomePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var welcomePlaying by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleMicrophone()
    }
    val micClick: () -> Unit = {
        if (state.micEnabled ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) vm.toggleMicrophone() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun replayWelcome() {
        val url = room.welcomeUrl ?: return
        welcomePlayer?.release()
        val player = MediaPlayer()
        welcomePlayer = player
        try {
            player.setDataSource(url)
            player.setOnPreparedListener { if (welcomePlayer === it) { welcomePlaying = true; vm.setWelcomePlaying(true); it.start() } }
            player.setOnCompletionListener { welcomePlaying = false; vm.setWelcomePlaying(false) }
            player.setOnErrorListener { _, _, _ -> welcomePlaying = false; vm.setWelcomePlaying(false); true }
            player.prepareAsync()
        } catch (_: Exception) {
            player.release()
            welcomePlayer = null
        }
    }
    DisposableEffect(room.id) { onDispose { welcomePlayer?.release(); vm.setWelcomePlaying(false) } }
    LaunchedEffect(room.id) {
        if (!state.hosting && vm.claimWelcome(room.id)) replayWelcome()
    }
    fun show(target: RoomPanel) {
        focus.clearFocus()
        panel = target
    }
    fun share() {
        scope.launch { shareLiveLink(context, vm.share(room.id, setup = false)) }
    }
    val visitProfile: (String) -> Unit = { uid ->
        if (uid.isNotBlank()) { vm.minimize(); openProfile(uid) }
    }

    val visibleEffect = state.effectSelection.visible(room.effect)
    Box(Modifier.fillMaxSize().background(CorusColors.Background)) {
        LiveRoomEffects(visibleEffect, room.hearts, !canControl || pager.currentPage == 1)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .widthIn(max = 650.dp).align(Alignment.TopCenter).padding(start = 16.dp, end = 16.dp, top = 4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).pointerInput(room.id) {
                    var drag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onVerticalDrag = { change, delta -> change.consume(); drag += delta },
                        onDragEnd = { if (drag > 95f) vm.minimize() },
                    )
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoomIconButton(Icons.Default.KeyboardArrowDown, stringResource(CorusStrings.corus_live_minimize_room), vm::minimize)
                Column(
                    Modifier.weight(1f).then(if (state.hosting) Modifier else Modifier.clickable { visitProfile(room.host.uid) }),
                ) {
                    if (state.hosting) {
                        LiveText(room.title, CorusFont.songTitle, modifier = Modifier.padding(start = 4.dp), lines = 2)
                    } else {
                        val username = room.host.username.removePrefix("@")
                        LiveText(username, CorusFont.usernameLarge, lines = 1)
                        if (room.title != username) LiveText(room.title, CorusFont.caption, CorusColors.Secondary, lines = 1)
                    }
                }
                RoomIconButton(Icons.Default.IosShare, stringResource(CorusStrings.corus_live_share_room), { if (canControl && room.collaborative) show(RoomPanel.INVITE) else share() })
                if (state.hosting) {
                    RoomIconButton(Icons.Outlined.Settings, stringResource(CorusStrings.corus_live_room_options), { show(RoomPanel.OPTIONS) })
                    RoomExitButton(stringResource(CorusStrings.corus_live_end_live)) { show(RoomPanel.END) }
                } else {
                    RoomIconButton(Icons.Outlined.Flag, stringResource(CorusStrings.corus_live_report_room), { show(RoomPanel.REPORT) })
                    if (room.welcomeUrl != null) RoomIconButton(Icons.Default.GraphicEq, stringResource(CorusStrings.corus_live_replay_welcome_message), ::replayWelcome)
                    if (state.dj != null) {
                        RoomExitButton(stringResource(CorusStrings.corus_live_step_down)) { vm.action("stopDJing") }
                    } else RoomIconButton(Icons.Default.Close, stringResource(CorusStrings.corus_live_leave_room), vm::leave)
                }
            }
            Spacer(Modifier.height(8.dp))
            val playerCard: @Composable () -> Unit = {
                AnimatedVisibility(!chatFocused) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoomLineup(room, state.hosting, state.voiceLevels, visitProfile) { show(RoomPanel.LINEUP) }
                        RoomPlayerCard(room, state, vm, welcomePlaying, { selectedSong = it }, { show(RoomPanel.HISTORY) })
                    }
                }
                RoomStatus(state.listenerCount, room.collaborative,
                    { show(RoomPanel.LISTENERS) }, { show(RoomPanel.LINEUP) })
            }
            val chat: @Composable ColumnScope.() -> Unit = {
                RoomChat(state, vm, visitProfile, { selectedMessage = it }, Modifier.weight(1f))
                ChatComposer(
                    text, { text = it.take(500) }, gif, { gif = null },
                    { vm.send(text, gif) { text = ""; gif = null } }, vm::heart,
                    room.revision > 0 && state.membership.number("likedRevision") == room.revision, { chatFocused = it },
                    showHeart = state.audioReady && !state.hosting && room.current != null,
                ) {
                    ChatActions(room, state, vm, { show(RoomPanel.REQUEST_SONG) }, { show(RoomPanel.GIF) })
                }
            }
            playerCard()
            if (canControl) {
                val latestChat = state.messages.lastOrNull {
                    it.author.uid != state.uid && it.kind != "listenerLeft"
                }
                var lastReadMessage by remember(room.id) { mutableStateOf<String?>(null) }
                LaunchedEffect(pager.currentPage, latestChat?.id) {
                    if (pager.currentPage == 1) lastReadMessage = latestChat?.id
                    else focus.clearFocus()
                }
                val unreadChat = latestChat?.takeIf { it.id != lastReadMessage }
                HorizontalPager(pager, Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
                    Column(Modifier.fillMaxSize()) {
                        if (page == 1) chat()
                        else {
                            HostControls(
                                room, state, vm, micClick, { show(RoomPanel.QUEUE) },
                                { scope.launch { pager.animateScrollToPage(1) } },
                                unreadChat, Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (!chatFocused) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).height(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                    ) {
                        repeat(2) { page ->
                            Box(Modifier.size(6.dp).clip(CircleShape)
                                .background(if (page == pager.currentPage) CorusColors.Text else CorusColors.Tertiary))
                        }
                    }
                }
            } else chat()
        }
    }

    when (panel) {
        RoomPanel.HISTORY -> LiveSheet(
            stringResource(CorusStrings.corus_live_recently_played), close,
            large = true, sheetToolbar = true, startsHalfExpanded = true,
        ) {
            LaunchedEffect(room.recent) { vm.refreshSaves(room.recent) }
            if (room.recent.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.History, null, tint = CorusColors.Secondary, modifier = Modifier.size(36.dp))
                    LiveText(stringResource(CorusStrings.corus_live_no_songs_played_yet), CorusFont.songTitle)
                }
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(room.recent, key = { it.playRevision to it.id }) { track ->
                    val saveIcon = if (LiveViewModel.saveId(track) in state.savedIds) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder
                    LiveTrackRow(
                        track, saveIcon.takeIf { state.access?.saves == true },
                        "Save song", { vm.save(track) }, onTrack = { close(); selectedSong = track },
                    )
                }
            }
        }
        RoomPanel.LISTENERS -> {
            LaunchedEffect(room.id, room.revision, room.likes, state.listenerCount) {
                vm.action("listeners") { data -> listenRoster.value = data.rows("listeners") }
            }
            LiveSheet(stringResource(CorusStrings.corus_live_listeners), close, large = state.listenerCount > 7) {
                val roster = listenRoster.value
                if (roster == null) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                else if (roster.isEmpty()) LiveText(stringResource(CorusStrings.corus_live_no_listeners_yet), color = CorusColors.Secondary)
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(roster.orEmpty(), key = { it.text("uid") }) { fields ->
                        val person = LivePerson.parse(fields)
                        PersonRow(person, { visitProfile(person.uid) }) {
                            if (fields.number("likedRevision") == room.revision) {
                                Icon(Icons.Default.Favorite, "Liked this song", tint = LiveRed, modifier = Modifier.size(15.dp))
                            }
                            if (state.hosting) LiveIconButton(Icons.Default.PersonRemove, stringResource(CorusStrings.corus_live_remove_listener), {
                                vm.action("removeListener", mapOf("targetUid" to person.uid)) {
                                    listenRoster.value = listenRoster.value?.filterNot { it.text("uid") == person.uid }
                                }
                            })
                        }
                    }
                }
            }
        }
        RoomPanel.OPTIONS -> LiveSheet(stringResource(CorusStrings.corus_live_room_options), close) {
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button) { panel = RoomPanel.EDIT_TITLE }
                        .heightIn(min = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LiveText(stringResource(R.string.live_room_title_field), CorusFont.username)
                        LiveText(room.title, color = CorusColors.Secondary, lines = 1)
                    }
                    Icon(Icons.Default.Edit, stringResource(R.string.live_edit_room_title),
                        tint = CorusColors.Accent, modifier = Modifier.size(20.dp))
                }
                HorizontalDivider(color = CorusColors.Divider)
                LiveText(stringResource(R.string.live_room_effects), CorusFont.username,
                    modifier = Modifier.semantics { heading() })
                RoomEffectSection(
                    title = stringResource(CorusStrings.style_picker_disco_effect),
                    effects = listOf(
                        "slow" to stringResource(CorusStrings.style_disco_slow_dance),
                        "disco" to stringResource(CorusStrings.style_disco_disco_ball),
                        "party" to stringResource(CorusStrings.style_disco_dance_party),
                    ),
                    selected = visibleEffect,
                    toggle = vm::toggleRoomEffect,
                )
                RoomEffectSection(
                    title = stringResource(R.string.live_room_weather),
                    effects = listOf(
                        "rain" to stringResource(CorusStrings.corus_live_rain),
                        "snow" to stringResource(CorusStrings.corus_live_snow),
                    ),
                    selected = visibleEffect,
                    toggle = vm::toggleRoomEffect,
                )
                if (room.collaborative) {
                    HorizontalDivider(Modifier.padding(top = 12.dp), color = CorusColors.Divider)
                    RoomSwitch("Allow DJ requests", room.allowDJRequests) {
                        vm.action("setRequestPermission", mapOf("type" to "dj", "allowed" to it))
                    }
                    RoomSwitch("Allow song requests", room.allowSongRequests) {
                        vm.action("setRequestPermission", mapOf("type" to "song", "allowed" to it))
                    }
                }
            }
        }
        RoomPanel.EDIT_TITLE -> EditRoomTitleSheet(
            room.customTitle,
            onDismiss = { panel = RoomPanel.OPTIONS },
            onSave = { title -> vm.renameRoom(room.id, title) },
        )
        RoomPanel.INVITE -> LiveSheet(stringResource(CorusStrings.native_1fd9ae1607aa), close) {
            if (openDJSpots > 0) LiveRow("Invite a DJ", Icons.Default.PersonAdd) { panel = RoomPanel.PICK_DJS }
            LiveRow("Invite Listeners", Icons.Default.IosShare) { close(); share() }
        }
        RoomPanel.PICK_DJS -> LivePersonPicker(
            emptyList(), vm, close, limit = openDJSpots,
            excluded = (room.djs + state.pendingDJs.filter { it.number("expiresAt") > vm.serverTime() })
                .map { it.text("uid") }.toSet() + room.host.uid,
        ) { people ->
            if (people.isNotEmpty() && people.size <= openDJSpots) vm.action("inviteDJs", mapOf("uids" to people.map { it.uid }))
            close()
        }
        RoomPanel.LINEUP -> LiveLineupSheet(
            room, state, vm, close, visitProfile,
            invite = ({ panel = RoomPanel.PICK_DJS }).takeIf { openDJSpots > 0 },
            requestSong = { panel = RoomPanel.REQUEST_SONG },
        )
        RoomPanel.QUEUE -> RoomQueueEditor(room, state, vm, close)
        RoomPanel.REQUEST_SONG -> LiveSongPicker("Request a song", vm, close) { track ->
            vm.action("requestSong", mapOf("track" to track.fields()))
            close()
        }
        RoomPanel.GIF -> LiveGifPicker(vm, close) { url -> gif = url; close() }
        RoomPanel.REPORT -> LiveReportSheet(close) { reason, details ->
            vm.action("reportRoom", mapOf("reason" to reason, "details" to details)) { close() }
        }
        RoomPanel.END -> {
            var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
            LaunchedEffect(room.id, room.djs) {
                if (room.djs.isNotEmpty()) vm.action("hostHandoffOptions") { result ->
                    candidates = (result["eligibleDJIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                }
            }
            LiveSheet(stringResource(CorusStrings.corus_live_end_live), close) {
                LiveText(stringResource(CorusStrings.corus_live_this_will_end_the_room_for_everyone), color = CorusColors.Secondary)
                room.djs.firstOrNull { it.text("uid") == candidates.firstOrNull() }?.let { dj ->
                    LiveRow(stringResource(CorusStrings.corus_live_step_down), Icons.Default.SwitchAccount, LivePerson.parse(dj).username) {
                        vm.action("stepDown", mapOf("targetUid" to dj.text("uid"))) { close() }
                    }
                }
                LivePrimary(stringResource(CorusStrings.corus_live_end_live)) { close(); vm.end() }
                TextButton(onClick = close, modifier = Modifier.align(Alignment.CenterHorizontally)) { LiveText(stringResource(CorusStrings.common_cancel)) }
            }
        }
        null -> Unit
    }
    state.membership["hostHandoff"].liveFields()?.let { offer ->
        val decline = { vm.action("declineHost", mapOf("offerId" to offer.text("id"))); Unit }
        LiveSheet("Take over as host?", decline) {
            LiveText("@${room.host.username} wants you to host this live room.")
            LivePrimary("Accept") { vm.action("acceptHost", mapOf("offerId" to offer.text("id"))) }
            TextButton(onClick = decline) { LiveText("Decline") }
        }
    }
    selectedMessage?.let { message ->
        LiveSheet("Message options", { selectedMessage = null }) {
            if (state.hosting) {
                LiveRow(stringResource(CorusStrings.corus_live_remove_message), Icons.Default.DeleteOutline) {
                    vm.action("removeMessage", mapOf("messageId" to message.id)); selectedMessage = null
                }
            } else if (message.author.uid != state.uid) {
                LiveRow(stringResource(CorusStrings.corus_live_report_message), Icons.Outlined.Flag) {
                    vm.action("report", mapOf("messageId" to message.id)); selectedMessage = null
                }
            }
        }
    }
    selectedSong?.let { track ->
        LiveSheet(track.song.name, { selectedSong = null }) {
            LiveTrackRow(track)
            LiveRow(stringResource(CorusStrings.native_84b771e51b43), Icons.Default.MusicNote) {
                selectedSong = null
                vm.minimize()
                openSong(track.song)
            }
            LiveRow("Save song", Icons.Outlined.BookmarkBorder) { vm.save(track); selectedSong = null }
        }
    }
}

@Composable
private fun RoomEffectSection(
    title: String,
    effects: List<Pair<String, String>>,
    selected: String,
    toggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LiveText(title, CorusFont.captionMedium, CorusColors.Secondary,
            modifier = Modifier.semantics { heading() })
        effects.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (id, label) ->
                    val active = selected == id
                    Row(
                        Modifier.weight(1f).clip(CircleShape)
                            .background(if (active) CorusColors.Accent else CorusColors.SheetControlBackground)
                            .selectable(active, role = Role.Button, onClick = { toggle(id) })
                            .heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = CorusFont.captionMedium, textAlign = TextAlign.Center,
                            color = if (active) Color.White else CorusColors.Text)
                        if (active) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EditRoomTitleSheet(initialTitle: String, onDismiss: () -> Unit, onSave: suspend (String) -> Unit) {
    val originalTitle = remember { initialTitle }
    var title by remember { mutableStateOf(originalTitle) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val editedTitle = title.trim().replace(Regex("[\\s\\p{Z}]+"), " ")
    val canSave = !saving && editedTitle != originalTitle
    val save: () -> Unit = {
        if (canSave) {
            saving = true
            failed = false
            focus.clearFocus()
            scope.launch {
                try {
                    onSave(editedTitle)
                    onDismiss()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failed = true
                } finally {
                    saving = false
                }
            }
        }
    }

    LiveSheet(stringResource(R.string.live_edit_room_title), onDismiss, dismissEnabled = !saving) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = limitLiveCharacters(it, LIVE_ROOM_TITLE_LIMIT) },
            modifier = Modifier.fillMaxWidth(),
            label = { LiveText(stringResource(R.string.live_room_title_field)) },
            textStyle = CorusFont.body,
            singleLine = true,
            enabled = !saving,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        if (failed) {
            LiveText(stringResource(R.string.edit_profile_save_error), CorusFont.caption, CorusColors.Secondary)
        }
        LivePrimary(
            stringResource(if (saving) CorusStrings.compose_draft_saving else R.string.live_save_room_title),
            enabled = canSave,
            onClick = save,
        )
    }
}

@Composable
private fun RoomIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    LiveIconButton(icon, label, onClick, tint = CorusColors.Text, touchSize = 44.dp, glyphSize = 17.dp)
}

@Composable
private fun RoomExitButton(label: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.heightIn(min = 34.dp).background(LiveRed.copy(alpha = .1f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiveText(label, CorusFont.captionMedium, LiveRed, lines = 1)
        }
    }
}

@Composable
private fun RoomLineup(
    room: LiveRoom,
    hosting: Boolean,
    voiceLevels: Map<String, Float>,
    openProfile: (String) -> Unit,
    openLineup: () -> Unit,
) {
    val hasFourDJs = room.djs.size >= LIVE_MAX_GUEST_DJS
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (hasFourDJs) 8.dp else 16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        val slot = if (hasFourDJs) Modifier.weight(1f) else Modifier.widthIn(max = 100.dp)
        (listOf(room.host) + room.djs.map(LivePerson::parse)).forEach { person ->
            val isHost = person.uid == room.host.uid
            val talking = room.voiceMode == "talk"
            val playing = !talking && room.status == "playing" && room.current != null &&
                room.pausedAt == null && room.pendingSkip == null && (room.currentDJ?.uid ?: room.host.uid) == person.uid
            val openPerson = if (isHost) ({ openProfile(person.uid) }) else openLineup
            Column(
                slot.clickable(enabled = !isHost || !hosting, onClick = openPerson),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    LiveAvatar(person, 48.dp, ring = isHost || playing || (voiceLevels[person.uid] ?: 0f) > .03f)
                    if (playing || (isHost && !talking && room.djs.isEmpty())) {
                        LiveText(
                            "LIVE", CorusFont.captionMedium.copy(fontWeight = FontWeight.Black), Color.White,
                            Modifier.align(Alignment.BottomCenter).background(Color(0xFF4774CA), CircleShape)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                LiveText(person.username.trim('@'), CorusFont.username, lines = 1)
                if (talking) LiveVoiceMeter(voiceLevels[person.uid] ?: 0f)
            }
        }
    }
}

@Composable
private fun RoomPlayerCard(
    room: LiveRoom, state: LiveUiState, vm: LiveViewModel, welcome: Boolean, song: (LiveTrack) -> Unit, history: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(CorusColors.CardBackground).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val track = room.current
        when {
            welcome -> LiveRow(stringResource(CorusStrings.corus_live_welcome_message), Icons.Default.GraphicEq) {}
            room.voiceMode == "talk" && room.pendingSkip == null -> {
                Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (state.micEnabled) Icons.Default.Mic else Icons.Default.MicOff, null, tint = CorusColors.Accent)
                    val level = if (state.micEnabled) state.voiceLevels[state.uid] ?: 0f
                        else state.voiceLevels.values.maxOrNull() ?: 0f
                    LiveText(
                        if (state.micEnabled) stringResource(CorusStrings.corus_live_mic_is_on)
                        else if (state.hosting) "Mic is muted" else "Listening live",
                        CorusFont.songTitle, modifier = Modifier.weight(1f),
                    )
                    LiveVoiceMeter(level)
                }
            }
            track == null || room.status == "waiting" -> {
                LiveText(stringResource(CorusStrings.corus_live_waiting_for_the_next_song), CorusFont.songTitle, modifier = Modifier.padding(vertical = 16.dp))
            }
            !state.audioReady && !state.hosting && state.audioError != null -> {
                LiveText(stringResource(CorusStrings.corus_live_connect_to_hear_the_music), CorusFont.songTitle)
                LivePrimary(stringResource(CorusStrings.music_service_spotify_sign_in), onClick = vm::reconnectAudio)
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.weight(1f).then(if (state.hosting) Modifier else Modifier.clickable { song(track) }),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LiveArtwork(track.song.albumArtURL, 66.dp, neutralPlaceholder = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            LiveText(
                                when {
                                    room.pendingSkip != null -> "Switching songs…"
                                    !state.hosting && !state.audioReady -> stringResource(CorusStrings.corus_live_tuning_in)
                                    !state.hosting && !state.muted -> "Now playing · Listening live"
                                    else -> stringResource(CorusStrings.full_player_queue_now_playing)
                                },
                                CorusFont.caption, CorusColors.Secondary, lines = 1,
                            )
                            LiveText(track.song.name, CorusFont.songTitle, lines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                LiveText(track.song.artistName, CorusFont.artistName, CorusColors.Secondary, Modifier.weight(1f, fill = false), lines = 1)
                                if (room.likes > 0) {
                                    LiveText("·", CorusFont.caption, CorusColors.Secondary)
                                    Icon(Icons.Default.Favorite, null, tint = CorusColors.Secondary, modifier = Modifier.size(11.dp))
                                    LiveText(room.likes.toString(), CorusFont.caption, CorusColors.Secondary)
                                }
                            }
                            if (room.djs.isNotEmpty() || room.currentDJ != null) {
                                val picker = room.currentDJ ?: room.host
                                LiveText("Picked by @${picker.username.trim('@')}", CorusFont.caption, CorusColors.Secondary, lines = 1)
                            }
                        }
                    }
                    if (state.access?.saves == true) RoomIconButton(
                        if (LiveViewModel.saveId(track) in state.savedIds) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        "Save song", { vm.save(track) },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).heightIn(min = 44.dp).clickable(onClick = history),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Default.History, null, tint = CorusColors.Secondary, modifier = Modifier.size(14.dp))
                LiveText(stringResource(CorusStrings.corus_live_recently_played), CorusFont.captionMedium, CorusColors.Secondary)
            }
            RoomIconButton(Icons.Default.SpeakerGroup, stringResource(CorusStrings.corus_live_audio_output), { showLiveAudioOutput(context) })
            if (track != null) RoomIconButton(
                if (state.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                if (state.muted) stringResource(CorusStrings.corus_live_unmute_audio) else stringResource(CorusStrings.corus_live_mute_audio), vm::toggleMute,
            )
        }
        state.audioError?.let { LiveText(it, CorusFont.caption, CorusColors.Secondary) }
    }
}

@Composable
internal fun LiveVoiceMeter(level: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(8) { index ->
            Box(Modifier.width(5.dp).height(6.dp).clip(CircleShape).background(
                if (level * 8f >= index + 1) CorusColors.Accent else CorusColors.Secondary.copy(alpha = .20f),
            ))
        }
    }
}

@Composable
private fun RoomStatus(count: Int, collaboration: Boolean, listeners: () -> Unit, lineup: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = CorusColors.Secondary.copy(alpha = .25f))
        LivePill(count.toString(), listeners, showPeople = true)
        if (collaboration) LivePill("DJs", lineup)
        HorizontalDivider(Modifier.weight(1f), color = CorusColors.Secondary.copy(alpha = .25f))
    }
}

@Composable
private fun LivePill(text: String, action: () -> Unit, showPeople: Boolean = false) {
    Box(Modifier.heightIn(min = 44.dp).clickable(onClick = action), contentAlignment = Alignment.Center) {
        Row(
            Modifier.border(1.dp, CorusColors.Divider, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showPeople) Icon(Icons.Default.People, "Listeners", tint = CorusColors.Secondary, modifier = Modifier.size(14.dp))
            LiveText(text, CorusFont.captionMedium, CorusColors.Secondary)
        }
    }
}

@Composable
private fun HostControls(
    room: LiveRoom, state: LiveUiState, vm: LiveViewModel, micClick: () -> Unit, queueEditor: () -> Unit,
    chat: () -> Unit, unreadChat: LiveMessage?, modifier: Modifier = Modifier,
) {
    var playNext by remember(room.id) { mutableStateOf<QueuePlaySelection?>(null) }
    val guest = state.dj
    val serverQueue = guest?.rows("queue")?.mapNotNull(LiveTrack::parse) ?: room.queue
    val version = guest?.number("version") ?: room.queueVersion
    val pendingOrder = remember(room.id, guest?.text("uid"), version) { mutableStateOf<List<LiveTrack>?>(null) }
    val queue = pendingOrder.value ?: serverQueue
    val dragPreview = remember(pendingOrder) { mutableStateOf<LiveQueueDrag?>(null) }
    val editingEnabled = room.pendingSkip == null && !state.queueUpdating
    val canPlay = state.hosting && room.djs.isEmpty() && room.pausedAt == null
    val canSkip = state.canSkipTrack(room, queue)
    val accentText = if (LocalCorusDarkTheme.current) Color(0xFF98BAFF) else Color(0xFF3A6BB9)
    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.hosting) {
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(CorusColors.Secondary.copy(alpha = .10f)).padding(2.dp)) {
                listOf("talk" to stringResource(CorusStrings.corus_live_talk), "music" to stringResource(CorusStrings.search_tab_music)).forEach { (mode, label) ->
                    val selected = mode == (room.voiceMode.ifBlank { "music" })
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(CircleShape)
                            .background(if (selected) CorusColors.Accent else Color.Transparent)
                            .clickable(enabled = !selected && room.pendingSkip == null) {
                                vm.action("voiceMode", mapOf("mode" to mode)) { if (mode == "talk" && !state.micEnabled) micClick() }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val tint = if (selected) Color.White else CorusColors.Text.copy(alpha = .76f)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (mode == "talk") Icons.Default.Mic else Icons.Default.MusicNote, null,
                                tint = tint, modifier = Modifier.size(16.dp))
                            LiveText(label, CorusFont.bodyMedium, tint)
                        }
                    }
                }
            }
        } else if (room.voiceEnabled && room.voiceMode != "talk") {
            val requested = room.talkRequests.any { it.text("uid") == state.uid }
            LiveRow(if (requested) "Cancel Talk request" else "Request Talk", Icons.Default.Mic) {
                vm.action(if (requested) "cancelTalkRequest" else "requestTalk")
            }
        }
        if (room.voiceMode == "talk") LiveRow(if (state.micEnabled) stringResource(CorusStrings.corus_live_mute_microphone) else "Unmute microphone", Icons.Default.Mic, onClick = micClick)
        if (state.hosting) room.talkRequests.forEach { request ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val username = room.djs.firstOrNull { it.text("uid") == request.text("uid") }?.let(LivePerson::parse)?.username.orEmpty()
                LiveText("@$username wants to talk", modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    vm.action("voiceMode", mapOf("mode" to "talk", "talkRequestId" to request.text("id"))) {
                        if (!state.micEnabled) micClick()
                    }
                }) { LiveText(stringResource(CorusStrings.selfie_permission_allow)) }
                LiveIconButton(Icons.Default.Close, "Decline", { vm.action("declineTalkRequest", mapOf("talkRequestId" to request.text("id"))) })
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LiveText(
                    if (guest != null || room.djs.isNotEmpty()) "My queue" else stringResource(CorusStrings.full_player_queue_title),
                    CorusFont.username,
                )
                LiveText(queue.size.toString(), CorusFont.caption, CorusColors.Secondary)
            }
            if (queue.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    Icon(Icons.Default.QueueMusic, null, tint = CorusColors.Secondary, modifier = Modifier.size(25.dp))
                    if (state.hosting && room.current != null && room.djs.none { it.rows("queue").isNotEmpty() }) {
                        LiveText("Your queue is empty", CorusFont.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text("Add music to keep the room going.", style = CorusFont.caption, color = CorusColors.Secondary, textAlign = TextAlign.Center)
                    } else {
                        Text("Add songs to your queue.", style = CorusFont.body, color = CorusColors.Secondary, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    val keys = liveQueueRowKeys(queue)
                    items(queue.size, key = { keys[it] }) { index ->
                        val track = queue[index]
                        HostQueueRow(
                            track, index, queue.size, editingEnabled, dragPreview,
                            onPlay = if (canPlay) { { playNext = QueuePlaySelection(track, index, version, room.revision) } } else null,
                            remove = {
                                vm.updateQueue(queue.toMutableList().apply { removeAt(index) }, version, state.hosting)
                            },
                            move = { destination ->
                                val reordered = queue.toMutableList().apply { add(destination, removeAt(index)) }
                                pendingOrder.value = reordered
                                if (!vm.updateQueue(reordered, version, state.hosting) { pendingOrder.value = null }) {
                                    pendingOrder.value = null
                                }
                            },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HostQueueButton(
                    stringResource(CorusStrings.corus_live_add_music), Icons.Default.Add, Modifier.weight(1f),
                    accentText, CorusColors.Accent.copy(alpha = .13f), editingEnabled, queueEditor,
                )
                if (guest != null || (state.hosting && room.current != null)) {
                    HostQueueButton(
                        stringResource(CorusStrings.corus_live_skip_song), Icons.Default.SkipNext, Modifier.weight(1f),
                        CorusColors.Text, CorusColors.Secondary.copy(alpha = .13f), canSkip, vm::skip,
                    )
                }
            }
        }
        val join = unreadChat?.takeIf { it.kind == "listenerJoined" }
        val chatTint = if (unreadChat == null) CorusColors.Secondary else accentText
        Row(
            Modifier.align(Alignment.CenterHorizontally).heightIn(min = 44.dp).clickable(onClick = chat).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (join == null) Icons.Default.ChatBubbleOutline else Icons.Default.PersonAdd, null, tint = chatTint, modifier = Modifier.size(14.dp))
            LiveText(
                when {
                    join != null -> "@${join.author.username.removePrefix("@")} joined the room"
                    unreadChat != null -> stringResource(CorusStrings.new_message_action)
                    else -> "Swipe left for chat"
                },
                CorusFont.caption, chatTint, lines = 1,
            )
            if (unreadChat != null) Box(Modifier.size(6.dp).background(CorusColors.Accent, CircleShape))
        }
    }
    playNext?.let { selection ->
        LiveSheet(selection.track.song.name, { playNext = null }) {
            val stillQueued = editingEnabled && canPlay && room.revision == selection.revision && version == selection.version
            LivePrimary(stringResource(CorusStrings.corus_live_play_now), enabled = stillQueued) {
                vm.action("playQueuedTrack", mapOf(
                    "index" to selection.index, "revision" to selection.revision, "queueVersion" to selection.version,
                ))
                playNext = null
            }
        }
    }
}

@Composable
private fun RoomQueueEditor(room: LiveRoom, state: LiveUiState, vm: LiveViewModel, close: () -> Unit) {
    val initial = remember(room.id) { state.dj?.rows("queue")?.mapNotNull(LiveTrack::parse) ?: room.queue }
    val version = remember(room.id) { state.dj?.number("version") ?: room.queueVersion }
    val hosting = remember(room.id) { state.hosting }
    val queue = remember(room.id) { initial.toMutableStateList() }
    val dragPreview = remember(room.id) { mutableStateOf<LiveQueueDrag?>(null) }
    val limit = if (hosting) 50 else 20
    var importing by remember { mutableStateOf(false) }
    val done = {
        val updated = queue.toList()
        if (updated != initial) vm.updateQueue(updated, version, hosting)
        close()
    }
    LiveSheet(
        if (hosting) stringResource(CorusStrings.full_player_queue_title) else "My queue",
        done, large = true, done = done, sheetToolbar = true, dismissKeyboardFromHeader = true,
    ) {
        LiveSongPickerContent(
            vm,
            choose = { track ->
                if (queue.size < limit && queue.none {
                        it.id == track.id || (track.song.isrc != null && track.song.isrc == it.song.isrc)
                    }
                ) queue.add(track)
            },
            queued = queue.toList(),
            canAdd = queue.size < limit,
            afterSearch = { query ->
                if (query.isBlank() && queue.size < limit) {
                    LiveRow("Import playlist", Icons.Default.Link) { importing = true }
                }
            },
        ) {
            if (hosting && room.current != null) item {
                RoomQueueSectionTitle(stringResource(CorusStrings.full_player_queue_now_playing))
                LiveTrackRow(
                    room.current,
                    queueStyle = true,
                    artworkSize = LiveSongPickerArtworkSize,
                    itemSpacing = 12.dp,
                )
                HorizontalDivider(
                    Modifier.padding(start = LiveSongPickerArtworkSize + 12.dp),
                    color = CorusColors.Divider,
                )
            }
            item {
                RoomQueueSectionTitle(
                    "${stringResource(CorusStrings.full_player_queue_title)} · ${queue.size}"
                )
                if (queue.isEmpty()) {
                    LiveText("Add songs to your queue.", color = CorusColors.Secondary, modifier = Modifier.padding(vertical = 16.dp))
                    HorizontalDivider(color = CorusColors.Divider)
                }
            }
            val keys = liveQueueRowKeys(queue)
            items(queue.size, key = { keys[it] }) { index ->
                HostQueueRow(
                    queue[index], index, queue.size, enabled = true, dragPreview = dragPreview, onPlay = null,
                    remove = { queue.removeAt(index) },
                    move = { destination -> queue.add(destination, queue.removeAt(index)) },
                )
            }
        }
    }
    if (importing) LivePlaylistPicker(vm, queue.toList(), limit - queue.size, { importing = false }) { tracks ->
        queue.addAll(tracks)
        importing = false
    }
}

@Composable
private fun RoomQueueSectionTitle(title: String) {
    LiveText(
        title.uppercase(Locale.getDefault()), CorusFont.caption, CorusColors.Secondary,
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp).semantics { heading() },
    )
}

@Composable
private fun HostQueueButton(
    title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, tint: Color, background: Color,
    enabled: Boolean, click: () -> Unit,
) {
    val contentColor = tint.copy(alpha = if (enabled) 1f else .35f)
    Row(
        modifier.heightIn(min = 44.dp).clip(CircleShape).background(background)
            .clickable(enabled = enabled, onClick = click).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(15.dp))
        LiveText(title, CorusFont.captionMedium, contentColor)
    }
}

@Composable
private fun HostQueueRow(
    track: LiveTrack, index: Int, count: Int, enabled: Boolean,
    dragPreview: MutableState<LiveQueueDrag?>, onPlay: (() -> Unit)?, remove: () -> Unit, move: (Int) -> Unit,
) {
    LiveQueueReorderRow(index, count, 60.dp, move, enabled = enabled, dragPreview = dragPreview) { handle ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .then(if (onPlay == null) Modifier else Modifier.clickable(enabled = enabled, onClick = onPlay)),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.widthIn(min = 14.dp), contentAlignment = Alignment.Center) {
                    LiveText((index + 1).toString(), CorusFont.caption, CorusColors.Secondary)
                }
                LiveArtwork(track.song.albumArtURL, 40.dp, neutralPlaceholder = true)
                Column(Modifier.weight(1f)) {
                    LiveText(track.song.name, CorusFont.username, lines = 1)
                    LiveText(track.song.artistName, CorusFont.caption, CorusColors.Secondary, lines = 1)
                }
            }
            LiveIconButton(
                Icons.Default.Close, stringResource(R.string.live_remove_queue_song), remove,
                enabled = enabled, touchSize = 44.dp, glyphSize = 17.dp,
            )
            handle()
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RoomChat(state: LiveUiState, vm: LiveViewModel, openProfile: (String) -> Unit, options: (LiveMessage) -> Unit, modifier: Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (state.messages.isNotEmpty()) list.scrollToItem(state.messages.lastIndex)
    }
    if (state.messages.isEmpty()) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ChatBubbleOutline, null, tint = CorusColors.Secondary, modifier = Modifier.size(22.dp))
            LiveText(stringResource(CorusStrings.corus_live_be_the_first_to_comment), CorusFont.body, CorusColors.Secondary)
        }
    } else LazyColumn(
        modifier.fillMaxWidth(), state = list,
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
    ) {
        items(state.messages, key = { it.id }) { message ->
            if (message.kind == "listenerJoined") {
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.PersonAdd, null, tint = CorusColors.Secondary, modifier = Modifier.size(14.dp))
                    LiveText("@${message.author.username.removePrefix("@")} joined the room", CorusFont.caption, CorusColors.Secondary)
                }
            } else {
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { options(message) }),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.clickable { openProfile(message.author.uid) }) { LiveAvatar(message.author, 30.dp) }
                    Column(Modifier.weight(1f)) {
                        LiveText(message.author.username, CorusFont.username, modifier = Modifier.clickable { openProfile(message.author.uid) })
                        if (message.text.isNotBlank()) LiveText(message.text)
                        message.gifUrl?.let { AsyncImage(it, null, Modifier.width(170.dp).heightIn(max = 170.dp).clip(RoundedCornerShape(9.dp))) }
                        if (message.kind == "songRequest" && message.fields.text("requestStatus").ifBlank { "pending" } == "pending") {
                            val target = mapOf("targetUid" to message.author.uid, "requestId" to message.id)
                            Row {
                                if (state.hosting || state.dj != null) {
                                    TextButton(onClick = { vm.action("queueSongRequest", target) }) { LiveText("Add to queue") }
                                    TextButton(onClick = { vm.action("dismissSongRequest", target) }) { LiveText(stringResource(CorusStrings.feed_switch_hint_dismiss)) }
                                } else if (message.author.uid == state.uid) {
                                    TextButton(onClick = { vm.action("cancelSongRequest", target) }) { LiveText("Cancel request") }
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
private fun ChatComposer(
    text: String, onText: (String) -> Unit, gif: String?, clearGif: () -> Unit, send: () -> Unit,
    heart: () -> Unit, liked: Boolean, focus: (Boolean) -> Unit, showHeart: Boolean,
    actions: @Composable () -> Unit,
) {
    if (gif != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(gif, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            LiveIconButton(Icons.Default.Close, stringResource(CorusStrings.corus_live_remove_gif), clearGif)
        }
    }
    val canSend = text.isNotBlank() || gif != null
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showHeart) LiveIconButton(
            if (liked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
            stringResource(CorusStrings.corus_live_like_song), heart,
            tint = if (liked) LiveRed else CorusColors.Text, touchSize = 44.dp, glyphSize = 25.dp,
        )
        Row(
            Modifier.weight(1f).clip(CircleShape).background(CorusColors.CardBackground)
                .border(1.dp, CorusColors.Secondary.copy(alpha = .22f), CircleShape).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                text, onText, Modifier.weight(1f).onFocusChanged { focus(it.isFocused) },
                textStyle = CorusFont.body.copy(color = CorusColors.Text),
                cursorBrush = SolidColor(CorusColors.Accent), maxLines = 3,
                decorationBox = { input -> Box { if (text.isEmpty()) LiveText(stringResource(CorusStrings.corus_live_say_something), color = CorusColors.Secondary); input() } },
            )
            actions()
        }
        IconButton(onClick = send, enabled = canSend, modifier = Modifier.size(44.dp)) {
            Box(
                Modifier.size(26.dp).background(
                    if (canSend) CorusColors.Accent else CorusColors.Tertiary.copy(alpha = .2f), CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowUpward, stringResource(CorusStrings.thread_send_aria),
                    tint = if (canSend) Color.White else CorusColors.Secondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ChatActions(
    room: LiveRoom,
    state: LiveUiState,
    vm: LiveViewModel,
    requestSong: () -> Unit,
    pickGif: () -> Unit,
) {
    var expanded by remember(room.id) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val now = vm.serverTime()
    val invited = state.invites.any { it.text("roomId") == room.id && it.number("expiresAt") > now }
    val requested = state.myDJRequest.text("status") == "pending"
    val lineupFull = room.djs.size + state.pendingDJs.count { it.number("expiresAt") > now } >= LIVE_MAX_GUEST_DJS
    Box {
        LiveIconButton(Icons.Default.Add, "More chat actions", { expanded = true }, touchSize = 44.dp, glyphSize = 18.dp)
        DropdownMenu(expanded, { expanded = false }, containerColor = CorusColors.CardBackground) {
            if (room.collaborative && !state.hosting && state.dj == null) {
                if (room.allowSongRequests) DropdownMenuItem(
                    text = { LiveText("Request Song") },
                    leadingIcon = { Icon(Icons.Default.MusicNote, null) },
                    onClick = { expanded = false; requestSong() },
                )
                if (!invited && requested) DropdownMenuItem(
                    text = { LiveText("Cancel DJ request") },
                    leadingIcon = { Icon(Icons.Default.Close, null) },
                    onClick = { expanded = false; vm.action("cancelDJRequest") },
                )
                if (!invited && !requested && room.allowDJRequests) DropdownMenuItem(
                    text = { LiveText(if (lineupFull) "DJ spots full" else "Request to DJ") },
                    leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                    enabled = !lineupFull,
                    onClick = { expanded = false; focus.clearFocus(); vm.action("requestDJ") },
                )
            }
            DropdownMenuItem(
                text = { LiveText("GIF") },
                leadingIcon = { Icon(Icons.Default.Photo, null) },
                onClick = { expanded = false; pickGif() },
            )
        }
    }
}

@Composable
private fun LiveLineupSheet(
    room: LiveRoom, state: LiveUiState, vm: LiveViewModel, close: () -> Unit,
    openProfile: (String) -> Unit, invite: (() -> Unit)?, requestSong: () -> Unit,
) {
    var handoff by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(room.id, room.djs) {
        if (state.hosting && room.djs.isNotEmpty()) vm.action("hostHandoffOptions") {
            handoff = (it["eligibleDJIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        }
    }
    LiveSheet("DJs", close, large = room.djs.size + state.djRequests.size > 3) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            PersonRow(room.host, { openProfile(room.host.uid) }) { LiveText("Host", CorusFont.caption, CorusColors.Secondary) }
            room.djs.forEach { raw ->
                val dj = LivePerson.parse(raw)
                var menu by remember(dj.uid) { mutableStateOf(false) }
                PersonRow(dj, { openProfile(dj.uid) }) {
                    if (state.hosting) {
                        Box {
                            LiveIconButton(Icons.Default.MoreHoriz, "DJ options", { menu = true })
                            DropdownMenu(menu, { menu = false }) {
                                if (dj.uid in handoff) DropdownMenuItem(
                                    text = { LiveText("Offer hosting") },
                                    onClick = { vm.action("offerHost", mapOf("targetUid" to dj.uid)); menu = false },
                                )
                                DropdownMenuItem(text = { LiveText("Move to listeners") }, onClick = {
                                    vm.action("moveDJToListeners", mapOf("targetUid" to dj.uid)); menu = false
                                })
                                DropdownMenuItem(text = { LiveText("Remove from room") }, onClick = {
                                    vm.action("removeParticipant", mapOf("targetUid" to dj.uid)); menu = false
                                })
                            }
                        }
                    }
                }
            }
            state.pendingDJs.forEach { raw ->
                val person = LivePerson.parse(raw)
                PersonRow(person, { openProfile(person.uid) }) {
                    LiveText("Invited", CorusFont.caption, CorusColors.Secondary)
                    LiveIconButton(Icons.Default.Close, "Cancel DJ invite", { vm.action("cancelDJInvite", mapOf("targetUid" to person.uid)) })
                }
            }
            state.djRequests.forEach { raw ->
                val person = LivePerson.parse(raw)
                PersonRow(person, { openProfile(person.uid) }) {
                    val request = mapOf("targetUid" to person.uid, "requestCreatedAt" to raw.number("createdAt"))
                    TextButton(onClick = { vm.action("approveDJRequest", request) }) { LiveText("Accept") }
                    LiveIconButton(Icons.Default.Close, "Decline", { vm.action("declineDJRequest", request) })
                }
            }
        }
        if (invite != null && (state.hosting || state.dj != null)) {
            LiveRow("Invite a DJ", Icons.Default.PersonAdd, onClick = invite)
        }
        if (!state.hosting && state.dj == null && room.allowDJRequests) {
            val requested = state.myDJRequest.text("status") == "pending"
            LiveRow(if (requested) "Cancel DJ request" else "Request to DJ", Icons.Default.Headphones) {
                vm.action(if (requested) "cancelDJRequest" else "requestDJ")
            }
        }
        if (!state.hosting && room.allowSongRequests) LiveRow("Request a song", Icons.Default.MusicNote, onClick = requestSong)
    }
}

@Composable
private fun PersonRow(person: LivePerson, onProfile: () -> Unit, trailing: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).clickable(onClick = onProfile), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveAvatar(person, 34.dp)
            LiveText("@${person.username.removePrefix("@")}", CorusFont.username, lines = 1)
        }
        trailing()
    }
}

@Composable
private fun RoomSwitch(text: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LiveText(text, modifier = Modifier.weight(1f))
        Switch(enabled, onChange)
    }
}

@Composable
private fun LiveGifPicker(vm: LiveViewModel, close: () -> Unit, choose: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val search = query.trim()
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember(search, attempt) { mutableStateOf<Result<List<String>>?>(null) }
    LaunchedEffect(search, attempt) {
        if (search.isNotEmpty()) delay(300)
        result = try {
            val urls = vm.gifs(search)
            coroutineContext.ensureActive()
            Result.success(urls.distinct())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            coroutineContext.ensureActive()
            Result.failure(error)
        }
    }
    LiveSheet(stringResource(CorusStrings.native_c275c265cabd), close, large = true) {
        LiveSearchField(query, stringResource(CorusStrings.parity_9e867d1a518b)) { query = it }
        val urls = result?.getOrNull()
        when {
            result?.isFailure == true -> Column(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LiveText(stringResource(CorusStrings.share_generic_error), color = CorusColors.Secondary)
                TextButton(onClick = { attempt++ }) {
                    Text(stringResource(R.string.feed_offline_retry))
                }
            }
            urls?.isEmpty() == true -> LiveText(
                stringResource(CorusStrings.share_post_no_results),
                color = CorusColors.Secondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp),
            )
            else -> key(search, attempt) {
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (urls == null) {
                        items(6) {
                            Row(
                                Modifier.fillMaxWidth().shimmer(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                repeat(3) {
                                    Box(
                                        Modifier.weight(1f).height(100.dp)
                                            .clip(RoundedCornerShape(6.dp)).background(CorusColors.Skeleton),
                                    )
                                }
                            }
                        }
                    } else {
                        items(urls.chunked(3), key = { it.first() }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                row.forEach { url ->
                                    ShimmerAsyncImage(
                                        model = url,
                                        contentDescription = stringResource(CorusStrings.comments_cd_gif),
                                        modifier = Modifier.weight(1f).height(100.dp).clickable { choose(url) },
                                        contentScale = ContentScale.Fit,
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveReportSheet(close: () -> Unit, report: (String, String) -> Unit) {
    val reasons = listOf("Spam or scam", "Harassment or bullying", "Hate speech", "Nudity or sexual content",
        "Violence or threats", "False information", "Intellectual property violation", "Other")
    var reason by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    LiveSheet(stringResource(CorusStrings.corus_live_report_room), close, large = true) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            reasons.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable { reason = option }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(reason == option, { reason = option })
                    LiveText(option)
                }
            }
            LiveSearchField(details, "Additional details") { details = limitLiveCharacters(it, 500) }
        }
        LivePrimary("Submit report", reason.isNotEmpty()) { report(reason, details) }
    }
}

@Composable
private fun LiveRoomEffects(effect: String, hearts: Long, showHearts: Boolean) {
    val animation = remember { Animatable(1f) }
    var lastHeart by remember { mutableLongStateOf(hearts) }
    LaunchedEffect(hearts) {
        if (hearts > lastHeart && showHearts) {
            animation.snapTo(0f)
            animation.animateTo(1f, tween(850))
        }
        lastHeart = hearts
    }
    Box(Modifier.fillMaxSize()) {
        when (effect) {
            "rain" -> RainEffectView(RainIntensity.LIGHT, Modifier.alpha(.8f))
            "snow" -> SnowEffectView(SnowIntensity.HEAVY)
            "slow", "disco", "party" -> {
                val intensity = when (effect) {
                    "slow" -> DiscoIntensity.LIGHT
                    "disco" -> DiscoIntensity.DISCO_BALL
                    else -> DiscoIntensity.DANCE_PARTY
                }
                DiscoScrim(intensity, Modifier.fillMaxSize())
                DiscoEffectView(intensity, Modifier.fillMaxSize())
            }
        }
        if (showHearts && animation.value < 1f) {
            Icon(
                Icons.Default.Favorite, null, tint = LiveRed,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 29.dp, bottom = (72 + animation.value * 112).dp)
                    .alpha(1f - animation.value),
            )
        }
    }
}

internal val LiveRed: Color
    @Composable get() = if (fm.corus.android.ui.theme.LocalCorusDarkTheme.current) Color(0xFFFF6169) else Color(0xFFC72535)

internal fun showLiveAudioOutput(context: Context) {
    val switched = Build.VERSION.SDK_INT >= 34 && MediaRouter2.getInstance(context).showSystemOutputSwitcher()
    if (!switched) {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
    }
}

internal fun shareLiveLink(context: Context, url: String) {
    if (url.isBlank()) return
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }, null))
}
