package fm.corus.android.ui.screens.notifications

import fm.corus.android.data.model.CymbalNotification

/** Result of [mergedNotificationList]: the new list, plus whether the stale
 * tail was dropped because the incoming window didn't overlap the current
 * list (used only for logging at the call site — the listener collector
 * re-arms hasMore after every merge regardless). */
data class NotificationMergeResult(
    val list: List<CymbalNotification>,
    val droppedStaleTail: Boolean,
)

/**
 * Pure list-merge for the Activity feed, extracted from
 * [NotificationsViewModel] so the gap guard is unit-testable (and to mirror
 * iOS `mergedNotificationList`). `incoming` is the newest-N server window
 * from the real-time listener; `current` is what's on screen, possibly
 * including older paginated items. Items inside the incoming window are
 * reconciled to server order; older paginated items are kept untouched.
 *
 * Gap guard: if `incoming` shares no ids with `current`, more than a full
 * window arrived since the list was built (e.g. overnight while the process
 * was frozen) and the two ranges are not contiguous — appending the old items
 * would render a silent hole between the fresh head and the stale tail (the
 * 07-14 missing-notifications report). The result is then just `incoming`;
 * load-more paginates from its bottom and refetches the rest.
 */
fun mergedNotificationList(
    current: List<CymbalNotification>,
    incoming: List<CymbalNotification>,
): NotificationMergeResult {
    if (current.isEmpty()) return NotificationMergeResult(incoming, droppedStaleTail = false)

    val incomingIds = incoming.map { it.id }.toSet()

    if (incoming.isNotEmpty() && current.none { it.id in incomingIds }) {
        return NotificationMergeResult(incoming, droppedStaleTail = true)
    }

    // Split: items in the incoming window vs older paginated items; head comes
    // from incoming order (includes new + updated items), tail is untouched.
    val tailItems = current.filter { it.id !in incomingIds }
    return NotificationMergeResult(incoming + tailItems, droppedStaleTail = false)
}

/**
 * Ids in [incoming] that were not already on screen — real-time arrivals
 * after the initial snapshot. Empty [currentIds] is treated as the first
 * window (not "everything is brand new") so the lastSeen cutoff can decide
 * highlighting instead. Mirrors iOS `brandNewIds`.
 */
fun brandNewNotificationIds(
    currentIds: Set<String>,
    incoming: List<CymbalNotification>,
): Set<String> {
    if (currentIds.isEmpty()) return emptySet()
    return incoming.map { it.id }.filter { it !in currentIds }.toSet()
}

/**
 * After a keyed LazyColumn prepend, Compose keeps the previously-first
 * visible row on screen, which strands newer rows above the fold. Pin to
 * the new head only when the user was still looking at the old head (they
 * thought they were at the top).
 */
fun shouldPinActivityToNewHead(
    previousHeadId: String?,
    newHeadId: String?,
    firstVisibleItemKey: String?,
): Boolean {
    if (newHeadId == null || previousHeadId == null || newHeadId == previousHeadId) return false
    return firstVisibleItemKey == previousHeadId
}
