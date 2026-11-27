package fm.corus.android.ui.screens.live

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import fm.corus.android.ui.components.LocalSkipImageRevealWhenCached
import fm.corus.android.ui.components.ShimmerAsyncImage
import com.valentinilk.shimmer.shimmer
import fm.corus.android.R
import fm.corus.android.localization.CorusStrings
import fm.corus.android.ui.components.rememberReducedMotion
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class LivePastRoom(val room: LiveRoom, val previews: List<LiveTrack>, val visitors: Int) {
    companion object {
        fun parse(fields: LiveFields): LivePastRoom? {
            val room = fields["room"].liveFields() ?: return null
            val id = fields.text("id").takeIf(String::isNotBlank) ?: return null
            return LivePastRoom(LiveRoom(id, room), fields.rows("previewTracks").mapNotNull(LiveTrack::parse), fields.number("visitorCount").toInt())
        }
    }
}

internal fun liveBrowseDJ(room: LiveRoom): LivePerson = room.currentDJ ?: room.current?.playedBy ?: room.host
internal fun liveBrowseLineup(room: LiveRoom): List<LivePerson> =
    (listOf(liveBrowseDJ(room), room.host) + room.djs.map(LivePerson::parse)).distinctBy { it.uid }

internal class LiveBrowsePageState {
    var rooms by mutableStateOf<List<LivePastRoom>>(emptyList())
    var cursor by mutableStateOf<LiveFields?>(null)
    var loaded by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var failed by mutableStateOf(false)
    suspend fun load(vm: LiveViewModel, scope: String, query: String, reset: Boolean = false) {
        if (loading) return
        loading = true
        failed = false
        try {
            var next = if (reset) null else cursor
            val result = mutableListOf<LivePastRoom>()
            do {
                val page = vm.browseRooms(scope, query, next)
                result += page.rows("rooms").mapNotNull(LivePastRoom::parse)
                next = page["nextCursor"].liveFields()
            } while (result.isEmpty() && next != null)
            rooms = ((if (reset) emptyList() else rooms) + result).distinctBy { it.room.id }
            cursor = next
            loaded = true
            vm.event("browse_loaded", "success", scope)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true; vm.event("browse_loaded", "failed", scope) }
        finally { loading = false }
    }
}

