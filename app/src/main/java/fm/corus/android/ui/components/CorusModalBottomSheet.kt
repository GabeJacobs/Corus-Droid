package fm.corus.android.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import fm.corus.android.ui.theme.LocalCorusPalette

/** Shared surface hierarchy for both Material sheets and the custom comments sheet. */
@Composable
internal fun CorusSheetTheme(content: @Composable () -> Unit) {
    val palette = LocalCorusPalette.current
    val scheme = MaterialTheme.colorScheme
    val background = CorusColors.SheetBackground
    val controls = CorusColors.SheetControlBackground
    val darkTheme = LocalCorusDarkTheme.current
    val sheetPalette = if (darkTheme) {
        palette.copy(
            background = background,
            cardBackground = controls,
            skeleton = Color(0xFF3A3A3C),
            divider = Color(0xFF3A3A3C),
        )
    } else {
        palette
    }
    CompositionLocalProvider(LocalCorusPalette provides sheetPalette) {
        MaterialTheme(
            colorScheme = if (darkTheme) scheme.copy(
                background = sheetPalette.background,
                surface = sheetPalette.background,
                surfaceVariant = sheetPalette.cardBackground,
                surfaceContainerLowest = sheetPalette.background,
                surfaceContainerLow = sheetPalette.background,
                surfaceContainer = sheetPalette.cardBackground,
                surfaceContainerHigh = sheetPalette.cardBackground,
                surfaceContainerHighest = sheetPalette.skeleton,
                outlineVariant = sheetPalette.divider,
            ) else scheme,
            content = content,
        )
    }
}

/** All app sheets share surface colors; callers retain their existing gestures and layout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorusModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    contentColor: Color = CorusColors.Text,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    CorusSheetTheme {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            sheetMaxWidth = sheetMaxWidth,
            shape = shape,
            containerColor = CorusColors.Background,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            scrimColor = scrimColor,
            dragHandle = dragHandle,
            contentWindowInsets = contentWindowInsets,
            properties = properties,
            content = content,
        )
    }
}
