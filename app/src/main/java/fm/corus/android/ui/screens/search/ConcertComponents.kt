package fm.corus.android.ui.screens.search

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import fm.corus.android.R
import fm.corus.android.data.repository.ConcertAttendance
import fm.corus.android.ui.components.CorusSheetCloseButton
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.delay

@Composable
private fun Bone(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)))
}

@Composable
internal fun ConcertRowSkeleton() {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)),
    ) {
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
    Surface(Modifier.size(218.dp, 170.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxSize().padding(16.dp).shimmer(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f)); Bone(Modifier.size(150.dp, 20.dp)); Bone(Modifier.size(120.dp, 12.dp)); Bone(Modifier.size(170.dp, 12.dp))
        }
    }
}

@Composable
internal fun ConcertDetailSkeleton(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.align(Alignment.CenterStart)) {
                CorusHeaderIconButton(
                    onClick = onBack,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(fm.corus.android.localization.CorusStrings.common_back),
                )
            }
            Text(stringResource(fm.corus.android.localization.CorusStrings.concert_label), style = CorusFont.screenTitle)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).shimmer(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Bone(Modifier.fillMaxWidth().aspectRatio(5f / 3f))
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
internal fun ConcertPlansCard(attendance: ConcertAttendance?, past: Boolean, unavailable: Boolean, onChoice: (String) -> Unit, onPeople: () -> Unit, boxed: Boolean = true, loadError: Boolean = false, onRetry: () -> Unit = {}) {
    val hasPeople = attendance != null && attendance.goingCount + attendance.interestedCount > 0
    var showPeople by remember { mutableStateOf(hasPeople) }
    LaunchedEffect(hasPeople) {
        if (hasPeople) delay(220)
        showPeople = hasPeople
    }
    val opacity by animateFloatAsState(if (showPeople && hasPeople) 1f else 0f, tween(150), label = "concertPeopleFade")
    Surface(
        Modifier.fillMaxWidth(),
        shape = if (boxed) RoundedCornerShape(16.dp) else RectangleShape,
        color = if (boxed) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent,
        border = if (boxed) BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)) else null,
    ) {
        Column(Modifier.animateContentSize(tween(220)).padding(if (boxed) 16.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(fm.corus.android.localization.CorusStrings.concert_your_plans), style = CorusFont.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("interested", "going").forEach { status ->
                    val selected = attendance?.status == status
                    val label = if (status == "going") { if (past && selected) fm.corus.android.localization.CorusStrings.concert_went else fm.corus.android.localization.CorusStrings.concert_im_going }
                        else { if (past && selected) fm.corus.android.localization.CorusStrings.concert_was_interested else fm.corus.android.localization.CorusStrings.concert_im_interested }
                    val active = (!past && !unavailable) || selected
                    OutlinedButton(onClick = { onChoice(status) }, enabled = active,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f),
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Icon(
                            if (status == "going") {
                                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle
                            } else {
                                if (selected) Icons.Filled.Star else Icons.Outlined.Star
                            },
                            null,
                            Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp)); Text(stringResource(label), style = CorusFont.button)
                    }
                }
            }
            if (past) {
                Text(
                    stringResource(fm.corus.android.localization.CorusStrings.concert_plans_started),
                    style = CorusFont.captionMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loadError) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(fm.corus.android.localization.CorusStrings.concert_plans_load_error), Modifier.weight(1f), style = CorusFont.caption)
                    TextButton(onClick = onRetry) { Text(stringResource(fm.corus.android.localization.CorusStrings.concert_retry), style = CorusFont.buttonSmall) }
                }
            }
            if ((past || unavailable) && attendance?.status != null) {
                TextButton(onClick = { onChoice(attendance.status) }) { Text(stringResource(fm.corus.android.localization.CorusStrings.concert_remove), style = CorusFont.buttonSmall) }
            }
            if (hasPeople) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(opacity).clickable(onClick = onPeople), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        val person = attendance!!.people.firstOrNull()
                        if (person?.avatarUrl != null) AsyncImage(person.avatarUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${attendance!!.goingCount} ${stringResource(fm.corus.android.localization.CorusStrings.concert_going_section)} · ${attendance.interestedCount} ${stringResource(fm.corus.android.localization.CorusStrings.concert_interested_section)}", style = CorusFont.captionMedium)
                        if (attendance.people.isNotEmpty()) Text(attendance.people.take(2).joinToString(", ") { it.name }, style = CorusFont.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(stringResource(fm.corus.android.localization.CorusStrings.rail_see_all), style = CorusFont.captionMedium)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
    }
}

@Composable
internal fun ConcertSheetHeader(title: String, showCloseButton: Boolean = true, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
        Text(title, style = CorusFont.screenTitle)
        if (showCloseButton) {
            CorusSheetCloseButton(
                onClick = onClose,
                contentDescription = stringResource(fm.corus.android.localization.CorusStrings.concert_close),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp),
            )
        }
    }
}
