package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalUser

internal fun stackedAvatarMembers(
    membersById: Map<String, CymbalUser>,
    currentUserId: String?,
    lastWriterIds: List<String>,
    memberIds: List<String>,
    lastMessageFromUserId: String? = null,
    lastMessageIsSystem: Boolean = false,
    limit: Int = 2,
): List<CymbalUser> {
    val writers = when {
        lastWriterIds.isNotEmpty() -> lastWriterIds
        !lastMessageIsSystem && !lastMessageFromUserId.isNullOrBlank() -> listOf(lastMessageFromUserId)
        else -> emptyList()
    }
    val seen = linkedSetOf<String>()
    val out = mutableListOf<CymbalUser>()
    fun consider(id: String) {
        if (id.isBlank() || id == currentUserId || id in seen) return
        val user = membersById[id] ?: return
        seen += id
        out += user
    }
    for (id in writers) {
        consider(id)
        if (out.size >= limit) return out
    }
    for (id in memberIds) {
        consider(id)
        if (out.size >= limit) return out
    }
    return out
}

internal fun stackedAvatarMembers(
    members: List<CymbalUser>,
    currentUserId: String?,
    lastWriterIds: List<String>,
    memberIds: List<String> = members.map { it.id },
    lastMessageFromUserId: String? = null,
    lastMessageIsSystem: Boolean = false,
): List<CymbalUser> =
    stackedAvatarMembers(
        membersById = members.associateBy { it.id },
        currentUserId = currentUserId,
        lastWriterIds = lastWriterIds,
        memberIds = memberIds,
        lastMessageFromUserId = lastMessageFromUserId,
        lastMessageIsSystem = lastMessageIsSystem,
    )
