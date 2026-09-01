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

    /**
     * Empty is only honest after this chip's first page has landed. An empty
     * All-window fallback before that is "not loaded yet" — showing it flashes
     * notifications (or "No activity") for a frame before the skeleton.
     */
    fun listPhase(
        filter: NotificationFilter,
        displayedIsEmpty: Boolean,
        isLoadingAll: Boolean,
        chipLoaded: Boolean,
        isFilterLoading: Boolean,
        knownCount: Int? = null,
    ): NotificationFilterListPhase {
        if (filter.isServerScoped) {
            if (!chipLoaded) return NotificationFilterListPhase.SKELETON
            if (displayedIsEmpty && isFilterLoading) return NotificationFilterListPhase.SKELETON
            return if (displayedIsEmpty) {
                NotificationFilterListPhase.EMPTY
            } else {
                NotificationFilterListPhase.CONTENT
            }
        }
        if (isActivityListLoading(isLoadingAll, displayedIsEmpty, knownCount)) {
            return NotificationFilterListPhase.SKELETON
        }
        return if (displayedIsEmpty) {
            NotificationFilterListPhase.EMPTY
        } else {
            NotificationFilterListPhase.CONTENT
        }
    }
}

/**
 * Whether the Activity All list should show its loading skeleton.
 *
 * A cheap existence snapshot (limit 1) lands at launch — same role as
 * likesCount on the profile. When the count is already 0, skip the
 * skeleton and show the empty state immediately. Unknown (`null`) keeps
 * the skeleton until the list lands. Non-zero keeps the skeleton while
 * rows hydrate.
 */
fun isActivityListLoading(
    isLoading: Boolean,
    displayedIsEmpty: Boolean,
    knownCount: Int? = null,
): Boolean {
    if (knownCount == 0) return false
    return isLoading && displayedIsEmpty
}

enum class NotificationFilterListPhase {
    SKELETON,
    EMPTY,
    CONTENT,
}
