package fm.corus.android.ui.screens.live

import fm.corus.android.ui.navigation.OtherProfileRoute

/** Seed the profile from identities already available to Live, without a network wait. */
internal fun liveProfilePreview(userId: String, state: LiveUiState, cached: LivePerson?): OtherProfileRoute {
    val room = state.presentedRoom
    val known = sequence {
        cached?.takeIf { it.uid == userId }?.let { yield(it) }
        room?.host?.let { yield(it) }
        room?.currentDJ?.let { yield(it) }
        room?.djs?.forEach { yield(LivePerson.parse(it)) }
        state.chatRows.forEach { yield(it.author) }
        state.pendingDJs.forEach { yield(LivePerson.parse(it)) }
        state.djRequests.forEach { yield(LivePerson.parse(it)) }
    }.filter { it.uid == userId }.toList()
    val profile = known.firstNotNullOfOrNull { it.profile }
    val seed = profile?.toOtherProfileRoute() ?: OtherProfileRoute(userId)
    return seed.copy(
        initialUsername = seed.initialUsername ?: known.firstOrNull {
            it.username.isNotBlank() && it.username != "…"
        }?.username,
        avatarURL = seed.avatarURL ?: known.firstNotNullOfOrNull { it.avatar?.takeIf(String::isNotBlank) },
    )
}
