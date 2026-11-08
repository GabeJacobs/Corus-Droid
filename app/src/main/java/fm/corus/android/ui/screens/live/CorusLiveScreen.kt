package fm.corus.android.ui.screens.live

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CorusLiveScreen(onBack: () -> Unit, viewModel: CorusLiveViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var title by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }
    var term by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }
    val room = state.room
    val hosting = viewModel.hosting
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) viewModel.mode("talk") }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, hosting, room?.voiceMode) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && hosting && room?.voiceMode == "talk") viewModel.mode("music") }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun close() { if (room != null && hosting && room.status != "ended") confirmLeave = true else viewModel.leave(onBack) }
    BackHandler { close() }
    Column(Modifier.fillMaxSize().background(CorusColors.Background).padding(horizontal = 20.dp).imePadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }) { Icon(Icons.Default.KeyboardArrowDown, "Close Live", tint = CorusColors.Text) }
            Text(room?.title ?: room?.let { "@${it.username}" } ?: "Corus Live", style = CorusFont.screenTitle, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (hosting && room?.status != "ended") TextButton(onClick = { confirmLeave = true }, colors = ButtonDefaults.textButtonColors(contentColor = CorusColors.Error)) { Text("End Live", fontWeight = FontWeight.Bold) }
        }
        state.error?.let { message -> Row(Modifier.fillMaxWidth().background(CorusColors.CardBackground, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = CorusColors.Text, modifier = Modifier.weight(1f)); IconButton(onClick = viewModel::clearError) { Icon(Icons.Default.Close, "Dismiss error", tint = CorusColors.Secondary) }
        } }
        if (state.available == null) { CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp), color = CorusColors.Accent); return@Column }
        if (state.available != true) { Text("Live isn’t available for this account yet.", color = CorusColors.Secondary); return@Column }
        if (room == null) {
            TextField(title, { title = it.take(48) }, placeholder = { Text("Room title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Text("Set queue", style = CorusFont.screenTitle, color = CorusColors.Text)
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(state.selected) { index, track -> LiveTrackRow(track) { IconButton(onClick = { viewModel.remove(index) }) { Icon(Icons.Default.Close, "Remove ${track.title}", tint = CorusColors.Secondary) } } }
                item { TextButton(onClick = { picker = true }) { Icon(Icons.Default.Add, null); Text(if (state.selected.isEmpty()) "Pick your first song" else "Add music") } }
                item { Text("You can change your queue while live.", color = CorusColors.Secondary) }
                if (state.rooms.isNotEmpty()) {
                    item { Text("Live now", style = CorusFont.screenTitle, color = CorusColors.Text, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp)) }
                    itemsIndexed(state.rooms) { _, live -> Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { viewModel.open(live.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(live.avatar, null, Modifier.size(44.dp).clip(CircleShape)); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(live.title ?: "@${live.username}", color = CorusColors.Text, fontWeight = FontWeight.Bold); Text(live.current?.title ?: "", color = CorusColors.Secondary, maxLines = 1) }; Text("● LIVE", color = CorusColors.Accent)
                    } }
                }
            }
            Text("Live on Android uses your connected Spotify app.", style = CorusFont.sectionHeader, color = CorusColors.Secondary, modifier = Modifier.padding(vertical = 12.dp))
            Button(onClick = { viewModel.create(title) }, enabled = state.selected.isNotEmpty() && !state.busy, colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (state.busy) "Connecting…" else "Go Live", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
        } else {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(room.avatar, null, Modifier.size(64.dp).clip(CircleShape)); Text("@${room.username}", style = CorusFont.screenTitle, color = CorusColors.Text, modifier = Modifier.padding(top = 8.dp))
            }
            room.current?.let { track -> Column(Modifier.fillMaxWidth().background(CorusColors.CardBackground, RoundedCornerShape(22.dp)).padding(16.dp)) {
                Text("Now Playing", color = CorusColors.Secondary); LiveTrackRow(track)
            } }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) { Text(if (room.status == "ended") "Room ended" else "● LIVE", color = CorusColors.Accent, fontWeight = FontWeight.Bold); Spacer(Modifier.width(12.dp)); Text("${state.people} listening", color = CorusColors.Secondary) }
            if (hosting && room.status != "ended") {
                Row(Modifier.fillMaxWidth().background(CorusColors.CardBackground, CircleShape).padding(4.dp)) { listOf("talk" to "Talk", "music" to "Music").forEach { (mode, label) ->
                    Button(onClick = { if (mode == "talk") microphone.launch(Manifest.permission.RECORD_AUDIO) else viewModel.mode(mode) }, enabled = !state.busy && (mode != "talk" || room.voiceEnabled), colors = ButtonDefaults.buttonColors(containerColor = if (room.voiceMode == mode) CorusColors.Accent else Color.Transparent, contentColor = if (room.voiceMode == mode) Color.White else CorusColors.Secondary), modifier = Modifier.weight(1f)) { Icon(if (mode == "talk") Icons.Default.Mic else Icons.Default.MusicNote, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label, fontWeight = FontWeight.Bold) }
                } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text("Queue · ${room.queue.size}", style = CorusFont.screenTitle, color = CorusColors.Text, modifier = Modifier.weight(1f)); if (hosting) TextButton(onClick = { picker = true }) { Icon(Icons.Default.Add, null); Text("Add music") } }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(room.queue) { _, track -> LiveTrackRow(track) }
                if (room.queue.isEmpty()) item { Text("Last song in the queue", color = CorusColors.Secondary, modifier = Modifier.fillMaxWidth().padding(20.dp)) }
                item { Text("Comments", style = CorusFont.screenTitle, color = CorusColors.Text, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)) }
                itemsIndexed(state.messages) { _, message -> Row(Modifier.padding(vertical = 5.dp)) { Text(message.first, fontWeight = FontWeight.Bold, color = CorusColors.Text); Spacer(Modifier.width(8.dp)); Text(message.second, color = CorusColors.Text) } }
            }
            if (hosting && room.queue.isNotEmpty()) TextButton(onClick = viewModel::skip, enabled = !state.busy && room.voiceMode != "talk", modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.SkipNext, null); Text("Skip song") }
            Row(verticalAlignment = Alignment.CenterVertically) { TextField(comment, { comment = it.take(500) }, placeholder = { Text("Add a comment…") }, modifier = Modifier.weight(1f)); IconButton(onClick = { viewModel.chat(comment); comment = "" }, enabled = comment.isNotBlank() && !state.busy) { Icon(Icons.Default.Send, "Send comment", tint = CorusColors.Accent) } }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text("End this room?") }, text = { Text("Everyone will leave the room.") }, confirmButton = { TextButton(onClick = { viewModel.leave(onBack) }, enabled = !state.busy) { Text("End Live") } }, dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } })
    if (picker) ModalBottomSheet(onDismissRequest = { picker = false }, containerColor = CorusColors.SheetBackground) {
        Column(Modifier.fillMaxWidth().padding(20.dp).heightIn(max = 560.dp)) {
            Text("Add music", style = CorusFont.screenTitle, color = CorusColors.Text)
            TextField(term, { term = it; viewModel.search(it) }, placeholder = { Text("Search songs") }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            LazyColumn { itemsIndexed(state.results) { _, track -> LiveTrackRow(track) { IconButton(onClick = { viewModel.add(track); picker = false }, enabled = !state.busy) { Icon(Icons.Default.Add, "Add ${track.title}", tint = CorusColors.Accent) } } } }
        }
    }
}

@Composable
private fun LiveTrackRow(track: LiveTrack, action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(track.artwork, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(CorusColors.CardBackground))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(track.title, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        action?.invoke()
    }
}
