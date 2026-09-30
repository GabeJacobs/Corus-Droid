package fm.corus.android.ui.screens.messaging

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class InboxScrollAnchor(var threadIds: List<String>, var wasAtTop: Boolean)

/** Keep the absolute top visible when keyed rows are inserted or reordered.
 * LazyColumn normally follows the previous first row, hiding a new row above it.
 * Request the index before the next measure; preserve the anchor while browsing.
 */
@Composable
internal fun KeepInboxTopVisible(
    listState: LazyListState,
    threadIds: List<String>,
    enabled: Boolean,
) {
    val isAtTop = listState.firstVisibleItemIndex == 0 &&
        listState.firstVisibleItemScrollOffset == 0
    val previous = remember(listState) { InboxScrollAnchor(threadIds, isAtTop) }
    if (previous.threadIds != threadIds) {
        // Read the position from the previous list: keyed-item anchoring can
        // already have advanced firstVisibleItemIndex for the new list.
        val keepTop = enabled && previous.wasAtTop && !listState.isScrollInProgress
        previous.threadIds = threadIds
        previous.wasAtTop = keepTop || isAtTop
        if (keepTop) listState.requestScrollToItem(0)
    } else {
        previous.wasAtTop = isAtTop
    }
}
