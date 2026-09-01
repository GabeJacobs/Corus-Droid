package fm.corus.android.domain

import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class NotificationFilterTest {

    private fun user(id: String) = CymbalUser(
        id = id,
        username = id,
        displayName = id,
    )

    private fun notif(
        id: String,
        type: NotificationType,
        from: String = "sender",
    ) = CymbalNotification(
        id = id,
        type = type,
        fromUser = user(from),
        timestamp = Date(),
        isRead = false,
    )

    @Test
    fun chipsHiddenWhenFlagOff() {
        val items = (0 until 10).map {
            notif("$it", if (it % 2 == 0) NotificationType.LIKE else NotificationType.COMMENT)
        }
        assertFalse(
            NotificationFilterVisibility.shouldShow(
                flagEnabled = false,
                alreadyUnlocked = false,
                notifications = items,
            ),
        )
        assertFalse(
            NotificationFilterVisibility.shouldShow(
                flagEnabled = false,
                alreadyUnlocked = true,
                notifications = items,
            ),
        )
    }

    @Test
    fun chipsHiddenUntilCountAndTypesMet() {
        val likes = (0 until 10).map { notif("l$it", NotificationType.LIKE) }
        assertFalse(
            NotificationFilterVisibility.shouldShow(
                flagEnabled = true,
                alreadyUnlocked = false,
                notifications = likes,
            ),
        )

        val mixedFew = listOf(
            notif("1", NotificationType.LIKE),
            notif("2", NotificationType.COMMENT),
            notif("3", NotificationType.FOLLOW),
        )
        assertFalse(
            NotificationFilterVisibility.shouldShow(
                flagEnabled = true,
                alreadyUnlocked = false,
                notifications = mixedFew,
            ),
        )

        val mixedEnough = (0 until 8).map { i ->
            val type = when (i % 3) {
                0 -> NotificationType.LIKE
                1 -> NotificationType.COMMENT
                else -> NotificationType.FOLLOW
            }
            notif("m$i", type)
        }
        assertTrue(
            NotificationFilterVisibility.shouldShow(
                flagEnabled = true,
                alreadyUnlocked = false,
                notifications = mixedEnough,
            ),
        )
    }

    @Test
    fun stickyUnlockShowsEvenWhenListShrinks() {
        assertTrue(
            NotificationFilterVisibility.shouldShow(
                flagEnabled = true,
                alreadyUnlocked = true,
                notifications = listOf(notif("1", NotificationType.LIKE)),
            ),
        )
    }

    @Test
    fun peopleYouFollowFiltersToFollowingSet() {
        val all = listOf(
            notif("1", NotificationType.LIKE, from = "a"),
            notif("2", NotificationType.COMMENT, from = "b"),
            notif("3", NotificationType.FOLLOW, from = "c"),
        )
        val shown = NotificationFilterVisibility.apply(
            filter = NotificationFilter.PEOPLE_YOU_FOLLOW,
            all = all,
            filtered = emptyList(),
            followingIds = setOf("a", "c"),
        )
        assertEquals(listOf("1", "3"), shown.map { it.id })
    }

    @Test
    fun peopleYouFollowUsesServerListWhenReady() {
        val all = listOf(notif("1", NotificationType.LIKE, from = "a"))
        val server = listOf(
            notif("9", NotificationType.COMMENT, from = "a"),
            notif("8", NotificationType.LIKE, from = "c"),
        )
        val shown = NotificationFilterVisibility.apply(
            filter = NotificationFilter.PEOPLE_YOU_FOLLOW,
            all = all,
            filtered = server,
            followingIds = setOf("a", "c"),
            filteredReady = true,
        )
        assertEquals(listOf("9", "8"), shown.map { it.id })
    }

    @Test
    fun peopleYouFollowEmptyWhenServerEmpty() {
        val all = listOf(notif("1", NotificationType.LIKE, from = "a"))
        val shown = NotificationFilterVisibility.apply(
            filter = NotificationFilter.PEOPLE_YOU_FOLLOW,
            all = all,
            filtered = emptyList(),
            followingIds = setOf("a"),
            filteredReady = true,
        )
        assertTrue(shown.isEmpty())
    }

    @Test
    fun commentsAndTagsQueryTypes() {
        assertEquals(
            listOf(NotificationType.COMMENT, NotificationType.REPLY),
            NotificationFilter.COMMENTS.queryTypes,
        )
        assertEquals(listOf(NotificationType.FOLLOW), NotificationFilter.FOLLOWS.queryTypes)
        assertEquals(
            listOf(NotificationType.MENTION, NotificationType.TAG),
            NotificationFilter.TAGS_AND_MENTIONS.queryTypes,
        )
        assertEquals(null, NotificationFilter.ALL.queryTypes)
        assertEquals(null, NotificationFilter.PEOPLE_YOU_FOLLOW.queryTypes)
        assertTrue(NotificationFilter.PEOPLE_YOU_FOLLOW.isServerScoped)
        assertTrue(NotificationFilter.COMMENTS.isServerScoped)
        assertFalse(NotificationFilter.ALL.isServerScoped)
    }

    @Test
    fun typeScopedHidesAllWindowWhileChipIsLoading() {
        val all = listOf(
            notif("1", NotificationType.LIKE),
            notif("2", NotificationType.COMMENT),
        )
        val shown = NotificationFilterVisibility.apply(
            filter = NotificationFilter.COMMENTS,
            all = all,
            filtered = emptyList(),
            followingIds = emptySet(),
            filterLoading = true,
        )
        assertTrue(shown.isEmpty())
    }

    @Test
    fun typeScopedFallsBackToAllWindowWhenFetchEmpty() {
        val all = listOf(
            notif("1", NotificationType.LIKE),
            notif("2", NotificationType.COMMENT),
            notif("3", NotificationType.REPLY),
        )
        val shown = NotificationFilterVisibility.apply(
            filter = NotificationFilter.COMMENTS,
            all = all,
            filtered = emptyList(),
            followingIds = emptySet(),
        )
        assertEquals(listOf("2", "3"), shown.map { it.id })
    }

    @Test
    fun analyticsValuesMatchIOSAndWeb() {
        assertEquals("all", NotificationFilter.ALL.value)
        assertEquals("people_you_follow", NotificationFilter.PEOPLE_YOU_FOLLOW.value)
        assertEquals("comments", NotificationFilter.COMMENTS.value)
        assertEquals("follows", NotificationFilter.FOLLOWS.value)
        assertEquals("tags_and_mentions", NotificationFilter.TAGS_AND_MENTIONS.value)
    }
}
