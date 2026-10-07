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
import androidx.compose.runtime.mutableStateOf
import fm.corus.android.ui.theme.CorusTheme
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
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
    private val sheetVisible = mutableStateOf(true)
    private fun sheet(count: Int, loaded: Int = count.coerceAtMost(28)) {
        compose.setContent {
            CorusTheme(darkTheme = false) {
                Box(Modifier.width(360.dp).height(640.dp).background(Color.White).testTag("profile-sheet")) {
                    if (sheetVisible.value) ShareMediaSheet(ShareMediaSubject.Profile(ShareProfileSubject("fixture", "gabe", "Gabe", null,
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

    @Test fun `grid switches keep the existing preview and blue background while replacement renders`() {
        val largeRender = CompletableDeferred<Unit>()
        val smallRender = CompletableDeferred<Unit>()
        var renders = 0
        val profile = ShareProfileSubject("transition", "gabe", "Gabe", null,
            artworkUrls = List(16) { "" }, postCount = 16)
        val art = PreparedProfileShareArt(List(16) { index ->
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.rgb(30 + index * 7, 40, 100))
            }
        })
        compose.setContent {
            CorusTheme(darkTheme = false) {
                Box(Modifier.width(360.dp).height(640.dp).background(Color.White).testTag("profile-sheet")) {
                    ProfileSharingV2Sheet(profile, instagramEnabled = false, analytics = null,
                        renderPreview = { context, subject, _, layout, background ->
                            when (++renders) {
                                2 -> largeRender.await()
                                3 -> smallRender.await()
                            }
                            renderProfileShareStory(context, subject, art, layout, background, transparent = true)
                        })
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Instagram Story preview").fetchSemanticsNodes().isNotEmpty()
        }
        val original = previewPixels()
        compose.onNodeWithText("4 × 4").performClick()
        compose.onNodeWithText("4 × 4").assertIsSelected()
        compose.onNodeWithContentDescription("Instagram Story preview").assertIsDisplayed()
        compose.onNodeWithText("Loading your grid…").assertDoesNotExist()
        compose.onNodeWithText("X").assertIsNotEnabled()
        val pendingLarge = previewPixels()
        assertTrue("Old content and background stay pixel-identical during render", original.sameAs(pendingLarge))
        assertEquals(ProfileStoryBackground.CORUS_BLUE.color, pendingLarge.getPixel(pendingLarge.width / 2, 10))
        compose.runOnIdle { largeRender.complete(Unit) }
        compose.waitForIdle()
        val large = previewPixels()
        compose.onNodeWithText("X").assertIsEnabled()
        assertFalse("New 4x4 content replaces the old 3x3", original.sameAs(large))
        compose.onNodeWithText("3 × 3").performClick()
        compose.onNodeWithContentDescription("Instagram Story preview").assertIsDisplayed()
        compose.onNodeWithText("X").assertIsNotEnabled()
        val pendingSmall = previewPixels()
        assertTrue(large.sameAs(pendingSmall))
        compose.runOnIdle { smallRender.complete(Unit) }
        compose.waitForIdle()
        val small = previewPixels()
        assertTrue("Switching back restores the same 3x3 composition", original.sameAs(small))
        capture("grid-switch-sheet")
        listOf(original, pendingLarge, large, pendingSmall, small).forEach { it.recycle() }
        art.slots.forEach { it?.recycle() }
    }

    @Test fun `locked layouts explain exact remaining count without switching or exporting`() {
        sheet(9)
        compose.onNodeWithText("Full").performClick()
        compose.onNodeWithText("Post 19 more coruses to unlock Full").assertIsDisplayed()
        compose.onNodeWithText("3 × 3").assertIsSelected()
        compose.onNodeWithText("5 × 5").performClick()
        compose.onNodeWithText("Post 16 more coruses to unlock 5 × 5").assertIsDisplayed()
        compose.onNodeWithText("3 × 3").assertIsSelected()
        compose.onNodeWithText("4 × 4").performClick()
        compose.onNodeWithText("Post 7 more coruses to unlock 4 × 4").assertIsDisplayed()
        compose.onNodeWithText("3 × 3").assertIsSelected()
        assertEquals(listOf(19, 16, 7), discoveries.map { it["posts_needed"] })
        assertEquals(listOf("full", "5x5", "4x4"), discoveries.map { it["layout"] })
        assertFalse(events.any { it["action"] in listOf("grid_changed", "export_started") })
        compose.onNodeWithText("Copy Link").assertIsDisplayed()
        capture("locked-layout-sheet")
    }

    @Test fun `available options descend from Full through five by five and blue defaults`() {
        sheet(28)
        val full = compose.onNodeWithText("Full").fetchSemanticsNode().boundsInRoot
        val extraLarge = compose.onNodeWithText("5 × 5").fetchSemanticsNode().boundsInRoot
        val large = compose.onNodeWithText("4 × 4").fetchSemanticsNode().boundsInRoot
        val small = compose.onNodeWithText("3 × 3").fetchSemanticsNode().boundsInRoot
        assertTrue(full.left < extraLarge.left && extraLarge.left < large.left && large.left < small.left)
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

    @Test fun `five by five becomes selectable at 25 without changing default or unlocking Full`() {
        sheet(25)
        compose.onNodeWithText("3 × 3").assertIsSelected()
        compose.onNodeWithText("5 × 5").performClick()
        compose.onNodeWithText("5 × 5").assertIsSelected()
        compose.waitUntil(10_000) { events.any { it["action"] == "preview_ready" && it["layout"] == "5x5" } }
        assertEquals("5x5", events.last { it["action"] == "grid_changed" }["layout"])
        compose.onNodeWithText("Full").performClick()
        compose.onNodeWithText("Post 3 more coruses to unlock Full").assertIsDisplayed()
        compose.onNodeWithText("5 × 5").assertIsSelected()
        capture("5x5-sheet")
    }

    @Test fun `24 posts leaves five by five locked and explains the last post needed`() {
        sheet(24)
        compose.onNodeWithText("5 × 5").performClick()
        compose.onNodeWithText("Post 1 more corus to unlock 5 × 5").assertIsDisplayed()
        compose.onNodeWithText("3 × 3").assertIsSelected()
        assertEquals(1, discoveries.single()["posts_needed"])
        assertFalse(events.any { it["action"] == "grid_changed" })
    }

    @Test fun `actual controls and copied link emit one session with safe funnel context`() {
        // Keep artwork pending so particle animation cannot drive the test clock.
        // This also proves link sharing remains usable while media is loading.
        sheet(25, loaded = 0)
        compose.onNodeWithText("5 × 5").performClick()
        compose.onNodeWithText("Purple").performClick()
        compose.onNodeWithText("Rain").performClick()
        compose.onNodeWithText("Snow").performClick()
        compose.onNodeWithText("Snow").performClick()
        compose.onNodeWithText("Copy Link").performClick()
        assertEquals(1, events.count { it["action"] == "opened" })
        assertEquals("5x5", events.last { it["action"] == "grid_changed" }["layout"])
        assertEquals("purple", events.last { it["action"] == "background_changed" }["background"])
        assertEquals(listOf("rain", "snow", "none"), events.filter { it["action"] == "effect_changed" }.map { it["effect"] })
        val handoff = events.last { it["action"] == "handoff_result" }
        assertEquals("copy_link", handoff["destination"]); assertEquals("copied", handoff["result"])
        assertEquals("link", handoff["format"]); assertEquals("5x5", handoff["layout"])
        val session = events.first()["share_session_id"]
        compose.runOnIdle { sheetVisible.value = false }
        compose.waitForIdle()
        assertEquals(1, events.count { it["action"] == "dismissed" })
        assertTrue(events.all { it["share_session_id"] == session })
        assertTrue(events.all { "username" !in it && "url" !in it && "profile_user_id" !in it })
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

    private fun previewPixels(): Bitmap {
        val node = compose.onNodeWithContentDescription("Instagram Story preview").fetchSemanticsNode()
        val bounds = node.boundsInRoot
        return Bitmap.createBitmap(bounds.width.roundToInt(), bounds.height.roundToInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
            compose.runOnIdle {
                val canvas = Canvas(bitmap)
                canvas.translate(-bounds.left, -bounds.top)
                (node.root as ViewRootForTest).view.draw(canvas)
            }
        }
    }
}
