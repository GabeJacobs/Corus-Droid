package fm.corus.android.ui.components

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import fm.corus.android.R
import fm.corus.android.data.remote.TMDBApiService
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.launch

data class ExpandedPhoto(
    val url: String,
    val subjectName: String?,
)

@Composable
internal fun FullScreenPhotoViewer(
    photo: ExpandedPhoto?,
    onDismiss: () -> Unit,
) {
    var shown by remember { mutableStateOf<ExpandedPhoto?>(null) }
    if (photo != null) shown = photo

    BackHandler(enabled = photo != null) { onDismiss() }

    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    var isLoaded by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var imageAspect by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(photo) {
        if (photo != null) {
            scale.snapTo(1f)
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
            isLoaded = false
            loadFailed = false
            imageAspect = 0f
        }
    }

    AnimatedVisibility(
        visible = photo != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        shown?.let { p ->
            val closeLabel = stringResource(R.string.full_screen_image_cd_close)
            val view = LocalView.current
            DisposableEffect(Unit) {
                val window = (view.context as? Activity)?.window
                if (window == null) {
                    onDispose {}
                } else {
                    val controller = WindowInsetsControllerCompat(window, view)
                    val hadLightStatusBars = controller.isAppearanceLightStatusBars
                    val hadLightNavigationBars = controller.isAppearanceLightNavigationBars
                    controller.isAppearanceLightStatusBars = false
                    controller.isAppearanceLightNavigationBars = false
                    onDispose {
                        controller.isAppearanceLightStatusBars = hadLightStatusBars
                        controller.isAppearanceLightNavigationBars = hadLightNavigationBars
                    }
                }
            }

            fun noteSuccess(state: AsyncImagePainter.State.Success) {
                isLoaded = true
                loadFailed = false
                val intrinsic = state.painter.intrinsicSize
                if (intrinsic.width > 0f && intrinsic.height > 0f) {
                    imageAspect = intrinsic.width / intrinsic.height
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .semantics {
                        onClick(label = closeLabel) {
                            onDismiss()
                            true
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onDismiss() },
                            onDoubleTap = { tap ->
                                val (targetScale, targetOffset) = doubleTapTransform(
                                    scale = scale.value,
                                    offset = Offset(offsetX.value, offsetY.value),
                                    tap = tap,
                                    containerW = size.width.toFloat(),
                                    containerH = size.height.toFloat(),
                                    imageAspect = imageAspect,
                                )
                                scope.launch {
                                    launch { scale.animateTo(targetScale) }
                                    launch { offsetX.animateTo(targetOffset.x) }
                                    launch { offsetY.animateTo(targetOffset.y) }
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            scope.launch {
                                scale.stop()
                                offsetX.stop()
                                offsetY.stop()
                            }
                            var currentScale = scale.value
                            var currentOffset = Offset(offsetX.value, offsetY.value)
                            var gateOpen = false
                            var gatePan = Offset.Zero
                            do {
                                val event = awaitPointerEvent()
                                if (!gateOpen) {
                                    if (event.changes.count { it.pressed } > 1) {
                                        gateOpen = true
                                        event.changes.forEach { change ->
                                            if (change.changedToDown()) change.consume()
                                        }
                                    } else {
                                        gatePan += event.calculatePan()
                                        if (gatePan.getDistance() > viewConfiguration.touchSlop) {
                                            gateOpen = true
                                        }
                                    }
                                }
                                if (gateOpen) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    if (zoomChange != 1f || panChange != Offset.Zero) {
                                        val (nextScale, nextOffset) = anchoredZoom(
                                            scale = currentScale,
                                            offset = currentOffset,
                                            zoomChange = zoomChange,
                                            pan = panChange,
                                            centroid = event.calculateCentroid(),
                                            containerW = size.width.toFloat(),
                                            containerH = size.height.toFloat(),
                                            imageAspect = imageAspect,
                                        )
                                        currentScale = nextScale
                                        currentOffset = nextOffset
                                        scope.launch {
                                            scale.snapTo(nextScale)
                                            offsetX.snapTo(nextOffset.x)
                                            offsetY.snapTo(nextOffset.y)
                                        }
                                    }
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale.value,
                            scaleY = scale.value,
                            translationX = offsetX.value,
                            translationY = offsetY.value,
                        ),
                ) {
                    AsyncImage(
                        model = p.url,
                        contentDescription = p.subjectName
                            ?.let { stringResource(R.string.photo_viewer_photo_of, it) }
                            ?: stringResource(R.string.photo_viewer_photo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onState = { state ->
                            when (state) {
                                is AsyncImagePainter.State.Success -> noteSuccess(state)
                                is AsyncImagePainter.State.Error -> if (!isLoaded) loadFailed = true
                                else -> Unit
                            }
                        },
                    )
                    val fullUrl = TMDBApiService.originalImageUrl(p.url)
                    if (fullUrl != p.url) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(fullUrl)
                                .size(coil3.size.Size.ORIGINAL)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            onState = { state ->
                                if (state is AsyncImagePainter.State.Success) noteSuccess(state)
                            },
                        )
                    }
                }
                if (!isLoaded && !loadFailed) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (loadFailed) {
                    Text(
                        text = stringResource(R.string.photo_viewer_error),
                        style = CorusFont.body,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding(),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = closeLabel,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

internal fun photoFitSize(containerW: Float, containerH: Float, imageAspect: Float): Size {
    if (containerW <= 0f || containerH <= 0f || imageAspect <= 0f) return Size.Zero
    return if (containerW / containerH > imageAspect) {
        Size(containerH * imageAspect, containerH)
    } else {
        Size(containerW, containerW / imageAspect)
    }
}

internal fun clampPhotoOffset(
    scale: Float,
    offset: Offset,
    containerW: Float,
    containerH: Float,
    imageAspect: Float,
): Offset {
    val fitted = photoFitSize(containerW, containerH, imageAspect)
    val limitX = ((fitted.width * scale - containerW) / 2f).coerceAtLeast(0f)
    val limitY = ((fitted.height * scale - containerH) / 2f).coerceAtLeast(0f)
    val x = offset.x.coerceIn(-limitX, limitX)
    val y = offset.y.coerceIn(-limitY, limitY)
    return Offset(if (x == 0f) 0f else x, if (y == 0f) 0f else y)
}

internal fun anchoredZoom(
    scale: Float,
    offset: Offset,
    zoomChange: Float,
    pan: Offset,
    centroid: Offset,
    containerW: Float,
    containerH: Float,
    imageAspect: Float,
): Pair<Float, Offset> {
    val newScale = (scale * zoomChange).coerceIn(1f, 4f)
    val p = centroid - Offset(containerW / 2f, containerH / 2f)
    val newOffset = p - (p - offset) * (newScale / scale) + pan
    return newScale to clampPhotoOffset(newScale, newOffset, containerW, containerH, imageAspect)
}

internal fun doubleTapTransform(
    scale: Float,
    offset: Offset,
    tap: Offset,
    containerW: Float,
    containerH: Float,
    imageAspect: Float,
): Pair<Float, Offset> {
    if (scale > 1.01f) return 1f to Offset.Zero
    return anchoredZoom(
        scale = scale,
        offset = offset,
        zoomChange = 2.5f / scale,
        pan = Offset.Zero,
        centroid = tap,
        containerW = containerW,
        containerH = containerH,
        imageAspect = imageAspect,
    )
}
