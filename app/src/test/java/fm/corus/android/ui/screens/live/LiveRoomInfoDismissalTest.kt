package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h800dp")
class LiveRoomInfoDismissalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun outsideTapDismissesReadOnlyRoomInfo() {
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            CorusTheme(darkTheme = false) {
                if (visible) LiveRoomInfoSheet(
                    initialTitle = "Room", initialDescription = "High chic",
                    fallbackTitle = "clifton", editable = false,
                    onDismiss = { visible = false }, onSave = { _, _ -> },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("High chic").assertIsDisplayed()
        // Tap the dimmed background, above the half-expanded sheet.
        compose.onNodeWithContentDescription("Close sheet").performTouchInput {
            click(Offset(center.x, 80f))
        }
        compose.waitForIdle()
        compose.onNodeWithText("High chic").assertDoesNotExist()
    }
}
