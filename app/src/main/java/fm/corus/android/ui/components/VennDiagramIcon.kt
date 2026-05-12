package fm.corus.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Two overlapping circles forming a Venn diagram. Matches the iOS
 * `VennDiagramIcon` and the web SVG version so the brand element looks
 * identical across all three platforms.
 *
 * @param size Overall icon bounding box.
 * @param color Stroke + fill color.
 * @param shadedIntersection When true, fills the lens-shaped overlap with a
 *   semi-transparent version of [color] — the most literal "in common" reading.
 *   Used on the teaser pill and the taste-match sheet hero.
 * @param stripedRight When true, the right circle is filled with diagonal
 *   stripes — used to distinguish "artist in common" from "song in common".
 */
@Composable
fun VennDiagramIcon(
    size: Dp = 20.dp,
    color: Color,
    modifier: Modifier = Modifier,
    shadedIntersection: Boolean = false,
    stripedRight: Boolean = false,
) {
    Canvas(modifier = modifier.size(size)) {
        val sizePx = this.size.width
        val radius = sizePx * 0.72f / 2f
        val strokeWidth = 1.6.dp.toPx()
        val centerY = sizePx / 2f
        val leftCenterX = sizePx * (0.5f - 0.14f)
        val rightCenterX = sizePx * (0.5f + 0.14f)

        // 1. Filled lens (intersection of the two circles) — draw a filled
        // left circle clipped to the right circle's bounding shape so only the
        // overlap region is painted.
        if (shadedIntersection) {
            val rightClip = Path().apply {
                addOval(
                    Rect(
                        left = rightCenterX - radius,
                        top = centerY - radius,
                        right = rightCenterX + radius,
                        bottom = centerY + radius,
                    )
                )
            }
            clipPath(rightClip, clipOp = ClipOp.Intersect) {
                drawCircle(
                    color = color.copy(alpha = 0.25f),
                    radius = radius,
                    center = Offset(leftCenterX, centerY),
                )
            }
        }

        // 2. Diagonal stripes inside the right circle.
        if (stripedRight) {
            val rightClip = Path().apply {
                addOval(
                    Rect(
                        left = rightCenterX - radius + 1f,
                        top = centerY - radius + 1f,
                        right = rightCenterX + radius - 1f,
                        bottom = centerY + radius - 1f,
                    )
                )
            }
            val step = maxOf(2f, sizePx * 0.14f)
            clipPath(rightClip, clipOp = ClipOp.Intersect) {
                var x = -sizePx
                while (x < sizePx * 2f) {
                    drawLine(
                        color = color,
                        start = Offset(x, 0f),
                        end = Offset(x + sizePx, sizePx),
                        strokeWidth = 1f,
                    )
                    x += step
                }
            }
        }

        // 3. Two outline circles on top.
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(leftCenterX, centerY),
            style = Stroke(width = strokeWidth),
        )
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(rightCenterX, centerY),
            style = Stroke(width = strokeWidth),
        )
    }
}
