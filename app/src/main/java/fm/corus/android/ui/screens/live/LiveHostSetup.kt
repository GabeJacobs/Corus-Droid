package fm.corus.android.ui.screens.live

import androidx.compose.ui.res.stringResource
import fm.corus.android.R
import fm.corus.android.localization.CorusStrings
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import fm.corus.android.data.model.TrackSource
import fm.corus.android.ui.components.BandcampBadgeOverlay
import fm.corus.android.ui.components.SoundCloudBadgeOverlay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import androidx.compose.ui.unit.dp
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.FlairStyle
import fm.corus.android.ui.components.dismissKeyboardOnDownwardDrag
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private enum class SetupPanel { QUEUE, INVITE, WELCOME, MIC, NOTIFY }

@Composable
internal fun LiveHostSetup(
    setup: LiveSetup,
    profile: CymbalUser?,
    vm: LiveViewModel,
    busy: LiveProgress?,
    checkingAccess: Boolean,
    roomOpened: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    var panel by remember(setup.id) { mutableStateOf<SetupPanel?>(null) }
    LaunchedEffect(vm, setup.id) { vm.preloadSongSuggestions() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val close = {
        focus.clearFocus()
        keyboard?.hide()
        vm.closeSetup()
    }
    BackHandler(enabled = visible && panel == null) {
        if (busy == null && !roomOpened) close()
    }
    LaunchedEffect(roomOpened, busy) {
        if (roomOpened && busy == null) vm.closeSetup()
    }

    Column(
        modifier.fillMaxSize().background(CorusColors.Background)
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .dismissKeyboardOnDownwardDrag(),
    ) {
        if (busy != null || roomOpened) {
            LiveLoading(busy ?: LiveProgress.STARTING, setup.queue.firstOrNull())
        } else {
            Box(
                Modifier.fillMaxWidth().padding(top = 4.dp).height(56.dp).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                LiveText(stringResource(CorusStrings.corus_live_go_live), CorusFont.screenTitle)
                TextButton(onClick = close, enabled = visible, modifier = Modifier.align(Alignment.CenterEnd)) {
                    LiveText(stringResource(CorusStrings.common_cancel), CorusFont.body, CorusColors.Secondary)
                }
            }
            Column(
                Modifier.weight(1f).widthIn(max = 480.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            ) {
                val avatarSize = if (setup.invitees.size > 1) 48.dp else 56.dp
                val showsInvite = setup.invitees.size < LIVE_MAX_GUEST_DJS
                val usesFourSlots = setup.invitees.size + (if (showsInvite) 1 else 0) == LIVE_MAX_GUEST_DJS
                Spacer(Modifier.height(32.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (usesFourSlots) 8.dp else 20.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Top,
                ) {
                    val slot = if (usesFourSlots) Modifier.weight(1f) else Modifier
                    Column(slot, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiveAvatar(LivePerson(profile?.id.orEmpty(), profile?.username.orEmpty(), profile?.avatarURL),
                            avatarSize, ring = true)
                        LiveText(profile?.username?.let { "@${it.removePrefix("@")}" }.orEmpty(), CorusFont.usernameLarge, lines = 1)
                    }
                    setup.invitees.forEach { person ->
                        Column(
                            slot.widthIn(max = 83.dp).clickable { panel = SetupPanel.INVITE },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.padding(4.dp)) { LiveAvatar(person, avatarSize) }
                            LiveText("@${person.username}", CorusFont.usernameLarge, lines = 1)
                        }
                    }
                    if (showsInvite) {
                        Column(
                            slot.clickable { panel = SetupPanel.INVITE },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.padding(4.dp).size(avatarSize).clip(CircleShape).background(CorusColors.SheetControlBackground),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, null, tint = CorusColors.Secondary, modifier = Modifier.size(21.dp))
                            }
                            LiveText("Invite DJ", CorusFont.usernameLarge, CorusColors.Secondary, lines = 1)
                        }
                    }
                }
                if (setup.invitees.isNotEmpty()) {
                    LiveText("DJ Invites will send when you go live", CorusFont.caption, CorusColors.Secondary,
                        Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp))
                }
                Spacer(Modifier.height(24.dp))
                BasicTextField(
                    setup.title, { value -> vm.editSetup { copy(title = limitLiveCharacters(value, LIVE_ROOM_TITLE_LIMIT)) } },
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(bottom = 8.dp),
                    singleLine = true, textStyle = CorusFont.songTitleLarge.copy(color = CorusColors.Text),
                    cursorBrush = SolidColor(CorusColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    decorationBox = { input ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (setup.title.isEmpty()) LiveText("Room title", CorusFont.songTitleLarge, CorusColors.Secondary.copy(alpha = .52f))
                            input()
                        }
                    },
                )
                HorizontalDivider(color = CorusColors.Secondary.copy(alpha = .18f))
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CorusColors.SheetControlBackground)
                        .clickable {
                            focus.clearFocus()
                            vm.editSetup { copy(loadingFirstSong = false) }
                            panel = SetupPanel.QUEUE
                        }.padding(16.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        LiveText(stringResource(CorusStrings.corus_live_set_queue), CorusFont.username)
                        val first = setup.queue.firstOrNull()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (setup.loadingFirstSong) {
                                Box(
                                    Modifier.size(64.dp).background(
                                        CorusColors.Accent.copy(alpha = .12f), RoundedCornerShape(8.dp),
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(Modifier.size(24.dp), color = CorusColors.Accent, strokeWidth = 2.dp)
                                }
                            } else {
                                LiveArtwork(first?.song?.albumArtURL, 64.dp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (first != null) LiveText(stringResource(CorusStrings.corus_live_first_song), CorusFont.caption, CorusColors.Secondary)
                                val firstSongLabel = if (setup.loadingFirstSong) R.string.live_loading_first_song
                                    else CorusStrings.corus_live_pick_your_first_song
                                LiveText(first?.song?.name ?: stringResource(firstSongLabel), CorusFont.bodyMedium, lines = 1)
                                LiveText(first?.song?.artistName ?: stringResource(CorusStrings.corus_live_you_can_change_your_queue_while_live),
                                    CorusFont.caption, CorusColors.Secondary, lines = 2)
                            }
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = CorusColors.Secondary,
                        modifier = Modifier.align(Alignment.CenterEnd).size(16.dp))
                }
                Spacer(Modifier.height(24.dp))
                LiveSetupOption(stringResource(CorusStrings.corus_live_welcome_message), Icons.Default.GraphicEq, setup.welcome != null) {
                    focus.clearFocus(); panel = SetupPanel.WELCOME
                }
                HorizontalDivider(Modifier.padding(start = 40.dp), color = CorusColors.Secondary.copy(alpha = .25f))
                LiveSetupOption(stringResource(CorusStrings.corus_live_test_microphone), Icons.Outlined.Mic, setup.micTested) {
                    focus.clearFocus(); panel = SetupPanel.MIC
                }
                HorizontalDivider(Modifier.padding(start = 40.dp), color = CorusColors.Secondary.copy(alpha = .25f))
                LiveSetupOption(stringResource(CorusStrings.corus_live_notify_followers), Icons.Outlined.NotificationsNone, setup.notifyFollowers) {
                    focus.clearFocus(); panel = SetupPanel.NOTIFY
                }
            }
            Box(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 32.dp),
            ) {
                LivePrimary(stringResource(CorusStrings.corus_live_go_live), setup.queue.isNotEmpty() && !checkingAccess && visible) {
                    focus.clearFocus()
                    vm.start()
                }
            }
        }
    }

    val closePanel = { panel = null }
    when (panel.takeIf { visible }) {
        SetupPanel.QUEUE -> LiveSetupQueueEditor(
            initial = setup.queue,
            onDone = { tracks -> vm.editSetup { copy(queue = tracks) }; closePanel() },
            vm = vm,
        )
        SetupPanel.INVITE -> LivePersonPicker(setup.invitees, vm, closePanel, setup = true) { selected ->
            vm.editSetup { copy(invitees = selected) }
            closePanel()
        }
        SetupPanel.MIC, SetupPanel.WELCOME -> LiveMicrophone(
            welcome = panel == SetupPanel.WELCOME, initial = setup.welcome, close = closePanel,
        ) { recording ->
            if (panel == SetupPanel.WELCOME) vm.editSetup { copy(welcome = recording, micTested = recording != null || micTested) }
            else vm.editSetup { copy(micTested = true) }
            closePanel()
        }
        SetupPanel.NOTIFY -> LiveSheet(stringResource(CorusStrings.corus_live_notify_followers), closePanel) {
            val messagePlaceholder = stringResource(R.string.live_notification_message_placeholder)
            val sheetFocus = LocalFocusManager.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveText(stringResource(CorusStrings.corus_live_notify_followers), modifier = Modifier.weight(1f))
                Switch(
                    checked = setup.notifyFollowers,
                    onCheckedChange = { enabled -> vm.editSetup { copy(notifyFollowers = enabled) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CorusColors.Background,
                        checkedTrackColor = CorusColors.Accent,
                        uncheckedThumbColor = CorusColors.Background,
                        uncheckedTrackColor = CorusColors.Tertiary,
                        uncheckedBorderColor = CorusColors.Tertiary,
                    ),
                )
            }
            LiveText(
                stringResource(R.string.live_notify_followers_description),
                CorusFont.caption, CorusColors.Secondary,
            )
            Column(
                Modifier.fillMaxWidth().background(CorusColors.CardBackground, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LiveText("CORUS", CorusFont.caption, CorusColors.Secondary)
                LiveText("@${profile?.username.orEmpty()} is live", CorusFont.username)
                BasicTextField(
                    value = setup.notificationMessage,
                    onValueChange = { message -> vm.editSetup { copy(notificationMessage = limitLiveCharacters(message, 140)) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics {
                        contentDescription = messagePlaceholder
                    },
                    textStyle = CorusFont.body.copy(color = CorusColors.Text),
                    cursorBrush = SolidColor(CorusColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { sheetFocus.clearFocus() }),
                    decorationBox = { input ->
                        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            if (setup.notificationMessage.isEmpty()) LiveText(messagePlaceholder, color = CorusColors.Secondary)
                            input()
                        }
                    },
                )
                LiveText("${setup.notificationMessage.codePointCount(0, setup.notificationMessage.length)}/140",
                    CorusFont.caption, CorusColors.Secondary, modifier = Modifier.align(Alignment.End))
            }
        }
        null -> Unit
    }
}

internal const val LIVE_ROOM_TITLE_LIMIT = 32
internal const val LIVE_MAX_GUEST_DJS = 3

internal fun limitLiveCharacters(value: String, max: Int): String {
    val boundary = android.icu.text.BreakIterator.getCharacterInstance()
    boundary.setText(value)
    repeat(max) { if (boundary.next() == android.icu.text.BreakIterator.DONE) return value }
    return value.substring(0, boundary.current())
}

@Composable
internal fun LiveLoading(progress: LiveProgress, track: LiveTrack? = null) {
    Column(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(CorusColors.Accent.copy(alpha = .24f), Color.Transparent)),
        ).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        LiveArtwork(track?.song?.albumArtURL, 132.dp)
        LiveText(
            when (progress) {
                LiveProgress.TUNING -> stringResource(CorusStrings.corus_live_tuning_in)
                LiveProgress.PREPARING -> stringResource(CorusStrings.corus_live_getting_ready)
                LiveProgress.CUEING -> stringResource(CorusStrings.corus_live_cueing_first_song)
                LiveProgress.STARTING -> stringResource(CorusStrings.corus_live_going_live)
            }, CorusFont.songTitleLarge,
        )
        track?.let { LiveText("${it.song.name} · ${it.song.artistName}", color = CorusColors.Secondary, lines = 2) }
        if (progress == LiveProgress.TUNING) {
            LinearProgressIndicator(modifier = Modifier.width(224.dp), color = CorusColors.Accent)
        } else {
            LinearProgressIndicator(
                progress = { if (progress == LiveProgress.PREPARING) .2f else .7f },
                modifier = Modifier.width(224.dp), color = CorusColors.Accent,
            )
        }
    }
}

