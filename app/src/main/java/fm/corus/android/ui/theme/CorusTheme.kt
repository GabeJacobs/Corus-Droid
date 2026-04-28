package fm.corus.android.ui.theme

import android.app.Activity
import android.view.View
import android.view.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = CorusColors.Accent,
    onPrimary = Color.White,
    secondary = LightCorusPalette.secondary,
    onSecondary = Color.White,
    background = LightCorusPalette.background,
    onBackground = LightCorusPalette.text,
    surface = LightCorusPalette.background,
    onSurface = LightCorusPalette.text,
    surfaceVariant = LightCorusPalette.cardBackground,
    onSurfaceVariant = LightCorusPalette.secondary,
    outline = LightCorusPalette.divider,
    error = CorusColors.Error,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = CorusColors.Accent,
    onPrimary = Color.White,
    secondary = DarkCorusPalette.secondary,
    onSecondary = Color.White,
    background = DarkCorusPalette.background,
    onBackground = DarkCorusPalette.text,
    surface = DarkCorusPalette.background,
    onSurface = DarkCorusPalette.text,
    surfaceVariant = DarkCorusPalette.cardBackground,
    onSurfaceVariant = DarkCorusPalette.secondary,
    outline = DarkCorusPalette.divider,
    error = CorusColors.Error,
    onError = Color.White,
)

val LocalCorusDarkTheme = compositionLocalOf { false }

@Composable
fun CorusTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkCorusPalette else LightCorusPalette
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CorusSystemBars(darkTheme)

    CompositionLocalProvider(
        LocalCorusPalette provides palette,
        LocalCorusDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

/**
 * Applies Corus system bar appearance (light/dark icons) to whichever window owns the current
 * composition — the Activity window at the root, or a Dialog window inside a ModalBottomSheet /
 * Dialog. Call this from sheet/dialog content so its sub-window also gets correct icon colors.
 */
@Composable
fun CorusSystemBars(darkTheme: Boolean = LocalCorusDarkTheme.current) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.findOwningWindow() ?: return@SideEffect
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}

private fun View.findOwningWindow(): Window? {
    var parent = this.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    return (context as? Activity)?.window
}