@Composable
internal fun LiveBrowseScreen(query: String, listState: LazyGridState, isActive: Boolean, refreshID: Int = 0) {
    val live = LocalLiveNavigation.current ?: return
    val vm = live.viewModel
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val ui by vm.state.collectAsState()
    val normalized = query.trim()
    var mine by remember(ui.uid) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val scope = if (mine) "mine" else "past"
    val model = remember(ui.uid, scope, normalized) { vm.browseModel(ui.uid, scope, normalized) }
    val activeSnapshot = remember(ui.uid, normalized) { vm.discoverySnapshotModel(ui.uid, normalized) }
    val coroutine = rememberCoroutineScope()
    val columns = if (LocalConfiguration.current.fontScale > 1.3f || normalized.isNotBlank()) 1 else 2
    LaunchedEffect(model, isActive, live.state.access != null) {
        if (isActive && live.state.access != null && !model.loaded) {
            if (normalized.isNotBlank()) { delay(350); vm.event("browse_searched", source = "live_browse") }
            model.load(vm, scope, normalized)
        }
    }
    LaunchedEffect(refreshID) {
        if (refreshID > 0 && isActive) {
            vm.event("browse_refreshed", source = scope)
            launch { model.load(vm, scope, normalized, reset = true) }
            launch { activeSnapshot.load(vm, normalized) }
        }
    }
    LaunchedEffect(activeSnapshot, isActive, resumed, live.state.access != null) {
        if (isActive && resumed && live.state.access != null) {
            if (normalized.isNotBlank()) delay(350)
            while (true) { activeSnapshot.load(vm, normalized); delay(20_000) }
        }
    }
    val revisionKey = ui.discovery.joinToString("|") { "${it.id}:${it.revision}:${it.status}:${it.currentDJ?.uid}" }
    var priorRevision by remember(ui.uid, normalized) { mutableStateOf(revisionKey) }
    LaunchedEffect(revisionKey, isActive, resumed) {
        if (isActive && resumed && priorRevision != revisionKey) activeSnapshot.load(vm, normalized)
        priorRevision = revisionKey
    }
    LaunchedEffect(isActive) { if (isActive) vm.event("browse_viewed", source = "search_live") }
    val seenRooms = remember(ui.uid, normalized, scope) { mutableSetOf<String>() }
    LaunchedEffect(listState, isActive, ui.uid, normalized, scope) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key.toString() } }.collect { keys ->
            keys.filter { it.startsWith("active-") || it.startsWith("past-") }.forEach { key ->
                if (seenRooms.add(key)) vm.event(
                    if (key.startsWith("active-")) "browse_room_impression" else "browse_past_room_impression",
                    source = "live_browse", roomId = key.substringAfter('-'))
            }
        }
    }
    // A successful delete closes the setlist. Refresh history when returning.
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(ui.openSavedSetlist?.id) {
        if (wasOpen && ui.openSavedSetlist == null) model.load(vm, scope, normalized, reset = true)
        wasOpen = ui.openSavedSetlist != null
    }
    if (live.state.access == null) {
        if (ui.accessResolved) LiveText(stringResource(CorusStrings.live_browse_unavailable), modifier = Modifier.padding(20.dp))
        else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { LiveBrowseSkeleton() } }
        return
    }
    val completeSnapshot = activeSnapshot.snapshot
    val active = completeSnapshot?.rooms.orEmpty()
    LazyVerticalGrid(columns = GridCells.Fixed(columns), state = listState,
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (active.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiveBrowseDot(isActive)
                    LiveText(stringResource(CorusStrings.live_browse_now).uppercase(), CorusFont.sectionHeader, CorusColors.Secondary)
                }
            }
            items(active, key = { "active-${it.room.id}" }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                val room = entry.room
                LiveBrowseActiveCard(room, entry.peopleCount, previews = entry.previews) {
                    vm.event("browse_room_opened", source = "live_browse", roomId = room.id)
                    vm.join(room.id)
                }
            }
        } else if (completeSnapshot == null && !activeSnapshot.failed) {
            items(2, key = { "active-loading-$it" }, span = { GridItemSpan(maxLineSpan) }) { LiveBrowseActiveSkeleton() }
        } else if (completeSnapshot == null && activeSnapshot.failed) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = { coroutine.launch { activeSnapshot.load(vm, normalized) } }) {
                    Text(stringResource(CorusStrings.live_browse_error))
                }
            }
        } else if (normalized.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    LiveText(stringResource(CorusStrings.live_browse_quiet), CorusFont.songTitle)
                    LiveText(stringResource(CorusStrings.live_browse_quiet_body), color = CorusColors.Secondary)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { menu = true }) {
                        Icon(Icons.Default.History, null, tint = CorusColors.Accent)
                        Spacer(Modifier.width(6.dp))
                        LiveText(stringResource(if (mine) CorusStrings.live_browse_yours else CorusStrings.live_browse_past).uppercase(), CorusFont.sectionHeader, CorusColors.Secondary)
                        Icon(Icons.Default.ExpandMore, null, tint = CorusColors.Secondary)
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        listOf(false, true).forEach { own ->
                            DropdownMenuItem(text = { LiveText(stringResource(if (own) CorusStrings.live_browse_yours else CorusStrings.live_browse_past)) }, onClick = {
                                mine = own; menu = false; vm.event("browse_scope_changed", source = if (own) "mine" else "past")
                            })
                        }
                    }
                }
                Button(onClick = { vm.event("browse_go_live", source = "live_browse"); vm.openSetup() }, enabled = live.state.canOpenSetup,
                    colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent, contentColor = Color.White),
                    shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                    Text(stringResource(CorusStrings.corus_live_go_live), style = CorusFont.captionMedium)
                }
            }
        }
        items(model.rooms, key = { "past-${it.room.id}" }) { entry ->
            LiveBrowsePastCard(entry) { vm.openPastRoom(entry.room) }
        }
        if ((!model.loaded && !model.failed) || model.cursor != null && !model.failed) {
            items(if (columns == 2) 4 else 3, key = { "loading-$it" }) { index ->
                LiveBrowseSkeleton()
                if (index == 0) LaunchedEffect(model, isActive, model.cursor, model.loaded) {
                    if (isActive && model.loaded && model.cursor != null) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == "loading-0" } }
                            .collect { visible -> if (visible && !model.loading) { vm.event("browse_pagination_requested", source = scope); model.load(vm, scope, normalized) } }
                    }
                }
            }
        }
        if (model.failed) item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = { coroutine.launch { model.load(vm, scope, normalized) } }) {
                Text(stringResource(CorusStrings.live_browse_error))
            }
        }
        if (model.loaded && model.rooms.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            LiveText(stringResource(if (normalized.isNotBlank()) CorusStrings.live_browse_no_matches else if (mine) CorusStrings.live_browse_no_yours else CorusStrings.live_browse_no_past), color = CorusColors.Secondary)
        }
    }
}

