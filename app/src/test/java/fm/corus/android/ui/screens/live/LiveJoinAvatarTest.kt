package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import fm.corus.android.service.DeepLinkDestination
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h800dp")
class LiveJoinAvatarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingRoomMetadataDoesNotFlashAnInitial() {
        val room = mutableStateOf<LiveRoom?>(null)
        compose.setContent {
            CorusTheme(darkTheme = false) {
                LiveRoomJoinSheet(
                    prompt = DeepLinkDestination.LiveRoom("room", hostName = "clifton"),
                    room = room.value,
                    close = {}, join = {},
                )
            }
        }
        compose.onNodeWithText("C").assertDoesNotExist()

        // Initials are appropriate once the server actually reports no photo.
        compose.runOnIdle {
            room.value = LiveRoom("room", mapOf("hostName" to "clifton", "status" to "live"))
        }
        compose.onNodeWithText("C").assertIsDisplayed()

        compose.runOnIdle {
            room.value = LiveRoom("room", mapOf(
                "hostName" to "clifton", "status" to "live",
                "hostAvatar" to "https://example.com/avatar.jpg",
            ))
        }
        compose.onNodeWithText("C").assertDoesNotExist()
    }
}
