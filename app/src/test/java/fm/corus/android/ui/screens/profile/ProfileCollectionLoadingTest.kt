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
        val model = model(pendingCounts)
        openCollection(model)
        compose.onNodeWithTag("collection_header_skeleton").assertIsDisplayed()
        compose.onNodeWithText("First to share").assertDoesNotExist()
        compose.onNodeWithText("No trophies or gifts yet").assertDoesNotExist()
        compose.onNodeWithText("No music trophies yet").assertDoesNotExist()
        compose.runOnIdle { pendingCounts.complete(CollectionCounts(0, 0)) }
        compose.onNodeWithText("No trophies or gifts yet").assertIsDisplayed()
        compose.onNodeWithTag("collection_header_skeleton").assertDoesNotExist()
    }

    @Test fun `a known empty profile opens directly to empty while refreshing counts`() {
        val pendingCounts = CompletableDeferred<CollectionCounts>()
        val model = model(pendingCounts, CollectionCounts(0, 0))
        openCollection(model)
        compose.onNodeWithText("No trophies or gifts yet").assertIsDisplayed()
        compose.onNodeWithTag("collection_header_skeleton").assertDoesNotExist()
        compose.onNodeWithText("First to share").assertDoesNotExist()
        verifyBlocking(model) { summary("owner") }
        verifyBlocking(model, never()) { trophies(any(), any(), anyOrNull()) }
    }

    @Test fun `zero trophies with unknown gifts still shows a skeleton`() {
        val pendingCounts = CompletableDeferred<CollectionCounts>()
        openCollection(model(pendingCounts, CollectionCounts(0, null)))
        compose.onNodeWithTag("collection_header_skeleton").assertIsDisplayed()
        compose.onNodeWithText("No trophies or gifts yet").assertDoesNotExist()
        compose.runOnIdle { pendingCounts.complete(CollectionCounts(0, 0)) }
        compose.onNodeWithText("No trophies or gifts yet").assertIsDisplayed()
    }

    @Test fun `a previously empty profile displays newly earned trophies after refreshing`() {
        val pendingCounts = CompletableDeferred<CollectionCounts>()
        val model = model(pendingCounts, CollectionCounts(0, 0), CollectionPage(
            listOf(CollectionItem("post", "A song", "An artist", "", "track", 0)), null,
        ))
        openCollection(model)
        compose.onNodeWithText("No trophies or gifts yet").assertIsDisplayed()
        compose.runOnIdle { pendingCounts.complete(CollectionCounts(1, 0)) }
        compose.onNodeWithText("No trophies or gifts yet").assertDoesNotExist()
        compose.onNodeWithText("A song").assertIsDisplayed()
    }

    private fun model(pendingCounts: CompletableDeferred<CollectionCounts>, cachedCounts: CollectionCounts? = null, page: CollectionPage = CollectionPage(emptyList(), null)): ProfileCollectionViewModel {
        val flags = mock<RemoteConfigService> {
            on { revision } doReturn MutableStateFlow(0)
            on { booksEnabled } doReturn false
        }
        return mock<ProfileCollectionViewModel> {
            on { viewer } doReturn "viewer"
            on { this.flags } doReturn flags
            on { visible("owner") } doReturn true
            on { cachedSummary("owner") } doReturn cachedCounts
            onBlocking { summary("owner") } doSuspendableAnswer { pendingCounts.await() }
            onBlocking { trophies(eq("owner"), any(), isNull()) } doReturn page
        }
    }

    private fun openCollection(model: ProfileCollectionViewModel) {
        compose.setContent { CorusTheme(darkTheme = false) { ProfileCollectionButton("owner", model = model, onFeed = { _, _ -> }) } }
        compose.onNodeWithContentDescription("Trophy Case").performClick()
        compose.waitForIdle()
    }
}
