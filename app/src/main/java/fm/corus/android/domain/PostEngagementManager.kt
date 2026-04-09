package fm.corus.android.domain

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.repository.PostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
 */
@Singleton
class PostEngagementManager @Inject constructor(
    private val postRepository: PostRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<String, EngagementState>>(emptyMap())
    val states: StateFlow<Map<String, EngagementState>> = _states.asStateFlow()

    fun getState(postId: String): EngagementState? = _states.value[postId]

    fun initState(postId: String, likeCount: Int, commentCount: Int, repostCount: Int, isLiked: Boolean, isSaved: Boolean) {
        _states.update { map ->
            if (map.containsKey(postId)) map
            else map + (postId to EngagementState(likeCount, commentCount, repostCount, isLiked, isSaved))
        }
    }

    fun toggleLike(postId: String, userId: String) {
        val current = _states.value[postId] ?: return
        val newLiked = !current.isLiked
        val newCount = if (newLiked) current.likeCount + 1 else maxOf(0, current.likeCount - 1)

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
            }
        }
    }

    fun toggleSave(postId: String, userId: String) {
        val current = _states.value[postId] ?: return
        val newSaved = !current.isSaved

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

    fun clearAll() {
        _states.value = emptyMap()
    }
}
