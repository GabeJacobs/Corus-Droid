package fm.corus.android.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import fm.corus.android.service.ReferralStatus
import fm.corus.android.ui.theme.NunitoFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReferralTypographyTest {
    @get:Rule val compose = createComposeRule()

    private fun renderedStyles() = compose.onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().indices.map { index ->
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        )[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        layouts.single().layoutInput.style
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `invite headline fits one line at phone footer width`() {
        compose.setContent {
            Box(Modifier.width(312.dp)) { ReferralHeadline("Get 3 months of Corus Club") }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        )[0].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertTrue(!layouts.single().didOverflowWidth)
        assertTrue(layouts.single().getLineRight(0) <= layouts.single().size.width + 1f)
        assertEquals(NunitoFamily, layouts.single().layoutInput.style.fontFamily)
    }

    @Test fun `empty history title and explanation render with Corus Nunito`() {
        compose.setContent { ReferralHistoryEmptyState() }
        val styles = renderedStyles()
        assertEquals(2, styles.size)
        styles.forEach { assertEquals(NunitoFamily, it.fontFamily) }
        assertEquals(FontWeight.Medium, styles.first().fontWeight)
        assertEquals(FontWeight.Normal, styles.last().fontWeight)
    }

    @Test fun `Settings invite header and card use Nunito while the code stays monospaced`() {
        compose.setContent {
            Column {
                ReferralSettingsHeader("ABCDEFGH", onExplain = {})
                ReferralSettingsCard(ReferralStatus(enabled = true, eligible = true), onClick = {})
            }
        }
        val families = renderedStyles().map { it.fontFamily }
        assertTrue(families.size >= 5)
        assertEquals(1, families.count { it == FontFamily.Monospace })
        assertTrue(families.all { it == NunitoFamily || it == FontFamily.Monospace })
    }
}
