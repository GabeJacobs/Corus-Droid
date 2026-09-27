package fm.corus.android.data.model

/** A share destination, not necessarily a person. Group IDs never go to getOrCreateThread. */
data class ShareRecipient(val user: CymbalUser? = null, val group: CymbalThread? = null) {
    init { require((user == null) != (group == null)) }
    val id: String get() = group?.let { "group:" + it.id } ?: requireNotNull(user).id
    val username: String get() = group?.let {
        it.groupName?.takeIf(String::isNotBlank)
            ?: it.members.take(3).joinToString(", ") { member -> member.displayName.ifBlank { member.username } }.ifBlank { "Group" }
    } ?: requireNotNull(user).username
    val displayName: String get() = if (group != null) username else requireNotNull(user).displayName
    val avatarURL: String? get() = group?.groupPhotoURL ?: user?.avatarURL
    val isVerified: Boolean get() = user?.isVerified ?: false
    val isClubMember: Boolean get() = user?.isClubMember ?: false
    val isBot: Boolean get() = user?.isBot ?: false
    val flairStyle: FlairStyle get() = user?.flairStyle ?: FlairStyle.NONE
}

fun recentShareRecipients(threads: List<CymbalThread>, cap: Int = 20): List<ShareRecipient> =
    threads.filter { !it.blocked && it.lastMessageFromUserId != null }
        .sortedByDescending { it.lastMessageAt }
        .mapNotNull { thread ->
            if (thread.isGroup) ShareRecipient(group = thread)
            else thread.otherUser?.takeIf { it.id.isNotBlank() }?.let { ShareRecipient(user = it) }
        }.distinctBy { it.id }.take(cap)

/**
 * Builds the Instagram V2 recipient rail without fetching recommendation data.
 * Existing conversations keep inbox order; remaining slots use only cached
 * follows, mutual connections, and qualified Taste Matches.
 */
fun shareSheetRecipients(
    recent: List<ShareRecipient>,
    cachedMatches: List<SuggestedUserMatch>,
    followedUserIds: Set<String>,
    currentUserId: String?,
    cap: Int = 20,
): List<ShareRecipient> {
    if (cap <= 0) return emptyList()
    val result = mutableListOf<ShareRecipient>()
    val seen = mutableSetOf<String>()

    fun append(recipient: ShareRecipient) {
        if (result.size < cap && seen.add(recipient.id)) result += recipient
    }

    recent.forEach(::append)
    val eligible = cachedMatches.filter { it.user.id != currentUserId && !it.user.isBot }
    val followed = eligible.filter { it.user.id in followedUserIds }
    val mutuals = eligible.filter {
        it.user.id !in followedUserIds && (it.suggestionReason?.mutualCount ?: 0) > 0
    }
    val tasteMatches = eligible.filter {
        it.user.id !in followedUserIds &&
            (it.suggestionReason?.mutualCount ?: 0) == 0 &&
            it.isTasteMatch
    }
    (followed + mutuals + tasteMatches).forEach { append(ShareRecipient(user = it.user)) }
    return result
}

fun groupMatchesShareQuery(thread: CymbalThread, query: String): Boolean =
    thread.isGroup && (thread.groupName.orEmpty().contains(query.trim(), ignoreCase = true) ||
        thread.members.any { it.username.contains(query.trim(), ignoreCase = true) || it.displayName.contains(query.trim(), ignoreCase = true) })
