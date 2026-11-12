package fm.corus.android.ui.screens.live

import androidx.compose.ui.res.stringResource
import fm.corus.android.localization.CorusStrings
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.blockTouchPassthrough
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SetupPanel { QUEUE, INVITE, WELCOME, MIC, NOTIFY }

@Composable
internal fun LiveHostSetup(setup: LiveSetup, profile: CymbalUser?, vm: LiveViewModel, busy: LiveProgress?) {
    var panel by remember(setup.id) { mutableStateOf<SetupPanel?>(null) }
    val focus = LocalFocusManager.current
    BackHandler(enabled = panel == null) { if (busy == null) vm.closeSetup() }
    Box(Modifier.fillMaxSize().zIndex(10f).background(CorusColors.Background).blockTouchPassthrough()) {
        Box(
            Modifier.fillMaxSize().statusBarsPadding().padding(top = 25.dp)
                .clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp))
                .background(CorusColors.SheetBackground).navigationBarsPadding().imePadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (busy != null) {
                    LiveLoading(busy, setup.queue.firstOrNull())
                } else {
                    Spacer(Modifier.height(4.dp))
                    LiveHeader(stringResource(CorusStrings.corus_live_go_live), vm::closeSetup)
                    Column(
                        Modifier.weight(1f).widthIn(max = 480.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    ) {
                        val avatarSize = if (setup.invitees.size > 1) 48.dp else 56.dp
                        Spacer(Modifier.height(32.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LiveAvatar(LivePerson(profile?.id.orEmpty(), profile?.username.orEmpty(), profile?.avatarURL),
                                    avatarSize, ring = true)
                                LiveText(profile?.username?.let { "@${it.removePrefix("@")}" }.orEmpty(), CorusFont.usernameLarge, lines = 1)
                            }
                            setup.invitees.forEach { person ->
                                Column(
                                    Modifier.widthIn(max = 83.dp).clickable { panel = SetupPanel.INVITE },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(Modifier.padding(4.dp)) { LiveAvatar(person, avatarSize) }
                                    LiveText("@${person.username}", CorusFont.usernameLarge, lines = 1)
                                }
                            }
                            if (setup.invitees.size < LIVE_MAX_GUEST_DJS) {
                                Column(
                                    Modifier.clickable { panel = SetupPanel.INVITE },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(Modifier.padding(4.dp).size(avatarSize).clip(CircleShape).background(CorusColors.SheetControlBackground),
                                        contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Add, null, tint = CorusColors.Secondary, modifier = Modifier.size(21.dp))
                                    }
                                    LiveText("Invite DJ", CorusFont.usernameLarge, CorusColors.Secondary)
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
                                .clickable { focus.clearFocus(); panel = SetupPanel.QUEUE }.padding(16.dp),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                LiveText(stringResource(CorusStrings.corus_live_set_queue), CorusFont.username)
                                val first = setup.queue.firstOrNull()
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    LiveArtwork(first?.song?.albumArtURL, 64.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (first != null) LiveText(stringResource(CorusStrings.corus_live_first_song), CorusFont.caption, CorusColors.Secondary)
                                        LiveText(first?.song?.name ?: stringResource(CorusStrings.corus_live_pick_your_first_song), CorusFont.bodyMedium, lines = 1)
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
                        LivePrimary(stringResource(CorusStrings.corus_live_go_live), setup.queue.isNotEmpty(), vm::start)
                    }
                }
            }
        }
    }

    val closePanel = { panel = null }
    when (panel) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveText(stringResource(CorusStrings.corus_live_notify_followers), modifier = Modifier.weight(1f))
                Switch(setup.notifyFollowers, onCheckedChange = { enabled -> vm.editSetup { copy(notifyFollowers = enabled) } })
            }
            LiveText(
                if (setup.notifyFollowers) stringResource(CorusStrings.corus_live_sent_when_your_room_goes_live) else "Turn on Notify followers to send your message.",
                CorusFont.caption, CorusColors.Secondary,
            )
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(CorusColors.CardBackground).padding(14.dp)) {
                LiveText("CORUS", CorusFont.caption, CorusColors.Secondary)
                LiveText("@${profile?.username.orEmpty()} is live", CorusFont.username)
                TextField(
                    setup.notificationMessage,
                    { message -> vm.editSetup { copy(notificationMessage = limitLiveCharacters(message, 140)) } },
                    Modifier.fillMaxWidth(),
                    placeholder = { LiveText("Write a custom message", color = CorusColors.Secondary) },
                    textStyle = CorusFont.body,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CorusColors.CardBackground, unfocusedContainerColor = CorusColors.CardBackground,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
            }
            LiveText("${setup.notificationMessage.codePointCount(0, setup.notificationMessage.length)}/140",
                CorusFont.caption, CorusColors.Secondary, modifier = Modifier.align(Alignment.End))
        }
        null -> Unit
    }
}

