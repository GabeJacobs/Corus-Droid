package fm.corus.android.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.GiftDefinition
import fm.corus.android.data.repository.GiftInventory
import fm.corus.android.data.repository.GiftRepository
import fm.corus.android.data.repository.GiftRepositoryException
import fm.corus.android.data.repository.GiftSendResult
import fm.corus.android.data.repository.SubscriptionRepository
import kotlinx.coroutines.CancellationException
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

    fun recordSend(postId: String, serverCountAtOpen: Int, alreadySent: Boolean) {
        _giftCounts.update { counts ->
            val existing = counts[postId] ?: serverCountAtOpen
            counts + (postId to maxOf(existing, serverCountAtOpen + if (alreadySent) 0 else 1))
        }
    }
}

data class GiftSelectionState(
    val loading: Boolean = true,
    val sending: Boolean = false,
    val inventory: GiftInventory? = null,
    val catalogIds: Set<String> = emptySet(),
    val selectedGiftId: String = GiftDefinition.selectable.first().id,
    val note: String = "",
    val sentGiftId: String? = null,
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

    fun open() {
        _state.value = GiftSelectionState()
        requestId = null
        requestSignature = null
        subscriptionRepository.fetchOfferings()
        refresh()
    }

    fun select(giftId: String) {
        _state.update { it.copy(selectedGiftId = giftId, error = null) }
    }

    fun updateNote(note: String) {
        _state.update { it.copy(note = note.take(240), error = null) }
    }

    fun refresh() {
        if (_state.value.sending) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
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
