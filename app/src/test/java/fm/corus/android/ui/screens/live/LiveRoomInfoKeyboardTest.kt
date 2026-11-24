package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h800dp")
class LiveRoomInfoKeyboardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun keyboardDoneClearsFieldFocusAndKeepsRoomDraftOpen() {
        var saves = 0
        var dismissals = 0
        compose.setContent {
            CorusTheme(darkTheme = false) {
                LiveRoomInfoSheet(
                    initialTitle = "Room", initialDescription = "",
                    fallbackTitle = "gabe", editable = true,
                    onDismiss = { dismissals++ },
                    uploadImage = { error("No image selected") },
                    onSave = { _, _, _ -> saves++ },
                )
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Room"))
            .performTextReplacement("testing")
        compose.onNode(hasSetTextAction() and hasText("testing")).assertIsFocused().performImeAction()
        compose.runOnIdle {
            assertEquals(0, saves)
            assertEquals(0, dismissals)
        }
        compose.onNode(hasSetTextAction() and hasText("testing")).assertIsNotFocused()
    }
}
