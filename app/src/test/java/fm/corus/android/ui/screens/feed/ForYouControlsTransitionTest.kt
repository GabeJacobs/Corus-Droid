package fm.corus.android.ui.screens.feed

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
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
    private fun controlsWidth(): Float {
        val column = tab().getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText("Your Mix", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // The combined label and controls stay centered within a fixed column.
        return column.left.value + column.right.value - label.left.value - label.right.value
    }

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

    @Test fun `tapped destinations never reveal controls on an intermediate Your Mix page`() {
        var selected by mutableStateOf("following")
        lateinit var pager: PagerState
        var chromeOffset = 0f
        compose.setContent {
            CorusTheme {
                pager = rememberPagerState { modes.size }
                val scope = rememberCoroutineScope()
                chromeOffset = feedTabBarPagerOffset(pager)
                Column(Modifier.width(393.dp)) {
                    FeedModeTabBar(modes, selected, chromeOffset, { mode ->
                        selected = mode
                        scope.launch { pager.animateScrollToPage(modes.indexOf(mode), animationSpec = tween(1000)) }
                    }, prototypeEnabled = true, onTune = {})
                    HorizontalPager(pager, Modifier.height(100.dp)) { Box(Modifier.height(100.dp)) }
                }
            }
        }
        val collapsed = controlsWidth()
        compose.mainClock.autoAdvance = false
        // Both directions cross Your Mix, and a longer jump crosses two tabs.
        listOf("trending", "following", "favorites", "following").forEach { destination ->
            compose.onNodeWithText(when (destination) {
                "trending" -> "Trending"
                "favorites" -> "Favorites"
                else -> "Following"
            }, useUnmergedTree = true).performClick()
            compose.mainClock.advanceTimeBy(32)
            repeat(9) { sample ->
                compose.mainClock.advanceTimeBy(100)
                compose.runOnIdle {
                    if (sample == 0) assertTrue("The feed must still slide", pager.isScrollInProgress)
                    assertEquals(modes.indexOf(destination).toFloat(), chromeOffset, 0.01f)
                }
                assertEquals("No controls flash while crossing Your Mix", collapsed, controlsWidth(), 1f)
            }
            compose.mainClock.advanceTimeBy(200)
            compose.runOnIdle { assertEquals(modes.indexOf(destination), pager.settledPage) }
        }
        // Entering Your Mix shows its full controls while the feed is still moving.
        compose.onNodeWithText("Your Mix", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            assertTrue(pager.isScrollInProgress)
            assertTrue(pager.currentPage + pager.currentPageOffsetFraction < 0.5f)
            assertEquals(1f, chromeOffset, 0.01f)
        }
        assertEquals(collapsed + 28f, controlsWidth(), 1f)
        compose.mainClock.advanceTimeBy(1200)
    }

    @Test fun `finger swipe and its settling motion keep controls tied to feed position`() {
        lateinit var pager: PagerState
        var chromeOffset = 0f
        compose.setContent {
            CorusTheme {
                pager = rememberPagerState { modes.size }
                chromeOffset = feedTabBarPagerOffset(pager)
                Column(Modifier.width(393.dp)) {
                    FeedModeTabBar(modes, modes[pager.settledPage], chromeOffset, {},
                        prototypeEnabled = true, onTune = {})
                    HorizontalPager(pager, Modifier.height(100.dp).testTag("pager")) {
                        Box(Modifier.height(100.dp))
                    }
                }
            }
        }
        val collapsed = controlsWidth()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("pager").performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.4f, 0f), delayMillis = 200)
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            val feedOffset = pager.currentPage + pager.currentPageOffsetFraction
            assertTrue(feedOffset > 0.1f && feedOffset < 0.5f)
            assertEquals(feedOffset, chromeOffset, 0.01f)
        }
        assertEquals(collapsed + 28f * chromeOffset, controlsWidth(), 1f)
        compose.onNodeWithTag("pager").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            assertTrue(pager.isScrollInProgress)
            assertEquals(pager.currentPage + pager.currentPageOffsetFraction, chromeOffset, 0.01f)
        }
        compose.mainClock.advanceTimeBy(2000)
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