@Composable
private fun LiveBrowseDot(active: Boolean) {
    val reduced = rememberReducedMotion()
    val pulse = if (active && !reduced) {
        val transition = rememberInfiniteTransition(label = "live status")
        val opacity by transition.animateFloat(1f, .75f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "live dot")
        opacity
    } else 1f
    Box(Modifier.size(8.dp).alpha(pulse).background(Color.Red, RoundedCornerShape(50)))
}

@Composable
internal fun LiveBrowseActiveCard(room: LiveRoom, people: Int?, previews: List<LiveTrack>? = null, open: () -> Unit) {
    val large = LocalConfiguration.current.fontScale > 1.3f
    val artworkTracks = previews ?: (listOfNotNull(room.current) + room.recent)
    val peopleLabel = stringResource(R.string.search_trending_people)
    val loadingLabel = stringResource(CorusStrings.common_loading)
    val details: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(room.title, Modifier.weight(1f), color = CorusColors.Text,
                style = CorusFont.songTitle.copy(fontSize = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.widthIn(min = 26.dp).clearAndSetSemantics { contentDescription = people?.let { "${java.text.NumberFormat.getIntegerInstance().format(it)} $peopleLabel" } ?: loadingLabel }) {
                Icon(Icons.Default.Person, null, tint = CorusColors.Secondary, modifier = Modifier.size(12.dp))
                if (people != null) Text(java.text.NumberFormat.getIntegerInstance().format(people), style = CorusFont.caption, color = CorusColors.Secondary)
                else Box(Modifier.size(12.dp, 10.dp).background(CorusColors.Skeleton, RoundedCornerShape(3.dp)))
            }
        }
        Spacer(Modifier.height(6.dp))
        val dj = liveBrowseDJ(room)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy((-9).dp)) {
                liveBrowseLineup(room).take(3).forEachIndexed { index, person ->
                    Box(Modifier.padding(bottom = 6.dp)) {
                        LiveAvatar(person, 30.dp, ring = person.uid == dj.uid, ringPadding = 1.dp)
                        if (person.uid == dj.uid) Text(stringResource(CorusStrings.corus_live_live),
                            Modifier.align(Alignment.BottomCenter).offset(y = 5.dp).background(CorusColors.Accent, RoundedCornerShape(4.dp)).padding(horizontal = 3.dp),
                            style = CorusFont.caption.copy(fontSize = 8.sp, fontWeight = FontWeight.ExtraBold), color = Color.White)
                    }
                }
                if (liveBrowseLineup(room).size > 3) LiveText("+${liveBrowseLineup(room).size - 3}", CorusFont.caption)
            }
            Column {
                Text("@${dj.username.trimStart('@')}", style = CorusFont.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LiveText(stringResource(if (room.current != null && room.voiceMode != "talk") R.string.corus_live_djing_now else CorusStrings.live_dj_label), CorusFont.caption, CorusColors.Secondary)
            }
        }
        LiveText(stringResource(CorusStrings.live_room_created_by, "@${(room.creator ?: room.host).username.trimStart('@')}"), CorusFont.caption, CorusColors.Secondary, lines = 1)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CorusColors.CardBackground).clickable(onClick = open).padding(12.dp)) {
        if (large) { LiveBrowseArtwork(room, artworkTracks, Modifier.size(96.dp)); Spacer(Modifier.height(10.dp)); details() }
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiveBrowseArtwork(room, artworkTracks, Modifier.size(96.dp))
            Column(Modifier.weight(1f), content = details)
        }
    }
}

/** Fade individual downloaded covers without animating the room or its layout. */
@Composable
private fun LiveBrowseRemoteArtwork(
    model: String?, contentDescription: String?, modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (rememberReducedMotion()) {
        val context = LocalContext.current
        val request = remember(context, model) {
            ImageRequest.Builder(context).data(model).crossfade(false).build()
        }
        AsyncImage(request, contentDescription, modifier.background(CorusColors.Skeleton), contentScale = contentScale)
    } else {
        CompositionLocalProvider(LocalSkipImageRevealWhenCached provides true) {
            ShimmerAsyncImage(model, contentDescription, modifier, contentScale = contentScale,
                skipFadeOnMemoryCache = true)
        }
    }
}

