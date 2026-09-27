package fm.corus.android.ui.screens.search

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import fm.corus.android.R
import fm.corus.android.data.repository.ConcertAttendance
import kotlinx.coroutines.delay

@Composable
private fun Bone(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)))
}

@Composable
internal fun ConcertRowSkeleton() {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp).shimmer(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Bone(Modifier.size(26.dp, 10.dp)); Bone(Modifier.size(32.dp, 30.dp)) }
            VerticalDivider(Modifier.height(58.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { Bone(Modifier.fillMaxWidth(.8f).height(16.dp)); Bone(Modifier.fillMaxWidth().height(12.dp)) }
            Bone(Modifier.size(64.dp))
        }
    }
}

@Composable
internal fun ConcertPreviewSkeleton() {
    Card(Modifier.size(218.dp, 170.dp), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxSize().padding(16.dp).shimmer(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f)); Bone(Modifier.size(150.dp, 20.dp)); Bone(Modifier.size(120.dp, 12.dp)); Bone(Modifier.size(170.dp, 12.dp))
        }
    }
}

@Composable
internal fun ConcertDetailSkeleton(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.padding(12.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).shimmer(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Bone(Modifier.fillMaxWidth().height(260.dp))
            Bone(Modifier.fillMaxWidth(.75f).height(25.dp))
            repeat(2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Bone(Modifier.size(70.dp, 12.dp)); Bone(Modifier.fillMaxWidth(.7f).height(20.dp)) }
            }
            Bone(Modifier.fillMaxWidth().height(115.dp))
            Bone(Modifier.size(95.dp, 22.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConcertPlansCard(attendance: ConcertAttendance?, past: Boolean, unavailable: Boolean, onChoice: (String) -> Unit, onPeople: () -> Unit) {
    val hasPeople = attendance != null && attendance.goingCount + attendance.interestedCount > 0
    var showPeople by remember { mutableStateOf(hasPeople) }
    LaunchedEffect(hasPeople) {
        if (hasPeople) delay(220)
        showPeople = hasPeople
    }
    val opacity by animateFloatAsState(if (showPeople && hasPeople) 1f else 0f, tween(150), label = "concertPeopleFade")
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.animateContentSize(tween(220)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.concert_your_plans), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("interested", "going").forEach { status ->
                    val selected = attendance?.status == status
                    val label = if (status == "going") { if (past && selected) R.string.concert_went else R.string.concert_im_going }
                        else { if (past && selected) R.string.concert_was_interested else R.string.concert_im_interested }
                    val active = (!past && !unavailable) || selected
                    OutlinedButton(onClick = { onChoice(status) }, enabled = active,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Icon(if (status == "going") Icons.Default.CheckCircle else Icons.Default.Star, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(stringResource(label))
                    }
                }
            }
            if ((past || unavailable) && attendance?.status != null) {
                TextButton(onClick = { onChoice(attendance.status) }) { Text(stringResource(R.string.concert_remove)) }
            }
            if (hasPeople) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(opacity).clickable(onClick = onPeople), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        val person = attendance!!.people.firstOrNull()
                        if (person?.avatarUrl != null) AsyncImage(person.avatarUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${attendance!!.goingCount} ${stringResource(R.string.concert_going_section)} · ${attendance.interestedCount} ${stringResource(R.string.concert_interested_section)}", style = MaterialTheme.typography.bodySmall)
                        if (attendance.people.isNotEmpty()) Text(attendance.people.take(2).joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(stringResource(R.string.concert_see_all), style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
    }
}

@Composable
internal fun ConcertSheetHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onClose) { Icon(androidx.compose.material.icons.Icons.Default.Close, stringResource(R.string.share_close)) }
    }
}
