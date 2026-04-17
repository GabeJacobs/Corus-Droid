package fm.corus.android.ui.components

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import java.io.File
import java.io.FileOutputStream

/**
 * Full-screen front-camera capture using CameraX. Used instead of the system camera intent
 * because Samsung One UI Camera ignores the front-camera intent extras.
 *
 * On capture success the JPEG is written to [outputFile] and [onCaptured] is invoked.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SelfieCaptureScreen(
    outputFile: File,
    onCaptured: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler { onCancel() }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val keyboardController = LocalSoftwareKeyboardController.current

    // Dismiss any open keyboard from the screen behind us so it doesn't cover the viewfinder.
    LaunchedEffect(Unit) { keyboardController?.hide() }

    // First-time request: ask the system directly, with no rationale screen flashing first.
    // Only show our prompt after the user has actively denied (shouldShowRationale == true).
    val needsSystemRequest =
        !cameraPermission.status.isGranted && !cameraPermission.status.shouldShowRationale
    LaunchedEffect(needsSystemRequest) {
        if (needsSystemRequest) cameraPermission.launchPermissionRequest()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            cameraPermission.status.isGranted -> CameraPreview(
                outputFile = outputFile,
                onCaptured = onCaptured,
                onCancel = onCancel,
            )
            cameraPermission.status.shouldShowRationale -> PermissionPrompt(
                onRequest = { cameraPermission.launchPermissionRequest() },
                onCancel = onCancel,
            )
            // else: black screen while the system permission dialog is up
        }
    }
}

@Composable
private fun CameraPreview(
    outputFile: File,
    onCaptured: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
        }
    }

    LaunchedEffect(controller, lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
    }

    var isCapturing by remember { mutableStateOf(false) }
    val previewView = remember {
        PreviewView(context).apply {
            this.controller = controller
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(CorusSpacing.xs),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Cancel",
                tint = Color.White,
            )
        }

        // Shutter button — large white circle with inner ring, matching common camera UI
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = CorusSpacing.xxxl)
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (isCapturing) 0.6f else 1f))
                .border(4.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                .clickable(enabled = !isCapturing) {
                    isCapturing = true
                    val previewAspect = if (previewView.width > 0 && previewView.height > 0) {
                        previewView.width.toFloat() / previewView.height.toFloat()
                    } else 0f
                    controller.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    saveRotatedJpeg(image, outputFile, previewAspect)
                                    onCaptured()
                                } finally {
                                    image.close()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isCapturing = false
                            }
                        },
                    )
                },
        )
    }
}

@Composable
private fun PermissionPrompt(
    onRequest: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(CorusSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Camera access needed",
            style = CorusFont.songTitleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Allow camera access to take a profile photo.",
            style = CorusFont.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = CorusSpacing.md),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CorusSpacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md, Alignment.CenterHorizontally),
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = CorusColors.CardBackground),
            ) {
                Text("Cancel", color = CorusColors.Text)
            }
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
            ) {
                Text("Allow", color = Color.White)
            }
        }
    }
}

/**
 * Decode the captured JPEG, apply the rotation hint from CameraX (which the JPEG's EXIF tag
 * may not encode reliably across devices), center-crop to [previewAspect] so the saved image
 * matches what the user saw in the FILL_CENTER viewfinder, and write to [outputFile].
 *
 * Pass [previewAspect] = 0f to skip cropping.
 */
private fun saveRotatedJpeg(image: ImageProxy, outputFile: File, previewAspect: Float) {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
    val rotation = image.imageInfo.rotationDegrees
    // Mirror horizontally so the saved selfie matches the mirrored viewfinder the user saw.
    val matrix = Matrix().apply {
        postRotate(rotation.toFloat())
        postScale(-1f, 1f)
    }
    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        .also { if (it != source) source.recycle() }
    val cropped = centerCropToAspect(rotated, previewAspect)
    FileOutputStream(outputFile).use { cropped.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    if (cropped != rotated) rotated.recycle()
    if (cropped != source && rotated != source) source.recycle()
}

/**
 * Center-crop [bitmap] so its width/height ratio equals [aspect]. If [aspect] <= 0 or the
 * bitmap already matches, returns [bitmap] unchanged.
 */
private fun centerCropToAspect(bitmap: Bitmap, aspect: Float): Bitmap {
    if (aspect <= 0f) return bitmap
    val srcAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    if (kotlin.math.abs(srcAspect - aspect) < 0.001f) return bitmap
    return if (srcAspect > aspect) {
        // Source is wider than target — trim the sides.
        val newWidth = (bitmap.height * aspect).toInt().coerceAtLeast(1)
        val xOffset = (bitmap.width - newWidth) / 2
        Bitmap.createBitmap(bitmap, xOffset, 0, newWidth, bitmap.height)
    } else {
        // Source is taller than target — trim the top/bottom.
        val newHeight = (bitmap.width / aspect).toInt().coerceAtLeast(1)
        val yOffset = (bitmap.height - newHeight) / 2
        Bitmap.createBitmap(bitmap, 0, yOffset, bitmap.width, newHeight)
    }
}
