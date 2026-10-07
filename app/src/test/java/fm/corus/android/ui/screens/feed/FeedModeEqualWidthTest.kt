package fm.corus.android.ui.screens.feed

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedModeEqualWidthTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `all four feed tabs retain equal widths while controls fade`() {
        var page by mutableFloatStateOf(1f)
        compose.setContent {
            CorusTheme {
                Box(Modifier.width(393.dp)) {
                    FeedModeTabBar(listOf("following", "tasteMatches", "trending", "favorites"),
                        "tasteMatches", page, {}, prototypeEnabled = true, onTune = {})
                }
            }
        }
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        for (position in listOf(1f, .5f, 0f, 1.5f, 2f)) {
            compose.runOnIdle { page = position }
            val widths = compose.onAllNodes(tab).fetchSemanticsNodes().map { it.boundsInRoot.width }
            assertEquals(4, widths.size)
            widths.forEach { assertEquals("Unequal tab widths at page $position", widths.first(), it, 1f) }
        }
    }

    @Test fun `three feed tabs share available width equally`() {
        compose.setContent {
            CorusTheme {
                Box(Modifier.width(393.dp)) {
                    FeedModeTabBar(listOf("following", "tasteMatches", "trending"),
                        "tasteMatches", 1f, {}, prototypeEnabled = true, onTune = {})
                }
            }
        }
        val widths = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .fetchSemanticsNodes().map { it.boundsInRoot.width }
        assertEquals(3, widths.size)
        widths.forEach { assertEquals(widths.first(), it, 1f) }
    }

    private fun assertLocalizedLabelsFit() {
        compose.setContent {
            CorusTheme {
                Box(Modifier.width(393.dp).testTag("equal-tabs")) {
                    FeedModeTabBar(listOf("following", "tasteMatches", "trending", "favorites"),
                        "tasteMatches", 1f, {}, prototypeEnabled = true, onTune = {})
                }
            }
        }
        // Draw the real Compose owner before checking the rendered glyph layout.
        val node = compose.onNodeWithTag("equal-tabs").fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val bitmap = Bitmap.createBitmap(bounds.width.roundToInt(), bounds.height.roundToInt(), Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            val canvas = Canvas(bitmap)
            canvas.translate(-bounds.left, -bounds.top)
            (node.root as ViewRootForTest).view.draw(canvas)
        }
        System.getProperty("corus.forYouEvidence")?.let { directory ->
            val locale = org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.locales[0].toLanguageTag()
            File(directory, "tabs-$locale.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true)
        assertEquals(4, texts.fetchSemanticsNodes().size)
        repeat(4) { index ->
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            texts[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val result = results.single()
            assertEquals(1, result.lineCount)
            // A paragraph may occupy the available width while fill=false
            // sizes Text to its glyphs. Check the glyph line, allowing pixel rounding.
            assertTrue("Label clips: ${result.layoutInput.text}",
                result.getLineLeft(0) >= -1f && result.getLineRight(0) <= result.size.width + 1f)
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            useUnmergedTree = true).onFirst().assertWidthIsEqualTo(28.dp)
    }

    @Test fun `English retains Your Mix with room for controls`() {
        assertLocalizedLabelsFit()
        compose.onNodeWithText("Your Mix").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "de-w393dp-h852dp")
    fun `German labels fit with controls`() = assertLocalizedLabelsFit()

    @Test @Config(qualifiers = "es-w393dp-h852dp")
    fun `Spanish labels fit with controls`() = assertLocalizedLabelsFit()

    @Test @Config(qualifiers = "fr-w393dp-h852dp")
    fun `French labels fit with controls`() = assertLocalizedLabelsFit()

    @Test @Config(qualifiers = "it-w393dp-h852dp")
    fun `Italian labels fit with controls`() = assertLocalizedLabelsFit()

    @Test @Config(qualifiers = "ja-w393dp-h852dp")
    fun `Japanese labels fit with controls`() = assertLocalizedLabelsFit()

    @Test @Config(qualifiers = "ko-w393dp-h852dp")
    fun `Korean labels fit with controls`() = assertLocalizedLabelsFit()

    @Test @Config(qualifiers = "pt-rBR-w393dp-h852dp")
    fun `Portuguese labels fit with controls`() = assertLocalizedLabelsFit()

    @Test @Config(qualifiers = "b+zh+Hans-w393dp-h852dp")
    fun `Chinese labels fit with controls`() = assertLocalizedLabelsFit()
}
