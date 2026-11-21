package fm.corus.android.ui.screens.live

import androidx.compose.ui.res.stringResource
import fm.corus.android.localization.CorusStrings
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import fm.corus.android.R
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.DiscoIntensity
import fm.corus.android.data.model.RainIntensity
import fm.corus.android.data.model.SnowIntensity
import fm.corus.android.ui.components.DiscoEffectView
import fm.corus.android.ui.components.DiscoScrim
import fm.corus.android.ui.components.RainEffectView
import fm.corus.android.ui.components.SnowEffectView
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.screens.messaging.REACTION_EMOJIS
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.MentionSuggestionsList
import fm.corus.android.ui.components.applyMention
import fm.corus.android.ui.components.buildMentionAnnotatedString
import fm.corus.android.ui.components.parseMentionQuery
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private enum class RoomPanel { HISTORY, LISTENERS, OPTIONS, INVITE, LINEUP, PICK_DJS, INFO, QUEUE, REQUEST_SONG, GIF, REPORT, END }

private data class QueuePlaySelection(val track: LiveTrack, val index: Int, val version: Long, val revision: Long)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiveRoomScreen(
    room: LiveRoom,
    state: LiveUiState,
    vm: LiveViewModel,
    openProfile: (String) -> Unit,
    openSong: (CymbalTrack) -> Unit,
    openNowPlaying: (CymbalTrack) -> Unit,
    showingNowPlaying: Boolean,
    headerAlpha: Float,
    minimizeDrag: Modifier,
) {
    var panel by remember(room.id) { mutableStateOf<RoomPanel?>(null) }
    // Resolve the host as the room appears, so opening Info reuses the ready profile.
    val infoHostProfile by produceState(room.host.profile, room.host.uid, state.hosting) {
        if (!state.hosting) {
            value = try {
                vm.miniPlayerHostProfile(room.host.uid)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                room.host.profile
            }
        }
    }
    LaunchedEffect(room.roomDescription, state.hosting) {
        if (!state.hosting && room.roomDescription.isBlank() && panel == RoomPanel.INFO) panel = null
    }
    var chatFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val hapticView = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    val composerFocus = remember { FocusRequester() }
    val keyboardVisible = WindowInsets.isImeVisible
    var keyboardWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardVisible) {
        if (keyboardWasVisible && !keyboardVisible && chatFocused) focus.clearFocus()
        keyboardWasVisible = keyboardVisible
    }
    val close = { panel = null }
    val canRequestSong = room.chatEnabled && room.allowSongRequests
    LaunchedEffect(canRequestSong) {
        if (!canRequestSong && panel == RoomPanel.REQUEST_SONG) close()
    }
    val canControl = state.hosting || state.dj != null
    LaunchedEffect(vm, state.uid, canControl) {
        if (canControl) vm.preloadSongSuggestions()
    }
    val openDJSpots = (LIVE_MAX_GUEST_DJS - room.djs.size -
        state.pendingDJs.count { it.number("expiresAt") > vm.serverTime() }).coerceAtLeast(0)
    val pager = rememberPagerState { 2 }
    var selectedMessageId by remember(room.id) { mutableStateOf<String?>(null) }
    var reactionDetails by remember(room.id) { mutableStateOf<Pair<String, String>?>(null) }
    var showingChatEmojiPicker by remember(room.id) { mutableStateOf(false) }
    fun hasMessageOptions(message: LiveMessage): Boolean =
        message.supportsReactions || state.hosting || message.author.uid != state.uid
    val selectedMessage = if (selectedMessageId == null) null else state.chatRows.find {
        it.id == selectedMessageId && hasMessageOptions(it)
    }
    LaunchedEffect(selectedMessage?.id) {
        if (selectedMessage == null) selectedMessageId = null
    }
    var selectedSong by remember { mutableStateOf<LiveTrack?>(null) }
    val visitSong: (LiveTrack) -> Unit = { track ->
        vm.minimize()
        openSong(track.song.withLiveDisplayArtwork())
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var draft by remember(room.id) { mutableStateOf(TextFieldValue()) }
    var editingMessage by remember(room.id) { mutableStateOf<LiveMessage?>(null) }
    var draftBeforeEdit by remember(room.id) { mutableStateOf(TextFieldValue()) }
    var savingMessageEdit by remember(room.id) { mutableStateOf(false) }
    var messageToDelete by remember(room.id) { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    fun finishEditingMessage() {
        focus.clearFocus()
        keyboard?.hide()
        editingMessage = null
        draft = draftBeforeEdit
        draftBeforeEdit = TextFieldValue()
    }
    LaunchedEffect(room.chatEnabled, savingMessageEdit) {
        if (!room.chatEnabled && editingMessage != null && !savingMessageEdit) finishEditingMessage()
    }
    val mentionPrefix = if (chatFocused && room.chatEnabled && !state.minimized && draft.selection.collapsed) {
        parseMentionQuery(draft.text, draft.selection.start)?.takeIf { liveMentionHandle.matches(it) }
    } else null
    var mentionSuggestions by remember(room.id, mentionPrefix) { mutableStateOf(emptyList<CymbalUser>()) }
    LaunchedEffect(room.id, state.uid, mentionPrefix) {
        if (mentionPrefix == null) return@LaunchedEffect
        delay(200)
        while (isActive) {
            mentionSuggestions = try {
                vm.chatMentions(room.id, mentionPrefix).map { person ->
                    CymbalUser(person.text("id"), person.text("username"), person.text("displayName"),
                        avatarURL = person["avatarURL"] as? String)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            delay(8_000)
        }
    }
    var gif by remember { mutableStateOf<String?>(null) }
    var focusAfterMessageOptions by remember(room.id) { mutableStateOf(false) }
    LaunchedEffect(selectedMessageId, focusAfterMessageOptions) {
        if (selectedMessageId == null && focusAfterMessageOptions) {
            focusAfterMessageOptions = false
            if (room.chatEnabled && (!canControl || pager.currentPage == 1)) {
                composerFocus.requestFocus()
                keyboard?.show()
            }
        }
    }
    LaunchedEffect(room.chatEnabled) {
        if (!room.chatEnabled) {
            if (chatFocused) focus.clearFocus()
            if (panel == RoomPanel.GIF) close()
        }
    }
    var onMicrophoneGranted by remember(room.id) { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = onMicrophoneGranted
        onMicrophoneGranted = null
        if (granted) action?.invoke()
    }
    val withMicrophonePermission: (() -> Unit) -> Unit = { action ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else if (onMicrophoneGranted == null) {
            onMicrophoneGranted = action
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val micClick: () -> Unit = {
        if (state.micEnabled) vm.toggleMicrophone() else withMicrophonePermission(vm::toggleMicrophone)
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
        LiveRoomEffects(visibleEffect)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .widthIn(max = 650.dp).align(Alignment.TopCenter).padding(start = 16.dp, end = 16.dp, top = 4.dp),
        ) {
            Column(Modifier.fillMaxWidth().then(minimizeDrag)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).alpha(headerAlpha),
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
                        if (room.welcomeUrl != null) {
                            var moreExpanded by remember(room.id) { mutableStateOf(false) }
                            Box {
                                RoomIconButton(Icons.Default.MoreHoriz, stringResource(R.string.full_player_cd_more), { moreExpanded = true })
                                DropdownMenu(moreExpanded, { moreExpanded = false }, containerColor = CorusColors.CardBackground) {
                                    DropdownMenuItem(
                                        text = { LiveText(stringResource(CorusStrings.corus_live_replay_welcome_message)) },
                                        leadingIcon = { Icon(Icons.Default.Replay, contentDescription = null) },
                                        enabled = !state.muted && !state.welcomePlaying,
                                        onClick = {
                                            moreExpanded = false
                                            vm.replayWelcome()
                                        },
                                    )
                                }
                            }
                        }
                        if (state.dj != null) {
                            RoomExitButton(stringResource(CorusStrings.corus_live_step_down)) { vm.action("stopDJing") }
                        } else RoomIconButton(Icons.Default.Close, stringResource(CorusStrings.corus_live_leave_room), vm::leave)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val audibleDJs = liveAudibleDJs(room, state)
                AnimatedVisibility(!chatFocused) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoomLineup(room, state.hosting, state.voiceLevels, state.unmutedMicrophoneIDs, visitProfile) {
                            show(RoomPanel.LINEUP)
                        }
                        if (state.welcomePlaying) {
                            RoomWelcomeMessageCard(room, state.welcomeProgress)
                        } else {
                            RoomPlayerCard(
                                room, state, vm, audibleDJs,
                                { openNowPlaying(it.song.withLiveDisplayArtwork()) },
                                { show(RoomPanel.HISTORY) },
                            )
                        }
                    }
                }
                if (chatFocused && room.voiceMode == "talk") {
                    val micOn = liveMicrophoneIsOn(state)
                    val speaker = audibleDJs.firstOrNull()
                    val meter = if (state.micEnabled) state.voiceLevels[state.uid] ?: 0f
                        else speaker?.let { state.voiceLevels[it.uid] }
                    if (meter != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LiveText(
                                stringResource(when {
                                    state.muted -> R.string.live_audio_muted
                                    state.voiceFailed -> R.string.live_couldnt_connect_to_live_voice
                                    micOn -> CorusStrings.corus_live_mic_is_on
                                    state.micEnabled -> R.string.live_connecting_microphone
                                    else -> R.string.live_listening_live
                                }),
                                CorusFont.captionMedium,
                            )
                            LiveVoiceMeter(meter)
                        }
                    }
                }
                RoomStatus(state.listenerCount, room.collaborative,
                    { show(RoomPanel.LISTENERS) }, { show(RoomPanel.LINEUP) },
                    if (state.hosting || room.roomDescription.isNotBlank()) ({ show(RoomPanel.INFO) }) else null)
            }
            val chatRows = remember(state.messages, state.djRequests, state.hosting, state.pendingLikeNotice, room.revision, room.chatEnabled) {
                state.chatRows
            }
            val chat: @Composable ColumnScope.() -> Unit = {
                val retryAt = state.myDJRequest.number("declinedAt").takeIf {
                    it > 0 && state.myDJRequest.text("status") == "declined"
                }?.plus(60_000)
                var coolingDown by remember(room.id, retryAt) {
                    mutableStateOf(retryAt != null && vm.serverTime() < retryAt)
                }
                LaunchedEffect(room.id, retryAt) {
                    if (retryAt != null) delay((retryAt - vm.serverTime()).coerceAtLeast(0))
                    coolingDown = false
                }
                val now = vm.serverTime()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize()) {
                        RoomChat(chatRows, state, vm, openDJSpots, visitProfile, { message, showEmojiPicker ->
                            if (hasMessageOptions(message)) {
                                focus.clearFocus()
                                keyboard?.hide()
                                showingChatEmojiPicker = showEmojiPicker
                                selectedMessageId = message.id
                            }
                        }, Modifier.weight(1f), inspectReaction = { message, emoji ->
                            hapticView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            focus.clearFocus()
                            keyboard?.hide()
                            reactionDetails = message.id to emoji
                        }, dismissKeyboardOnScroll = chatFocused && keyboardVisible)
                        if (!state.hosting && state.dj == null &&
                            state.invites.none { it.text("roomId") == room.id && it.number("expiresAt") > vm.serverTime() }
                        ) {
                            DJRequestStatus(state, room.chatEnabled && coolingDown) {
                                vm.requestAction("cancelDJRequest", mapOf("requestCreatedAt" to state.myDJRequest.number("createdAt")))
                            }
                        }
                        if (editingMessage != null && room.chatEnabled) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(CorusColors.CardBackground).padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LiveText(stringResource(CorusStrings.messaging_thread_editing), CorusFont.caption,
                                    CorusColors.Secondary, Modifier.weight(1f))
                                IconButton(onClick = ::finishEditingMessage, enabled = !savingMessageEdit, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Default.Close, stringResource(CorusStrings.comments_cd_cancel_edit),
                                        tint = CorusColors.Secondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    MentionSuggestionsList(
                        users = mentionSuggestions,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth().background(CorusColors.Background),
                        onSelect = { user ->
                            val caret = draft.selection.start
                            if (parseMentionQuery(draft.text, caret) != mentionPrefix) return@MentionSuggestionsList
                            val suffix = draft.text.drop(caret).takeWhile { liveMentionHandle.matches(it.toString()) }
                            val completed = applyMention(draft.copy(selection = TextRange(caret + suffix.length)), user.username)
                            if (completed.text.length <= 500) draft = completed
                        },
                    )
                }
                ChatComposer(
                    draft, { draft = it.copy(text = it.text.take(500)) }, gif, { gif = null },
                    {
                        val editing = editingMessage
                        if (editing == null) {
                            val sentText = draft.text
                            val sentGif = gif
                            vm.send(sentText, sentGif) {
                                if (editingMessage == null) {
                                    if (draft.text == sentText) draft = TextFieldValue()
                                } else if (draftBeforeEdit.text == sentText) {
                                    draftBeforeEdit = TextFieldValue()
                                }
                                if (gif == sentGif) gif = null
                            }
                        } else if (!savingMessageEdit) {
                            val text = draft.text.trim()
                            savingMessageEdit = true
                            scope.launch {
                                try {
                                    if (vm.editMessage(room.id, editing.id, text)) finishEditingMessage()
                                } finally {
                                    savingMessageEdit = false
                                }
                            }
                        }
                    }, vm::heart,
                    state.hasLikedCurrentSong, { chatFocused = it },
                    showHeart = if (state.hosting) room.canLikeFromMiniPlayer(state.uid, now)
                        else state.audioReady && room.miniPlayerTrack(now) != null,
                    chatEnabled = room.chatEnabled,
                    composerFocus = composerFocus,
                    originalText = editingMessage?.text,
                    savingEdit = savingMessageEdit,
                    sending = room.id in state.pendingChatSends,
                ) {
                    ChatActions(room, state, vm, coolingDown, { show(RoomPanel.REQUEST_SONG) }, { show(RoomPanel.GIF) })
                }
            }
            if (canControl) {
                val latestChat = chatRows.lastOrNull {
                    it.author.uid != state.uid && it.kind != "listenerLeft" && !it.isSongLikeNotice
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
                                room, state, vm, micClick, withMicrophonePermission, { show(RoomPanel.QUEUE) },
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
        val hostControlsVisible = canControl && pager.currentPage == 0
        LiveHeartBurst(
            state.incomingHearts,
            Modifier.align(Alignment.BottomStart).navigationBarsPadding()
                .padding(start = if (hostControlsVisible) 16.dp else 36.dp, bottom = if (hostControlsVisible) 24.dp else 72.dp),
        )
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
                        stringResource(R.string.live_save_song), { vm.save(track) }, onTrack = { close(); selectedSong = track },
                        showLikes = true,
                    )
                }
            }
        }
        RoomPanel.LISTENERS -> LiveListenersSheet(vm, room, state.listenerCount, state.uid, close, visitProfile)
        RoomPanel.OPTIONS -> LiveSheet(stringResource(CorusStrings.corus_live_room_options), close) {
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button) { panel = RoomPanel.INFO }
                        .heightIn(min = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LiveText(stringResource(R.string.live_room_info), CorusFont.username)
                        LiveText(stringResource(R.string.live_room_info_settings_detail), CorusFont.caption, CorusColors.Secondary, lines = 1)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        tint = CorusColors.Secondary, modifier = Modifier.size(20.dp))
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
                val canSetRequestPermissions = state.hosting && room.collaborative &&
                    (state.pendingChatEnabled ?: room.chatEnabled)
                if (state.hosting) {
                    HorizontalDivider(Modifier.padding(top = 12.dp), color = CorusColors.Divider)
                }
                if (state.hosting) {
                    if (state.access?.privateRooms == true || room.isPrivate) {
                        val label = stringResource(R.string.live_private_room)
                        var savingPrivacy by remember(room.id) { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LiveText(label, CorusFont.username)
                                LiveText(stringResource(R.string.live_private_room_description), CorusFont.caption, CorusColors.Secondary)
                            }
                            Switch(
                                checked = room.isPrivate,
                                enabled = !savingPrivacy,
                                modifier = Modifier.semantics { contentDescription = label },
                                onCheckedChange = { value ->
                                    savingPrivacy = true
                                    vm.setRoomPrivacy(room.id, value) { savingPrivacy = false }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = CorusColors.Accent),
                            )
                        }
                    }
                    RoomSwitch(
                        stringResource(R.string.live_enable_chat),
                        state.pendingChatEnabled ?: room.chatEnabled,
                        interactive = state.pendingChatEnabled == null,
                        onChange = vm::setChatEnabled,
                    )
                }
                if (canSetRequestPermissions) {
                    RoomSwitch(stringResource(R.string.live_allow_dj_requests), room.allowDJRequests) {
                        vm.action("setRequestPermission", mapOf("type" to "dj", "allowed" to it))
                    }
                    RoomSwitch(stringResource(R.string.live_allow_song_requests), room.allowSongRequests) {
                        vm.action("setRequestPermission", mapOf("type" to "song", "allowed" to it))
                    }
                }
            }
        }
        RoomPanel.INFO -> LiveRoomInfoSheet(
            room.customTitle, room.roomDescription, room.title, state.hosting,
            onDismiss = close,
            onSave = { title, description -> vm.updateRoomInfo(room.id, title, description) },
            hostContent = if (state.hosting) null else ({
                LiveRoomInfoHost(room.host, infoHostProfile) {
                    close()
                    visitProfile(room.host.uid)
                }
            }),
        )
        RoomPanel.INVITE -> LiveSheet(stringResource(CorusStrings.native_1fd9ae1607aa), close) {
            if (openDJSpots > 0) LiveRow(stringResource(R.string.live_invite_dj), Icons.Default.PersonAdd) { panel = RoomPanel.PICK_DJS }
            LiveRow(stringResource(CorusStrings.corus_live_share_room), Icons.Default.IosShare) { close(); share() }
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
        )
        RoomPanel.QUEUE -> RoomQueueEditor(room, state, vm, close)
        RoomPanel.REQUEST_SONG -> if (canRequestSong) {
            val request = state.mySongRequest
            val track = request["track"].liveFields()?.let(LiveTrack::parse)
            if (track != null && request.text("status") == "pending") {
                LiveSheet(stringResource(R.string.live_song_request_sent), close) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LiveText(track.song.name, CorusFont.bodyMedium)
                        LiveText(track.song.artistName, CorusFont.caption, CorusColors.Secondary)
                        TextButton(
                            enabled = !state.requestBusy,
                            onClick = {
                                vm.requestAction("cancelSongRequest", mapOf("requestMessageId" to request.text("chatMessageId")))
                            },
                        ) {
                            LiveText(stringResource(R.string.live_cancel_song_request), color = CorusColors.Error)
                        }
                    }
                }
            } else {
                LiveSongPicker(stringResource(R.string.live_request_song), vm, close, canAdd = !state.requestBusy) {
                    vm.requestAction("requestSong", mapOf("track" to it.fields()))
                    close()
                }
            }
        }
        RoomPanel.GIF -> if (room.chatEnabled) LiveGifPicker(vm, close) { url -> gif = url; close() }
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
            LiveSheet(stringResource(R.string.live_end_room_title), close) {
                Text(
                    stringResource(R.string.live_end_room_message),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                )
                room.djs.firstOrNull { it.text("uid") == candidates.firstOrNull() }?.let { dj ->
                    LiveRow(stringResource(CorusStrings.corus_live_step_down), Icons.Default.SwitchAccount, LivePerson.parse(dj).username) {
                        vm.action("stepDown", mapOf("targetUid" to dj.text("uid"))) { close() }
                    }
                }
                LivePrimary(stringResource(CorusStrings.corus_live_end_live), destructive = true) {
                    close()
                    vm.end()
                }
                TextButton(onClick = close, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    LiveText(
                        stringResource(R.string.live_keep_hosting),
                        CorusFont.bodyMedium.copy(textAlign = TextAlign.Center),
                    )
                }
            }
        }
        null -> Unit
    }
    state.membership["hostHandoff"].liveFields()?.let { offer ->
        val decline = { vm.action("declineHost", mapOf("offerId" to offer.text("id"))); Unit }
        LiveSheet(stringResource(R.string.live_take_over_as_host), decline) {
            LiveText(stringResource(R.string.live_handoff_invite, "@${room.host.username}"))
            LivePrimary(stringResource(R.string.live_request_accept)) { vm.action("acceptHost", mapOf("offerId" to offer.text("id"))) }
            TextButton(onClick = decline) { LiveText(stringResource(R.string.live_decline_talk_request)) }
        }
    }
    selectedMessage?.let { message ->
        val dismissOptions = { selectedMessageId = null }
        val isMine = message.author.uid == state.uid
        val pickingEmoji = showingChatEmojiPicker && message.supportsReactions && room.chatEnabled
        LiveSheet(
            title = if (message.supportsReactions) null else stringResource(R.string.live_message_options),
            close = dismissOptions,
            large = pickingEmoji,
            startsHalfExpanded = pickingEmoji,
            horizontalContentPadding = if (message.supportsReactions && !pickingEmoji) 4.dp else 20.dp,
        ) {
            if (pickingEmoji) {
                val selected = state.reactionEmojis(message).filterTo(mutableSetOf()) { emoji ->
                    val uid = state.uid
                    uid != null && uid in state.reactionUsers(message, emoji)
                }
                val pending = state.pendingChatReactions.keys.filter { it.first == message.id }.mapTo(mutableSetOf()) { it.second }
                LiveEmojiPicker(selected, pending) { emoji ->
                    reactToChatMessage(vm, hapticView, message.id, emoji)
                    dismissOptions()
                }
            } else Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (message.supportsReactions && room.chatEnabled) {
                    BoxWithConstraints(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val spacing = ((maxWidth - 44.dp * (REACTION_EMOJIS.size + 1)) / REACTION_EMOJIS.size)
                            .coerceIn(0.dp, 4.dp)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                        ) {
                            REACTION_EMOJIS.forEach { emoji ->
                                ChatReactionButton(message, emoji, state, showsCount = false) {
                                    reactToChatMessage(vm, hapticView, message.id, emoji)
                                    dismissOptions()
                                }
                            }
                            IconButton(
                                onClick = { showingChatEmojiPicker = true },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(
                                    Modifier.size(38.dp).background(CorusColors.Secondary.copy(alpha = .08f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.AddReaction, stringResource(R.string.notifications_row_reactions),
                                        tint = CorusColors.Secondary, modifier = Modifier.size(21.dp))
                                }
                            }
                        }
                    }
                }
                Column(
                    Modifier.padding(horizontal = if (message.supportsReactions) 16.dp else 0.dp),
                ) {
                    if (isMine && message.supportsReactions && message.text.isNotBlank() &&
                        room.chatEnabled && !savingMessageEdit) {
                        LiveChatAction(stringResource(CorusStrings.live_edit_message), Icons.Outlined.Edit) {
                            if (editingMessage == null) draftBeforeEdit = draft
                            editingMessage = message
                            draft = TextFieldValue(message.text, TextRange(message.text.length))
                            focusAfterMessageOptions = true
                            dismissOptions()
                        }
                    }
                    val canReply = room.chatEnabled && !isMine && editingMessage == null
                    if (canReply) {
                        LiveChatAction(stringResource(R.string.comments_reply), Icons.AutoMirrored.Outlined.Reply) {
                            draft = liveChatReplyDraft(draft, message.author.username)
                            focusAfterMessageOptions = true
                            dismissOptions()
                        }
                    }
                    if (message.text.isNotBlank()) {
                        LiveChatAction(stringResource(R.string.live_copy_text), Icons.Outlined.ContentCopy) {
                            clipboard.setText(AnnotatedString(message.text))
                            dismissOptions()
                        }
                    }
                    if ((message.text.isNotBlank() || canReply) && (!isMine || message.supportsReactions)) {
                        HorizontalDivider(Modifier.padding(vertical = 3.dp), thickness = Dp.Hairline,
                            color = CorusColors.Text.copy(alpha = .12f))
                    }
                    if (isMine && message.supportsReactions) {
                        LiveChatAction(stringResource(R.string.live_delete_message), Icons.Outlined.Delete, destructive = true) {
                            messageToDelete = message.id
                            dismissOptions()
                        }
                    }
                    if (state.hosting && !isMine) {
                        LiveChatAction(stringResource(R.string.live_delete_message), Icons.Outlined.Delete, destructive = true) {
                            vm.action("removeMessage", mapOf("messageId" to message.id))
                            dismissOptions()
                        }
                        LiveChatAction(stringResource(R.string.live_kick_user_from_room), Icons.Outlined.PersonRemove, destructive = true) {
                            vm.action("removeListener", mapOf("messageId" to message.id))
                            dismissOptions()
                        }
                    } else if (!isMine) {
                        LiveChatAction(stringResource(CorusStrings.corus_live_report_message), Icons.Outlined.Flag, destructive = true) {
                            vm.action("report", mapOf("messageId" to message.id))
                            dismissOptions()
                        }
                    }
                }
            }
        }
    }
    messageToDelete?.let { messageId ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text(stringResource(CorusStrings.live_confirm_delete_message)) },
            text = { Text(stringResource(CorusStrings.live_delete_message_confirmation_body)) },
            confirmButton = {
                TextButton(onClick = {
                    messageToDelete = null
                    vm.action("deleteOwnMessage", mapOf("messageId" to messageId))
                }) { Text(stringResource(R.string.comments_menu_delete), color = CorusColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    reactionDetails?.let { (messageId, emoji) ->
        val message = state.chatRows.firstOrNull { it.id == messageId }
        val groups = message?.let { row ->
            orderedChatReactionEmojis(state, row).associateWith { state.reactionUsers(row, it).distinct() }
        }.orEmpty()
        if (groups.isEmpty()) {
            LaunchedEffect(messageId) { reactionDetails = null }
        } else {
            LiveChatReactionDetails(groups, emoji, vm::chatReactionPeople) { reactionDetails = null }
        }
    }
    selectedSong?.let { track ->
        LiveSheet(track.song.name, { selectedSong = null }) {
            LiveTrackRow(track)
            LiveRow(stringResource(CorusStrings.native_84b771e51b43), Icons.Default.MusicNote) {
                selectedSong = null
                visitSong(track)
            }
            LiveRow(stringResource(R.string.live_save_song), Icons.Outlined.BookmarkBorder) { vm.save(track); selectedSong = null }
        }
    }
    HostUnavailableSongChoice(
        state = state,
        showRequest = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && panel == null &&
            selectedMessage == null && reactionDetails == null && selectedSong == null && !showingNowPlaying,
        onSkip = vm::skip,
    )
    HostTalkRequest(
        room = room,
        vm = vm,
        showRequest = state.unavailableHostSongRevision == null && state.hosting && !state.minimized && lifecycle.isAtLeast(Lifecycle.State.RESUMED) &&
            panel == null && selectedMessage == null && reactionDetails == null && selectedSong == null && !showingNowPlaying,
        onApproved = { if (!state.micEnabled) micClick() },
    )
}

@Composable
internal fun HostUnavailableSongChoice(state: LiveUiState, showRequest: Boolean, onSkip: (String, Long) -> Unit) {
    val room = state.room ?: return
    var dismissed by remember(room.id) { mutableStateOf(emptySet<Long>()) }
    val revision = state.unavailableHostSongRevision
    if (!showRequest || revision == null || revision in dismissed) return
    fun dismiss() { dismissed = dismissed + revision }
    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text(stringResource(CorusStrings.live_not_available_on_spotify)) },
        text = { Text(room.current?.song?.name.orEmpty() + "\n\n" + stringResource(CorusStrings.live_apple_music_listeners_may_hear)) },
        confirmButton = {
            TextButton(onClick = { dismiss(); onSkip(room.id, revision) }, enabled = state.canSkipTrack(room)) {
                Text(stringResource(CorusStrings.corus_live_skip_song))
            }
        },
        dismissButton = {
            TextButton(onClick = ::dismiss) { Text(stringResource(CorusStrings.live_continue_anyway)) }
        },
    )
}

