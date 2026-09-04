package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.repository.MessageRepository

/** Whether a conversation may be put in front of the caller yet, or at all. */
enum class ThreadAccess { RESOLVING, OPEN, UNAVAILABLE }

/**
 * The one rule for whether the caller may see a conversation, mirroring the
 * server's refusal so both sides hide exactly the same ones. It answers for a
 * row of the inbox and for the conversation the user is trying to open, because
 * those are the same question asked twice.
 *
 * A block is stamped on the blocker's own mirror row for as long as the block
 * exists, which is the only word available for a block made on another device;
 * [blockedIds] is the device's own block set, which answers the instant the user
 * blocks and without waiting for the stamp to come back. A ban is not row data —
 * it is global, and would have to mean something different for a group's member
 * list — so it is resolved against the device's live banned set. Group rows
 * always survive: one blocked or banned member must not hide a conversation from
 * everyone else in it.
 */
internal fun mayShowThread(
    thread: CymbalThread,
    blockedIds: Set<String>,
    isBanned: (String) -> Boolean,
): Boolean =
    thread.isGroup ||
        (!thread.blocked && thread.otherUserId !in blockedIds && !isBanned(thread.otherUserId))

/**
 * Whether a 1:1 inbox row has a peer the list can name. `listThreads` joins
 * `otherUser` server-side and leaves it null when the account is gone or the
 * profile is incomplete; iOS drops those via `fetchVisibleUsers` hidden ids.
 * Groups always pass — their title does not depend on a single peer profile.
 */
internal fun hasDisplayableInboxPeer(thread: CymbalThread): Boolean =
    thread.isGroup || !thread.otherUser?.username.isNullOrBlank()

/**
 * Badge contribution of one inbox mirror row. Hidden 1:1 peers (banned,
 * blocked, or a missing account the inbox already dropped) must not count —
 * otherwise the envelope shows a number the user can never clear.
 */
internal fun unreadContribution(
    threadId: String,
    unreadCount: Int,
    isGroup: Boolean,
    otherUserId: String,
    blocked: Boolean,
    activeThreadId: String?,
    isHiddenPeer: (String) -> Boolean,
): Int {
    if (threadId == activeThreadId) return 0
    if (!isGroup && (blocked || (otherUserId.isNotEmpty() && isHiddenPeer(otherUserId)))) return 0
    return unreadCount
}

/**
 * Whether the conversation behind [row] may be opened. Nothing is drawn until
 * the answer is known, so no entry point — a tapped push, a deep link, a tap in
 * the app — can put a refused conversation on screen even for a frame, and a
 * block landing while it is open takes it away.
 *
 * A row that is merely missing from the local cache is not an answer: a cold
 * deep link has never held the row, and calling that unavailable would refuse
 * every conversation opened from a notification.
 *
 * Inbox-miss compose ([composeFromPeer]) is the exception: the peer card is
 * already known from the previous screen, so we paint it immediately while
 * getOrCreate runs — matching iOS — unless the device already knows the peer
 * is blocked or banned. A later row (or create failure) still overrides.
 */
internal fun resolveThreadAccess(
    row: MessageRepository.ThreadRowSnapshot?,
    blockedIds: Set<String>,
    isBanned: (String) -> Boolean,
    composeFromPeer: Boolean = false,
    composePeerUserId: String = "",
): ThreadAccess = when {
    row != null -> when {
        row.thread != null ->
            if (mayShowThread(row.thread, blockedIds, isBanned)) ThreadAccess.OPEN
            else ThreadAccess.UNAVAILABLE
        row.fromCache -> ThreadAccess.RESOLVING
        else -> ThreadAccess.UNAVAILABLE
    }
    composeFromPeer -> {
        val peerId = composePeerUserId
        if (peerId.isNotBlank() && (peerId in blockedIds || isBanned(peerId))) {
            ThreadAccess.UNAVAILABLE
        } else {
            ThreadAccess.OPEN
        }
    }
    else -> ThreadAccess.RESOLVING
}