@Composable
private fun LiveSetupQueueEditor(
    initial: List<LiveTrack>,
    vm: LiveViewModel,
    onDone: (List<LiveTrack>) -> Unit,
    limit: Int = 50,
) {
    val queue = remember { mutableStateListOf<LiveTrack>().apply { addAll(initial.drop(1)) } }
    var first by remember { mutableStateOf(initial.firstOrNull()) }
    var pickSong by remember { mutableStateOf<Boolean?>(null) }
    var importing by remember { mutableStateOf(false) }
    val selection = listOfNotNull(first) + queue
    val commit = { onDone(listOfNotNull(first) + queue) }
    LiveSheet(
        stringResource(CorusStrings.corus_live_set_queue),
        commit,
        large = true,
        done = commit,
        sheetToolbar = true,
    ) {
        LazyColumn(
            Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(top = 36.dp),
        ) {
            item {
                LiveText(stringResource(CorusStrings.corus_live_first_song), CorusFont.username)
                Spacer(Modifier.height(16.dp))
                if (first == null) QueueAddMusic(Icons.Default.MusicNote) { pickSong = true }
                first?.let { track ->
                    var menu by remember { mutableStateOf(false) }
                    LiveTrackRow(track, onTrack = { pickSong = true }, handle = {
                        Box {
                            LiveIconButton(Icons.Default.MoreHoriz, stringResource(CorusStrings.corus_live_first_song), { menu = true })
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { LiveText(stringResource(CorusStrings.corus_live_choose_song)) }, onClick = { menu = false; pickSong = true })
                                DropdownMenuItem(text = { LiveText(stringResource(CorusStrings.concert_remove)) }, onClick = { menu = false; first = null })
                            }
                        }
                    })
                }
                HorizontalDivider(Modifier.padding(start = 56.dp), color = CorusColors.Divider)
                Spacer(Modifier.height(32.dp))
            }
            item {
                LiveText(stringResource(CorusStrings.full_player_queue_title), CorusFont.username)
                Spacer(Modifier.height(16.dp))
            }
            items(queue.size, key = { queue[it].id }) { index ->
                LiveQueueReorderRow(
                    index, queue.size, 70.dp,
                    move = { destination -> queue.add(destination, queue.removeAt(index)) },
                    modifier = Modifier.animateItem(),
                ) { handle ->
                    LiveTrackRow(
                        queue[index], Icons.Default.Close, stringResource(R.string.live_remove_queue_song),
                        action = { queue.removeAt(index) }, handle = handle, queueStyle = true,
                    )
                }
            }
            if (selection.size < limit) {
                item {
                    QueueAddMusic(Icons.Default.QueueMusic) { pickSong = false }
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = CorusColors.Divider)
                }
                item {
                    val tint = if (LocalCorusDarkTheme.current) Color(0xFF98BAFF) else Color(0xFF3A6BB9)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 18.dp).clip(CircleShape)
                            .background(CorusColors.Accent.copy(alpha = .13f)).clickable { importing = true }
                            .heightIn(min = 44.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Link, null, tint = tint, modifier = Modifier.size(18.dp))
                        LiveText("Import playlist", CorusFont.body.copy(fontWeight = FontWeight.Bold), tint)
                    }
                    HorizontalDivider(Modifier.padding(start = 144.dp), color = CorusColors.Divider)
                }
            }
            item {
                LiveText(
                    stringResource(CorusStrings.corus_live_you_can_change_your_queue_while_live),
                    CorusFont.caption, CorusColors.Secondary, Modifier.padding(vertical = 18.dp),
                )
                HorizontalDivider(color = CorusColors.Divider)
            }
        }
    }
    pickSong?.let { firstSlot ->
        LiveSongPicker(
            if (firstSlot) stringResource(CorusStrings.corus_live_choose_song) else stringResource(CorusStrings.corus_live_add_music),
            vm,
            close = { pickSong = null },
            queued = if (firstSlot) emptyList() else selection,
            canAdd = firstSlot || selection.size < limit,
        ) { track ->
            if (firstSlot) {
                queue.removeAll { it.id == track.id || (track.song.isrc != null && track.song.isrc == it.song.isrc) }
                first = track
                pickSong = null
            } else if (selection.size < limit &&
                selection.none { it.id == track.id || (track.song.isrc != null && track.song.isrc == it.song.isrc) }
            ) queue.add(track)
        }
    }
    if (importing) LivePlaylistPicker(vm, selection, limit - selection.size, { importing = false }) { tracks ->
        if (first == null && tracks.isNotEmpty()) {
            first = tracks.first()
            queue.addAll(tracks.drop(1))
        } else queue.addAll(tracks)
        importing = false
    }
}

