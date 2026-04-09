package fm.corus.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.delay

@Composable
fun TrophyCelebrationView(
    post: CymbalPost,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    var showTrophy by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showSparkles by remember { mutableStateOf(false) }

    val trophyScale by animateFloatAsState(
        targetValue = if (showTrophy) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "trophy-scale",
    )

    val sparkleRotation = rememberInfiniteTransition(label = "sparkle-rotation")
    val sparkleAngle by sparkleRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sparkle-angle",
    )

    LaunchedEffect(visible) {
        if (visible) {
            delay(100)
            showTrophy = true
            delay(300)
            showSparkles = true
            delay(200)
            showText = true
        } else {
            showTrophy = false
            showText = false
            showSparkles = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
            ) {
                // Trophy + sparkles
                Box(contentAlignment = Alignment.Center) {
                    // Sparkle ring
                    if (showSparkles) {
                        repeat(8) { i ->
                            val angle = sparkleAngle + (i * 45f)
                            val radians = Math.toRadians(angle.toDouble())
                            val radius = 60.dp
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700).copy(alpha = 0.7f),
                                modifier = Modifier
                                    .offset(
                                        x = (radius.value * kotlin.math.cos(radians)).dp,
                                        y = (radius.value * kotlin.math.sin(radians)).dp,
                                    )
                                    .size(12.dp)
                                    .rotate(angle),
                            )
                        }
                    }

                    // Trophy
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = "Trophy",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier
                            .size(72.dp)
                            .scale(trophyScale),
                    )
                }

                Spacer(modifier = Modifier.height(CorusSpacing.lg))

                // Text
                AnimatedVisibility(visible = showText, enter = fadeIn()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "FIRST TO SHARE!",
                            style = CorusFont.appTitle,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Text(
                            "You're the first on Corus to post this",
                            style = CorusFont.body,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(CorusSpacing.xxl))

                        // Album art or poster
                        val imageUrl = post.displayImageLargeURL ?: post.displayImageURL
                        if (imageUrl != null) {
                            val isMovie = post.isMovie
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .then(
                                        if (isMovie) Modifier.size(170.dp, 255.dp)
                                        else Modifier.size(240.dp)
                                    )
                                    .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
                                contentScale = ContentScale.Crop,
                            )
                        }

                        Spacer(modifier = Modifier.height(CorusSpacing.md))

                        // Song/film info
                        Text(
                            post.displayTitle,
                            style = CorusFont.songTitleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (post.displaySubtitle.isNotBlank()) {
                            Text(
                                post.displaySubtitle,
                                style = CorusFont.body,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Spacer(modifier = Modifier.height(CorusSpacing.xxl))

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.width(120.dp),
                        ) {
                            Text("NICE!", style = CorusFont.button, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
