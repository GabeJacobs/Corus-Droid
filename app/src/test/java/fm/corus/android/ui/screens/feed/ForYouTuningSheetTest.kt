package fm.corus.android.ui.screens.feed

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.domain.ForYouStayCloseProgress
import fm.corus.android.domain.ForYouTuningMode
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.theme.CorusTheme
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

    @Test fun `locked remote default has no Default badge matching iOS`() {
        compose.setContent {
            CorusTheme {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.BALANCED, ForYouTuningMode.STAY_CLOSE,
                        onApply = {}, onDismiss = {}, progress = ForYouStayCloseProgress(4))
                }
            }
        }
        compose.onNodeWithText("Locked").assertIsDisplayed()
        compose.onNodeWithText("Default").assertDoesNotExist()
    }

    @Test fun `tapping locked Stay Close explains the requirement without changing selection`() {
        var applied: ForYouTuningMode? = null
        compose.setContent {
            CorusTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.BALANCED, ForYouTuningMode.BALANCED,
                        onApply = { applied = it }, onDismiss = {}, progress = ForYouStayCloseProgress(4))
                }
            }
        }
        compose.onNodeWithText("4/5 posts shared").assertIsDisplayed()
        compose.onNodeWithText("Stay Close").performClick()
        compose.onNodeWithText("Unlock Stay Close").assertIsDisplayed()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("Share a song or film").assertDoesNotExist()
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertEquals(ForYouTuningMode.BALANCED, applied) }
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
        compose.onNodeWithText("Tune Your Feed").assertIsDisplayed()
        compose.onNodeWithText("Eclectic").assertIsDisplayed()
        compose.onNodeWithText("Balanced").assertIsDisplayed()
        compose.onNodeWithText("Default").assertIsDisplayed()
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
        compose.onNodeWithContentDescription("Close").performClick()
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
        compose.onNodeWithContentDescription("Tune Your Feed: Balanced").performClick()
        compose.runOnIdle { assertTrue(tuned) }
    }
    @Test fun `expired trial shows a Club lock and opens paywall without applying Stay Close`() {
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
        compose.onNodeWithText("Corus Club").assertIsDisplayed()
        compose.onNodeWithText("Unlock Stay Close with Corus Club.").assertIsDisplayed()
        compose.onNodeWithText("Stay Close").performClick()
        compose.runOnIdle { assertTrue(club); assertNull(applied) }
    }

    @Test fun `eligible free users see seven day preview disclosure and can apply Stay Close`() {
        var applied: ForYouTuningMode? = null
        compose.setContent {
            CorusTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticManager provides mock()) {
                    ForYouTuningSheet(ForYouTuningMode.BALANCED, ForYouTuningMode.BALANCED,
                        onApply = { applied = it }, onDismiss = {}, progress = ForYouStayCloseProgress(5))
                }
            }
        }
        compose.onNodeWithText("7-day free preview, then Corus Club.").assertIsDisplayed()
        compose.onNodeWithText("Stay Close").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertEquals(ForYouTuningMode.STAY_CLOSE, applied) }
    }

}
