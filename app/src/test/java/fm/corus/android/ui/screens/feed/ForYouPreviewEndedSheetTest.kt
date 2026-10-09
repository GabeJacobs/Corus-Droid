package fm.corus.android.ui.screens.feed

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import fm.corus.android.service.ForYouPreviewEndedEvent
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp")
class ForYouPreviewEndedSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `Continue only dismisses and does not open Club`() {
        var dismissed = 0
        var club = 0
        val visible = mutableStateOf(true)
        val tracking = mutableListOf<ForYouPreviewEndedEvent>()
        compose.setContent { CorusTheme {
            if (visible.value) ForYouPreviewEndedSheet(true, { dismissed++; visible.value = false }, { club++ }, tracking::add)
        } }
        compose.onNodeWithText("Your Balanced and Stay Close preview has ended.").assertIsDisplayed()
        compose.onNodeWithText("You’re now using Eclectic.").assertIsDisplayed()
        compose.onNodeWithText("Try Corus Club for Free").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Your Balanced and Stay Close preview has ended.")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(24.sp, layouts.single().layoutInput.style.fontSize)
        compose.onNodeWithTag("for_you_preview_ended_continue").performClick()
        compose.waitUntil { dismissed == 1 }
        compose.runOnIdle {
            assertEquals(0, club)
            assertEquals(listOf("your_mix_preview_ended_shown", "your_mix_preview_ended_dismissed"), tracking.map { it.name })
            assertEquals(mapOf("has_club_trial" to 1L, "reason" to "continue"), tracking.last().params)
        }
    }

    @Test fun `ineligible offer joins Club after the notice dismisses`() {
        val events = mutableListOf<String>()
        val tracking = mutableListOf<ForYouPreviewEndedEvent>()
        val visible = mutableStateOf(true)
        compose.setContent { CorusTheme(darkTheme = true) {
            if (visible.value) ForYouPreviewEndedSheet(false, { events.add("dismiss"); visible.value = false }, { events.add("club") }, tracking::add)
        } }
        compose.onNodeWithText("Join Corus Club").assertIsDisplayed()
        compose.onNodeWithText("Try Corus Club for Free").assertDoesNotExist()
        compose.onNodeWithTag("for_you_preview_ended_club").performClick()
        compose.waitUntil { events.size == 2 }
        compose.runOnIdle {
            assertEquals(listOf("dismiss", "club"), events)
            assertEquals(listOf("your_mix_preview_ended_shown", "your_mix_preview_ended_club_tapped"), tracking.map { it.name })
            assertEquals(mapOf("has_club_trial" to 0L), tracking.last().params)
        }
    }

    @Test fun `recomposition does not repeat impression and removal records passive dismissal`() {
        val visible = mutableStateOf(true)
        val trial = mutableStateOf(true)
        val tracking = mutableListOf<ForYouPreviewEndedEvent>()
        compose.setContent { CorusTheme {
            if (visible.value) ForYouPreviewEndedSheet(trial.value, {}, {}, tracking::add)
        } }
        compose.onNodeWithText("Try Corus Club for Free").assertIsDisplayed()
        compose.runOnIdle { trial.value = false }
        compose.onNodeWithText("Join Corus Club").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, tracking.size); visible.value = false }
        compose.waitUntil { tracking.size == 2 }
        compose.runOnIdle {
            assertEquals("your_mix_preview_ended_dismissed", tracking.last().name)
            assertEquals(mapOf("has_club_trial" to 1L, "reason" to "dismiss"), tracking.last().params)
        }
    }
}
