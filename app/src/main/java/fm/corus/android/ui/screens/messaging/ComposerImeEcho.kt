package fm.corus.android.ui.screens.messaging

/**
 * After Send, Compose TextField / IME can emit another `onValueChange` with
 * the outgoing string (or a slightly older prefix of it). Drop those so the
 * box stays empty — the Android analogue of iOS invalidating queued UITextView
 * syncs in `takeText()`.
 *
 * A brand-new first character after send is kept (so typing "H" after sending
 * "Hello" still works). Substantial prefixes match the iOS screenshot race
 * where the last line or two never made it into the stale snapshot.
 */
internal fun dropComposerImeEcho(sentText: String?, incoming: String): Boolean {
    if (sentText.isNullOrEmpty() || incoming.isEmpty()) return false
    if (incoming == sentText) return true
    return incoming.length > 1 &&
        sentText.startsWith(incoming) &&
        incoming.length * 2 >= sentText.length
}
