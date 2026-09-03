package fm.corus.android.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PromptOverlayHostStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `inactive prompt cannot clear the active prompt`() {
        val host = PromptOverlayHostState()
        val spotifyPrompt = Any()
        val inactivePlaylistPrompt = Any()
        val request = request("Link Spotify")

        host.publish(spotifyPrompt, request)
        host.clear(inactivePlaylistPrompt)

        assertEquals(request, host.request)
    }

    @Test
    fun `disposing previous prompt cannot clear its replacement`() {
        val host = PromptOverlayHostState()
        val playlistPrompt = Any()
        val spotifyPrompt = Any()
        val spotifyRequest = request("Link Spotify")

        host.publish(playlistPrompt, request("Export playlist"))
        host.publish(spotifyPrompt, spotifyRequest)
        host.clear(playlistPrompt)

        assertEquals(spotifyRequest, host.request)
    }

    @Test
    fun `active prompt can clear its own request`() {
        val host = PromptOverlayHostState()
        val owner = Any()

        host.publish(owner, request("Link Spotify"))
        host.clear(owner)

        assertNull(host.request)
    }

    @Test
    fun `hosted card appears while another prompt is inactive`() {
        composeRule.setContent {
            CorusTheme(darkTheme = false) {
                val host = rememberPromptOverlayHostState()
                CompositionLocalProvider(LocalPromptOverlayHost provides host) {
                    Box {
                        CorusPromptOverlay(
                            visible = true,
                            title = "Link Spotify",
                            message = "Choose how you want to listen.",
                            buttons = listOf(
                                CorusPromptButton(label = "Link Spotify", onClick = {}),
                            ),
                        )
                        CorusPromptOverlay(
                            visible = false,
                            title = "Export playlist",
                            message = "Inactive prompt",
                            buttons = emptyList(),
                        )
                        PromptOverlayHost(host)
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Choose how you want to listen.", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    private fun request(title: String) = PromptOverlayRequest(
        visible = true,
        revealed = true,
        title = title,
        message = "Message",
        buttons = emptyList(),
        iconRes = null,
        footnote = null,
        modifier = Modifier,
        onBack = {},
    )
}
