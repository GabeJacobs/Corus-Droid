package fm.corus.android.ui.screens.profile

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.theme.CorusTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileCollectionLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `an empty first page waits for fresh summary before showing an empty message`() {
        val pendingCounts = CompletableDeferred<CollectionCounts>()
        val flags = mock<RemoteConfigService> {
            on { revision } doReturn MutableStateFlow(0)
            on { booksEnabled } doReturn false
        }
        val model = mock<ProfileCollectionViewModel> {
            on { viewer } doReturn "viewer"
            on { this.flags } doReturn flags
            on { visible("owner") } doReturn true
            onBlocking { summary("owner") } doSuspendableAnswer { pendingCounts.await() }
            onBlocking { trophies(eq("owner"), any(), isNull()) } doReturn CollectionPage(emptyList(), null)
        }
        compose.setContent { CorusTheme(darkTheme = false) { ProfileCollectionButton("owner", model = model, onFeed = { _, _ -> }) } }
        compose.onNodeWithContentDescription("Trophy Case").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No trophies or gifts yet").assertDoesNotExist()
        compose.onNodeWithText("No music trophies yet").assertDoesNotExist()
        compose.runOnIdle { pendingCounts.complete(CollectionCounts(0, 0)) }
        compose.onNodeWithText("No trophies or gifts yet").assertIsDisplayed()
    }
}
