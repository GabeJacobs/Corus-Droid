package fm.corus.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import fm.corus.android.R
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

/**
 * Feed + own-profile compose `+`. One leading inset and hit box so the
 * glyph sits on the same edge when switching tabs (matches iOS).
 */
@Composable
fun ComposePlusButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(start = CorusSpacing.composePlusLeading)
            .size(CorusSpacing.composePlusSide)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.tab_cd_compose),
            tint = CorusColors.Secondary,
            modifier = Modifier.size(CorusSpacing.composePlusIcon),
        )
    }
}
