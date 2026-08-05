package fm.corus.android.ui.screens.destination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.service.EntityLink
import fm.corus.android.service.EntityLinkResolution
import fm.corus.android.service.EntitySegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Turns the key a `corus.fm/{segment}/{key}` URL carried into the id its page
 * is served under.
 *
 * An id key resolves with no round trip at all, so every link shared before
 * clean URLs keeps behaving exactly as it did — offline included. A slug is
 * looked up against the map the server owns, because the app has no way, and
 * must have no way, to derive an entity from a name without asking a catalog
 * API.
 */
@HiltViewModel
class EntityLinkViewModel @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
) : ViewModel() {

    sealed class State {
        data object Resolving : State()
        data class Ready(val segment: EntitySegment, val entityId: String) : State()

        /**
         * Nothing owns this key. Identical to the answer for something hidden or
         * banned, which is the point: neither may be told apart from a name that
         * was never used.
         */
        data object Missing : State()
        data object Unreachable : State()
    }

    private val _state = MutableStateFlow<State>(State.Resolving)
    val state: StateFlow<State> = _state.asStateFlow()

    fun resolve(segment: String, key: String) {
        val entitySegment = EntitySegment.from(segment)
        if (entitySegment == null) {
            _state.value = State.Missing
            return
        }
        when (val resolution = EntityLink.resolution(key, entitySegment)) {
            is EntityLinkResolution.Id -> _state.value = State.Ready(entitySegment, resolution.id)
            is EntityLinkResolution.Slug -> {
                _state.value = State.Resolving
                viewModelScope.launch {
                    _state.value = try {
                        cloudFunctions.resolveEntityLink(entitySegment, resolution.slug)
                            ?.let { State.Ready(entitySegment, it) } ?: State.Missing
                    } catch (e: Exception) {
                        State.Unreachable
                    }
                }
            }
        }
    }
}
