package fm.corus.android.domain

import com.google.firebase.firestore.ListenerRegistration
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.PostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class EngagementState(
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val repostCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
)

/**
 * Manages optimistic engagement state for posts.
 * Mirrors iOS PostEngagementStore — local state is updated immediately,
 * then synced to Firestore in the background.
 *
 * Also manages per-post real-time Firestore listeners with reference counting
 * (matching iOS PostEngagementStore.startListening/stopListening).
 */
@Singleton
class PostEngagementManager @Inject constructor(
    private val postRepository: PostRepository,
    private val firestoreDataSource: FirestoreDataSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<String, EngagementState>>(emptyMap())
    val states: StateFlow<Map<String, EngagementState>> = _states.asStateFlow()

    /** Post IDs the user has modified locally (like/save toggle) — these are preserved on refresh. */
    private val userModifiedPostIds = Collections.synchronizedSet(mutableSetOf<String>())

    // ── Real-time listener infrastructure (matching iOS PostEngagementStore) ──

    /** Reference count per postId — listener is active while refCount > 0. */
    private val listenerRefCounts = ConcurrentHashMap<String, Int>()

    /** Active Firestore ListenerRegistrations keyed by postId. */
    private val activeListeners = ConcurrentHashMap<String, ListenerRegistration>()

    /** Post IDs where a like/unlike network request is in flight.
     *  While in flight, listener updates skip likeCount to avoid overwriting optimistic state. */
    private val likeInFlightIds = Collections.synchronizedSet(mutableSetOf<String>())

    fun getState(postId: String): EngagementState? = _states.value[postId]

    fun initState(postId: String, likeCount: Int, commentCount: Int, repostCount: Int, isLiked: Boolean, isSaved: Boolean) {
        _states.update { map ->
            if (map.containsKey(postId) && userModifiedPostIds.contains(postId)) map
            else {
                val existing = map[postId]
                if (existing != null) {
                    // Preserve local isLiked/isSaved state to avoid flash;
                    // checkLikeStatuses() will reconcile from Firestore shortly after.
                    map + (postId to existing.copy(likeCount = likeCount, commentCount = commentCount, repostCount = repostCount))
                } else {
                    map + (postId to EngagementState(likeCount, commentCount, repostCount, isLiked, isSaved))
                }
            }
        }
    }

    // ── Real-time listeners ──

    fun startListening(postId: String) {
        val newCount = listenerRefCounts.merge(postId, 1, Int::plus) ?: 1
        if (newCount == 1) {
            val registration = firestoreDataSource.listenForPostUpdates(
                postId = postId,
                onUpdate = { likeCount, commentCount, repostCount ->
                    applyListenerUpdate(postId, likeCount, commentCount, repostCount)
                },
            )
            activeListeners[postId] = registration
        }
    }

    fun stopListening(postId: String) {
        val newCount = listenerRefCounts.merge(postId, -1, Int::plus) ?: 0
        if (newCount <= 0) {
            listenerRefCounts.remove(postId)
            activeListeners.remove(postId)?.remove()
        }
    }

    private fun applyListenerUpdate(postId: String, likeCount: Int, commentCount: Int, repostCount: Int) {
        _states.update { map ->
            val current = map[postId] ?: return@update map
            // If a like is in flight, preserve the optimistic likeCount
            val safeLikeCount = if (likeInFlightIds.contains(postId)) current.likeCount else likeCount
            map + (postId to current.copy(
                likeCount = safeLikeCount,
                commentCount = commentCount,
                repostCount = repostCount,
            ))
        }
    }

    // ── Optimistic engagement actions ──

    fun toggleLike(postId: String, userId: String) {
        val current = _states.value[postId] ?: return
        val newLiked = !current.isLiked
        val newCount = if (newLiked) current.likeCount + 1 else maxOf(0, current.likeCount - 1)

        likeInFlightIds.add(postId)
        userModifiedPostIds.add(postId)
        _states.update { map ->
            map + (postId to current.copy(isLiked = newLiked, likeCount = newCount))
        }

        scope.launch {
            try {
                if (newLiked) postRepository.likePost(userId, postId)
                else postRepository.unlikePost(userId, postId)
            } catch (e: Exception) {
                // Rollback on failure
                _states.update { map ->
                    map + (postId to current)
                }
            } finally {
                likeInFlightIds.remove(postId)
                userModifiedPostIds.remove(postId)
            }
        }
    }

    fun toggleSave(postId: String, userId: String) {
        val current = _states.value[postId] ?: return
        val newSaved = !current.isSaved

        userModifiedPostIds.add(postId)
        _states.update { map ->
            map + (postId to current.copy(isSaved = newSaved))
        }

        scope.launch {
            try {
                if (newSaved) postRepository.savePost(userId, postId)
                else postRepository.unsavePost(userId, postId)
            } catch (e: Exception) {
                _states.update { map ->
                    map + (postId to current)
                }
            } finally {
                userModifiedPostIds.remove(postId)
            }
        }
    }

    fun incrementCommentCount(postId: String) {
        _states.update { map ->
            val current = map[postId] ?: return@update map
            map + (postId to current.copy(commentCount = current.commentCount + 1))
        }
    }

    fun decrementCommentCount(postId: String) {
        _states.update { map ->
            val current = map[postId] ?: return@update map
            map + (postId to current.copy(commentCount = (current.commentCount - 1).coerceAtLeast(0)))
        }
    }

    fun repostPost(post: CymbalPost, userId: String) {
        // Optimistic UI: increment repost count on the original post
        _states.update { map ->
            val current = map[post.id] ?: return@update map
            map + (post.id to current.copy(repostCount = current.repostCount + 1))
        }

        scope.launch {
            try {
                postRepository.repostPost(userId, post)
            } catch (e: Exception) {
                // Rollback on failure
                _states.update { map ->
                    val current = map[post.id] ?: return@update map
                    map + (post.id to current.copy(repostCount = (current.repostCount - 1).coerceAtLeast(0)))
                }
            }
        }
    }

    /**
     * Check actual like status from Firestore for the given posts.
     * Mirrors iOS PostCard.checkStatus() — the backend doesn't return isLiked,
     * so we query the likes subcollection directly.
     */
    fun checkLikeStatuses(postIds: List<String>, userId: String) {
        scope.launch {
            val results = postIds.map { postId ->
                async {
                    postId to (try { postRepository.isPostLiked(userId, postId) } catch (_: Exception) { null })
                }
            }.awaitAll()

            _states.update { map ->
                var updated = map
                for ((postId, liked) in results) {
                    if (liked == null) continue
                    if (userModifiedPostIds.contains(postId)) continue
                    val current = updated[postId] ?: continue
                    if (current.isLiked != liked) {
                        updated = updated + (postId to current.copy(isLiked = liked))
                    }
                }
                updated
            }
        }
    }

    fun clearAll() {
        activeListeners.values.forEach { it.remove() }
        activeListeners.clear()
        listenerRefCounts.clear()
        likeInFlightIds.clear()
        _states.value = emptyMap()
        userModifiedPostIds.clear()
    }
}
