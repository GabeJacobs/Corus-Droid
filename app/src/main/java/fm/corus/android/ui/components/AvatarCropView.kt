package fm.corus.android.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import fm.corus.android.R
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full-screen square crop overlay for avatar photos.
 * Port of iOS AvatarCropView (ProfileView.swift:944-1077).
 *
 * Presented in a [Dialog] window so it covers tab-bar / mini-player chrome
 * (same role as iOS `fullScreenCover`). Compose Dialog windows report
 * `navigationBars` insets as 0 (see [CorusDraggableSheet]), so bottom safe-area
 * padding is read from the Activity window instead.
 */
@Composable
fun AvatarCropView(
    bitmap: Bitmap,
    onConfirm: (ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var cropSizePx by remember { mutableFloatStateOf(0f) }

    // Dismiss keyboard when crop view appears
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboardController?.hide() }

    val systemBarPadding = rememberActivitySystemBarPadding()

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        // Dialog owns back dismissal via onDismissRequest; keep BackHandler for
        // callers that may host this without a Dialog in tests / previews.
        BackHandler { onCancel() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Activity-window insets: Dialog's own WindowInsets.navigationBars is 0.
                    .padding(top = systemBarPadding.first, bottom = systemBarPadding.second)
            ) {
                // Cancel button
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                ) {
                    Text(stringResource(R.string.avatar_crop_cancel), color = Color.White, fontSize = 17.sp)
                }

                Spacer(Modifier.weight(1f))

                // Crop area
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val cropSizeDp = maxWidth
                    val density = LocalDensity.current
                    cropSizePx = with(density) { cropSizeDp.toPx() }
                    val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()

                    // Display dimensions: aspect-fill the square (matches iOS displaySize)
                    val displayW = if (aspect >= 1f) cropSizePx * aspect else cropSizePx
                    val displayH = if (aspect >= 1f) cropSizePx else cropSizePx / aspect
                    val displayWidthDp = with(density) { displayW.toDp() }
                    val displayHeightDp = with(density) { displayH.toDp() }

                    fun clampedOffset(off: Offset, s: Float): Offset {
                        val maxX = max(0f, (displayW * s - cropSizePx) / 2f)
                        val maxY = max(0f, (displayH * s - cropSizePx) / 2f)
                        return Offset(
                            off.x.coerceIn(-maxX, maxX),
                            off.y.coerceIn(-maxY, maxY),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(cropSizeDp)
                            .clipToBounds()
                            .border(0.5.dp, Color.White.copy(alpha = 0.4f), RectangleShape)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = max(1f, scale * zoom)
                                    val newOffset = offset + pan
                                    offset = clampedOffset(newOffset, scale)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                // requiredSize escapes the parent's square constraints so the
                                // image renders at its true aspect-fill dimensions (taller than
                                // the crop square for portrait photos). Without this, the parent
                                // clamps height to cropSizeDp and FillBounds squishes the image.
                                .requiredSize(displayWidthDp, displayHeightDp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Use Photo — content padding above the gesture/nav safe area.
                Button(
                    onClick = {
                        val cropped = cropBitmap(bitmap, scale, offset, cropSizePx)
                        onConfirm(cropped)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = UsePhotoBottomPadding)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(stringResource(R.string.avatar_crop_use_photo), fontSize = 17.sp)
                }
            }
        }
    }
}

/** Minimum clearance for the gesture handle when an OEM/Dialog reports a thin/zero inset. */
private val MinGestureNavPadding = 24.dp

/**
 * Extra space between Use Photo and the top of the system gesture/nav inset.
 * 16.dp (iOS) reads tight against Android's gesture handle; 32.dp matches the
 * breathing room of other full-bleed Corus bottom CTAs.
 */
private val UsePhotoBottomPadding = 32.dp

/**
 * Status-bar / bottom-gesture padding from the Activity window.
 *
 * Compose [Dialog] windows report `navigationBars` as 0 (verified in
 * [CorusDraggableSheet]), which parks bottom CTAs under the gesture bar. The host
 * Activity window still has the real insets. Prefer the larger of nav-bar and
 * mandatory-system-gesture insets — on this emulator nav is 24.dp while the
 * gesture zone is 32.dp, and padding only to nav leaves the CTA flush with the
 * handle.
 */
