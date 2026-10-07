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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileShareV2SheetTest {
    @get:Rule val compose = createComposeRule()
    private val events = mutableListOf<Map<String, Any>>()
    private val discoveries = mutableListOf<Map<String, Any>>()
    private val themes = mutableListOf<ShareCardTheme>()
    private val backgrounds = mutableListOf<String>()
    private fun sheet(count: Int, loaded: Int = count.coerceAtMost(28)) {
        compose.setContent {
            CorusTheme(darkTheme = false) {
                Box(Modifier.width(360.dp).height(640.dp).background(Color.White).testTag("profile-sheet")) {
                    ShareMediaSheet(ShareMediaSubject.Profile(ShareProfileSubject("fixture", "gabe", "Gabe", null,
                        artworkUrls = List(loaded) { "" }, postCount = count)),
                        recentContacts = emptyList(), searchResults = emptyList(), isSearching = false, isLoadingContacts = false,
                        onSearchQueryChange = {}, onSendToUser = { _, _ -> }, onDismiss = {}, isOwnProfile = true, profileSharingV2 = true,
                        profileShareAnalytics = ProfileShareAnalytics("fixture", true, "action_row", { _, _ -> }, {}, { themes.add(it) },
                            onV2Event = { events.add(it) }, onDiscovery = { discoveries.add(it) }, onBackgroundChanged = { backgrounds.add(it) }))
                }
            }
        }
    }

    @Test fun `known Full eligibility selects Full before images arrive`() {
        sheet(28, loaded = 0)
        compose.onNodeWithText("Full").assertIsSelected()
        compose.onNodeWithText("3 × 3").assertIsNotSelected()
        assertEquals("full", events.first { it["action"] == "opened" }["layout"])
        assertFalse(events.any { it["action"] == "grid_changed" })
        compose.onNodeWithText("Disco").assertDoesNotExist()
        compose.onNodeWithText("Copy Link").assertIsDisplayed()
        capture("full-loading-sheet")
    }

    @Test fun `locked layouts explain exact remaining count without switching or exporting`() {
        sheet(9)
        compose.onNodeWithText("Full").performClick()
        compose.onNodeWithText("Post 19 more coruses to unlock Full").assertIsDisplayed()
        compose.onNodeWithText("3 × 3").assertIsSelected()
        compose.onNodeWithText("4 × 4").performClick()
        compose.onNodeWithText("Post 7 more coruses to unlock 4 × 4").assertIsDisplayed()
        compose.onNodeWithText("3 × 3").assertIsSelected()
        assertEquals(listOf(19, 7), discoveries.map { it["posts_needed"] })
        assertFalse(events.any { it["action"] in listOf("grid_changed", "export_started") })
        compose.onNodeWithText("Copy Link").assertIsDisplayed()
        capture("locked-layout-sheet")
    }

    @Test fun `available options have iOS order and blue is selected by default`() {
        sheet(28)
        val full = compose.onNodeWithText("Full").fetchSemanticsNode().boundsInRoot
        val large = compose.onNodeWithText("4 × 4").fetchSemanticsNode().boundsInRoot
        val small = compose.onNodeWithText("3 × 3").fetchSemanticsNode().boundsInRoot
        assertTrue(full.left < large.left && large.left < small.left)
        compose.onNodeWithText("Blue").assertIsSelected()
        assertTrue(themes.isEmpty()); assertTrue(backgrounds.isEmpty())
        compose.onNodeWithText("Purple").performClick()
        assertEquals(listOf("purple"), backgrounds)
        compose.onNodeWithText("White").performScrollTo().performClick()
        assertEquals(listOf(ShareCardTheme.LIGHT), themes)
        assertEquals("white", events.last { it["action"] == "background_changed" }["background"])
        compose.onNodeWithText("Copy Link").assertIsDisplayed()
        capture("full-sheet")
    }

    private fun capture(name: String) {
        val directory = System.getProperty("corus.profileShareEvidence") ?: return
        val node = compose.onNodeWithTag("profile-sheet").fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val bitmap = Bitmap.createBitmap(bounds.width.roundToInt(), bounds.height.roundToInt(), Bitmap.Config.ARGB_8888)
        compose.runOnIdle { val canvas = Canvas(bitmap); canvas.translate(-bounds.left, -bounds.top); (node.root as ViewRootForTest).view.draw(canvas) }
        File(directory).mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
