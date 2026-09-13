package fm.corus.android.ui.screens.messaging

// Viewer-owned blocks only. Shared groups do not provide two-way invisibility.
internal fun isBlockedGroupAuthor(isGroup: Boolean, authorId: String?, blocked: Set<String>): Boolean =
    isGroup && !authorId.isNullOrEmpty() && authorId in blocked

internal fun collapseGroupMessage(isGroup: Boolean, authorId: String, isSystem: Boolean, blocked: Set<String>, revealed: Boolean): Boolean =
    !isSystem && !revealed && isBlockedGroupAuthor(isGroup, authorId, blocked)
