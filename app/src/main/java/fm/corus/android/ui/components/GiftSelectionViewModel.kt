package fm.corus.android.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.GiftDefinition
import fm.corus.android.data.model.PostGiftPreview
import fm.corus.android.data.repository.GiftInventory
import fm.corus.android.data.repository.GiftRepository
import fm.corus.android.data.repository.GiftRepositoryException
import fm.corus.android.data.repository.GiftSendResult
import fm.corus.android.data.repository.SubscriptionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

object GiftPresentationStore {
    private val _giftCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val giftCounts: StateFlow<Map<String, Int>> = _giftCounts.asStateFlow()

    /** Receipts from the sendGift response, so the row paints on the first frame after the
     *  sheet dismisses instead of waiting on the post refresh / getPostGifts fallback. */
    private val _recentGifts = MutableStateFlow<Map<String, List<PostGiftPreview>>>(emptyMap())
    val recentGifts: StateFlow<Map<String, List<PostGiftPreview>>> = _recentGifts.asStateFlow()

    fun recordSend(
        postId: String,
        serverCountAtOpen: Int,
        alreadySent: Boolean,
        recent: List<PostGiftPreview> = emptyList(),
    ) {
        _giftCounts.update { counts ->
            val existing = counts[postId] ?: serverCountAtOpen
            counts + (postId to maxOf(existing, serverCountAtOpen + if (alreadySent) 0 else 1))
        }
        if (recent.isNotEmpty()) _recentGifts.update { it + (postId to recent) }
    }

    /** The post's own previews once they reflect every gift; otherwise the locally recorded ones. */
    fun previewsFor(post: CymbalPost, overrides: Map<String, List<PostGiftPreview>>): List<PostGiftPreview> {
        val local = overrides[post.id] ?: return post.recentGifts
        return if (post.recentGifts.size >= local.size) post.recentGifts else local
    }
}

/** In-memory note drafts per post, so an accidental dismiss (drag, scrim, back)
 *  does not lose what the sender typed. Cleared once the gift is sent. */
object GiftNoteDrafts {
    private val drafts = mutableMapOf<String, String>()
    fun get(postId: String): String = drafts[postId].orEmpty()
    fun put(postId: String, note: String) { if (note.isEmpty()) drafts.remove(postId) else drafts[postId] = note }
    fun clear(postId: String) { drafts.remove(postId) }
}

