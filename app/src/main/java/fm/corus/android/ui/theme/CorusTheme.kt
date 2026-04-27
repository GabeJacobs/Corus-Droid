package fm.corus.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

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

@Composable
fun CorusTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkCorusPalette else LightCorusPalette
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalCorusPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
