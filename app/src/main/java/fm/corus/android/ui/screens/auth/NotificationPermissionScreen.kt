package fm.corus.android.ui.screens.auth

import android.graphics.BlurMaskFilter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import fm.corus.android.ui.util.PushNotificationPermission
import android.text.format.DateFormat as AndroidDateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val NOTIFICATION_PRIMER_PHONE_WIDTH = 364.dp
internal val NOTIFICATION_PRIMER_PHONE_HEIGHT = 500.dp
private val NOTIFICATION_PRIMER_DEVICE_WIDTH = 268.dp
private val NOTIFICATION_PRIMER_DEVICE_HEIGHT = 476.dp
private val NOTIFICATION_PRIMER_BANNER_WIDTH = 348.dp
private val NOTIFICATION_PRIMER_BANNER_TOP = 150.dp

/** Scale the lock-screen mock to leftover space without colliding with the
 *  pinned CTA. Caps below 1 — Android screens are typically wider than
 *  iPhone, and scale=1 made the 268dp device read as a wide slab. */
internal fun notificationPrimerPhoneScale(availableWidth: Dp, availableHeight: Dp): Float {
    if (availableWidth <= 0.dp || availableHeight <= 0.dp) return 0f
    return minOf(
        availableWidth / NOTIFICATION_PRIMER_PHONE_WIDTH,
        availableHeight / NOTIFICATION_PRIMER_PHONE_HEIGHT,
        0.9f,
    )
}

/**
 * Last onboarding step: explain why notifications matter, then fire the
 * system dialog only if they tap Allow. Not now skips straight to the feed
 * and marks the ask complete so MainTab does not re-prompt.
 */
@Composable
internal fun NotificationPermissionScreen(
    viewModel: SocialSetupViewModel,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    var isRequesting by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }
    val pushPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.analyticsService.logNotificationPermissionResult(granted)
        viewModel.markPushPermissionRequested()
        onFinished()
    }

    LaunchedEffect(Unit) {
        viewModel.analyticsService.logNotificationPermissionPrimerShown()
    }

    val finishWithoutSystemPrompt = skip@{
        if (isFinishing || isRequesting) return@skip
        isFinishing = true
        viewModel.analyticsService.logNotificationPermissionPrimerTapped("not_now")
        viewModel.finishPushPermissionPrimer(onFinished)
    }

    BackHandler(onBack = finishWithoutSystemPrompt)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CorusColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = CorusSpacing.xxl, top = 60.dp, end = CorusSpacing.xxl),
        ) {
            Text(
                stringResource(R.string.onboarding_notif_title),
                style = CorusFont.custom(900, 28),
                color = CorusColors.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(CorusSpacing.sm))
            Text(
                stringResource(R.string.onboarding_notif_subtitle),
                style = CorusFont.bodyMedium,
                color = CorusColors.Secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp),
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = CorusSpacing.md, start = CorusSpacing.xxxl, end = CorusSpacing.xxxl),
            contentAlignment = Alignment.TopCenter,
        ) {
            val scale = notificationPrimerPhoneScale(maxWidth, maxHeight)
            Box(
                modifier = Modifier.size(
                    width = NOTIFICATION_PRIMER_PHONE_WIDTH * scale,
                    height = NOTIFICATION_PRIMER_PHONE_HEIGHT * scale,
                ),
                contentAlignment = Alignment.Center,
            ) {
                LockScreenNotificationPreview(
                    modifier = Modifier
                        .size(
                            width = NOTIFICATION_PRIMER_PHONE_WIDTH,
                            height = NOTIFICATION_PRIMER_PHONE_HEIGHT,
                        )
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }

        Button(
            onClick = {
                if (isRequesting) return@Button
                isRequesting = true
                viewModel.analyticsService.logNotificationPermissionPrimerTapped("allow")
                viewModel.markPushPermissionRequested()
                if (PushNotificationPermission.shouldRequestPushPermission(context)) {
                    pushPermissionLauncher.launch(PushNotificationPermission.permission)
                } else {
                    onFinished()
                }
            },
            enabled = !isRequesting,
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            contentPadding = PaddingValues(vertical = CorusSpacing.lg),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xxl),
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
        ) {
            Text(
                stringResource(R.string.onboarding_notif_allow),
                style = CorusFont.button,
                color = Color.White,
            )
        }
        Text(
            stringResource(R.string.onboarding_notif_not_now),
            style = CorusFont.captionMedium,
            color = CorusColors.Secondary,
            modifier = Modifier
                .padding(top = CorusSpacing.lg, bottom = CorusSpacing.xxxl)
                .clickable(enabled = !isRequesting && !isFinishing, onClick = finishWithoutSystemPrompt),
        )
    }
}

