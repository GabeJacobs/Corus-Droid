package fm.corus.android.ui.screens.profile

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusFont
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProfileCollectionLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun musicSkeletonMatchesLoadedTile() = verifyTile("track")
    @Test fun filmSkeletonMatchesLoadedTile() = verifyTile("movie")
    @Test fun bookSkeletonMatchesLoadedTile() = verifyTile("book")
    @Test fun skeletonMatchesWithLargeText() = verifyTile("track", fontScale = 1.8f)

    private fun verifyTile(media: String, fontScale: Float = 1f) {
        val loading = mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Box(Modifier.width(110.dp)) {
                    if (loading.value) CollectionTileSkeleton(media, Modifier.testTag("tile"))
                    else CollectionTileLayout(
                        media = media,
                        modifier = Modifier.testTag("tile"),
                        artwork = { Box(Modifier.fillMaxSize()) },
                        title = { Text("A title that truncates", Modifier.testTag("title"), style = CorusFont.songTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        subtitle = { Text("An artist that truncates", Modifier.testTag("subtitle"), style = CorusFont.artistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        date = { Text("October 8, 2026", Modifier.testTag("date"), style = CorusFont.timestamp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        val skeleton = composeRule.onNodeWithTag("tile").getUnclippedBoundsInRoot()
        val lines = composeRule.onAllNodes(hasText(" ") and hasAnyAncestor(hasTestTag("tile")), useUnmergedTree = true)
        val skeletonLines = (0..2).map { lines[it].getUnclippedBoundsInRoot() }
        composeRule.runOnIdle { loading.value = false }
        val loaded = composeRule.onNodeWithTag("tile").getUnclippedBoundsInRoot()
        assertEquals("The next grid row must not move after loading", (skeleton.bottom - skeleton.top).value, (loaded.bottom - loaded.top).value, .1f)
        listOf("title", "subtitle", "date").forEachIndexed { index, tag ->
            val line = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertEquals("$tag position", skeletonLines[index].top.value, line.top.value, .1f)
            assertEquals("$tag line height", (skeletonLines[index].bottom - skeletonLines[index].top).value, (line.bottom - line.top).value, .1f)
        }
    }
}