internal const val LIVE_ROOM_TITLE_LIMIT = 32
internal const val LIVE_MAX_GUEST_DJS = 2

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
    val density = LocalDensity.current
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
            items(queue, key = { it.id }) { track ->
                var drag by remember { mutableFloatStateOf(0f) }
                LiveTrackRow(
                    track, Icons.Default.RemoveCircleOutline, "Remove song", { queue.remove(track) },
                    handle = {
                        Icon(
                            Icons.Default.DragHandle, "Reorder song", tint = CorusColors.Secondary,
                            modifier = Modifier.size(40.dp).padding(9.dp).pointerInput(track.id) {
                                val threshold = with(density) { 56.dp.toPx() }
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { drag = 0f }, onDragEnd = { drag = 0f }, onDragCancel = { drag = 0f },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        drag += delta.y
                                        val index = queue.indexOf(track)
                                        val next = if (drag > threshold) index + 1 else if (drag < -threshold) index - 1 else index
                                        if (next != index && next in queue.indices) {
                                            queue.add(next, queue.removeAt(index))
                                            drag = 0f
                                        }
                                    },
                                )
                            },
                        )
                    },
                )
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
        LiveSongPicker(if (firstSlot) stringResource(CorusStrings.corus_live_choose_song) else stringResource(CorusStrings.corus_live_add_music), vm, { pickSong = null }) { track ->
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
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 57.dp).then(if (onTrack == null) Modifier else Modifier.clickable(onClick = onTrack)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LiveArtwork(track.song.albumArtURL, 44.dp)
        Column(Modifier.weight(1f)) {
            LiveText(track.song.name, CorusFont.songTitle, lines = 1)
            LiveText(track.song.artistName, CorusFont.caption, CorusColors.Secondary, lines = 1)
        }
        if (icon != null) LiveIconButton(icon, label, action)
        handle?.invoke()
    }
}

@Composable
internal fun LiveSongPicker(title: String, vm: LiveViewModel, close: () -> Unit, choose: (LiveTrack) -> Unit) {
    LiveSheet(title, close, large = true) {
        LiveSongPickerContent(vm, choose)
    }
}

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
    var suggestions by remember { mutableStateOf(LiveSuggestions()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<String?>(null) }
    val preview = remember { mutableStateOf<MediaPlayer?>(null) }
    val scope = rememberCoroutineScope()
    var previewJob by remember { mutableStateOf<Job?>(null) }
    fun stopPreview() {
        preview.value?.release()
        preview.value = null
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
                val url = vm.previewUrl(track)
                if (url == null) {
                    playing = null
                    return@launch
                }
                val media = MediaPlayer()
                preview.value = media
                media.setDataSource(url)
                media.setOnPreparedListener {
                    if (preview.value === it) {
                        vm.event("preview_started", source = if (vm.state.value.room == null) "host_setup" else "host_room_queue")
                        vm.setAuditionPlaying(true)
                        it.start()
                    }
                }
                media.setOnCompletionListener { stopPreview() }
                media.setOnErrorListener { _, _, _ -> stopPreview(); true }
                media.prepareAsync()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                stopPreview()
            }
        }
    }
    LaunchedEffect(query) {
        tracks = emptyList()
        failed = false
        loading = true
        try {
            delay(280)
            if (query.isBlank()) suggestions = vm.suggestions() else tracks = vm.search(query.trim())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    LiveSearchField(query, stringResource(CorusStrings.parity_9de9debbfd35), showSearchIcon = true) { query = it }
    afterSearch(query)
    if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = CorusColors.Accent)
    else if (failed) LiveText("Couldn’t load songs. Try again.", color = CorusColors.Secondary)
    else if (tracks.isEmpty() && query.isNotBlank()) LiveText(stringResource(CorusStrings.search_no_songs_found), color = CorusColors.Secondary)
    val sections = if (query.isBlank()) listOf(
        "RECENT POSTS" to suggestions.recent, "SAVED SONGS" to suggestions.saved,
        stringResource(CorusStrings.compose_trending_songs) to suggestions.trending,
    ) else listOf("" to tracks)
    LazyColumn(Modifier.weight(1f, fill = false)) {
        if (query.isBlank()) queueContent()
        sections.forEach { (heading, songs) ->
            if (heading.isNotEmpty() && songs.isNotEmpty()) item(key = heading) {
                LiveText(heading, CorusFont.sectionHeader, CorusColors.Secondary)
            }
            items(songs, key = { "$heading:${it.id}" }) { track ->
                val inQueue = queued.any { it.id == track.id || (track.song.isrc != null && track.song.isrc == it.song.isrc) }
                val add = if (canAdd && !inQueue) ({ choose(track) }) else null
                LiveTrackRow(track, onTrack = add, handle = {
                    LiveIconButton(
                        if (playing == track.id) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(CorusStrings.corus_live_preview_song), { audition(track) },
                    )
                    LiveIconButton(
                        if (inQueue) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                        if (inQueue) "In queue" else "Add song", { add?.invoke() }, enabled = add != null,
                    )
                })
            }
        }
        if (query.isNotBlank()) queueContent()
    }
}

