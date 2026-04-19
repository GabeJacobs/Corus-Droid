package fm.corus.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Per-post state for the "view back cover" vinyl-flip animation. Holds the
 * resolved back-cover URL, a loading progress bar, and the flipped flag so the
 * album art can render front/back faces and run the 3D rotation.
 */
@Stable
class BackCoverFlipState {
    var isFlipped by mutableStateOf(false)
        internal set
    var backCoverURL by mutableStateOf<String?>(null)
        internal set
    var isLoading by mutableStateOf(false)
        internal set
    var showNotFound by mutableStateOf(false)
        internal set
    val progress = Animatable(0f)

    fun flipBack() {
        isFlipped = false
    }
}

@Composable
fun rememberBackCoverFlipState(): BackCoverFlipState = remember { BackCoverFlipState() }

/**
 * Kicks off the fetch-and-flip sequence: show a progress bar, call the backend
 * for the back-cover URL, then flip to the back face. If no URL is returned,
 * surface a transient "No back cover available" hint.
 */
fun CoroutineScope.launchBackCoverFlip(
    state: BackCoverFlipState,
    postId: String,
    onFetch: suspend (String) -> String?,
) {
    if (state.isLoading) return
    if (state.isFlipped) return
    if (state.backCoverURL != null) {
        state.isFlipped = true
        return
    }
    launch {
        state.isLoading = true
        state.showNotFound = false
        state.progress.snapTo(0f)
        state.progress.animateTo(
            targetValue = 0.3f,
            animationSpec = tween(durationMillis = 800, easing = LinearEasing),
        )
        val progressJob = launch {
            delay(800)
            state.progress.animateTo(
                targetValue = 0.9f,
                animationSpec = tween(durationMillis = 3000, easing = LinearEasing),
            )
        }
        val url = onFetch(postId)
        progressJob.cancel()

        if (url != null) {
            state.progress.animateTo(1f, animationSpec = tween(durationMillis = 200))
            state.backCoverURL = url
            delay(50)
            state.isLoading = false
            state.isFlipped = true
        } else {
            state.progress.animateTo(1f, animationSpec = tween(durationMillis = 300))
            delay(300)
            state.isLoading = false
            state.showNotFound = true
            delay(2000)
            state.showNotFound = false
        }
    }
}