@Composable
private fun QueueAddMusic(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 72.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(7.dp)).background(CorusColors.SheetControlBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = CorusColors.Secondary, modifier = Modifier.size(22.dp))
        }
        LiveText(stringResource(CorusStrings.corus_live_add_music), CorusFont.bodyMedium, modifier = Modifier.weight(1f))
        Icon(Icons.Default.AddCircle, null, tint = CorusColors.Accent, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun LiveTrackRow(
    track: LiveTrack,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String = "",
    action: () -> Unit = {},
    onTrack: (() -> Unit)? = null,
    handle: @Composable (() -> Unit)? = null,
    queueStyle: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (queueStyle) 70.dp else 57.dp)
            .then(if (onTrack == null) Modifier else Modifier.clickable(onClick = onTrack)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LiveArtwork(track.song.albumArtURL, 44.dp)
        Column(Modifier.weight(1f)) {
            LiveText(track.song.name, if (queueStyle) CorusFont.body else CorusFont.songTitle, lines = 1)
            LiveText(track.song.artistName, CorusFont.caption, CorusColors.Secondary, lines = 1)
        }
        if (icon != null) LiveIconButton(icon, label, action, glyphSize = if (queueStyle) 18.dp else 24.dp)
        handle?.invoke()
    }
}

@Composable
internal fun LiveSongPicker(
    title: String,
    vm: LiveViewModel,
    close: () -> Unit,
    queued: List<LiveTrack> = emptyList(),
    canAdd: Boolean = true,
    choose: (LiveTrack) -> Unit,
) {
    LiveSheet(title, close, large = true, done = close, dismissKeyboardFromHeader = true) {
        LiveSongPickerContent(vm, choose, queued = queued, canAdd = canAdd)
    }
}