@Composable
private fun rememberActivitySystemBarPadding(): Pair<Dp, Dp> {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val decor = context.findActivity()?.window?.decorView ?: view.rootView

    return remember(decor, density) {
        val insets = ViewCompat.getRootWindowInsets(decor)
        val statusPx = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val navPx = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val gesturePx =
            insets?.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())?.bottom ?: 0
        val status = with(density) { statusPx.toDp() }
        val bottomInset = with(density) { maxOf(navPx, gesturePx).toDp() }
        // Floor gesture-mode clearance (same rationale as MainTabScreen's tab bar).
        val bottomPadded =
            if (bottomInset <= 0.dp) MinGestureNavPadding
            else maxOf(bottomInset, MinGestureNavPadding)
        status to bottomPadded
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Compute the crop rectangle in source-bitmap pixels.
 * Returns (x, y, size) clamped to bitmap bounds.
 *
 * Port of iOS cropImage(viewSize:) from ProfileView.swift:1062-1076.
 */
internal fun computeCropRect(
    srcW: Int,
    srcH: Int,
    scale: Float,
    offset: Offset,
    cropViewPx: Float,
): Triple<Int, Int, Int> {
    val aspect = srcW.toFloat() / srcH.toFloat()

    // Display size in the same unit as cropViewPx (aspect-fill logic)
    val displayW = if (aspect >= 1f) cropViewPx * aspect else cropViewPx
    val displayH = if (aspect >= 1f) cropViewPx else cropViewPx / aspect

    // pixels-per-display-unit
    val ppd = srcW.toFloat() / displayW

    // Size of the crop square in source pixels
    val cropPixels = (cropViewPx / scale * ppd).roundToInt()

    // Origin in source pixels. Offset is in display-unit pixels, relative to center.
    val originX = ((srcW - cropPixels) / 2f - offset.x / scale * ppd).roundToInt()
    val originY = ((srcH - cropPixels) / 2f - offset.y / scale * ppd).roundToInt()

    // Clamp to bitmap bounds
    val x = originX.coerceIn(0, srcW - 1)
    val y = originY.coerceIn(0, srcH - 1)
    val size = cropPixels.coerceIn(1, min(srcW - x, srcH - y))

    return Triple(x, y, size)
}

/**
 * Crop the source bitmap to the square region defined by [scale] and [offset],
 * returning JPEG bytes.
 */
internal fun cropBitmap(
    source: Bitmap,
    scale: Float,
    offset: Offset,
    cropViewPx: Float = 1f,
): ByteArray {
    val (x, y, size) = computeCropRect(source.width, source.height, scale, offset, cropViewPx)
    val cropped = Bitmap.createBitmap(source, x, y, size, size)
    val out = ByteArrayOutputStream()
    cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
    return out.toByteArray()
}

/**
 * TakePicture contract that requests the front (selfie) camera.
 *
 * There is no universal Android API to force front-camera mode via intent — different
 * OEMs respect different extras. We set all of the commonly-honored keys so the
 * request works on as many devices as possible. Camera apps that ignore every key
 * fall back to whichever camera the user had selected last, which matches the
 * default `TakePicture` behavior.
 */
class TakeSelfiePicture : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent {
        return super.createIntent(context, input).apply {
            // Keys honored by different camera apps / OEMs (Samsung, LG, HTC, stock).
            // Value 1 == front on legacy keys; 0 == front on the MediaStore key
            // (matches CameraCharacteristics.LENS_FACING_FRONT).
            putExtra("android.intent.extras.CAMERA_FACING", 1)
            putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            putExtra("com.google.assistant.extra.USE_FRONT_CAMERA", true)
        }
    }
}

/**
 * Decode a content URI to a Bitmap with EXIF orientation applied.
 */
fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (bitmap == null) return null

        // Read EXIF orientation
        val exifStream = context.contentResolver.openInputStream(uri) ?: return bitmap
        val exif = ExifInterface(exifStream)
        exifStream.close()
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )

        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        if (rotation == 0f) {
            bitmap
        } else {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    } catch (_: Exception) {
        null
    }
}
