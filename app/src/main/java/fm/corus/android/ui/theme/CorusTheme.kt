package fm.corus.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CorusColorScheme = lightColorScheme(
    primary = CorusColors.Accent,
    onPrimary = Color.White,
    secondary = CorusColors.Secondary,
    onSecondary = Color.White,
    background = CorusColors.Background,
    onBackground = CorusColors.Text,
    surface = CorusColors.Background,
    onSurface = CorusColors.Text,
    surfaceVariant = CorusColors.CardBackground,
    onSurfaceVariant = CorusColors.Secondary,
    outline = CorusColors.Divider,
    error = CorusColors.Error,
    onError = Color.White,
)

@Composable
fun CorusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CorusColorScheme,
        content = content,
    )
}
