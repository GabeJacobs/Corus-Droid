package fm.corus.android.ui.screens.live

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.corus.android.R
import fm.corus.android.localization.CorusStrings
import fm.corus.android.ui.components.rememberReducedMotion
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun LiveBrowseSetlistHeader(room: LiveRoom, tracks: List<LiveTrack>, vm: LiveViewModel, close: () -> Unit, onProfile: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var menu by remember(room.id) { mutableStateOf(false) }
    var delete by remember(room.id) { mutableStateOf(false) }
    var deleting by remember(room.id) { mutableStateOf(false) }
    var deleteFailed by remember(room.id) { mutableStateOf(false) }
    var showDJs by remember(room.id) { mutableStateOf(false) }
    val djs = tracks.mapNotNull { it.playedBy }.distinctBy { it.uid }
    val creator = room.creator ?: room.host
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth()) {
        if (room.status == "ended" && creator.uid == state.uid) Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, stringResource(CorusStrings.parity_see_more), tint = CorusColors.Accent) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.corus_live_delete_past_room_action), color = CorusColors.Error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = CorusColors.Error) }, onClick = {
                        menu = false; delete = true
                        vm.event("past_room_delete_prompted", source = "setlist", roomId = room.id)
                    })
            }
        }
        LiveArtwork(room.roomImageURL ?: room.setlist.firstOrNull()?.song?.liveDisplayArtwork ?: tracks.firstOrNull()?.song?.liveDisplayArtwork,
            120.dp, Modifier.align(Alignment.Center))
        }
        LiveText(room.title, CorusFont.songTitleLarge.copy(fontSize = 24.sp, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
        LiveText(stringResource(CorusStrings.live_room_created_by, "@${creator.username.trimStart('@')}"), CorusFont.bodyMedium, CorusColors.Secondary)
        val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(room.endedAt))
        val duration = ((room.endedAt - room.createdAt).coerceAtLeast(0) / 60000)
        val measures = buildList {
            if (duration >= 60) add(android.icu.util.Measure(duration / 60, android.icu.util.MeasureUnit.HOUR))
            add(android.icu.util.Measure(duration % 60, android.icu.util.MeasureUnit.MINUTE))
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        val formattedDuration = android.icu.text.MeasureFormat.getInstance(context.resources.configuration.locales[0], android.icu.text.MeasureFormat.FormatWidth.NARROW).formatMeasures(*measures.toTypedArray())
        LiveText(if (room.createdAt > 0) "$date · $formattedDuration" else date, CorusFont.caption, CorusColors.Secondary)
        if (room.roomDescription.isNotBlank()) LiveBrowseDescription(room, vm)
        if (djs.isNotEmpty()) TextButton(onClick = { showDJs = true; vm.event("setlist_djs_opened", source = "setlist", roomId = room.id) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveText(stringResource(CorusStrings.corus_live_djs), CorusFont.caption, CorusColors.Secondary)
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) { djs.take(4).forEach { LiveAvatar(it, 30.dp) } }
                Spacer(Modifier.weight(1f))
                LiveText(stringResource(CorusStrings.rail_see_all), CorusFont.caption, CorusColors.Accent)
                Icon(Icons.Default.ChevronRight, null, tint = CorusColors.Accent)
            }
        }
    }
    if (showDJs) LiveSheet(stringResource(CorusStrings.corus_live_djs), { showDJs = false }) {
        djs.forEach { person ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                showDJs = false
                vm.event("setlist_dj_opened", source = "setlist", roomId = room.id)
                onProfile(person.uid)
            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveAvatar(person, 36.dp)
                LiveText("@${person.username.trimStart('@')}", CorusFont.bodyMedium)
            }
        }
    }
    if (delete) LiveSheet(stringResource(R.string.corus_live_delete_past_room_title), { if (!deleting) delete = false }, dismissEnabled = !deleting) {
        LiveText(stringResource(R.string.corus_live_delete_past_room_message), color = CorusColors.Secondary)
        if (deleteFailed) LiveText(stringResource(R.string.corus_live_delete_past_room_failed), color = CorusColors.Error)
        LivePrimary(stringResource(R.string.corus_live_delete_past_room_action), destructive = true, enabled = !deleting, loading = deleting) {
            deleting = true; deleteFailed = false
            scope.launch {
                try { vm.deletePastRoom(room); delete = false; close() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { deleteFailed = true; vm.event("past_room_deleted", "failed", "setlist", room.id) }
                finally { deleting = false }
            }
        }
        TextButton(onClick = { delete = false; vm.event("past_room_delete_cancelled", source = "setlist", roomId = room.id) }, enabled = !deleting, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(CorusStrings.common_cancel))
        }
    }
}

@Composable
private fun LiveBrowseDescription(room: LiveRoom, vm: LiveViewModel) {
    var expanded by remember(room.id, room.roomDescription) { mutableStateOf(false) }
    var cut by remember(room.id, room.roomDescription) { mutableStateOf<Int?>(null) }
    val reduced = rememberReducedMotion()
    val more = stringResource(CorusStrings.parity_see_more)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val less = stringResource(CorusStrings.concert_show_less)
    val accent = CorusColors.Accent
    val body = room.roomDescription.trim()
    val text = buildAnnotatedString {
        append(if (!expanded && cut != null) body.take(cut!!).trimEnd() + "… " else body)
        if (cut != null) withStyle(SpanStyle(color = accent)) { append(if (expanded) " $less" else more) }
    }
    Text(text, modifier = Modifier.fillMaxWidth().animateContentSize(tween(if (reduced) 0 else 200), Alignment.TopCenter)
        .then(if (cut != null) Modifier.clickable {
            expanded = !expanded
            vm.event("setlist_description_toggled", if (expanded) "expanded" else "collapsed", "setlist", room.id)
        } else Modifier), style = CorusFont.body, color = CorusColors.Secondary, textAlign = TextAlign.Center,
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        onTextLayout = { result ->
            if (cut == null && result.hasVisualOverflow) {
                var low = 0
                var high = body.length
                var best = 0
                while (low <= high) {
                    val middle = (low + high) / 2
                    val candidate = body.take(middle).trimEnd() + "… " + more
                    val fit = measurer.measure(candidate, style = result.layoutInput.style,
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = result.layoutInput.constraints.maxWidth))
                    if (fit.lineCount <= 2) { best = middle; low = middle + 1 } else high = middle - 1
                }
                cut = best
            }
        })
}
