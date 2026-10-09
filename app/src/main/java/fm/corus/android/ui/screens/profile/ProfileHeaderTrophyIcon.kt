package fm.corus.android.ui.screens.profile

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A toolbar trophy with the stroke weight of the neighboring navigation icons. */
@Composable
internal fun ProfileHeaderTrophyIcon(
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = profileHeaderTrophy,
        contentDescription = contentDescription,
        tint = tint,
        // The cup is top-heavy: optical alignment is slightly below geometric center.
        modifier = modifier.offset(y = 1.dp),
    )
}

private val profileHeaderTrophy: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProfileHeaderTrophy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // Keep the wider cup silhouette with a standard toolbar stroke weight.
            moveTo(6.5f, 3.5f)
            lineTo(17.5f, 3.5f)
            lineTo(17.5f, 8f)
            curveTo(17.5f, 12f, 15.4f, 14.5f, 12f, 14.5f)
            curveTo(8.6f, 14.5f, 6.5f, 12f, 6.5f, 8f)
            close()

            moveTo(6.5f, 5.5f)
            lineTo(3f, 5.5f)
            lineTo(3f, 8f)
            curveTo(3f, 10.8f, 4.7f, 12.3f, 7.6f, 12.3f)

            moveTo(17.5f, 5.5f)
            lineTo(21f, 5.5f)
            lineTo(21f, 8f)
            curveTo(21f, 10.8f, 19.3f, 12.3f, 16.4f, 12.3f)

            moveTo(12f, 14.5f)
            lineTo(12f, 20.5f)
            moveTo(8f, 20.5f)
            lineTo(16f, 20.5f)
        }
    }.build()
}
