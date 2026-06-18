package fm.corus.android.service

/**
 * Tracks the DM thread the user is currently viewing in the foreground.
 *
 * Set/cleared by [fm.corus.android.ui.screens.messaging.MessageThreadScreen]'s
 * lifecycle observer (set on START, cleared on STOP / dispose), so it is only
 * non-null while the thread is genuinely on-screen and the app is foregrounded.
 *
 * Read by:
 *  - [CorusFirebaseMessagingService] to suppress a push for the open thread.
 *  - [fm.corus.android.data.repository.UnreadCountsRepository] to exclude the
 *    open thread from the unread-message badge (so it never ticks up for a
 *    message you are already looking at).
 */
object ActiveThreadTracker {
    @Volatile
    var activeThreadId: String? = null
}
