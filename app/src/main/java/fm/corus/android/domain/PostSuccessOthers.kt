package fm.corus.android.domain

import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser

/**
 * Eligibility + row-picking for the post-success "other people also posted
 * this" sheet. Pure so the compose path can stay byte-identical when the
 * Remote Config flag is off (the flag is checked at the call site).
 *
 * Post 1 is Club only (first-post paywall). The others sheet runs on
 * posts 2..[MAX_LIFETIME_POSTS]. The Remote Config flag is still the
 * audience gate — flag-off never reaches this helper, so existing users
 * stay on today's toast path.
 */
object PostSuccessOthers {
    const val MAX_PEOPLE = 3
    const val MAX_LIFETIME_POSTS = 4
    const val FETCH_TIMEOUT_MS = 2_000L

    const val ENFORCE_LIFETIME_CAP = true

    fun shouldAttempt(
        isFirstPoster: Boolean,
        totalPostCount: Int,
        enforceLifetimeCap: Boolean = ENFORCE_LIFETIME_CAP,
        maxLifetimePosts: Int = MAX_LIFETIME_POSTS,
    ): Boolean {
        if (isFirstPoster) return false
        if (totalPostCount <= 1) return false
        if (enforceLifetimeCap && totalPostCount > maxLifetimePosts) return false
        return true
    }

    fun pickEligible(
        posts: List<CymbalPost>,
        currentUserId: String,
        followingIds: Set<String>,
        limit: Int = MAX_PEOPLE,
    ): List<PostSuccessOthersPerson> {
        val seen = mutableSetOf<String>()
        val captioned = mutableListOf<PostSuccessOthersPerson>()
        val uncaptioned = mutableListOf<PostSuccessOthersPerson>()
        for (post in posts) {
            val uid = post.user.id
            if (uid.isEmpty() || uid == currentUserId) continue
            if (followingIds.contains(uid)) continue
            if (!seen.add(uid)) continue
            val caption = post.caption?.trim().orEmpty()
            val person = PostSuccessOthersPerson(
                user = post.user,
                caption = caption,
                postId = post.id,
            )
            if (caption.isEmpty()) uncaptioned.add(person) else captioned.add(person)
        }
        return (captioned + uncaptioned).take(limit)
    }

    fun otherCount(uniquePosterCount: Int?, visibleCount: Int): Int {
        if (uniquePosterCount != null) {
            return maxOf(visibleCount, maxOf(0, uniquePosterCount - 1))
        }
        return visibleCount
    }
}

data class PostSuccessOthersPerson(
    val user: CymbalUser,
    val caption: String,
    val postId: String,
)

data class PostSuccessOthersMediaInfo(
    val title: String,
    val subtitle: String,
    val artURL: String?,
    /** Film posters are 2:3; tracks stay square. */
    val isPoster: Boolean,
    val track: CymbalTrack? = null,
    val movie: CymbalMovie? = null,
)

data class PostSuccessOthersPayload(
    val people: List<PostSuccessOthersPerson>,
    val otherCount: Int,
    val media: PostSuccessOthersMediaInfo,
) {
    /** Matches `post_created.media_type` so sheet events join the same reports. */
    val analyticsMediaType: String
        get() = when {
            media.track != null -> "track"
            media.movie != null -> "movie"
            else -> "track"
        }
}