internal val LiveSongPickerArtworkSize = 48.dp
internal const val LIVE_SONG_SUGGESTION_BATCH_SIZE = 4

@Composable
internal fun ColumnScope.LiveSongPickerContent(
    vm: LiveViewModel,
    choose: (LiveTrack) -> Unit,
    queued: List<LiveTrack> = emptyList(),
    canAdd: Boolean = true,
    afterSearch: @Composable (String) -> Unit = {},
    queueContent: LazyListScope.() -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<LiveTrack>>(emptyList()) }
    val state by vm.state.collectAsState()
    val suggestions = state.songSuggestions
    var recentLimit by remember(state.uid) { mutableIntStateOf(LIVE_SONG_SUGGESTION_BATCH_SIZE) }
    var savedLimit by remember(state.uid) { mutableIntStateOf(LIVE_SONG_SUGGESTION_BATCH_SIZE) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var nextOffset by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf<String?>(null) }
    var trackError by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var preview by remember { mutableStateOf<ExoPlayer?>(null) }
    val scope = rememberCoroutineScope()
    var previewJob by remember { mutableStateOf<Job?>(null) }
    fun stopPreview() {
        preview?.release()
        preview = null
        playing = null
        vm.setAuditionPlaying(false)
    }
    DisposableEffect(Unit) { onDispose { previewJob?.cancel(); stopPreview() } }
    fun audition(track: LiveTrack) {
        val wasPlaying = playing == track.id
        previewJob?.cancel()
        stopPreview()
        if (wasPlaying) return
        playing = track.id
        previewJob = scope.launch {
            try {
                val url = vm.previewUrl(track) ?: error("Preview unavailable")
                val audio = newLiveStreamPlayer(context)
                preview = audio
                audio.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED && preview === audio) stopPreview()
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        if (preview === audio) {
                            stopPreview()
                            trackError = "Couldn’t preview this song."
                        }
                    }
                })
                audio.prepareLiveStream(url)
                if (preview === audio) {
                    vm.setAuditionPlaying(true)
                    audio.play()
                    vm.event("preview_started", source = if (vm.state.value.room == null) "host_setup" else "host_room_queue")
                    delay(30_000)
                    if (preview === audio) stopPreview()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                stopPreview()
                trackError = "Couldn’t preview this song."
            }
        }
    }
    val latestChoose by rememberUpdatedState(choose)
    val latestCanAdd by rememberUpdatedState(canAdd)
    fun addTrack(track: LiveTrack) {
        if (!track.playsFromSource) {
            latestChoose(track)
            return
        }
        if (adding != null) return
        adding = track.id
        scope.launch {
            try {
                val resolved = vm.prepareQueueTrack(track)
                currentCoroutineContext().ensureActive()
                if (latestCanAdd) latestChoose(resolved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                trackError = "This track is unavailable for Live."
            } finally {
                adding = null
            }
        }
    }
    trackError?.let { message ->
        AlertDialog(
            onDismissRequest = { trackError = null },
            text = { LiveText(message) },
            confirmButton = {
                TextButton(onClick = { trackError = null }) {
                    LiveText(stringResource(android.R.string.ok))
                }
            },
        )
    }
    LaunchedEffect(vm, state.uid) { vm.preloadSongSuggestions() }
    LaunchedEffect(query, state.access?.sourceTracks) {
        tracks = emptyList()
        nextOffset = null
        failed = false
        if (query.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            delay(300)
            var page = vm.search(query.trim())
            while (page.tracks.isEmpty() && page.nextOffset != null) {
                page = vm.search(query.trim(), requireNotNull(page.nextOffset))
            }
            tracks = page.tracks
            nextOffset = page.nextOffset
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    LiveSongPickerSearch(query) { query = it }
    afterSearch(query)
    val browsing = query.isBlank()
    val sections = if (browsing) listOf(
        SongPickerSection("recent", stringResource(R.string.live_recent_coruses), suggestions?.recent.orEmpty(), recentLimit) {
            recentLimit += LIVE_SONG_SUGGESTION_BATCH_SIZE
        },
        SongPickerSection("saved", stringResource(R.string.live_recent_saves), suggestions?.saved.orEmpty(), savedLimit) {
            savedLimit += LIVE_SONG_SUGGESTION_BATCH_SIZE
        },
        SongPickerSection("trending", stringResource(CorusStrings.compose_trending_songs), suggestions?.trending.orEmpty()),
    ) else listOf(SongPickerSection("results", "", tracks))
    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).dismissKeyboardOnDownwardDrag()) {
        if (browsing) queueContent()
        if (browsing && sections.all { it.tracks.isEmpty() }) {
            item {
                when {
                    state.songSuggestionsFailed -> TextButton(onClick = { vm.preloadSongSuggestions(force = true) }) {
                        LiveText(stringResource(R.string.live_retry_loading_songs))
                    }
                    suggestions == null -> SongPickerLoadingIndicator()
                    else -> LiveText(stringResource(R.string.live_search_to_find_song), color = CorusColors.Secondary)
                }
            }
        } else if (!browsing && (loading || failed || tracks.isEmpty())) {
            item {
                if (loading) SongPickerLoadingIndicator()
                else LiveText(
                    if (failed) "Couldn’t load songs. Try again." else stringResource(CorusStrings.search_no_songs_found),
                    color = CorusColors.Secondary,
                )
            }
        }
        sections.filter { it.tracks.isNotEmpty() }.forEach { section ->
            if (section.title.isNotEmpty()) item(key = "${section.id}:heading") {
                LiveText(
                    section.title.uppercase(Locale.getDefault()), CorusFont.caption, CorusColors.Secondary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                )
            }
            val visible = section.tracks.take(section.limit)
            items(visible, key = { "${section.id}:${it.id}" }) { track ->
                val inQueue = queued.any { it.id == track.id || (track.song.isrc != null && track.song.isrc == it.song.isrc) }
                val add = if (canAdd && !inQueue && (!track.playsFromSource || adding == null)) {
                    { addTrack(track) }
                } else null
                SongPickerTrackRow(track, playing == track.id, inQueue, adding == track.id, { audition(track) }, add)
                if (track.id != visible.last().id) {
                    HorizontalDivider(Modifier.padding(start = 60.dp), color = CorusColors.Divider)
                }
            }
            val showMore = section.showMore
            if (showMore != null && visible.size < section.tracks.size) item(key = "${section.id}:more") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = showMore) {
                        LiveText(stringResource(CorusStrings.parity_see_more), CorusFont.captionMedium, CorusColors.Secondary)
                        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp), tint = CorusColors.Secondary)
                    }
                }
            }
        }
        if (!browsing) nextOffset?.let { offset ->
            item(key = "more:${query}:$offset") {
                LaunchedEffect(query, offset, state.access?.sourceTracks) {
                    try {
                        val page = vm.search(query.trim(), offset)
                        tracks = (tracks + page.tracks).distinctBy { it.id }
                        nextOffset = page.nextOffset
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        nextOffset = null
                    }
                }
            }
        }
        if (!browsing) queueContent()
    }
}

