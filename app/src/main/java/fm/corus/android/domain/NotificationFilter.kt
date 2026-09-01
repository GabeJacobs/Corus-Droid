package fm.corus.android.domain

import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.NotificationType

/**
 * Instagram-style Activity filter chips. Every chip except [ALL] is fetched
 * server-side (`types` and/or `peopleYouFollow` on getNotifications).
 */
enum class NotificationFilter(val value: String) {
    ALL("all"),
    PEOPLE_YOU_FOLLOW("people_you_follow"),
    COMMENTS("comments"),
    FOLLOWS("follows"),
    TAGS_AND_MENTIONS("tags_and_mentions");

    /** Callable / Firestore `types`. Null means no type constraint. */
    val queryTypes: List<NotificationType>?
        get() = when (this) {
            ALL, PEOPLE_YOU_FOLLOW -> null
            COMMENTS -> listOf(NotificationType.COMMENT, NotificationType.REPLY)
            FOLLOWS -> listOf(NotificationType.FOLLOW)
            TAGS_AND_MENTIONS -> listOf(NotificationType.MENTION, NotificationType.TAG)
        }

    val isTypeScoped: Boolean get() = queryTypes != null

    /** Server fetch via getNotifications (type `in` and/or peopleYouFollow). */
    val isServerScoped: Boolean get() = this != ALL
}

object NotificationFilterVisibility {
    const val DEFAULT_MIN_COUNT = 8
    const val DEFAULT_MIN_TYPES = 3

    fun shouldShow(
        flagEnabled: Boolean,
        alreadyUnlocked: Boolean,
        notifications: List<CymbalNotification>,
        minCount: Int = DEFAULT_MIN_COUNT,
        minTypes: Int = DEFAULT_MIN_TYPES,
    ): Boolean {
        if (!flagEnabled) return false
        if (alreadyUnlocked) return true
        val types = notifications.map { it.type }.toSet()
        return notifications.size >= maxOf(1, minCount) && types.size >= maxOf(1, minTypes)
    }

    fun apply(
        filter: NotificationFilter,
        all: List<CymbalNotification>,
        filtered: List<CymbalNotification>,
        followingIds: Set<String>,
        filteredReady: Boolean = false,
        filterLoading: Boolean = false,
    ): List<CymbalNotification> = when (filter) {
        NotificationFilter.ALL -> all
        NotificationFilter.PEOPLE_YOU_FOLLOW -> {
            // Don't paint the All-window subset while the chip fetch is in
            // flight — that is the "one comment, then the rest" flash.
            if (holdForServer(filteredReady, filterLoading, filtered.isEmpty())) {
                emptyList()
            } else if (filteredReady || filtered.isNotEmpty()) {
                if (followingIds.isEmpty()) filtered
                else filtered.filter { followingIds.contains(it.fromUser.id) }
            } else {
                all.filter { followingIds.contains(it.fromUser.id) }
            }
        }
        NotificationFilter.COMMENTS,
        NotificationFilter.FOLLOWS,
        NotificationFilter.TAGS_AND_MENTIONS -> {
            if (holdForServer(filteredReady, filterLoading, filtered.isEmpty())) {
                emptyList()
            } else if (filteredReady || filtered.isNotEmpty()) {
                filtered
            } else {
                val types = filter.queryTypes?.map { it.value }?.toSet().orEmpty()
                all.filter { it.type.value in types }
            }
        }
    }

    private fun holdForServer(
        filteredReady: Boolean,
        filterLoading: Boolean,
        filteredEmpty: Boolean,
    ): Boolean = filterLoading && !filteredReady && filteredEmpty
}
