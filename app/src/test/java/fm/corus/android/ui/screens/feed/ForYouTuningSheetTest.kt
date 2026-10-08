package fm.corus.android.ui.screens.feed

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.domain.ForYouStayCloseProgress
import fm.corus.android.domain.ForYouTuningMode
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.theme.CorusTheme
import fm.corus.android.ui.theme.CorusFont
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp")
class ForYouTuningSheetTest {
    @get:Rule val compose = createComposeRule()

    private fun showTuningSheet() {
        compose.setContent {
            CorusTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.BALANCED, ForYouTuningMode.BALANCED,
                        onApply = {}, onDismiss = {}, progress = ForYouStayCloseProgress(5))
                }
            }
        }
    }

    @Test fun `tuning sheet explains Your Mix using its current name`() {
        showTuningSheet()
        compose.onNodeWithText("Choose how Your Mix is tuned:").assertIsDisplayed()
        compose.onNodeWithText("The more you post, the better Your Mix understands your taste.").assertIsDisplayed()
    }

    @Test fun `tuning title renders with the standard screen title typography`() {
        showTuningSheet()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Tune Your Mix").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        val renderedStyle = layouts.single().layoutInput.style
        assertEquals(CorusFont.screenTitle.fontSize, renderedStyle.fontSize)
        assertEquals(CorusFont.screenTitle.fontWeight, renderedStyle.fontWeight)
    }

    @Test fun `locked remote default has no Default badge matching iOS`() {
        compose.setContent {
            CorusTheme {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.BALANCED, ForYouTuningMode.STAY_CLOSE,
                        onApply = {}, onDismiss = {}, progress = ForYouStayCloseProgress(4))
                }
            }
        }
        compose.onAllNodesWithText("Locked").assertCountEquals(2)
        compose.onNodeWithText("Default").assertDoesNotExist()
    }

    @Test fun `both posting locked modes explain the requirement and leave Eclectic selected`() {
        var applied: ForYouTuningMode? = null
        compose.setContent {
            CorusTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.ECLECTIC, ForYouTuningMode.BALANCED,
                        onApply = { applied = it }, onDismiss = {}, progress = ForYouStayCloseProgress(4))
                }
            }
        }
        compose.onNodeWithText("4/5 posts shared").assertDoesNotExist()
        compose.onAllNodesWithText("Requires at least 5 posts").assertCountEquals(2)
        for (mode in listOf("Balanced", "Stay Close")) {
            compose.onNodeWithText(mode).performClick()
            compose.onNodeWithText("Unlock $mode Mix").assertIsDisplayed()
            compose.onNodeWithText("Got it").performClick()
        }
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertEquals(ForYouTuningMode.ECLECTIC, applied) }
    }

    @Test fun `selection is a draft until Apply and all iOS choices are reachable`() {
        var applied: ForYouTuningMode? = null
        var dismissed = false
        compose.setContent {
            CorusTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.BALANCED, ForYouTuningMode.BALANCED,
                        onApply = { applied = it }, onDismiss = { dismissed = true }, progress = ForYouStayCloseProgress(5))
                }
            }
        }
        compose.onNodeWithText("Tune Your Mix").assertIsDisplayed()
        compose.onNodeWithText("Eclectic").assertIsDisplayed()
        compose.onNodeWithText("Balanced").assertIsDisplayed()
        compose.onNodeWithText("Default").assertDoesNotExist()
        compose.onNodeWithText("Stay Close").performClick()
        compose.runOnIdle { assertNull(applied); assertFalse(dismissed) }
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertEquals(ForYouTuningMode.STAY_CLOSE, applied); assertTrue(dismissed) }
    }

    @Test fun `closing the sheet discards the draft`() {
        var applied = false
        var dismissed = false
        compose.setContent {
            CorusTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.BALANCED, ForYouTuningMode.ECLECTIC,
                        onApply = { applied = true }, onDismiss = { dismissed = true })
                }
            }
        }
        compose.onNodeWithText("Eclectic").performClick()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .onFirst().performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.runOnIdle { assertFalse(applied); assertTrue(dismissed) }
    }

    @Test fun `For You tab and its tuning button are visible only for prototype users`() {
        var tuned = false
        compose.setContent {
            CorusTheme(darkTheme = false) {
                FeedModeTabBar(listOf("following", "tasteMatches", "trending"), "tasteMatches", 1f, {},
                    prototypeEnabled = true, onTune = { tuned = true })
            }
        }
        compose.onNodeWithText("Your Mix").assertIsDisplayed()
        compose.onNodeWithText("Matches").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tune Your Mix: Balanced").performClick()
        compose.runOnIdle { assertTrue(tuned) }
    }
    @Test fun `expired trial stays selected and opens Club only from the bottom button`() {
        var club = false
        var applied: ForYouTuningMode? = null
        compose.setContent {
            CorusTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.BALANCED, ForYouTuningMode.BALANCED,
                        onApply = { applied = it }, onDismiss = {},
                        progress = ForYouStayCloseProgress(5, serverPaywallLocked = true),
                        onClub = { club = true })
                }
            }
        }
        compose.onAllNodesWithText("Corus Club").assertCountEquals(2)
        compose.onNodeWithText("Requires at least 5 posts").assertDoesNotExist()
        compose.onNodeWithText("Stay Close").performClick()
        compose.runOnIdle { assertFalse(club); assertNull(applied) }
        compose.onNodeWithTag("for_you_apply").performClick()
        compose.runOnIdle { assertTrue(club); assertNull(applied) }
    }

    @Test fun `five posts hide the posting requirement and allow applying Stay Close`() {
        var applied: ForYouTuningMode? = null
        compose.setContent {
            CorusTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.BALANCED, ForYouTuningMode.BALANCED,
                        onApply = { applied = it }, onDismiss = {}, progress = ForYouStayCloseProgress(5))
                }
            }
        }
        compose.onNodeWithText("Requires at least 5 posts").assertDoesNotExist()
        compose.onNodeWithText("7-day free preview, then Corus Club.").assertDoesNotExist()
        compose.onNodeWithText("Stay Close").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertEquals(ForYouTuningMode.STAY_CLOSE, applied) }
    }

    @Test fun `expired Balanced with eligible Club trial selects first then offers Unlock for Free`() {
        var requested: ForYouTuningMode? = null
        var applied: ForYouTuningMode? = null
        compose.setContent {
            CorusTheme {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.ECLECTIC, ForYouTuningMode.BALANCED,
                        onApply = { applied = it }, onDismiss = {},
                        progress = ForYouStayCloseProgress(5, serverPaywallLocked = true),
                        hasClubIntroTrial = true, onClub = { requested = it })
                }
            }
        }
        compose.onNodeWithText("Balanced").performClick()
        compose.runOnIdle { assertNull(requested); assertNull(applied) }
        compose.onNodeWithText("Unlock for Free").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(ForYouTuningMode.BALANCED, requested); assertNull(applied) }
    }
    @Test fun `expired shared preview can still apply Eclectic without a paywall`() {
        var applied: ForYouTuningMode? = null
        var requested = false
        compose.setContent {
            CorusTheme {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.ECLECTIC, ForYouTuningMode.BALANCED,
                        onApply = { applied = it }, onDismiss = {},
                        progress = ForYouStayCloseProgress(5, serverPaywallLocked = true),
                        onClub = { requested = true })
                }
            }
        }
        compose.onNodeWithTag("for_you_apply").performClick()
        compose.runOnIdle { assertEquals(ForYouTuningMode.ECLECTIC, applied); assertFalse(requested) }
    }

    @Test
    @Config(qualifiers = "ja-w393dp-h852dp")
    fun `Japanese sheet uses translated mode names and Apply`() {
        showTuningSheet()
        compose.onNodeWithText("あなたのミックスを調整").assertIsDisplayed()
        compose.onNodeWithText("多彩").assertIsDisplayed()
        compose.onNodeWithText("バランス").assertIsDisplayed()
        compose.onNodeWithText("好みに近く").assertIsDisplayed()
        compose.onNodeWithText("適用").assertIsDisplayed()
        compose.onNodeWithText("Balanced").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "ko-w393dp-h852dp")
    fun `Korean unlock explanation keeps the translated mode name`() {
        compose.setContent {
            CorusTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.ECLECTIC, ForYouTuningMode.BALANCED,
                        onApply = {}, onDismiss = {}, progress = ForYouStayCloseProgress(4))
                }
            }
        }
        compose.onNodeWithText("균형 있게").performClick()
        compose.onNodeWithText("균형 있게 믹스 잠금 해제").assertIsDisplayed()
        compose.onNodeWithText("노래나 영화를 5개 공유하면 “균형 있게”와 “취향에 가깝게”를 사용할 수 있어요.").assertIsDisplayed()
        compose.onNodeWithText("Balanced").assertDoesNotExist()
    }


}