@Composable
internal fun LivePersonPicker(
    initial: List<LivePerson>,
    vm: LiveViewModel,
    close: () -> Unit,
    setup: Boolean = false,
    done: (List<LivePerson>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var people by remember { mutableStateOf<List<LivePerson>>(emptyList()) }
    var selection by remember { mutableStateOf(initial) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var expand by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        loading = true
        failed = false
        try {
            delay(250)
            people = vm.people(query)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            people = emptyList()
            failed = true
        } finally {
            loading = false
        }
    }
    LiveSheet("Invite DJs", close, large = true, sheetToolbar = true, startsHalfExpanded = initial.isEmpty(), expand = expand) {
        LiveSearchField(
            query, stringResource(CorusStrings.nav_search),
            Modifier.padding(top = 12.dp).onFocusChanged {
                if (it.isFocused) expand = true
            },
            showSearchIcon = true,
        ) { query = it }
        LazyColumn(Modifier.weight(1f)) {
            if (loading || failed || people.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(color = CorusColors.Accent)
                    else LiveText(
                        when {
                            failed -> "Couldn’t search for people. Try another search."
                            query.isBlank() -> "Search for someone to invite."
                            else -> "No people found"
                        },
                        color = CorusColors.Secondary,
                    )
                }
            }
            items((selection + people).distinctBy { it.uid }, key = { it.uid }) { person ->
                val checked = selection.any { it.uid == person.uid }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 55.dp).clickable {
                        if (checked) selection = selection.filterNot { it.uid == person.uid }
                        else if (selection.size < LIVE_MAX_GUEST_DJS) {
                            selection = selection + person
                            expand = true
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LiveAvatar(person, 36.dp)
                    LiveText("@${person.username}", CorusFont.username, modifier = Modifier.weight(1f))
                    Icon(if (checked) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline, null, tint = CorusColors.Accent)
                }
            }
        }
        if (selection.isNotEmpty() || (setup && initial.isNotEmpty())) {
            if (setup && selection.isNotEmpty()) {
                LiveText("DJ Invites will send when you go live", CorusFont.caption, CorusColors.Secondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            LivePrimary(if (setup) stringResource(CorusStrings.corus_live_save) else "Send invite") { done(selection) }
        }
    }
}

@Composable
internal fun LivePlaylistPicker(
    vm: LiveViewModel, existing: List<LiveTrack>, slots: Int, close: () -> Unit, add: (List<LiveTrack>) -> Unit,
) {
    var link by remember { mutableStateOf("") }
    var name by remember { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf<List<LiveTrack>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LiveSheet("Import playlist", close, large = true) {
        LiveText("Open a playlist in Spotify, tap Share, then Copy Link. Paste the link below.", color = CorusColors.Secondary)
        LiveSearchField(link, "Playlist link") { link = it }
        LivePrimary("Load playlist", enabled = link.isNotBlank() && !loading) {
            scope.launch {
                loading = true
                error = null
                name = null
                try {
                    val (playlistName, items) = vm.spotifyPlaylist(link)
                    name = playlistName
                    val ids = existing.mapTo(mutableSetOf()) { it.id }
                    val isrcs = existing.mapNotNullTo(mutableSetOf()) { it.song.isrc }
                    tracks = items.filter { ids.add(it.id) && (it.song.isrc?.let(isrcs::add) != false) }
                    selected = tracks.take(slots).mapTo(mutableSetOf()) { it.id }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.localizedMessage ?: "Couldn’t load this playlist. Check the link and try again."
                } finally { loading = false }
            }
        }
        if (loading) LiveText("Loading playlist…")
        error?.let { LiveText(it, color = CorusColors.Secondary) }
        name?.let { LiveText(it, CorusFont.songTitle) }
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(tracks, key = { it.id }) { track ->
                val checked = track.id in selected
                LiveTrackRow(track, if (checked) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline, "Select song", {
                    if (checked) selected = selected - track.id else if (selected.size < slots) selected = selected + track.id
                })
            }
        }
        if (name != null) LivePrimary("Add songs (${selected.size})", selected.isNotEmpty()) { add(tracks.filter { it.id in selected }) }
    }
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