data class GiftSelectionState(
    val loading: Boolean = true,
    val sending: Boolean = false,
    val inventory: GiftInventory? = null,
    val catalogIds: Set<String> = emptySet(),
    val selectedGiftId: String = GiftDefinition.selectable.first().id,
    val note: String = "",
    val sentGiftId: String? = null,
    /** Server said this viewer already has a gift on the post (it returns THAT gift). */
    val alreadySentGiftId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class GiftSelectionViewModel @Inject constructor(
    private val repository: GiftRepository,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GiftSelectionState())
    val state: StateFlow<GiftSelectionState> = _state.asStateFlow()
    val hasClubIntroTrial: StateFlow<Boolean> = subscriptionRepository.hasClubIntroTrial

    private var requestId: String? = null
    private var requestSignature: Pair<String, String>? = null

    private var postId: String? = null

    private companion object {
        const val FULL_CAPACITY = 3
        val POST_PURCHASE_RETRY_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000)
    }

    fun open(postId: String) {
        this.postId = postId
        val warm = repository.cachedStatus()
        _state.value = GiftSelectionState(
            loading = warm == null,
            inventory = warm?.inventory,
            catalogIds = warm?.catalogIds?.toSet() ?: emptySet(),
            note = GiftNoteDrafts.get(postId),
        )
        requestId = null
        requestSignature = null
        subscriptionRepository.fetchOfferings()
        refresh()
    }

    private var quotaPrefetchedForUser: String? = null

    suspend fun prefetchQuota(userId: String) {
        if (quotaPrefetchedForUser == userId) return
        quotaPrefetchedForUser = userId
        repository.prefetchStatus()
    }

    suspend fun prefetch(context: android.content.Context) {
        fm.corus.android.ui.screens.notifications.GiftRiveFile.get(context)
        repository.prefetchStatus()
    }

    fun select(giftId: String) {
        _state.update { it.copy(selectedGiftId = giftId, error = null) }
    }

    fun updateNote(note: String) {
        val trimmed = note.take(240)
        postId?.let { GiftNoteDrafts.put(it, trimmed) }
        _state.update { it.copy(note = trimmed, error = null) }
    }

    fun refresh() {
        if (_state.value.sending) return
        viewModelScope.launch {
            // Keep an already-painted (cached) inventory on screen while revalidating.
            _state.update { it.copy(loading = it.inventory == null, error = null) }
            try {
                val status = repository.getStatus()
                _state.update {
                    it.copy(
                        loading = false,
                        inventory = status.inventory,
                        catalogIds = status.catalogIds.toSet(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.message ?: "Corus couldn't load your Gifts.") }
            }
        }
    }

    /**
     * Club purchase just succeeded. Drop the stale inventory so the picker shows its
     * skeleton (not the old "Get 3 Gifts" upsell) while the SERVER re-reads entitlement.
     * Capacity is never granted client-side: it only comes from getGiftStatus. If the
     * server still says capacity < [FULL_CAPACITY] (webhook/sync lag), retry a few times.
     */
    fun refreshAfterPurchase() {
        if (_state.value.sending) return
        _state.update { it.copy(loading = true, inventory = null, error = null) }
        viewModelScope.launch {
            var lastError: String? = null
            for (attempt in 0..POST_PURCHASE_RETRY_DELAYS_MS.size) {
                if (attempt > 0) delay(POST_PURCHASE_RETRY_DELAYS_MS[attempt - 1])
                try {
                    val status = repository.getStatus()
                    lastError = null
                    // Keep the skeleton up between retries; only publish a final answer.
                    val isFinal = status.inventory.capacity >= FULL_CAPACITY ||
                        attempt == POST_PURCHASE_RETRY_DELAYS_MS.size
                    if (isFinal) {
                        _state.update {
                            it.copy(inventory = status.inventory, catalogIds = status.catalogIds.toSet())
                        }
                        break
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    lastError = error.message ?: "Corus couldn't load your Gifts."
                }
            }
            _state.update { it.copy(loading = false, error = if (it.inventory == null) lastError else null) }
        }
    }

    fun send(postId: String, onSent: (GiftSendResult) -> Unit) {
        val snapshot = _state.value
        if (snapshot.sending || snapshot.inventory?.isEmpty != false) return
        val note = snapshot.note.trim()
        val signature = snapshot.selectedGiftId to note
        if (signature != requestSignature) {
            requestSignature = signature
            requestId = UUID.randomUUID().toString()
        }
        val stableRequestId = requestId ?: return

        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            try {
                val result = repository.sendGift(
                    postId = postId,
                    giftType = snapshot.selectedGiftId,
                    note = note.takeIf { it.isNotEmpty() },
                    requestId = stableRequestId,
                )
                // Same gift back = a retry of a send that already succeeded (lost response).
                // A different gift back = the server refused a second one; nothing was sent.
                if (result.alreadySent && result.giftType != snapshot.selectedGiftId) {
                    // Nothing was sent or consumed. Say so instead of a success screen.
                    _state.update {
                        it.copy(sending = false, inventory = result.inventory, alreadySentGiftId = result.giftType)
                    }
                    return@launch
                }
                GiftNoteDrafts.clear(postId)
                _state.update {
                    it.copy(
                        sending = false,
                        inventory = result.inventory,
                        sentGiftId = result.giftType,
                    )
                }
                onSent(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: GiftRepositoryException.NoSlots) {
                _state.update { it.copy(sending = false, error = error.message) }
                refresh()
            } catch (error: Exception) {
                _state.update { it.copy(sending = false, error = error.message ?: "Gift couldn't be sent. Please try again.") }
            }
        }
    }
}
