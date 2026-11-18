package fm.corus.android.ui.screens.live

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import fm.corus.android.R
import fm.corus.android.data.model.VinylStyle
import fm.corus.android.ui.components.rememberReducedMotion
import kotlinx.coroutines.delay

/** The same sleeve-and-record reveal used when joining a room on iOS. */
@Composable
internal fun LiveJoiningRecordArtwork(url: String?) = key(url) {
    val context = LocalContext.current
    val reducedMotion = rememberReducedMotion()
    val request = remember(url) {
        ImageRequest.Builder(context).data(url).size(512).crossfade(false).build()
    }
    val artwork = rememberAsyncImagePainter(request)
    val artworkState by artwork.state.collectAsState()
    val reveal = remember { Animatable(0f) }
    val ready = artworkState is AsyncImagePainter.State.Success
    LaunchedEffect(ready, reducedMotion) {
        if (ready) {
            if (reducedMotion) reveal.snapTo(1f) else {
                delay(300)
                reveal.animateTo(1f, tween(950))
            }
        }
    }

    Box(Modifier.size(226.dp, 168.dp), contentAlignment = Alignment.CenterStart) {
        // Use one decoded painter for both parts so the label and sleeve appear together.
        if (ready) {
            val style = VinylStyle.BLACK
            val canvasWidth = 144.dp * (style.canvasW / 262f)
            val canvasHeight = canvasWidth * style.canvasRatio
            Box(
                Modifier.offset(x = (5f + 77f * reveal.value).dp).size(144.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.vinyl_black), null,
                    Modifier.offset(
                        x = canvasWidth * (.5f - style.labelXFrac - style.labelWFrac / 2),
                        y = canvasHeight * (.5f - style.labelYFrac - style.labelHFrac / 2),
                    ).requiredSize(canvasWidth, canvasHeight),
                    contentScale = ContentScale.FillBounds,
                )
                Image(
                    artwork, null,
                    Modifier.size(canvasWidth * style.labelWFrac, canvasHeight * style.labelHFrac).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.size(6.dp).background(style.previewColor, CircleShape))
            }
            Image(
                artwork, null,
                Modifier.size(154.dp).shadow(18.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            LiveArtwork(url, 154.dp, showPlaceholderWhileLoading = true)
        }
    }
}