@Composable
private fun LockScreenNotificationPreview(
    modifier: Modifier = Modifier,
) {
    val isDark = LocalCorusDarkTheme.current
    val phoneFill = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE8E8ED)
    val clockColor = if (isDark) Color(0xFF8E8E93) else CorusColors.Text.copy(alpha = 0.45f)
    val punchHoleFill = if (isDark) Color.Black.copy(alpha = 0.65f) else Color(0xFF3A3A3C)
    val bannerFill = if (isDark) Color(0xFF3A3A3C) else Color.White
    val barColor = if (isDark) Color(0xFF55555A) else Color(0xFFC7C7CC)
    val locale = Locale.getDefault()
    val dateText = remember(locale) {
        SimpleDateFormat(
            AndroidDateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"),
            locale,
        ).format(Date())
    }
    val timeText = remember(locale) {
        SimpleDateFormat("h:mm", locale).format(Date())
    }

    val phoneShape = RoundedCornerShape(48.dp)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        // Mask the whole device (fill + stroke) so the bottom dissolves
        // instead of leaving a hard rounded outline. Matches iOS, but
        // reaches clear before the corner so the lip is gone.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(
                    width = NOTIFICATION_PRIMER_DEVICE_WIDTH,
                    height = NOTIFICATION_PRIMER_DEVICE_HEIGHT,
                )
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black,
                                0.48f to Color.Black,
                                0.70f to Color.Black.copy(alpha = 0.28f),
                                0.86f to Color.Transparent,
                                1f to Color.Transparent,
                            ),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(phoneShape)
                    .border(1.dp, CorusColors.Divider, phoneShape)
                    .background(phoneFill),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(punchHoleFill),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 48.dp),
                ) {
                    Text(
                        dateText,
                        color = clockColor,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    )
                    Text(
                        timeText,
                        color = clockColor,
                        style = TextStyle(
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-1).sp,
                            lineHeight = 72.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = NOTIFICATION_PRIMER_BANNER_TOP)
                .width(NOTIFICATION_PRIMER_BANNER_WIDTH),
            contentAlignment = Alignment.TopCenter,
        ) {
            NotificationBanner(
                bannerFill,
                barColor,
                scale = 0.88f,
                offsetY = 40.dp,
                alpha = if (isDark) 0.45f else 0.52f,
                showIcon = false,
                isDark = isDark,
            )
            NotificationBanner(
                bannerFill,
                barColor,
                scale = 0.94f,
                offsetY = 18.dp,
                alpha = if (isDark) 0.68f else 0.74f,
                showIcon = false,
                isDark = isDark,
            )
            NotificationBanner(
                bannerFill,
                barColor,
                scale = 1f,
                offsetY = 0.dp,
                alpha = 1f,
                showIcon = true,
                isDark = isDark,
            )
        }
    }
}

@Composable
private fun NotificationBanner(
    bannerFill: Color,
    barColor: Color,
    scale: Float,
    offsetY: Dp,
    alpha: Float,
    showIcon: Boolean,
    isDark: Boolean,
) {
    val bannerShape = RoundedCornerShape(24.dp)
    val shadowColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.22f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .offset(y = offsetY)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = false
            }
            .fillMaxWidth()
            .bannerDropShadow(color = shadowColor, cornerRadius = 24.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(bannerShape)
            .background(bannerFill)
            .then(
                if (isDark) Modifier
                else Modifier.border(0.5.dp, Color.Black.copy(alpha = 0.08f), bannerShape),
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        if (showIcon) {
            Image(
                painter = painterResource(R.drawable.corus_app_icon),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column {
            Box(
                modifier = Modifier
                    .width(108.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(156.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor.copy(alpha = 0.7f)),
            )
        }
    }
}

/** iOS-style soft drop shadow (radius 16, y 6). Compose `shadow()` is
 *  clipped by the banner's graphicsLayer, so the white card vanished
 *  against the page background. */
private fun Modifier.bannerDropShadow(color: Color, cornerRadius: Dp): Modifier = drawBehind {
    val blurPx = 16.dp.toPx()
    val offsetY = 6.dp.toPx()
    val radius = cornerRadius.toPx()
    val paint = Paint()
    val frameworkPaint = paint.asFrameworkPaint()
    frameworkPaint.isAntiAlias = true
    frameworkPaint.color = color.toArgb()
    frameworkPaint.maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
    drawIntoCanvas { canvas ->
        canvas.drawRoundRect(
            left = 0f,
            top = offsetY,
            right = size.width,
            bottom = size.height + offsetY,
            radiusX = radius,
            radiusY = radius,
            paint = paint,
        )
    }
}
