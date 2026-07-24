package fm.corus.android.ui.screens.destination

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DestinationPhotoTapTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val viewPhotoLabel: String
        get() = context.getString(R.string.photo_viewer_open_cd)

    private fun hasClickLabel(label: String) = SemanticsMatcher("click label = $label") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    @Test
    fun `artist hero card tap fires the callback`() {
        var taps = 0
        composeRule.setContent {
            ArtistHeroCard(
                heroImage = "file:///hero.jpg",
                artistName = "Tame Impala",
                artistLabel = "Artist",
                onTap = { taps++ },
            )
        }

        composeRule.onNodeWithContentDescription("Tame Impala").performClick()

        composeRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun `artist hero card exposes the View photo click label`() {
        composeRule.setContent {
            ArtistHeroCard(
                heroImage = "file:///hero.jpg",
                artistName = "Tame Impala",
                artistLabel = "Artist",
                onTap = {},
            )
        }

        composeRule.onNodeWithContentDescription("Tame Impala").assert(hasClickLabel(viewPhotoLabel))
    }

    @Test
    fun `immersive hero tap fires the callback while expanded`() {
        var taps = 0
        composeRule.setContent {
            ImmersiveArtistHero(
                heroImage = "file:///hero.jpg",
                artistName = "Tame Impala",
                artistLabel = "Artist",
                onTap = { taps++ },
                tapEnabled = true,
            )
        }

        composeRule.onNodeWithContentDescription("Tame Impala").performClick()

        composeRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun `immersive hero exposes no click action and fires nothing once the bar owns the surface`() {
        var taps = 0
        composeRule.setContent {
            ImmersiveArtistHero(
                heroImage = "file:///hero.jpg",
                artistName = "Tame Impala",
                artistLabel = "Artist",
                onTap = { taps++ },
                tapEnabled = false,
            )
        }

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Tame Impala").performClick()

        composeRule.runOnIdle { assertEquals(0, taps) }
    }

    @Test
    fun `director photo card tap delivers exactly the rendered url`() {
        val url = "https://image.tmdb.org/t/p/h632/dir.jpg"
        var received: String? = null
        composeRule.setContent {
            DirectorPhotoCard(
                photo = url,
                directorName = "Denis Villeneuve",
                onTap = { received = it },
            )
        }

        composeRule.onNodeWithContentDescription("Denis Villeneuve").performClick()

        composeRule.runOnIdle { assertEquals(url, received) }
    }

    @Test
    fun `imageless director card exposes no click action`() {
        composeRule.setContent {
            DirectorPhotoCard(
                photo = null,
                directorName = "Denis Villeneuve",
                onTap = {},
            )
        }

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
