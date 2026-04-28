package fm.corus.android.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Standard icon for screen headers / top app bars. Use inside an [IconButton]
 * (or [CorusHeaderIconButton]) to keep size + tint uniform across screens.
 */
@Composable
fun CorusHeaderIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = CorusColors.Text,
    size: Dp = CorusSpacing.iconLg,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

@Composable
fun CorusHeaderIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = CorusColors.Text,
    size: Dp = CorusSpacing.iconLg,
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

@Composable
fun CorusHeaderIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color = CorusColors.Text,
    size: Dp = CorusSpacing.iconLg,
) {
    IconButton(onClick = onClick) {
        CorusHeaderIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            size = size,
        )
    }
}

@Composable
fun CorusHeaderIconButton(
    onClick: () -> Unit,
    painter: Painter,
    contentDescription: String?,
    tint: Color = CorusColors.Text,
    size: Dp = CorusSpacing.iconLg,
) {
    IconButton(onClick = onClick) {
        CorusHeaderIcon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            size = size,
        )
    }
}
