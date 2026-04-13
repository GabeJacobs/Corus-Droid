package fm.corus.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors

/**
 * Realistic spinning vinyl record for the Cymbal Club offer page.
 * Matches the iOS CymbalClubVinyl component.
 */
@Composable
fun CymbalClubVinyl(size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "vinyl_rotation",
    )

    Box(
        modifier = Modifier
            .size(size)
            .clipToBounds()
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        // Draw the vinyl disc with grooves and light reflection
        Canvas(modifier = Modifier.size(size)) {
            drawVinylDisc()
        }

        // Label area (center circle) — Corus Club color
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            // Label fill
            drawCircle(
                color = CorusColors.Club,
                radius = this.size.width * 0.18f,
                center = center,
            )
            // Label inner ring
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = this.size.width * 0.14f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
            )
        }

        // Cymbal logo on label
        Image(
            painter = painterResource(R.drawable.logo_no_background),
            contentDescription = null,
            modifier = Modifier.size(size * 0.22f),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.9f)),
        )
    }
}

private fun DrawScope.drawVinylDisc() {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.width / 2f

    // Outer disc
    drawCircle(
        color = Color(0xFF1A1A1A),
        radius = radius,
        center = center,
    )

    // Groove rings
    var fraction = 0.42f
    while (fraction <= 0.95f) {
        drawCircle(
            color = Color.White.copy(alpha = 0.06f),
            radius = radius * fraction,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5f),
        )
        fraction += 0.035f
    }

    // Light reflection sweep
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.06f),
                Color.Transparent,
                Color.Transparent,
                Color.Transparent,
                Color.Transparent,
                Color.Transparent,
            ),
            center = center,
        ),
        radius = radius * 0.95f,
        center = center,
    )
}
