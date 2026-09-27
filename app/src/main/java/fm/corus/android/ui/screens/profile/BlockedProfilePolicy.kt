package fm.corus.android.ui.screens.profile

internal enum class BlockedProfileVisibility {
    FULL,
    LIMITED,
    UNAVAILABLE,
}

internal fun blockedProfileVisibility(
    userId: String,
    blockedIds: Set<String>,
    blockedByIds: Set<String>,
): BlockedProfileVisibility = when (userId) {
    in blockedIds -> BlockedProfileVisibility.LIMITED
    in blockedByIds -> BlockedProfileVisibility.UNAVAILABLE
    else -> BlockedProfileVisibility.FULL
}
