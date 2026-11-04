package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import fm.corus.android.R
import fm.corus.android.domain.DailyHashtagBannerCandidate
import fm.corus.android.domain.parseDailyHashtagBanner
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

data class DailyHashtagBannerState(val candidate: DailyHashtagBannerCandidate?, val dismiss: () -> Unit)

@Composable
fun rememberDailyHashtagBanner(enabled: Boolean): DailyHashtagBannerState {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val prefs = remember(context) { context.getSharedPreferences("corus_daily_hashtag_banner", 0) }
    var dismissed by remember { mutableStateOf(prefs.getString("dismissed", "") ?: "") }
    var candidate by remember(uid, enabled) { mutableStateOf<DailyHashtagBannerCandidate?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uid, enabled) {
        candidate = null
        if (!enabled || uid == null) return@LaunchedEffect
        var fetchedAt = 0L
        while (true) {
            now = System.currentTimeMillis()
            if (now - fetchedAt >= 300_000L) {
                candidate = try {
                    val doc = FirebaseFirestore.getInstance().collection("trending_cache")
                        .document("hashtags").get(Source.SERVER).await()
                    val summary = doc.get("dailyBanner") as? Map<*, *>
                    @Suppress("UNCHECKED_CAST")
                    parseDailyHashtagBanner(
                        summary?.get("day") as? String,
                        (summary?.get("updatedAt") as? Timestamp)?.toDate()?.time,
                        summary?.get("items") as? List<Map<String, Any>>,
                        System.currentTimeMillis(),
                    )
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                fetchedAt = System.currentTimeMillis()
            }
            delay(30_000L)
        }
    }
    val visible = candidate?.takeIf { enabled && uid != null && it.isCurrent(now) && dismissed != "${it.day}|${it.name}" }
    return DailyHashtagBannerState(visible) {
        visible?.let {
            dismissed = "${it.day}|${it.name}"
            prefs.edit().putString("dismissed", dismissed).apply()
        }
    }
}

@Composable
fun TrendingHashtagBanner(state: DailyHashtagBannerState, onHashtagTap: (String) -> Unit, onAnalytics: (String, String, String) -> Unit, isVisible: Boolean) {
    val tag = state.candidate ?: return
    LaunchedEffect(tag.day, tag.name, isVisible) {
        if (isVisible) onAnalytics("shown", tag.name, tag.day)
    }
    val hashtag = "#${tag.name}"
    val title = stringResource(R.string.corus_trending_hashtag_banner_title, hashtag)
    val titleText = buildAnnotatedString {
        append(title)
        val start = title.indexOf(hashtag)
        if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold), start, start + hashtag.length)
    }
    Row(Modifier.fillMaxWidth().background(CorusColors.Accent), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 44.dp).clickable {
                onAnalytics("tapped", tag.name, tag.day)
                onHashtagTap(tag.name)
            }
                .padding(start = 20.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Tag, null, Modifier.size(20.dp), tint = Color.White)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(titleText, style = CorusFont.body.copy(fontSize = 15.sp), color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.corus_trending_hashtag_banner_explore), style = CorusFont.caption, color = Color.White, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(12.dp), tint = Color.White)
                }
            }
        }
        IconButton(onClick = {
            onAnalytics("dismissed", tag.name, tag.day)
            state.dismiss()
        }, modifier = Modifier.padding(end = 6.dp)) {
            Icon(Icons.Default.Close, stringResource(R.string.corus_trending_hashtag_banner_dismiss), Modifier.size(16.dp), tint = Color.White)
        }
    }
}
