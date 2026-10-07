package fm.corus.android.ui.screens.auth

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Exercise the real modal sheet, including its platform dismissal affordances. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UserPreviewSheetLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var dismissals = 0

    private fun render() {
        val visible = mutableStateOf(true)
        val nowPlaying = mock<NowPlayingManager>()
        compose.setContent {
            CorusTheme(darkTheme = false) {
                if (visible.value) UserPreviewSheet(
                    user = CymbalUser(id = "preview-user", username = "listener", displayName = "Listener"),
                    usesRevisedDesign = true, posts = emptyList(), isLoading = false,
                    isLoadingMore = false, hasMore = false, isFollowed = false,
                    nowPlaying = nowPlaying, onFollow = {}, onLoadMore = {},
                    onDismiss = { dismissals++; visible.value = false },
                )
            }
        }
        // Allow the entrance animation and guarded sheet's resting-position observer to settle.
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    @Test fun `revised preview starts at the profile without a close button row`() {
        render()
        compose.onNodeWithText("Listener").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close player").assertDoesNotExist()
    }

    @Test fun `tapping outside dismisses the preview`() {
        render()
        // The scrim spans the window; its center is covered by the sheet. Tap its exposed top.
        compose.onNodeWithContentDescription("Close sheet").performTouchInput {
            click(Offset(40f, 120f))
        }
        compose.waitUntil(5_000) { dismissals == 1 }
        assertEquals(1, dismissals)
        compose.onNodeWithText("Listener").assertDoesNotExist()
    }

    @Test fun `dragging down dismisses the preview`() {
        render()
        compose.onNodeWithContentDescription("Drag handle").performTouchInput {
            swipe(start = center, end = center.copy(y = center.y + 650f), durationMillis = 350)
        }
        compose.waitForIdle()
        assertEquals(1, dismissals)
    }

    @Test fun `android back dismisses the preview`() {
        render()
        compose.runOnIdle { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, dismissals)
    }
}
