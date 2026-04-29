package fm.corus.android.ui.screens.profile

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard for the profile-grid scroll restoration bug (tap a post,
 * tap back → returned to top of profile instead of the scroll position).
 *
 * Two pieces of UI state load-bear here. If either reverts to plain `remember`,
 * scroll restoration breaks for non-obvious reasons:
 *
 * - `isFeaturedArtReady` controls whether the post-grid items are emitted at
 *   all. If it isn't saveable, on pop-back it resets to `false`, the grid
 *   items branch emits nothing, and the LazyGrid clamps the saved index to
 *   whatever low index is reachable among only the header items.
 * - `selectedSegment` controls which list (Music / Film / Likes / Saves) is
 *   showing. If it isn't saveable, popping back from a non-default tab lands
 *   the user on Music with a stale scroll index that points into the wrong
 *   list.
 *
 * The LazyGrid header was also split into multiple `item(span = 3)` blocks
 * with stable keys so that `firstVisibleItemIndex` advances meaningfully
 * across the header instead of staying pinned at 0 with a giant offset that
 * gets clamped on restore. We assert the expected keys are present so the
 * structural split isn't accidentally collapsed back into one mega-item.
 */
class ProfileScreenScrollRestorationGuardTest {
    private val source = File(
        "src/main/java/fm/corus/android/ui/screens/profile/ProfileScreen.kt"
    ).readText()

    @Test
    fun `selectedSegment is rememberSaveable`() {
        assertTrue(
            "selectedSegment must use rememberSaveable so the active tab " +
                "survives navigation; otherwise pop-back lands on Music " +
                "regardless of which tab the user came from.",
            source.contains("var selectedSegment by rememberSaveable"),
        )
    }

    @Test
    fun `isFeaturedArtReady is rememberSaveable`() {
        assertTrue(
            "isFeaturedArtReady must use rememberSaveable; otherwise on " +
                "pop-back it resets to false, the grid-items branch emits " +
                "nothing, and the LazyGrid can't reach the saved scroll index.",
            source.contains("var isFeaturedArtReady by rememberSaveable"),
        )
    }

    @Test
    fun `header is split into keyed LazyGrid items`() {
        val expectedKeys = listOf(
            "header_row",
            "avatar_stats",
            "bio",
            "tabs",
            "featured",
        )
        for (key in expectedKeys) {
            assertTrue(
                "Profile header must keep an `item(span = ..., key = \"$key\") " +
                    "{ ... }` block. Collapsing the header back into one " +
                    "giant item makes firstVisibleItemIndex stay 0 with a " +
                    "huge offset that gets clamped on restore.",
                source.contains("key = \"$key\""),
            )
        }
    }
}