@Composable
private fun SongPickerLoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = CorusColors.Accent)
    }
}

private data class SongPickerSection(
    val id: String,
    val title: String,
    val tracks: List<LiveTrack>,
    val limit: Int = tracks.size,
    val showMore: (() -> Unit)? = null,
)

@Composable
private fun LiveSongPickerSearch(query: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val placeholder = stringResource(CorusStrings.parity_9de9debbfd35)
    BasicTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        singleLine = true,
        textStyle = CorusFont.body.copy(color = CorusColors.Text),
        cursorBrush = SolidColor(CorusColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        decorationBox = { input ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .background(CorusColors.SheetControlBackground, RoundedCornerShape(12.dp)).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = CorusColors.Secondary)
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (query.isEmpty()) LiveText(placeholder, color = CorusColors.Secondary, lines = 1)
                    input()
                }
                if (query.isNotEmpty()) {
                    LiveIconButton(
                        Icons.Default.Cancel, stringResource(CorusStrings.search_clear_aria), { onChange("") },
                        tint = CorusColors.Tertiary, glyphSize = 20.dp,
                    )
                }
            }
        },
    )
}

@Composable
private fun SongPickerTrackRow(
    track: LiveTrack,
    playing: Boolean,
    inQueue: Boolean,
    adding: Boolean,
    preview: () -> Unit,
    add: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val previewLabel = if (playing) stringResource(R.string.live_stop_song_preview)
            else stringResource(CorusStrings.corus_live_preview_song)
        Box(
            Modifier.size(LiveSongPickerArtworkSize).clip(RoundedCornerShape(7.dp))
                .clickable(onClickLabel = previewLabel, onClick = preview)
                .semantics { contentDescription = previewLabel },
            contentAlignment = Alignment.Center,
        ) {
            LiveArtwork(track.song.albumArtURL ?: track.song.albumArtLargeURL, LiveSongPickerArtworkSize, neutralPlaceholder = true)
            Box(Modifier.size(24.dp).background(Color.Black.copy(alpha = .48f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(16.dp), tint = Color.White)
            }
            when (track.song.source) {
                TrackSource.BANDCAMP -> BandcampBadgeOverlay(Modifier.align(Alignment.BottomEnd))
                TrackSource.SOUNDCLOUD -> SoundCloudBadgeOverlay(Modifier.align(Alignment.BottomEnd))
                else -> Unit
            }
        }
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).then(if (add != null) Modifier.clickable(onClick = add) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                LiveText(track.song.name, CorusFont.body, lines = 1)
                LiveText(track.song.artistName, CorusFont.caption, CorusColors.Secondary, lines = 1)
            }
            if (adding) CircularProgressIndicator(
                Modifier.padding(horizontal = 14.dp).size(20.dp), color = CorusColors.Accent, strokeWidth = 2.dp,
            ) else Icon(
                if (inQueue) Icons.Default.Check else Icons.Default.Add,
                if (inQueue) stringResource(R.string.live_song_in_queue) else stringResource(R.string.live_add_song),
                Modifier.padding(horizontal = 14.dp).size(20.dp),
                tint = if (add != null || inQueue) CorusColors.Accent else CorusColors.Tertiary,
            )
        }
    }
}

