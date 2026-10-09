package fm.corus.android.ui.screens.profile

import android.app.Application
import androidx.activity.ComponentDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.FlairStyle
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.ui.theme.CorusTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditProfileNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun draftModel(hasUnsavedEdits: Boolean = true): EditProfileViewModel {
        val user = CymbalUser(id = "fixture", username = "gabe", displayName = "Gabe")
        val subscriptions = mock<SubscriptionRepository> {
            on { hasFullAccessFlow } doReturn MutableStateFlow(false)
        }
        return mock {
            on { profile } doReturn MutableStateFlow(user)
            on { displayName } doReturn MutableStateFlow(if (hasUnsavedEdits) "Unsaved name" else user.displayName)
            on { username } doReturn MutableStateFlow(user.username)
            on { bio } doReturn MutableStateFlow("")
            on { website } doReturn MutableStateFlow("")
            on { showTrophies } doReturn MutableStateFlow(true)
            on { showCityOnProfile } doReturn MutableStateFlow(true)
            on { tabPreferences } doReturn MutableStateFlow(user.tabPreferences(false))
            on { usernameState } doReturn MutableStateFlow(EditProfileViewModel.UsernameState.IDLE)
            on { usernameInvalidReason } doReturn MutableStateFlow(null)
            on { isSaving } doReturn MutableStateFlow(false)
            on { saveError } doReturn MutableStateFlow(null)
            on { hasUnsavedChanges } doReturn hasUnsavedEdits
            on { styleSelections } doReturn MutableStateFlow(StyleSelections())
            on { latestTrackPost } doReturn MutableStateFlow(null)
            on { latestMoviePost } doReturn MutableStateFlow(null)
            on { hasTrackPosts } doReturn MutableStateFlow(false)
            on { hasMoviePosts } doReturn MutableStateFlow(false)
            on { isStyleSaving } doReturn MutableStateFlow(false)
            on { subscriptionRepository } doReturn subscriptions
        }
    }

    @Test fun `customize keeps unsaved edits without asking to discard`() {
        val model = draftModel()
        compose.setContent { CorusTheme(darkTheme = false) { EditProfileScreen(viewModel = model) } }
        compose.onNodeWithText("Customize Profile Style").performClick()
        compose.onNodeWithText("Unsaved Changes").assertDoesNotExist()
        compose.onNodeWithText("Choose Badge Flair").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Unsaved name").assertIsDisplayed()
        verify(model, times(1)).loadProfile()
    }

    @Test fun `closing edit profile protects the draft`() {
        val model = draftModel()
        compose.setContent { CorusTheme(darkTheme = false) { EditProfileScreen(viewModel = model) } }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Unsaved Changes").assertIsDisplayed()
        compose.onNodeWithText("Keep Editing").performClick()
        compose.onNodeWithText("Unsaved name").assertIsDisplayed()
    }

    @Test fun `customization slides in from the right and slides back out`() {
        val model = draftModel()
        compose.setContent { CorusTheme(darkTheme = false) { EditProfileScreen(viewModel = model) } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Customize Profile Style").performClick()
        compose.mainClock.advanceTimeBy(96)
        val enteringX = compose.onNodeWithText("Choose Badge Flair").getUnclippedBoundsInRoot().left.value
        compose.mainClock.advanceTimeBy(400)
        val settledX = compose.onNodeWithText("Choose Badge Flair").getUnclippedBoundsInRoot().left.value
        assertTrue("Customization should move left from the right edge: $enteringX -> $settledX", enteringX > settledX + 20f)

        compose.onNodeWithContentDescription("Back").performClick()
        compose.mainClock.advanceTimeBy(96)
        val exitingX = compose.onNodeWithText("Choose Badge Flair").getUnclippedBoundsInRoot().left.value
        assertTrue("Back should move customization toward the right edge: $settledX -> $exitingX", exitingX > settledX + 20f)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Choose Badge Flair").assertDoesNotExist()
        compose.onNodeWithText("Unsaved name").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test fun `system back from customization returns to the draft without a discard prompt`() {
        val model = draftModel()
        compose.setContent { CorusTheme(darkTheme = false) { EditProfileScreen(viewModel = model) } }
        compose.onNodeWithText("Customize Profile Style").performClick()
        compose.runOnIdle {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithText("Unsaved name").assertIsDisplayed()
        compose.onNodeWithText("Unsaved Changes").assertDoesNotExist()
    }

    @Test fun `successful profile save closes the sheet`() {
        val model = draftModel()
        var dismissed = false
        doAnswer {
            (model.isSaving as MutableStateFlow<Boolean>).value = true
            it.getArgument<() -> Unit>(0).invoke()
            null
        }.whenever(model).save(any())
        compose.setContent {
            CorusTheme(darkTheme = false) { EditProfileScreen(viewModel = model, onBack = { dismissed = true }) }
        }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun `unchanged profile has no done or save action`() {
        val model = draftModel(hasUnsavedEdits = false)
        compose.setContent { CorusTheme(darkTheme = false) { EditProfileScreen(viewModel = model) } }
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test fun `editing and reverting hides save without showing done`() {
        val model = draftModel(hasUnsavedEdits = false)
        compose.setContent { CorusTheme(darkTheme = false) { EditProfileScreen(viewModel = model) } }
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.runOnIdle { (model.displayName as MutableStateFlow<String>).value = "New name" }
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.runOnIdle { (model.displayName as MutableStateFlow<String>).value = "Gabe" }
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

    @Test fun `applying style changes returns to done without another profile save`() {
        val model = draftModel(hasUnsavedEdits = false)
        val styles = model.styleSelections as MutableStateFlow<StyleSelections>
        styles.value = StyleSelections(profileFlair = FlairStyle.CHECKMARK)
        doAnswer {
            styles.value = it.getArgument(0)
            it.getArgument<() -> Unit>(1).invoke()
            null
        }.whenever(model).saveStyleSelections(any(), any())
        val showEditor = mutableStateOf(true)
        compose.setContent {
            CorusTheme(darkTheme = false) {
                if (showEditor.value) EditProfileScreen(viewModel = model, onBack = { showEditor.value = false })
            }
        }
        compose.onNodeWithText("Customize Profile Style").performClick()
        compose.onNodeWithText("None").performClick()
        compose.onNodeWithText("Save Changes").performClick()
        compose.onNodeWithText("Done").assertIsEnabled()
        compose.onNodeWithText("Save").assertDoesNotExist()
        verify(model).saveStyleSelections(eq(StyleSelections()), any())
        compose.runOnIdle { (model.displayName as MutableStateFlow<String>).value = "New name" }
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.runOnIdle { (model.displayName as MutableStateFlow<String>).value = "Gabe" }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertTrue(!showEditor.value) }
        verify(model, never()).save(any())
        compose.runOnIdle { showEditor.value = true }
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

    @Test fun `applying an unchanged style does not show done`() {
        val model = draftModel(hasUnsavedEdits = false)
        compose.setContent { CorusTheme(darkTheme = false) { EditProfileScreen(viewModel = model) } }
        compose.onNodeWithText("Customize Profile Style").performClick()
        compose.onNodeWithText("Save Changes").performClick()
        compose.onNodeWithText("Done").assertDoesNotExist()
        verify(model, never()).saveStyleSelections(any(), any())
    }
}
