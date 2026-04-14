package fm.corus.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Two overlapping circles forming a Venn diagram, matching the iOS VennDiagramIcon.
 * Each circle is 62% of [size], offset left/right by 14%.
 */
@Composable
fun VennDiagramIcon(
    size: Dp = 20.dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val sizePx = this.size.width
        val radius = sizePx * 0.62f / 2f
        val strokeWidth = 1.6.dp.toPx()
        val centerY = sizePx / 2f
        val leftCenterX = sizePx * (0.5f - 0.14f)
        val rightCenterX = sizePx * (0.5f + 0.14f)

        drawCircle(
            color = color,
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(leftCenterX, centerY),
            style = Stroke(width = strokeWidth),
        )
        drawCircle(
            color = color,
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(rightCenterX, centerY),
            style = Stroke(width = strokeWidth),
        )
    }
}