@Composable
internal fun LiveBrowseArtwork(room: LiveRoom, tracks: List<LiveTrack>, modifier: Modifier) {
    val urls = tracks.mapNotNull { it.song.liveDisplayArtwork }.distinct().take(4)
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(CorusColors.Accent.copy(alpha = .07f))) {
        when {
            room.roomImageURL != null -> LiveBrowseRemoteArtwork(room.roomImageURL, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            urls.size == 4 -> Column(Modifier.fillMaxSize()) {
                urls.chunked(2).forEach { row -> Row(Modifier.weight(1f)) {
                    row.forEach { LiveBrowseRemoteArtwork(it, null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Fit) }
                } }
            }
            urls.isNotEmpty() -> {
                LiveBrowseRemoteArtwork(urls.first(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Row(Modifier.align(Alignment.BottomEnd).padding(3.dp)) {
                    urls.drop(1).forEach { LiveBrowseRemoteArtwork(it, null, Modifier.size(30.dp).background(CorusColors.CardBackground).padding(1.dp), contentScale = ContentScale.Fit) }
                }
            }
            else -> Icon(Icons.Default.MusicNote, null, Modifier.size(28.dp).align(Alignment.Center), tint = CorusColors.Accent)
        }
    }
}

@Composable
internal fun LiveBrowsePastCard(entry: LivePastRoom, open: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = open), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // Same 104dp artwork band as the iOS archive grid.
        val covers = entry.previews.mapNotNull { it.song.liveDisplayArtwork }.distinct().take(2)
        Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(12.dp)).background(CorusColors.Accent.copy(alpha = .07f))) {
            if (entry.room.roomImageURL != null) {
                LiveBrowseRemoteArtwork(entry.room.roomImageURL, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                covers.firstOrNull()?.let { url ->
                    LiveBrowseRemoteArtwork(url, null, Modifier.align(Alignment.BottomEnd).padding(8.dp).size(32.dp)
                        .clip(RoundedCornerShape(5.dp)), contentScale = ContentScale.Fit)
                }
            } else if (covers.isNotEmpty()) Row(Modifier.fillMaxSize()) {
                covers.forEach { LiveBrowseRemoteArtwork(it, null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop) }
            } else Icon(Icons.Default.MusicNote, null, Modifier.size(28.dp).align(Alignment.Center), tint = CorusColors.Accent)
        }
        Spacer(Modifier.height(5.dp))
        Text(entry.room.title, style = CorusFont.songTitle.copy(fontSize = 15.sp), color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LiveText(stringResource(CorusStrings.live_room_created_by, "@${(entry.room.creator ?: entry.room.host).username.trimStart('@')}"), CorusFont.caption, CorusColors.Secondary, lines = 1)
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, null, Modifier.size(12.dp), tint = CorusColors.Secondary)
            Spacer(Modifier.width(3.dp)); LiveText(java.text.NumberFormat.getIntegerInstance().format(entry.visitors), CorusFont.caption, CorusColors.Secondary)
            Spacer(Modifier.weight(1f))
            LiveText(java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(entry.room.endedAt)), CorusFont.caption, CorusColors.Secondary)
        }
    }
}

@Composable
private fun LiveBrowseActiveSkeleton() {
    val reduced = rememberReducedMotion()
    Row((if (reduced) Modifier else Modifier.shimmer()).fillMaxWidth().clearAndSetSemantics {}
        .clip(RoundedCornerShape(16.dp)).background(CorusColors.CardBackground).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)).background(CorusColors.Skeleton))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(16.dp).background(CorusColors.Skeleton, RoundedCornerShape(3.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).background(CorusColors.Skeleton, RoundedCornerShape(50)))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.fillMaxWidth().height(14.dp).background(CorusColors.Skeleton, RoundedCornerShape(3.dp)))
                    Box(Modifier.fillMaxWidth(.7f).height(11.dp).background(CorusColors.Skeleton, RoundedCornerShape(3.dp)))
                }
            }
            Box(Modifier.fillMaxWidth(.85f).height(12.dp).background(CorusColors.Skeleton, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun LiveBrowseSkeleton() {
    val reduced = rememberReducedMotion()
    Column((if (reduced) Modifier else Modifier.shimmer()).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(12.dp)).background(CorusColors.Skeleton))
        Box(Modifier.fillMaxWidth(.85f).height(16.dp).background(CorusColors.Skeleton, RoundedCornerShape(3.dp)))
        Box(Modifier.fillMaxWidth(.65f).height(12.dp).background(CorusColors.Skeleton, RoundedCornerShape(3.dp)))
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.size(28.dp, 12.dp).background(CorusColors.Skeleton)); Spacer(Modifier.weight(1f))
            Box(Modifier.size(42.dp, 12.dp).background(CorusColors.Skeleton))
        }
    }
}
