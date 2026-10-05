package fm.corus.android.ui.screens.feed

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp")
class ForYouControlsTransitionTest {
    @get:Rule val compose = createComposeRule()
    private val modes = listOf("following", "tasteMatches", "trending", "favorites")
    private val description = "Tune Your Feed: Balanced"
    private fun tab() = compose.onNode(hasText("Your Mix") and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    private fun SemanticsNodeInteraction.widthDp(): Float = getUnclippedBoundsInRoot().let {
        it.right.value - it.left.value
    }
    private fun controlsWidth(): Float = tab().widthDp() -
        compose.onNodeWithText("Your Mix", useUnmergedTree = true).widthDp()

    @Test fun `retapping selected For You opens controls without selecting or scrolling the feed`() {
        var tunes = 0
        var selections = 0
        compose.setContent {
            CorusTheme {
                FeedModeTabBar(modes, "tasteMatches", 1f, { selections++ },
                    prototypeEnabled = true, onTune = { tunes++ })
            }
        }
        compose.onNodeWithText("Your Mix", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, tunes); assertEquals(0, selections) }
    }

    @Test fun `entering For You selects it and only a subsequent tap opens controls`() {
        var selected by mutableStateOf("following")
        var tunes = 0
        var selections = 0
        compose.setContent {
            CorusTheme {
                FeedModeTabBar(modes, selected, if (selected == "following") 0f else 1f,
                    { selections++; selected = it }, prototypeEnabled = true, onTune = { tunes++ })
            }
        }
        compose.onNodeWithText("Your Mix", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals("tasteMatches", selected); assertEquals(1, selections); assertEquals(0, tunes) }
        compose.onNodeWithText("Your Mix", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, selections); assertEquals(1, tunes) }
    }

    @Test fun `retapping ordinary Matches preserves the selection callback`() {
        var tunes = 0
        var selections = 0
        compose.setContent {
            CorusTheme {
                FeedModeTabBar(modes, "tasteMatches", 1f, { selections++ },
                    prototypeEnabled = false, onTune = { tunes++ })
            }
        }
        compose.onNode(hasText("Matches") and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.runOnIdle { assertEquals(1, selections); assertEquals(0, tunes) }
    }

    @Test fun `partially visible controls are absent from accessibility until For You is selected`() {
        var selected by mutableStateOf("following")
        var offset by mutableFloatStateOf(0.5f)
        var tunes = 0
        compose.setContent {
            CorusTheme {
                FeedModeTabBar(modes, selected, offset, { selected = it },
                    prototypeEnabled = true, onTune = { tunes++ })
            }
        }
        compose.onNodeWithContentDescription(description).assertDoesNotExist()
        compose.runOnIdle { selected = "tasteMatches"; offset = 1f }
        compose.onNodeWithContentDescription(description).performClick()
        compose.runOnIdle { assertEquals(1, tunes); selected = "trending"; offset = 1.5f }
        compose.onNodeWithContentDescription(description).assertDoesNotExist()
    }

    @Test fun `controls release exactly half their layout space during swipes from either side`() {
        var offset by mutableFloatStateOf(0f)
        compose.setContent {
            CorusTheme {
                Box(Modifier.width(393.dp).background(Color.White)) {
                    FeedModeTabBar(modes, "following", offset, {}, prototypeEnabled = true, onTune = {})
                }
            }
        }
        val collapsed = controlsWidth()
        compose.runOnIdle { offset = 0.5f }
        assertEquals(collapsed + 14f, controlsWidth(), 1f)
        compose.runOnIdle { offset = 1f }
        assertEquals(collapsed + 28f, controlsWidth(), 1f)
        compose.runOnIdle { offset = 1.5f }
        assertEquals(collapsed + 14f, controlsWidth(), 1f)
        compose.runOnIdle { offset = 2f }
        assertEquals(collapsed, controlsWidth(), 1f)
    }

    @Test fun `disabled system animations use selected tab instead of fractional fade`() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var selected by mutableStateOf("following")
        var offset by mutableFloatStateOf(0f)
        compose.setContent {
            CorusTheme {
                FeedModeTabBar(modes, selected, offset, {}, prototypeEnabled = true, onTune = {})
            }
        }
        val collapsed = controlsWidth()
        compose.runOnIdle { offset = 0.5f }
        assertEquals(collapsed, controlsWidth(), 1f)
        compose.runOnIdle { selected = "tasteMatches" }
        assertEquals(collapsed + 28f, controlsWidth(), 1f)
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        compose.runOnIdle { selected = "trending"; offset = 1.5f }
        assertEquals(collapsed, controlsWidth(), 1f)
        compose.runOnIdle {
            Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
        assertEquals(collapsed + 14f, controlsWidth(), 1f)
    }

    @Test fun `ordinary users keep Matches and have no controls button`() {
        compose.setContent {
            CorusTheme {
                FeedModeTabBar(modes, "tasteMatches", 1f, {},
                    prototypeEnabled = false, onTune = {})
            }
        }
        compose.onNodeWithText("Matches").assertIsDisplayed()
        compose.onNodeWithText("Your Mix").assertDoesNotExist()
        compose.onNodeWithContentDescription(description).assertDoesNotExist()
    }
}
