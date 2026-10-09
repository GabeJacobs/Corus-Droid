package fm.corus.android.ui.screens.profile

/** Prefetch two rows without overlapping requests or automatically retrying failures. */
internal fun shouldLoadCollectionPage(
    hasCursor: Boolean,
    loading: Boolean,
    failed: Boolean,
    itemCount: Int,
    lastVisibleItem: Int,
): Boolean = hasCursor && !loading && !failed &&
    (itemCount == 0 || (lastVisibleItem >= 0 && lastVisibleItem >= itemCount - 1 - 6))
