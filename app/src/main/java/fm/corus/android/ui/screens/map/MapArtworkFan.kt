package fm.corus.android.ui.screens.map

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.domain.MapPostChanges
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Decoded post covers float independently of the fixed city marker anchor. */
@Composable
fun MapArtworkFan(
    cityId: String?, people: List<MapPerson>, visible: Boolean,
    point: android.graphics.PointF?, loadLatest: (suspend (List<String>) -> Map<String, CymbalPost?>)?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val revision by MapPostChanges.revisions.collectAsState()
    val loader by rememberUpdatedState(loadLatest)
    val ids = people.take(3).map { it.user.id }
    val key = "$cityId:${ids.joinToString(",")}:$revision"
    var resolvedKey by remember { mutableStateOf("") }
    var covers by remember { mutableStateOf(emptyList<android.graphics.Bitmap>()) }
    var raised by remember { mutableStateOf(false) }
    LaunchedEffect(key, visible) {
        raised = false
        if (!visible || cityId == null || ids.isEmpty()) return@LaunchedEffect
        if (resolvedKey != key) {
            delay(250)
            try {
                val posts = loader?.invoke(ids) ?: return@LaunchedEffect
                val urls = ids.mapNotNull { posts[it]?.displayImageURL }.distinct().take(3)
                val decoded = urls.mapNotNull { url ->
                    val result = SingletonImageLoader.get(context).execute(
                        ImageRequest.Builder(context).data(url).size((44*density).roundToInt()).allowHardware(false).build()
                    ) as? SuccessResult
                    result?.image?.toBitmap()
                }
                covers = decoded; resolvedKey = key
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                return@LaunchedEffect
            }
        }
        if (ValueAnimator.areAnimatorsEnabled()) delay(35)
        raised = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible && raised && resolvedKey == key) 1f else 0f,
        animationSpec = if (ValueAnimator.areAnimatorsEnabled()) spring(dampingRatio = .74f, stiffness = 250f) else snap(),
        label = "City artwork fan",
    )
    if (point == null) return
    Box(Modifier.offset { IntOffset((point.x - 22*density).roundToInt(), (point.y - 131*density).roundToInt()) }.size(44.dp,66.dp)) {
        (if (resolvedKey == key) covers else emptyList()).forEachIndexed { index, bitmap ->
            val spread = index - (covers.size-1)/2f
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).zIndex(3-abs(spread)).graphicsLayer {
                    alpha = progress.coerceIn(0f,1f)
                    translationX = spread*36*density*progress
                    translationY = (38*(1-progress)+(abs(spread)*10-4)*progress)*density
                    rotationZ = spread*19*progress
                    scaleX = .45f+.55f*progress; scaleY = scaleX
                    shadowElevation = 6*density
                    shape = RoundedCornerShape(10.dp); clip = true
                })
        }
    }
}