@Composable
private fun HostTalkRequest(
    room: LiveRoom,
    vm: LiveViewModel,
    showRequest: Boolean,
    onApproved: () -> Unit,
) {
    var handledRequests by remember(room.id) { mutableStateOf(emptySet<String>()) }
    var resolving by remember(room.id) { mutableStateOf(false) }
    val latestRequests by rememberUpdatedState(room.talkRequests)
    if (!showRequest || room.voiceMode == "talk" || room.status == "ended" || room.talkLimitReached(vm.serverTime())) return
    val request = room.talkRequests
        .filter { request ->
            val id = request.text("id")
            id.isNotBlank() && id !in handledRequests && room.djs.any { it.text("uid") == request.text("uid") }
        }
        .minByOrNull { it.number("createdAt") } ?: return
    val dj = LivePerson.parse(room.djs.first { it.text("uid") == request.text("uid") })
    val handle = "@${dj.username.trimStart('@')}"

    fun resolve(approve: Boolean) {
        if (resolving) return
        resolving = true
        val requestId = request.text("id")
        vm.resolveTalkRequest(
            requestId,
            approve,
            onResolved = { approved ->
                if (approved) {
                    handledRequests = handledRequests + latestRequests.map { it.text("id") }
                    onApproved()
                } else {
                    handledRequests = handledRequests + requestId
                }
            },
            onFinished = { resolving = false },
        )
    }

    LiveSheet(
        title = stringResource(R.string.live_talk_request),
        close = { resolve(false) },
        dismissEnabled = false,
    ) {
        LiveAvatar(dj, 48.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        val message = stringResource(R.string.live_talk_request_message, handle)
        Text(
            buildAnnotatedString {
                append(message)
                val handleStart = message.indexOf(handle)
                if (handleStart >= 0) {
                    addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold), handleStart, handleStart + handle.length)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            style = CorusFont.body,
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
        )
        LivePrimary(stringResource(R.string.live_switch_to_talk), enabled = !resolving && room.pendingSkip == null) {
            resolve(true)
        }
        TextButton(
            onClick = { resolve(false) },
            enabled = !resolving,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            LiveText(stringResource(R.string.live_decline_talk_request), color = CorusColors.Secondary)
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
private fun RoomIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    LiveIconButton(icon, label, onClick, tint = CorusColors.Text, touchSize = 48.dp, glyphSize = 20.dp)
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
    unmutedMicrophoneIDs: Set<String>,
    openProfile: (String) -> Unit,
    openLineup: () -> Unit,
) {
    val hasFourDJs = room.djs.size >= LIVE_MAX_GUEST_DJS
    val playingDJUid = room.playingDJUid
    val nextDJUid = room.nextDJUid
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (hasFourDJs) 8.dp else 16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        val slot = if (hasFourDJs) Modifier.weight(1f) else Modifier.widthIn(max = 100.dp)
        (listOf(room.host) + room.djs.map(LivePerson::parse)).forEach { person ->
            val isHost = person.uid == room.host.uid
            val talking = room.voiceMode == "talk"
            val playing = playingDJUid == person.uid
            val showLiveBadge = playing || (isHost && !talking && room.djs.isEmpty())
            val showNextBadge = !showLiveBadge && nextDJUid == person.uid
            val micUnmuted = talking && person.uid in unmutedMicrophoneIDs
            val openPerson = if (isHost) ({ openProfile(person.uid) }) else openLineup
            Column(
                slot.clickable(enabled = !isHost || !hosting, onClick = openPerson),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    DJPlaybackWaves(playing, Modifier.matchParentSize())
                    LiveAvatar(
                        person, 48.dp,
                        ring = talking || showLiveBadge || showNextBadge,
                        ringWidth = 2.5.dp,
                        ringColor = if (showLiveBadge || micUnmuted) CorusColors.Accent else CorusColors.Secondary.copy(alpha = .55f),
                    )
                    if (showLiveBadge || showNextBadge) {
                        LiveText(
                            if (showLiveBadge) stringResource(CorusStrings.corus_live_live).uppercase() else stringResource(R.string.live_next_up).uppercase(),
                            CorusFont.captionMedium.copy(fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = .3.sp),
                            Color.White,
                            Modifier.align(Alignment.BottomCenter).offset(y = 4.dp)
                                .wrapContentWidth(unbounded = true).widthIn(max = 58.dp)
                                .background(if (showLiveBadge) Color(0xFF4774CA) else Color(0xFF60626A), CircleShape)
                                .border(1.dp, CorusColors.Background, CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            lines = 1,
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
private fun DJPlaybackWaves(playing: Boolean, modifier: Modifier) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val reduceMotion = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    val accent = CorusColors.Accent
    val fadeDuration = if (reduceMotion) 0 else 250
    AnimatedVisibility(
        visible = playing,
        modifier = modifier,
        enter = fadeIn(tween(fadeDuration)),
        exit = fadeOut(tween(fadeDuration)),
    ) {
        if (reduceMotion || !lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(accent.copy(alpha = .65f), radius = size.minDimension / 2 + 2.25.dp.toPx(),
                    style = Stroke(1.5.dp.toPx()))
            }
        } else {
            val phase by rememberInfiniteTransition(label = "DJ playback waves").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing)),
                label = "Wave phase",
            )
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 1.5.dp.toPx()
                repeat(2) { index ->
                    val wavePhase = (phase + index * .5f) % 1f
                    val fade = sin(PI * wavePhase).toFloat()
                    val scale = 1.03f + wavePhase * .32f
                    drawCircle(
                        color = accent.copy(alpha = .58f * fade * fade),
                        radius = (size.minDimension - stroke) / 2 * scale,
                        style = Stroke(stroke * scale),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomWelcomeMessageCard(room: LiveRoom, progress: Float?) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(CorusColors.CardBackground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveAvatar(room.host, 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LiveText(stringResource(CorusStrings.corus_live_welcome_message), CorusFont.caption, CorusColors.Secondary)
                val handle = room.hostHandle
                val message = if (handle == null) stringResource(R.string.live_welcome_message_from_host)
                    else stringResource(R.string.live_welcome_message_from_user, handle)
                LiveText(message, CorusFont.username)
            }
        }
        if (progress == null) {
            CircularProgressIndicator(Modifier.size(18.dp), color = CorusColors.Accent, strokeWidth = 2.dp)
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = CorusColors.Accent,
                trackColor = CorusColors.Secondary.copy(alpha = .25f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

private fun liveMicrophoneIsOn(state: LiveUiState): Boolean =
    (state.hosting || state.dj != null) && state.micEnabled && state.voiceConnected &&
        !state.muted && !state.voiceFailed && state.uid in state.unmutedMicrophoneIDs

private fun liveAudibleDJs(room: LiveRoom, state: LiveUiState): List<LivePerson> {
    if (state.muted || room.voiceMode != "talk") return emptyList()
    return (listOf(room.host) + room.djs.map(LivePerson::parse)).filter {
        it.uid != state.uid && (state.voiceLevels[it.uid] ?: 0f) > 0f
    }
}

@Composable
private fun RoomPlayerCard(
    room: LiveRoom, state: LiveUiState, vm: LiveViewModel, audibleDJs: List<LivePerson>,
    song: (LiveTrack) -> Unit, history: () -> Unit,
) {
    val context = LocalContext.current
    val hostWaitStartsAt = room.hostWaitStartsAt
    var waitingForHost by remember(room.id, hostWaitStartsAt) {
        mutableStateOf(hostWaitStartsAt != null && vm.serverTime() >= hostWaitStartsAt)
    }
    LaunchedEffect(room.id, hostWaitStartsAt) {
        if (hostWaitStartsAt != null) {
            delay((hostWaitStartsAt - vm.serverTime()).coerceAtLeast(0))
            waitingForHost = true
        }
    }
    val track = room.current
    val showingTalk = room.voiceMode == "talk" && room.pendingSkip == null
    val songUnavailable = state.isSpotifySongUnavailable
    if (!state.hosting && !waitingForHost && !showingTalk && track != null && room.status != "waiting" &&
        !songUnavailable && (state.connectingProvider != null || (!state.audioReady && state.audioError != null))
    ) {
        RoomConnectMusicCard(state, vm)
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(CorusColors.CardBackground).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            waitingForHost -> RoomWaitingTrack(room, vm, waitingForHost = true, countdown = false)
            showingTalk -> {
                val canUseMic = state.hosting || state.dj != null
                // micEnabled is the requested state; LiveKit confirms publication separately.
                val micOn = liveMicrophoneIsOn(state)
                val micMuted = canUseMic && !state.micEnabled
                val speakerHandle = (audibleDJs.singleOrNull()?.uid ?: room.talkSpeakerUid
                    ?.takeIf { it in state.unmutedMicrophoneIDs }
                    ?: state.lastTalkSpeakerUid?.takeIf { state.holdingTalkPresentation })
                    ?.takeUnless { it == state.uid }?.let(room::djHandle)
                Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (micMuted) Icons.Default.MicOff else Icons.Default.Mic, null, tint = CorusColors.Accent)
                    LiveText(
                        when {
                            state.muted -> stringResource(R.string.live_audio_muted)
                            state.voiceFailed -> stringResource(R.string.live_couldnt_connect_to_live_voice)
                            micOn -> stringResource(CorusStrings.corus_live_mic_is_on)
                            canUseMic && state.micEnabled -> stringResource(R.string.live_connecting_microphone)
                            state.voiceReconnecting || (!state.voiceConnected && !state.hasReceivedRemoteMicrophone) ||
                                (!canUseMic && state.waitingForRemoteMicrophone) -> stringResource(R.string.live_connecting_audio)
                            audibleDJs.size > 1 -> stringResource(R.string.live_djs_talking)
                            speakerHandle != null -> stringResource(R.string.live_dj_is_talking, speakerHandle)
                            micMuted -> stringResource(R.string.live_mic_is_muted)
                            else -> stringResource(CorusStrings.corus_live_talk_mode)
                        },
                        CorusFont.songTitle, modifier = Modifier.weight(1f),
                    )
                    if (state.hosting) room.talkDeadline?.let { TalkCountdown(it, vm) }
                }
            }
            track == null || room.status == "waiting" -> {
                RoomWaitingTrack(room, vm, waitingForHost = false, countdown = state.hosting)
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
                                    room.pendingSkip != null -> stringResource(R.string.live_switching_songs)
                                    songUnavailable -> stringResource(CorusStrings.live_not_available_on_spotify)
                                    !state.hosting && !state.audioReady -> stringResource(CorusStrings.corus_live_tuning_in)
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
                        }
                    }
                    if (state.access?.saves == true) RoomIconButton(
                        if (LiveViewModel.saveId(track) in state.savedIds) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        stringResource(R.string.live_save_song), { vm.save(track) },
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
            if (track != null && !waitingForHost) RoomIconButton(
                if (state.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                if (state.muted) stringResource(CorusStrings.corus_live_unmute_audio) else stringResource(CorusStrings.corus_live_mute_audio), vm::toggleMute,
            )
        }
        state.audioError?.let { LiveText(it, CorusFont.caption, CorusColors.Secondary) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomConnectMusicCard(state: LiveUiState, vm: LiveViewModel) {
    val appleAvailable = fm.corus.android.domain.AppleMusicPlayback.isAvailable()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(CorusColors.CardBackground).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LiveText(stringResource(R.string.live_connect_a_streaming_provider),
                CorusFont.songTitle.copy(textAlign = TextAlign.Center), modifier = Modifier.semantics { heading() })
            LiveText(stringResource(R.string.live_requires_apple_music_or_spotify_premium),
                CorusFont.caption.copy(textAlign = TextAlign.Center), CorusColors.Secondary)
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (appleAvailable) {
                LiveProviderButton("Apple Music", R.drawable.apple_music_logo, Modifier.weight(1f),
                    enabled = state.connectingProvider == null && !state.muted,
                    busy = state.connectingProvider == MusicService.APPLE_MUSIC,
                ) { vm.connectListenerProvider(MusicService.APPLE_MUSIC) }
            }
            LiveProviderButton("Spotify", R.drawable.spotify_logo, Modifier.weight(1f),
                enabled = state.connectingProvider == null && !state.muted,
                busy = state.connectingProvider == MusicService.SPOTIFY,
            ) { vm.connectListenerProvider(MusicService.SPOTIFY) }
        }
        if (state.muted) RoomIconButton(Icons.Default.VolumeOff,
            stringResource(CorusStrings.corus_live_unmute_audio), vm::toggleMute)
        val message = if (state.connectingProvider != null) stringResource(R.string.live_connecting_your_player)
            else state.audioError
        message?.let { LiveText(it, CorusFont.caption.copy(textAlign = TextAlign.Center), CorusColors.Secondary) }
    }
}

@Composable
private fun RoomWaitingTrack(
    room: LiveRoom, vm: LiveViewModel, waitingForHost: Boolean, countdown: Boolean,
) {
    val accent = if (LocalCorusDarkTheme.current) Color(0xFF98BAFF) else Color(0xFF3A6BB9)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(CorusColors.Accent.copy(alpha = .16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            LiveText(
                stringResource(if (waitingForHost) R.string.live_waiting_for_host
                    else CorusStrings.corus_live_waiting_for_the_next_song),
                CorusFont.bodyMedium,
            )
        }
        if (countdown) {
            val seconds by produceState(
                ((room.deadline - vm.serverTime()).coerceAtLeast(0L) + 999L) / 1_000L,
                room.id, room.deadline,
            ) {
                do {
                    val remaining = (room.deadline - vm.serverTime()).coerceAtLeast(0L)
                    value = (remaining + 999L) / 1_000L
                    if (remaining == 0L) break
                    delay((remaining - 1) % 1_000L + 1)
                } while (true)
            }
            LiveText(
                stringResource(R.string.live_seconds_to_add_song, seconds),
                CorusFont.bodyMedium.copy(fontFeatureSettings = "tnum"), accent,
            )
            LiveText(stringResource(R.string.live_room_timer_ends_room), CorusFont.caption, CorusColors.Secondary)
        }
    }
}

@Composable
internal fun LiveVoiceMeter(level: Float) {
    val activeBars = liveVoiceMeterBars(level)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(LiveVoiceMeterSegmentCount) { index ->
            Box(Modifier.width(5.dp).height(6.dp).clip(CircleShape).background(
                if (index < activeBars) CorusColors.Accent else CorusColors.Secondary.copy(alpha = .20f),
            ))
        }
    }
}

@Composable
private fun RoomStatus(count: Int, collaboration: Boolean, listeners: () -> Unit, lineup: () -> Unit, info: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = CorusColors.Secondary.copy(alpha = .25f))
        LivePill(count.toString(), listeners, showPeople = true)
        if (collaboration) LivePill(stringResource(R.string.live_djs), lineup)
        if (info != null) LivePill(stringResource(R.string.live_room_info_button), info)
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
            if (showPeople) Icon(Icons.Default.People, stringResource(R.string.corus_live_listeners), tint = CorusColors.Secondary, modifier = Modifier.size(14.dp))
            LiveText(text, CorusFont.captionMedium, CorusColors.Secondary)
        }
    }
}

@Composable
private fun TalkCountdown(deadline: Long, vm: LiveViewModel) {
    val seconds by produceState(301L, deadline) {
        delay((deadline - vm.serverTime() - 300_000).coerceAtLeast(0))
        do {
            val remaining = (deadline - vm.serverTime()).coerceAtLeast(0)
            value = (remaining + 999) / 1_000
            if (remaining > 0) delay((remaining % 1_000).takeIf { it > 0 } ?: 1_000)
        } while (remaining > 0)
    }
    if (seconds <= 300) {
        val duration = String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
        LiveText(stringResource(CorusStrings.live_talk_time_left, duration), CorusFont.captionMedium, CorusColors.Accent)
    }
}

@Composable
private fun HostTalkModeControl(
    room: LiveRoom, state: LiveUiState, vm: LiveViewModel, withMicrophonePermission: (() -> Unit) -> Unit,
    talkDisabled: Boolean,
) {
    val context = LocalContext.current
    val talking = room.voiceMode == "talk"
    val queued = state.showsTalkQueued(room)
    val offersTiming = !talking && room.voiceEnabled && room.status == "playing" && room.pausedAt == null
    val busy = room.pendingSkip != null || state.talkScheduleChange != null || state.hostTalkStarting
    var menuOpen by remember(room.id, room.revision, offersTiming) { mutableStateOf(false) }
    val talkNow = { withMicrophonePermission { vm.startHostTalk(room.id, room.revision) } }
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(CorusColors.Secondary.copy(alpha = .10f)).padding(2.dp)) {
        Box(Modifier.weight(1f)) {
            val label = if (queued) stringResource(CorusStrings.live_talk_queued) else stringResource(CorusStrings.corus_live_talk)
            LiveAudioModeButton(
                label, if (queued) Icons.Default.Schedule else Icons.Default.Mic,
                talking, !busy && !talking && room.voiceEnabled && (!talkDisabled || (queued && offersTiming)),
                showMenu = offersTiming,
            ) {
                if (offersTiming) menuOpen = true else talkNow()
            }
            DropdownMenu(
                expanded = menuOpen && offersTiming && !busy,
                onDismissRequest = { menuOpen = false },
                containerColor = CorusColors.CardBackground,
            ) {
                DropdownMenuItem(
                    text = { LiveText(stringResource(CorusStrings.live_talk_now)) },
                    leadingIcon = { Icon(Icons.Default.Mic, null) },
                    enabled = !talkDisabled,
                    onClick = { menuOpen = false; talkNow() },
                )
                DropdownMenuItem(
                    text = {
                        LiveText(stringResource(if (queued) CorusStrings.live_cancel_scheduled_talk else CorusStrings.live_talk_after_this_song))
                    },
                    leadingIcon = { Icon(Icons.Default.Schedule, null) },
                    enabled = !talkDisabled || queued,
                    onClick = {
                        menuOpen = false
                        vm.setTalkAfterSong(room.id, room.revision, enabled = !queued) {
                            Toast.makeText(context, CorusStrings.live_talk_starts_after_song, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            LiveAudioModeButton(
                stringResource(CorusStrings.search_tab_music), Icons.Default.MusicNote,
                !talking, !busy && talking, onClick = vm::resumeHostMusic,
            )
        }
    }
}

@Composable
private fun LiveAudioModeButton(
    label: String, icon: ImageVector, selected: Boolean, enabled: Boolean,
    showMenu: Boolean = false, onClick: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).clip(CircleShape)
            .background(if (selected) CorusColors.Accent else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (selected) Color.White else CorusColors.Text.copy(alpha = .76f)
        Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            LiveText(label, CorusFont.bodyMedium, tint, lines = 1)
            if (showMenu) Icon(Icons.Default.KeyboardArrowDown, null, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostControls(
    room: LiveRoom, state: LiveUiState, vm: LiveViewModel, micClick: () -> Unit,
    withMicrophonePermission: (() -> Unit) -> Unit, queueEditor: () -> Unit,
    chat: () -> Unit, unreadChat: LiveMessage?, modifier: Modifier = Modifier,
) {
    var playNext by remember(room.id) { mutableStateOf<QueuePlaySelection?>(null) }
    val guest = state.dj
    val serverQueue = guest?.rows("queue")?.mapNotNull(LiveTrack::parse) ?: room.queue
    val version = guest?.number("version") ?: room.queueVersion
    val pendingOrder = remember(room.id, guest?.text("uid"), version) { mutableStateOf<List<LiveTrack>?>(null) }
    val queue = pendingOrder.value ?: serverQueue
    val dragPreview = remember(pendingOrder) { mutableStateOf<LiveQueueDrag?>(null) }
    val queueScroll = rememberLazyListState()
    val editingEnabled = room.pendingSkip == null && !state.queueUpdating
    val canPlay = state.hosting && room.djs.isEmpty() && room.pausedAt == null &&
        !room.talkAfterSongArmed && state.talkScheduleChange == null
    val resetAt = room.talkDailyResetAt
    val dailyLimitReached by produceState(resetAt != null && resetAt > vm.serverTime(), resetAt) {
        value = resetAt != null && resetAt > vm.serverTime()
        if (value && resetAt != null) {
            delay((resetAt - vm.serverTime()).coerceAtLeast(0))
            value = false
        }
    }
    val talkDisabled = room.talkRoomLimitReached || dailyLimitReached
    val canSkip = state.canSkipTrack(room, queue) && !(state.hosting && room.talkAfterSongArmed && talkDisabled)
    val talkQueued = state.showsTalkQueued(room)
    val nextUp = if (talkQueued) stringResource(CorusStrings.corus_live_talk) else room.nextDJUid?.let { uid ->
        if (uid == room.host.uid) room.hostHandle
        else room.djs.firstOrNull { it.text("uid") == uid }?.let(LivePerson::parse)?.username
            ?.trim()?.trimStart('@')?.takeIf { it.isNotEmpty() }?.let { "@$it" }
    }
    val accentText = if (LocalCorusDarkTheme.current) Color(0xFF98BAFF) else Color(0xFF3A6BB9)
    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.hosting) {
            HostTalkModeControl(room, state, vm, withMicrophonePermission, talkDisabled)
        } else if (room.voiceEnabled && room.voiceMode != "talk") {
            val requested = room.talkRequests.any { it.text("uid") == state.uid }
            RoomVoiceButton(
                stringResource(if (requested) R.string.live_cancel_talk_request else R.string.live_request_to_talk),
                Icons.Default.Mic,
                enabled = room.pendingSkip == null && (!talkDisabled || requested),
            ) {
                vm.action(if (requested) "cancelTalkRequest" else "requestTalk")
            }
        }
        if (talkDisabled && room.voiceMode != "talk") {
            val message = if (room.talkRoomLimitReached) {
                stringResource(CorusStrings.live_talk_room_limit)
            } else {
                val format = android.text.format.DateFormat.getTimeFormat(LocalContext.current)
                stringResource(CorusStrings.live_talk_daily_limit, format.format(Date(requireNotNull(resetAt))))
            }
            LiveText(message, CorusFont.caption, CorusColors.Secondary)
        }
        if (room.voiceMode == "talk") {
            RoomVoiceButton(
                stringResource(if (state.micEnabled) CorusStrings.other_profile_menu_mute else CorusStrings.common_unmute),
                if (state.micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                onClick = micClick,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LiveText(
                    if (guest != null || room.djs.isNotEmpty()) stringResource(R.string.live_my_queue) else stringResource(CorusStrings.full_player_queue_title),
                    CorusFont.username,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                if (nextUp != null) {
                    val label = stringResource(R.string.live_next_up)
                    val handleColor = CorusColors.Text
                    Text(
                        buildAnnotatedString {
                            append(label)
                            append(" · ")
                            val handleStart = length
                            append(nextUp)
                            addStyle(SpanStyle(color = handleColor, fontWeight = FontWeight.SemiBold), handleStart, length)
                        },
                        modifier = Modifier.align(Alignment.CenterVertically).padding(start = 8.dp),
                        color = CorusColors.Secondary,
                        style = CorusFont.caption,
                    )
                }
            }
            if (queue.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    Icon(Icons.Default.QueueMusic, null, tint = CorusColors.Secondary, modifier = Modifier.size(25.dp))
                    if (state.hosting && room.current != null && room.djs.none { it.rows("queue").isNotEmpty() }) {
                        LiveText(stringResource(R.string.live_your_queue_is_empty), CorusFont.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text(stringResource(R.string.live_add_music_to_keep_the_room_going), style = CorusFont.caption, color = CorusColors.Secondary, textAlign = TextAlign.Center)
                    } else {
                        Text(stringResource(R.string.live_add_songs_to_your_queue), style = CorusFont.body, color = CorusColors.Secondary, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = queueScroll) {
                    val keys = liveQueueRowKeys(queue)
                    items(queue.size, key = { keys[it] }) { index ->
                        val track = queue[index]
                        HostQueueRow(
                            track, index, queue.size, editingEnabled, dragPreview,
                            scrollState = queueScroll,
                            onPlay = if (canPlay) { { playNext = QueuePlaySelection(track, index, version, room.revision) } } else null,
                            remove = {
                                vm.updateQueue(queue.toMutableList().apply { removeAt(index) }, version, state.hosting)
                            },
                            move = { destination ->
                                val reordered = queue.toMutableList().apply { add(destination, removeAt(index)) }
                                pendingOrder.value = reordered
                                if (!vm.updateQueue(reordered, version, state.hosting) { pendingOrder.value = null }) {
                                    pendingOrder.value = null
                                } else if (destination == 0) {
                                    queueScroll.requestScrollToItem(0)
                                } else {
                                    // Keep the same viewport when the row carrying the scroll anchor moves.
                                    queueScroll.requestScrollToItem(
                                        queueScroll.firstVisibleItemIndex, queueScroll.firstVisibleItemScrollOffset,
                                    )
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
                        if (state.hosting && talkQueued) stringResource(CorusStrings.live_skip_to_talk)
                        else stringResource(CorusStrings.corus_live_skip_song),
                        Icons.Default.SkipNext, Modifier.weight(1f),
                        CorusColors.Text, CorusColors.Secondary.copy(alpha = .13f), canSkip,
                    ) {
                        val skip = { vm.skip(room.id, room.revision) }
                        if (state.hosting && room.talkAfterSongArmed) withMicrophonePermission(skip) else skip()
                    }
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
                    join != null -> stringResource(join.joinNoticeStringRes, "@${join.author.username.removePrefix("@")}")
                    unreadChat != null -> stringResource(CorusStrings.new_message_action)
                    else -> stringResource(R.string.live_swipe_left_for_chat)
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
private fun RoomVoiceButton(title: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    val accent = CorusColors.Accent
    val tint = accent.copy(alpha = if (enabled) 1f else .4f)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(CircleShape)
            .background(accent.copy(alpha = .12f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        LiveText(title, CorusFont.bodyMedium, tint)
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
        if (hosting) stringResource(CorusStrings.full_player_queue_title) else stringResource(R.string.live_my_queue),
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
            prepareBeforeChoosing = false,
            afterSearch = { query ->
                if (query.isBlank() && queue.size < limit) {
                    LiveRow(stringResource(R.string.live_import_playlist), Icons.Default.Link) { importing = true }
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
                    LiveText(stringResource(R.string.live_add_songs_to_your_queue), color = CorusColors.Secondary, modifier = Modifier.padding(vertical = 16.dp))
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
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
        LiveText(title, CorusFont.captionMedium.copy(fontSize = 13.sp), contentColor)
    }
}

@Composable
private fun HostQueueRow(
    track: LiveTrack, index: Int, count: Int, enabled: Boolean,
    dragPreview: MutableState<LiveQueueDrag?>, onPlay: (() -> Unit)?, remove: () -> Unit, move: (Int) -> Unit,
    scrollState: LazyListState? = null,
) {
    LiveQueueReorderRow(index, count, 60.dp, move, enabled = enabled, dragPreview = dragPreview, scrollState = scrollState) { handle ->
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

private val LiveMessage.joinNoticeStringRes: Int
    get() = if (fields["rejoined"] == true) CorusStrings.corus_live_listener_rejoined else R.string.live_listener_joined

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun RoomChat(
    rows: List<LiveMessage>, state: LiveUiState, vm: LiveViewModel, openDJSpots: Int,
    openProfile: (String) -> Unit, options: (LiveMessage, Boolean) -> Unit, modifier: Modifier,
    inspectReaction: ((LiveMessage, String) -> Unit)? = null,
    dismissKeyboardOnScroll: Boolean = false,
) {
    val list = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val hapticView = LocalView.current
    val openOptions by rememberUpdatedState(options)
    val canReact = state.room?.chatEnabled == true
    val mentionColor = CorusColors.Accent
    val reactionsLabel = stringResource(R.string.notifications_row_reactions)
    val likeLabel = stringResource(R.string.comments_cd_like)
    LaunchedEffect(rows.lastOrNull()?.id, rows.lastOrNull { it.supportsReactions }?.id) {
        if (rows.isNotEmpty()) list.scrollToItem(0)
    }
    val chatModifier = modifier.fillMaxWidth().then(
        if (dismissKeyboardOnScroll) Modifier.imeNestedScroll() else Modifier,
    )
    if (rows.isEmpty()) {
        val emptyChatModifier = if (dismissKeyboardOnScroll) {
            chatModifier.scrollable(rememberScrollableState { 0f }, Orientation.Vertical)
        } else chatModifier
        Column(emptyChatModifier, verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ChatBubbleOutline, null, tint = CorusColors.Secondary, modifier = Modifier.size(22.dp))
            LiveText(stringResource(CorusStrings.corus_live_be_the_first_to_comment), CorusFont.body, CorusColors.Secondary)
        }
    } else LazyColumn(
        chatModifier, state = list, reverseLayout = true,
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
    ) {
        items(rows.asReversed(), key = { it.id }) { message ->
            if (message.kind == "listenerJoined" || message.kind == "roomTitleChanged" || message.isSongLikeNotice) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        if (message.isSongLikeNotice) 6.dp else 8.dp, Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when {
                            message.isSongLikeNotice -> Icons.Default.Favorite
                            message.kind == "roomTitleChanged" -> Icons.Default.Edit
                            else -> Icons.Default.PersonAdd
                        },
                        null, tint = CorusColors.Secondary, modifier = Modifier.size(14.dp),
                    )
                    if (message.isSongLikeNotice || message.kind == "listenerJoined") {
                        val handle = if (message.isSongLikeNotice) {
                            message.likeNoticeHandle
                        } else {
                            "@${message.author.username.removePrefix("@")}"
                        }
                        val notice = if (message.isSongLikeNotice) {
                            message.likeNoticeText { liker ->
                                if (liker == null) stringResource(R.string.live_song_liked)
                                else stringResource(R.string.live_user_liked_song, liker)
                            }
                        } else {
                            stringResource(message.joinNoticeStringRes, handle.orEmpty())
                        }
                        Text(
                            buildAnnotatedString {
                                append(notice)
                                if (handle != null) {
                                    val start = notice.indexOf(handle)
                                    if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold), start, start + handle.length)
                                }
                            },
                            style = CorusFont.caption,
                            textAlign = TextAlign.Center,
                            color = CorusColors.Secondary,
                        )
                    } else {
                        val title = message.fields.text("roomTitle")
                        val notice = if (title.isBlank()) stringResource(R.string.live_room_title_cleared)
                            else stringResource(R.string.live_room_title_changed, title)
                        LiveText(notice, CorusFont.caption.copy(textAlign = TextAlign.Center), CorusColors.Secondary)
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().then(
                        if (message.isPrivateDJRequest || message.supportsReactions) Modifier
                        else Modifier.combinedClickable(onClick = {}, onLongClick = { options(message, false) }),
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.clickable { openProfile(message.author.uid) }) { LiveAvatar(message.author, 28.dp) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        LiveText(message.author.username, CorusFont.username, modifier = Modifier.clickable { openProfile(message.author.uid) })
                        if (message.kind == "songRequest" || message.kind == "djRequest") {
                            ChatRequestCard(message, state, vm, openDJSpots)
                        } else {
                            val opensOptions = message.supportsReactions
                            Column(
                                Modifier.fillMaxWidth().then(if (!opensOptions) Modifier else Modifier
                                    .pointerInput(message.id, canReact, haptics, hapticView) {
                                        detectTapGestures(
                                            onDoubleTap = if (canReact) {
                                                { reactToChatMessage(vm, hapticView, message.id, "❤️", addOnly = true) }
                                            } else null,
                                            onLongPress = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                openOptions(message, false)
                                            },
                                        )
                                    }
                                    .semantics(mergeDescendants = true) {
                                        onClick(label = reactionsLabel) { openOptions(message, false); true }
                                        if (canReact) {
                                            customActions = listOf(CustomAccessibilityAction(likeLabel) {
                                                reactToChatMessage(vm, hapticView, message.id, "❤️", addOnly = true)
                                                true
                                            })
                                        }
                                    }),
                            ) {
                                if (message.text.isNotBlank()) {
                                    val emojiOnly = remember(message.text, message.gifUrl) {
                                        message.gifUrl == null && liveEmojiPattern.replace(message.text, "").isBlank()
                                    }
                                    if (emojiOnly) {
                                        Text(message.text.trim(), fontSize = 32.sp, lineHeight = 38.sp)
                                    } else {
                                        val text = remember(message.text, mentionColor) {
                                            buildMentionAnnotatedString(
                                                message.text,
                                                linkColor = Color.Unspecified,
                                                mentionColor = mentionColor,
                                            )
                                        }
                                        Text(text, style = CorusFont.body, color = CorusColors.Text)
                                    }
                                }
                                if (message.isEdited) {
                                    LiveText(stringResource(CorusStrings.comment_edited_badge), CorusFont.caption, CorusColors.Secondary)
                                }
                                message.gifUrl?.let {
                                    AsyncImage(it, stringResource(CorusStrings.comments_cd_gif), Modifier.width(170.dp).heightIn(max = 170.dp).clip(RoundedCornerShape(9.dp)))
                                }
                            }
                            val emojis = orderedChatReactionEmojis(state, message)
                            if (emojis.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    emojis.forEach { emoji ->
                                        ChatReactionButton(
                                            message, emoji, state, showsCount = true,
                                            inspect = inspectReaction?.let { action -> { action(message, emoji) } },
                                        ) {
                                            reactToChatMessage(vm, hapticView, message.id, emoji)
                                        }
                                    }
                                    if (canReact) {
                                        LiveIconButton(
                                            Icons.Default.AddReaction, reactionsLabel, { openOptions(message, true) },
                                            touchSize = 44.dp, glyphSize = 18.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Android's ICU regex engine supplies the Unicode emoji properties.
private val liveEmojiPattern by lazy {
    val glyph = """(?:[#*0-9]\x{FE0F}?\x{20E3}|(?:\p{Emoji_Presentation}|(?![#*0-9])\p{Emoji}(?=\x{FE0F}|\p{Emoji_Modifier}))\x{FE0F}?\p{Emoji_Modifier}?(?:[\x{E0020}-\x{E007E}]+\x{E007F})?)"""
    Regex("""$glyph(?:\x{200D}$glyph)*""")
}

private fun orderedChatReactionEmojis(state: LiveUiState, message: LiveMessage): List<String> {
    val used = state.reactionEmojis(message).filter { state.reactionUsers(message, it).isNotEmpty() }
    return REACTION_EMOJIS.filter { it in used } + used.filterNot { it in REACTION_EMOJIS }.sorted()
}

private fun reactToChatMessage(vm: LiveViewModel, view: View, messageId: String, emoji: String, addOnly: Boolean = false) {
    if (vm.setChatReaction(messageId, emoji, addOnly)) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

@Composable
private fun LiveChatAction(label: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) CorusColors.Error else CorusColors.Text
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(label, color = color, style = CorusFont.body.copy(fontSize = 14.sp))
    }
}

@Composable
private fun ChatReactionButton(
    message: LiveMessage, emoji: String, state: LiveUiState, showsCount: Boolean,
    inspect: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val users = state.reactionUsers(message, emoji)
    val reacted = state.uid != null && state.uid in users
    val accentText = if (LocalCorusDarkTheme.current) Color(0xFF98BAFF) else Color(0xFF3A6BB9)
    val tint = if (reacted) accentText else CorusColors.Secondary
    val enabled = state.room?.chatEnabled == true && state.uid != null
    val reactionsLabel = stringResource(R.string.notifications_row_reactions)
    val currentInspect by rememberUpdatedState(inspect)
    val currentClick by rememberUpdatedState(onClick)
    val interaction = if (inspect == null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier.pointerInput(message.id, emoji, enabled) {
            detectTapGestures(
                onTap = if (enabled) ({ currentClick() }) else null,
                onLongPress = { currentInspect?.invoke() },
            )
        }.semantics(mergeDescendants = true) {
            role = Role.Button
            onClick(action = {
                if (enabled) currentClick() else currentInspect?.invoke()
                true
            })
            onLongClick(label = reactionsLabel) {
                currentInspect?.invoke()
                true
            }
        }
    }
    Box(
        Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clip(CircleShape)
            .then(interaction)
            .semantics { selected = reacted },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.background(tint.copy(alpha = if (reacted) .15f else .08f), CircleShape)
                .then(if (reacted) Modifier.border(1.dp, tint, CircleShape) else Modifier)
                .padding(horizontal = 8.dp, vertical = if (showsCount) 4.dp else 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = if (showsCount) 16.sp else 22.sp)
            if (showsCount) {
                LiveText(
                    java.text.NumberFormat.getIntegerInstance().format(users.size),
                    CorusFont.caption.copy(fontWeight = FontWeight.SemiBold), tint,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatRequestCard(message: LiveMessage, state: LiveUiState, vm: LiveViewModel, openDJSpots: Int) {
    val pending = message.requestStatus == "pending"
    val djRequest = message.kind == "djRequest"
    val track = message.requestedTrack
    val status = when (message.requestStatus) {
        "accepted" -> R.string.live_request_accepted
        "declined" -> R.string.live_request_declined
        "queued" -> R.string.live_request_queued
        "cancelled" -> R.string.live_request_cancelled
        "dismissed" -> R.string.live_request_dismissed
        else -> null
    }
    Column(
        Modifier.background(CorusColors.CardBackground, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!djRequest && track != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LiveArtwork(track.song.albumArtURL, 36.dp, neutralPlaceholder = true)
                Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LiveText(track.song.name, CorusFont.bodyMedium, lines = 1)
                    LiveText(track.song.artistName, CorusFont.caption, CorusColors.Secondary, lines = 1)
                    status?.let { LiveText(stringResource(it), CorusFont.caption, CorusColors.Secondary) }
                }
                if (pending && (state.hosting || state.dj != null)) {
                    ChatRequestButton(stringResource(R.string.post_menu_add_to_queue), primary = true, enabled = !state.requestBusy) {
                        vm.requestAction("queueSongRequest", message.requestTarget())
                    }
                }
            }
        } else {
            val accent = if (LocalCorusDarkTheme.current) Color(0xFF98BAFF) else Color(0xFF3A6BB9)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.align(Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (djRequest) Icons.Default.PersonAdd else Icons.Default.MusicNote, null,
                        tint = accent, modifier = Modifier.size(14.dp))
                    LiveText(
                        stringResource(if (djRequest) R.string.live_requested_to_dj else R.string.live_song_request),
                        CorusFont.captionMedium, accent,
                    )
                }
                if (pending && djRequest && state.hosting) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ChatRequestButton(
                            stringResource(R.string.live_request_accept), primary = true,
                            enabled = !state.requestBusy && openDJSpots > 0,
                        ) { vm.requestAction("approveDJRequest", message.requestTarget()) }
                        ChatRequestButton(
                            stringResource(R.string.live_request_decline), enabled = !state.requestBusy,
                        ) { vm.requestAction("declineDJRequest", message.requestTarget()) }
                    }
                } else if (pending && !djRequest && (state.hosting || state.dj != null)) {
                    ChatRequestButton(stringResource(R.string.post_menu_add_to_queue), primary = true, enabled = !state.requestBusy) {
                        vm.requestAction("queueSongRequest", message.requestTarget())
                    }
                }
            }
            if (!djRequest && message.text.isNotBlank()) LiveText(message.text, CorusFont.caption)
            status?.let { LiveText(stringResource(it), CorusFont.caption, CorusColors.Secondary) }
        }
    }
}

@Composable
private fun ChatRequestButton(
    title: String, enabled: Boolean, primary: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else .5f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.heightIn(min = 32.dp)
                .background(if (primary) CorusColors.Accent else CorusColors.SheetControlBackground, CircleShape)
                .then(if (primary) Modifier else Modifier.border(1.dp, CorusColors.Secondary, CircleShape))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiveText(title, CorusFont.captionMedium, if (primary) Color.White else CorusColors.Text)
        }
    }
}

private val liveMentionHandle = Regex("[a-zA-Z0-9_.]{1,32}")

internal fun liveChatReplyDraft(draft: TextFieldValue, username: String): TextFieldValue {
    val mention = "@${username.trim().trim('@')} "
    val reply = if (draft.text.startsWith(mention)) draft.text else (mention + draft.text).take(500)
    return TextFieldValue(reply, selection = TextRange(mention.length.coerceAtMost(reply.length)))
}

@Composable
private fun ChatComposer(
    draft: TextFieldValue, onText: (TextFieldValue) -> Unit, gif: String?, clearGif: () -> Unit, send: () -> Unit,
    heart: () -> Unit, liked: Boolean, focus: (Boolean) -> Unit, showHeart: Boolean,
    chatEnabled: Boolean, composerFocus: FocusRequester,
    originalText: String?, savingEdit: Boolean, sending: Boolean,
    actions: @Composable () -> Unit,
) {
    val text = draft.text
    val editing = originalText != null
    if (chatEnabled && !editing && gif != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(gif, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            LiveIconButton(Icons.Default.Close, stringResource(CorusStrings.corus_live_remove_gif), clearGif)
        }
    }
    val canSend = !savingEdit && if (editing) text.isNotBlank() && text.trim() != originalText
        else !sending && (text.isNotBlank() || gif != null)
    val cursorTint = if (LocalCorusDarkTheme.current) Color(0xFF98BAFF) else Color(0xFF315EA8)
    var wrappedLines by remember { mutableIntStateOf(1) }
    val multiline = '\n' in text || wrappedLines > 1
    val composerShape = RoundedCornerShape(if (multiline) 18.dp else 22.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showHeart && !editing) LiveLikeButton(liked, glyphSize = 25.dp, onLike = heart)
        Row(
            Modifier.weight(1f).heightIn(min = 44.dp).clip(composerShape)
                .background(CorusColors.CardBackground.copy(alpha = if (chatEnabled) .6f else 1f))
                .border(1.dp, CorusColors.Secondary.copy(alpha = .22f), composerShape)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (chatEnabled) {
                BasicTextField(
                    draft,
                    onText,
                    Modifier.weight(1f).padding(vertical = 8.dp).padding(end = if (editing) 12.dp else 0.dp)
                        .focusRequester(composerFocus).onFocusChanged { focus(it.isFocused) },
                    readOnly = savingEdit,
                    textStyle = CorusFont.body.copy(color = CorusColors.Text),
                    cursorBrush = SolidColor(cursorTint),
                    singleLine = false,
                    minLines = 1,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    onTextLayout = { wrappedLines = it.lineCount },
                    decorationBox = { input ->
                        Box {
                            if (text.isEmpty()) {
                                LiveText(
                                    stringResource(if (editing) CorusStrings.live_edit_message_placeholder else CorusStrings.corus_live_say_something),
                                    color = CorusColors.Text.copy(alpha = .75f),
                                )
                            }
                            input()
                        }
                    },
                )
            } else {
                LiveText(
                    stringResource(R.string.live_chat_disabled),
                    color = CorusColors.Secondary,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 4.dp),
                )
            }
            if (!editing) actions()
        }
        if (chatEnabled) {
            IconButton(onClick = send, enabled = canSend, modifier = Modifier.size(44.dp)) {
                Box(
                    Modifier.size(26.dp).background(
                        if (canSend) CorusColors.Accent else CorusColors.Tertiary.copy(alpha = .2f), CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.ArrowUpward, stringResource(if (editing) R.string.common_save else CorusStrings.thread_send_aria),
                        tint = if (canSend) Color.White else CorusColors.Secondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatActions(
    room: LiveRoom,
    state: LiveUiState,
    vm: LiveViewModel,
    djRequestCoolingDown: Boolean,
    requestSong: () -> Unit,
    pickGif: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val now = vm.serverTime()
    val invited = state.invites.any { it.text("roomId") == room.id && it.number("expiresAt") > now }
    val requested = state.myDJRequest.text("status") == "pending"
    val lineupFull = room.djs.size + state.pendingDJs.count { it.number("expiresAt") > now } >= LIVE_MAX_GUEST_DJS
    val pendingSong = state.mySongRequest.text("status") == "pending"
    val canManageRequests = room.collaborative && !state.hosting && state.dj == null
    val hasRequestToCancel = canManageRequests && (pendingSong || (!invited && requested))
    if (!room.chatEnabled && !hasRequestToCancel) return
    var expanded by remember(room.id) { mutableStateOf(false) }
    Box {
        LiveIconButton(
            Icons.Default.Add, stringResource(R.string.live_more_chat_actions), { expanded = true },
            tint = if (room.chatEnabled) CorusColors.Text.copy(alpha = .75f) else CorusColors.Secondary,
            touchSize = 44.dp, glyphSize = 18.dp,
        )
        DropdownMenu(expanded, { expanded = false }, containerColor = CorusColors.CardBackground) {
            if (canManageRequests) {
                if (room.chatEnabled && room.allowSongRequests) DropdownMenuItem(
                    text = { LiveText(stringResource(R.string.live_request_song)) },
                    leadingIcon = { Icon(Icons.Default.MusicNote, null) },
                    onClick = { expanded = false; requestSong() },
                )
                if (!room.chatEnabled && pendingSong) DropdownMenuItem(
                    text = { LiveText(stringResource(R.string.live_cancel_song_request)) },
                    leadingIcon = { Icon(Icons.Default.Close, null) },
                    enabled = !state.requestBusy,
                    onClick = {
                        expanded = false
                        vm.requestAction("cancelSongRequest", mapOf("requestMessageId" to state.mySongRequest.text("chatMessageId")))
                    },
                )
                if (!invited && requested) DropdownMenuItem(
                    text = { LiveText(stringResource(R.string.live_cancel_dj_request)) },
                    leadingIcon = { Icon(Icons.Default.Close, null) },
                    enabled = !state.requestBusy,
                    onClick = { expanded = false; vm.requestAction("cancelDJRequest") },
                )
                if (room.chatEnabled && !invited && !requested && room.allowDJRequests) DropdownMenuItem(
                    text = { LiveText(if (lineupFull) stringResource(R.string.live_dj_spots_full) else stringResource(R.string.live_request_to_dj)) },
                    leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                    enabled = !lineupFull && !state.requestBusy && !djRequestCoolingDown &&
                        state.djRequestFeedback != LiveDJRequestFeedback.SENT,
                    onClick = { expanded = false; focus.clearFocus(); vm.requestAction("requestDJ") },
                )
            }
            if (room.chatEnabled) DropdownMenuItem(
                text = { LiveText("GIF") },
                leadingIcon = { Icon(Icons.Default.Photo, null) },
                onClick = { expanded = false; pickGif() },
            )
        }
    }
}

@Composable
private fun DJRequestStatus(state: LiveUiState, coolingDown: Boolean, cancel: () -> Unit) {
    val pending = state.myDJRequest.text("status") == "pending"
    val label = when {
        pending || state.djRequestFeedback == LiveDJRequestFeedback.SENT -> CorusStrings.corus_live_dj_request_sent
        state.djRequestFeedback == LiveDJRequestFeedback.SENDING -> CorusStrings.corus_live_dj_request_sending
        state.djRequestFeedback == LiveDJRequestFeedback.FAILED -> CorusStrings.corus_live_dj_request_failed
        coolingDown -> R.string.live_dj_request_cooldown
        else -> return
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PersonAdd, null, tint = CorusColors.Secondary, modifier = Modifier.size(14.dp))
            LiveText(stringResource(label), CorusFont.caption.copy(textAlign = TextAlign.Center), CorusColors.Secondary)
        }
        if (pending) {
            TextButton(onClick = cancel, enabled = !state.requestBusy) {
                Text(stringResource(R.string.live_cancel_dj_request), style = CorusFont.caption)
            }
        }
    }
}

@Composable
private fun LiveLineupSheet(
    room: LiveRoom, state: LiveUiState, vm: LiveViewModel, close: () -> Unit,
    openProfile: (String) -> Unit, invite: (() -> Unit)?,
) {
    var handoff by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(room.id, room.djs) {
        if (state.hosting && room.djs.isNotEmpty()) vm.action("hostHandoffOptions") {
            handoff = (it["eligibleDJIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        }
    }
    LiveSheet(stringResource(R.string.live_djs), close, large = room.djs.size + state.djRequests.size > 3) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            PersonRow(room.host, { openProfile(room.host.uid) }) { LiveText(stringResource(R.string.live_host), CorusFont.caption, CorusColors.Secondary) }
            room.djs.forEach { raw ->
                val dj = LivePerson.parse(raw)
                var menu by remember(dj.uid) { mutableStateOf(false) }
                PersonRow(dj, { openProfile(dj.uid) }) {
                    if (state.hosting) {
                        Box {
                            LiveIconButton(Icons.Default.MoreHoriz, stringResource(R.string.live_dj_options), { menu = true })
                            DropdownMenu(menu, { menu = false }) {
                                if (dj.uid in handoff) DropdownMenuItem(
                                    text = { LiveText(stringResource(R.string.live_make_host)) },
                                    leadingIcon = { Icon(Icons.Default.VpnKey, null, tint = CorusColors.Accent) },
                                    onClick = { vm.action("offerHost", mapOf("targetUid" to dj.uid)); menu = false },
                                )
                                DropdownMenuItem(
                                    text = { LiveText(stringResource(R.string.live_remove_dj)) },
                                    leadingIcon = { Icon(Icons.Default.PersonRemove, null) },
                                    onClick = { vm.action("moveDJToListeners", mapOf("targetUid" to dj.uid)); menu = false },
                                )
                            }
                        }
                    }
                }
            }
            if (state.hosting) {
                if (state.djRequests.isNotEmpty()) {
                    LiveText(stringResource(R.string.live_dj_requests), CorusFont.captionMedium, CorusColors.Secondary)
                }
                state.djRequests.forEach { raw ->
                    val person = LivePerson.parse(raw)
                    val request = mapOf("targetUid" to person.uid, "requestCreatedAt" to raw.number("createdAt"))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PersonRow(person, { openProfile(person.uid) },
                            subtitle = stringResource(R.string.live_wants_to_dj)) {}
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChatRequestButton(stringResource(R.string.live_request_decline), enabled = !state.requestBusy) {
                                vm.requestAction("declineDJRequest", request)
                            }
                            ChatRequestButton(stringResource(R.string.live_request_approve), primary = true,
                                enabled = !state.requestBusy && invite != null) {
                                vm.requestAction("approveDJRequest", request)
                            }
                        }
                    }
                }
                if (state.pendingDJs.isNotEmpty()) {
                    LiveText(stringResource(R.string.live_dj_invitations), CorusFont.captionMedium, CorusColors.Secondary)
                }
                state.pendingDJs.forEach { raw ->
                    val person = LivePerson.parse(raw)
                    PersonRow(person, { openProfile(person.uid) }, subtitle = stringResource(R.string.live_invited)) {
                        TextButton(
                            enabled = !state.requestBusy,
                            onClick = { vm.requestAction("cancelDJInvite", mapOf("targetUid" to person.uid)) },
                        ) {
                            LiveText(stringResource(android.R.string.cancel), CorusFont.captionMedium, CorusColors.Error)
                        }
                    }
                }
            }
        }
        if (invite != null && (state.hosting || state.dj != null)) {
            LiveRow(stringResource(R.string.live_invite_dj), Icons.Default.PersonAdd, onClick = invite)
        }
        if (!state.hosting && state.dj == null && state.myDJRequest.text("status") == "declined") {
            LiveText(stringResource(R.string.live_dj_request_declined), CorusFont.caption, CorusColors.Secondary)
        }
    }
}

@Composable
private fun PersonRow(
    person: LivePerson, onProfile: () -> Unit, subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).clickable(onClick = onProfile), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveAvatar(person, 34.dp)
            Column {
                LiveText("@${person.username.removePrefix("@")}", CorusFont.username, lines = 1)
                subtitle?.let { LiveText(it, CorusFont.caption, CorusColors.Secondary) }
            }
        }
        trailing()
    }
}

@Composable
private fun RoomSwitch(text: String, checked: Boolean, interactive: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LiveText(text, modifier = Modifier.weight(1f))
        Switch(checked, onChange, enabled = interactive)
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
    val reasons = listOf(
        "Spam or scam" to CorusStrings.report_reason_spam,
        "Harassment or bullying" to CorusStrings.report_reason_harassment,
        "Hate speech" to CorusStrings.report_reason_hate,
        "Nudity or sexual content" to CorusStrings.report_reason_nudity,
        "Violence or threats" to CorusStrings.report_reason_violence,
        "False information" to CorusStrings.report_reason_false_info,
        "Intellectual property violation" to CorusStrings.report_reason_ip_violation,
        "Other" to CorusStrings.report_reason_other,
    )
    var reason by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    LiveSheet(stringResource(CorusStrings.corus_live_report_room), close, large = true) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            reasons.forEach { (option, label) ->
                Row(Modifier.fillMaxWidth().clickable { reason = option }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(reason == option, { reason = option })
                    LiveText(stringResource(label))
                }
            }
            LiveSearchField(details, stringResource(R.string.live_additional_details)) { details = limitLiveCharacters(it, 500) }
        }
        LivePrimary(stringResource(R.string.live_submit_report), reason.isNotEmpty()) { report(reason, details) }
    }
}

@Composable
private fun LiveRoomEffects(effect: String) {
    Box(Modifier.fillMaxSize()) {
        when (effect) {
            "rain" -> RainEffectView(
                RainIntensity.MEDIUM, Modifier.alpha(0.5f), windAngleDegrees = 5f, particleDensity = 0.6875f,
                fallSpeedScale = 0.91f, startsFilled = true,
            )
            "snow" -> SnowEffectView(SnowIntensity.MEDIUM, Modifier.alpha(0.8f), startsFilled = true)
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
