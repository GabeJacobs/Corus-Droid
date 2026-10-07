package fm.corus.android.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusTheme
import java.io.File
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileInvitationSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `empty flagged profile keeps legacy preview and advertises the collage`() {
        compose.setContent {
            CorusTheme(darkTheme = false) {
                Box(Modifier.width(360.dp).height(640.dp).background(Color.White).testTag("invitation-sheet")) {
                    ShareMediaSheet(ShareMediaSubject.Profile(ShareProfileSubject("fixture", "farleythethird", "farleythethird", null)),
                        recentContacts = emptyList(), searchResults = emptyList(), isSearching = false,
                        isLoadingContacts = false, onSearchQueryChange = {}, onSendToUser = { _, _ -> },
                        onDismiss = {}, isOwnProfile = true, profileSharingV2 = true)
                }
            }
        }
        compose.onNodeWithText("Share your profile").assertIsDisplayed()
        compose.onNodeWithText("Copy Link").assertIsDisplayed()
        compose.onNodeWithText("Share your taste as a collage").assertIsDisplayed()
        compose.onNodeWithText("Post 9 more coruses to unlock.").assertIsDisplayed()
        compose.onNodeWithText("Blue").assertDoesNotExist()
        compose.onNodeWithText("Link preview").assertDoesNotExist()
        compose.onNodeWithText("Light").assertIsDisplayed()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Copy Link").assertIsDisplayed()
        capture("sheet-legacy-teaser")
    }

    @Test fun `flag off keeps the legacy empty profile sheet`() {
        compose.setContent {
            CorusTheme(darkTheme = false) {
                ShareMediaSheet(ShareMediaSubject.Profile(ShareProfileSubject("fixture", "farleythethird", "farleythethird", null)),
                    recentContacts = emptyList(), searchResults = emptyList(), isSearching = false,
                    isLoadingContacts = false, onSearchQueryChange = {}, onSendToUser = { _, _ -> },
                    onDismiss = {}, isOwnProfile = true, profileSharingV2 = false)
            }
        }
        compose.onNodeWithText("Link preview").assertDoesNotExist()
        compose.onNodeWithText("Find me on Corus").assertDoesNotExist()
        compose.onNodeWithText("Light").assertIsDisplayed()
        compose.onNodeWithText("Dark").assertIsDisplayed()
    }

    private fun capture(name: String) {
        val directory = System.getProperty("corus.invitationEvidence") ?: return
        val node = compose.onNodeWithTag("invitation-sheet").fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val bitmap = Bitmap.createBitmap(bounds.width.roundToInt(), bounds.height.roundToInt(), Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            val canvas = Canvas(bitmap)
            canvas.translate(-bounds.left, -bounds.top)
            (node.root as ViewRootForTest).view.draw(canvas)
        }
        File(directory).mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