@Composable
internal fun LivePersonPicker(
    initial: List<LivePerson>,
    vm: LiveViewModel,
    close: () -> Unit,
    setup: Boolean = false,
    limit: Int = LIVE_MAX_GUEST_DJS,
    excluded: Set<String> = emptySet(),
    done: (List<LivePerson>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var expand by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf<List<LivePerson>?>(null) }
    var results by remember { mutableStateOf<List<LivePerson>>(emptyList()) }
    var loadedQuery by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(initial) }
    val searchQuery = query.trim().trim('@').trim()
    val searching = focused || query.isNotEmpty()
    fun toggle(person: LivePerson) {
        if (selection.any { it.uid == person.uid }) {
            selection = selection.filterNot { it.uid == person.uid }
        } else if (selection.size < limit) {
            selection = selection + person
        }
    }

    LaunchedEffect(vm) {
        recent = try {
            vm.recentPeople()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }
    LaunchedEffect(searchQuery) {
        loadedQuery = null
        failed = false
        if (searchQuery.isEmpty()) return@LaunchedEffect
        try {
            delay(250)
            results = vm.people(searchQuery)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            results = emptyList()
            failed = true
        }
        loadedQuery = searchQuery
    }

    LiveSheet("Invite DJs", close, large = true, sheetToolbar = true, startsHalfExpanded = true, expand = expand) {
        val sheetFocus = LocalFocusManager.current
        val sheetKeyboard = LocalSoftwareKeyboardController.current
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) expand = true
            },
            singleLine = true,
            textStyle = CorusFont.body,
            placeholder = { LiveText(stringResource(CorusStrings.nav_search), color = CorusColors.Secondary) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = CorusColors.Secondary) },
            trailingIcon = if (query.isEmpty()) null else {
                {
                    LiveIconButton(
                        icon = Icons.Default.Cancel,
                        label = stringResource(CorusStrings.search_clear_aria),
                        onClick = { query = "" },
                        tint = CorusColors.Tertiary,
                        glyphSize = 20.dp,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { sheetFocus.clearFocus() }),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CorusColors.SheetControlBackground,
                unfocusedContainerColor = CorusColors.SheetControlBackground,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
        )

        if (selection.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item { LiveText(stringResource(CorusStrings.share_recipient_to), CorusFont.caption, CorusColors.Secondary) }
                items(selection, key = { it.uid }) { person ->
                    Row(
                        Modifier.clip(CircleShape).background(CorusColors.CardBackground)
                            .clickable(onClickLabel = "Remove ${person.username}") { toggle(person) }
                            .heightIn(min = 44.dp).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiveAvatar(person, 28.dp)
                        InvitePersonName(person, compact = true, modifier = Modifier.widthIn(max = 130.dp))
                        Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = CorusColors.Secondary)
                    }
                }
            }
        }

        val people = (if (searchQuery.isEmpty()) recent.orEmpty() else results).filterNot { it.uid in excluded }
        val loading = if (searchQuery.isEmpty()) recent == null else loadedQuery != searchQuery
        if (loading || people.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.TopCenter) {
                if (loading) CircularProgressIndicator(color = CorusColors.Accent)
                else LiveText(
                    when {
                        searchQuery.isEmpty() -> "Search for someone to invite."
                        failed -> "Couldn’t search for people. Try another search."
                        else -> "No people found"
                    },
                    color = CorusColors.Secondary,
                )
            }
        } else if (searching) {
            val listState = rememberLazyListState()
            LaunchedEffect(listState, focused) {
                if (focused) {
                    snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                        if (scrolling) sheetKeyboard?.hide()
                    }
                }
            }
            LazyColumn(Modifier.weight(1f), state = listState) {
                items(people, key = { it.uid }) { person ->
                    val selected = selection.any { it.uid == person.uid }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .selectable(selected, enabled = selected || selection.size < limit) { toggle(person) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiveAvatar(person, 36.dp)
                        Column(Modifier.weight(1f)) {
                            InvitePersonName(person)
                            person.profile?.displayName?.takeIf(String::isNotBlank)?.let {
                                LiveText(it, CorusFont.caption, CorusColors.Secondary, lines = 1)
                            }
                        }
                        if (selected) Icon(Icons.Default.CheckCircle, null, tint = CorusColors.Accent)
                    }
                    HorizontalDivider(Modifier.padding(start = 48.dp), color = CorusColors.Divider)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(people, key = { it.uid }) { person ->
                    val selected = selection.any { it.uid == person.uid }
                    Column(
                        Modifier.fillMaxWidth()
                            .selectable(selected, enabled = selected || selection.size < limit) { toggle(person) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box {
                            LiveAvatar(person, 60.dp)
                            if (selected) Icon(
                                Icons.Default.CheckCircle, null,
                                Modifier.align(Alignment.BottomEnd).size(22.dp)
                                    .background(CorusColors.SheetBackground, CircleShape),
                                tint = CorusColors.Accent,
                            )
                        }
                        InvitePersonName(person, compact = true)
                    }
                }
            }
        }
        if (selection.isNotEmpty() || (setup && initial.isNotEmpty())) {
            if (setup && selection.isNotEmpty()) {
                LiveText("DJ Invites will send when you go live", CorusFont.caption, CorusColors.Secondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            LivePrimary(if (setup) stringResource(CorusStrings.corus_live_save) else "Send invite") {
                sheetFocus.clearFocus()
                done(selection)
            }
        }
    }
}

@Composable
private fun InvitePersonName(person: LivePerson, compact: Boolean = false, modifier: Modifier = Modifier) {
    val profile = person.profile
    UsernameWithFlair(
        username = person.username.removePrefix("@"),
        isVerified = profile?.isVerified == true,
        isClubMember = profile?.isClubMember == true,
        isBot = profile?.isBot == true,
        flairStyle = profile?.flairStyle ?: FlairStyle.NONE,
        style = if (compact) CorusFont.captionMedium.copy(fontWeight = FontWeight.Bold) else CorusFont.username,
        modifier = modifier,
        flairIconSize = if (compact) 10.dp else 14.dp,
    )
}

@Composable
internal fun LivePlaylistPicker(
    vm: LiveViewModel, existing: List<LiveTrack>, slots: Int, close: () -> Unit, add: (List<LiveTrack>) -> Unit,
) {
    var link by remember { mutableStateOf("") }
    val playlistId = remember(link) { spotifyPlaylistId(link) }
    var playlist by remember(playlistId) { mutableStateOf<Pair<String, List<LiveTrack>>?>(null) }
    var retry by remember(playlistId) { mutableIntStateOf(0) }
    var failed by remember(playlistId) { mutableStateOf(false) }
    var selected by remember(playlistId) { mutableStateOf<Set<String>>(emptySet()) }
    val tracks = remember(playlist, existing) {
        val ids = existing.mapTo(mutableSetOf()) { it.id }
        val isrcs = existing.mapNotNullTo(mutableSetOf()) { it.song.isrc }
        playlist?.second.orEmpty().filter { ids.add(it.id) && (it.song.isrc?.let(isrcs::add) != false) }
    }
    val limit = minOf(slots.coerceAtLeast(0), tracks.size)
    val selection = tracks.filter { it.id in selected }.take(limit)
    LiveSheet(
        stringResource(R.string.live_import_playlist), close,
        large = true, sheetToolbar = true, dismissKeyboardFromHeader = true,
    ) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(playlistId, retry, slots > 0) {
            if (playlistId == null || slots <= 0) return@LaunchedEffect
            failed = false
            delay(400)
            try {
                val result = vm.spotifyPlaylist(playlistId)
                currentCoroutineContext().ensureActive()
                focus.clearFocus()
                keyboard?.hide()
                playlist = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiveText(stringResource(R.string.live_playlist_import_instructions), color = CorusColors.Secondary)
                    LiveText(stringResource(R.string.live_playlist_public_link_hint), CorusFont.captionMedium, CorusColors.Secondary)
                    if (slots <= 0) {
                        LiveText(stringResource(R.string.live_playlist_queue_full), CorusFont.username)
                    } else {
                        val hint = stringResource(R.string.live_playlist_link)
                        BasicTextField(
                            value = link,
                            onValueChange = { link = it },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
                            textStyle = CorusFont.body.copy(color = CorusColors.Text),
                            cursorBrush = SolidColor(CorusColors.Accent),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false,
                            ),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
                            maxLines = 4,
                            decorationBox = { input ->
                                Box(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .background(CorusColors.SheetControlBackground, RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (link.isEmpty()) LiveText(hint, color = CorusColors.Secondary)
                                    input()
                                }
                            },
                        )
                    }
                }
            }
            if (slots > 0 && playlist == null) item {
                when {
                    failed -> Column {
                        LiveText(stringResource(R.string.live_playlist_load_failed), CorusFont.caption, CorusColors.Secondary)
                        TextButton(onClick = { failed = false; retry++ }) {
                            LiveText(stringResource(CorusStrings.gift_try_again), color = CorusColors.PlayerLink)
                        }
                    }
                    playlistId != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = CorusColors.Accent, strokeWidth = 2.dp)
                        LiveText(stringResource(R.string.live_playlist_loading), CorusFont.caption, CorusColors.Secondary)
                    }
                    link.isNotBlank() -> LiveText(stringResource(R.string.live_playlist_invalid_link), CorusFont.caption, CorusColors.Secondary)
                }
            }
            playlist?.let { (name, _) ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiveText(name, CorusFont.username, modifier = Modifier.weight(1f), lines = 2)
                        if (limit > 0) {
                            val full = selection.size == limit
                            TextButton(
                                onClick = { selected = if (full) emptySet() else tracks.take(limit).mapTo(mutableSetOf()) { it.id } },
                                contentPadding = PaddingValues(start = 12.dp, top = 8.dp, bottom = 8.dp),
                            ) {
                                LiveText(
                                    stringResource(if (full) R.string.live_playlist_clear_selection else R.string.live_playlist_select_all),
                                    CorusFont.captionMedium, CorusColors.PlayerLink,
                                )
                            }
                        }
                    }
                    if (tracks.isEmpty()) {
                        LiveText(stringResource(R.string.live_playlist_no_new_songs), color = CorusColors.Secondary)
                    }
                }
                items(tracks, key = { it.id }) { track ->
                    val checked = selection.any { it.id == track.id }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(checked, enabled = checked || selection.size < limit) {
                                selected = if (checked) selected - track.id else selected + track.id
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LiveArtwork(track.song.albumArtURL ?: track.song.albumArtLargeURL, 44.dp, neutralPlaceholder = true)
                        Column(Modifier.weight(1f)) {
                            LiveText(track.song.name, CorusFont.body, lines = 1)
                            LiveText(track.song.artistName, CorusFont.caption, CorusColors.Secondary, lines = 1)
                        }
                        Icon(
                            if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            null,
                            tint = if (checked) CorusColors.Accent else CorusColors.Secondary,
                        )
                    }
                }
            }
        }
        if (playlist != null && slots > 0) {
            val label = if (selection.isEmpty()) stringResource(R.string.live_playlist_add)
                else stringResource(R.string.live_playlist_add_count, selection.size)
            LivePrimary(label, enabled = selection.isNotEmpty()) { add(selection) }
        }
    }
}

private val SpotifyPlaylistIdPattern = Regex("[A-Za-z0-9]{22}")

private fun spotifyPlaylistId(link: String): String? {
    val uri = Uri.parse(link.trim())
    if (!uri.scheme.equals("https", ignoreCase = true) ||
        !uri.host.equals("open.spotify.com", ignoreCase = true)
    ) return null
    val segments = uri.pathSegments
    if (segments.getOrNull(segments.size - 2) != "playlist") return null
    return segments.lastOrNull()?.takeIf(SpotifyPlaylistIdPattern::matches)
}


@Composable
private fun LiveSetupOption(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    complete: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = CorusColors.Secondary, modifier = Modifier.size(19.dp))
        }
        LiveText(title, CorusFont.bodyMedium, modifier = Modifier.weight(1f))
        Icon(
            if (complete) Icons.Default.Check else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            if (complete) stringResource(CorusStrings.corus_live_ready) else null,
            tint = if (complete) CorusColors.Accent else CorusColors.Secondary,
            modifier = Modifier.size(16.dp),
        )
    }
}
