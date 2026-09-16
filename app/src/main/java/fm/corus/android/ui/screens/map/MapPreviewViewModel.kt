package fm.corus.android.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.service.RemoteConfigService
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapPreviewState(
    val cities: List<MapCitySummary> = emptyList(),
    val ownCity: MapCity? = null,
    val ownPresenceReady: Boolean = false,
    val directoryReady: Boolean = false,
)

/** Search-card loader kept separate so opening the full Map still fetches every facet and city. */
@HiltViewModel
class MapPreviewViewModel @Inject constructor(
    private val repository: MapRepository,
    private val auth: FirebaseAuth,
    val remote: RemoteConfigService,
) : ViewModel() {
    private val uid = auth.currentUser?.uid
    private val mutable = MutableStateFlow(MapPreviewState())
    val state = mutable.asStateFlow()
    val enabled get() = remote.mapEnabled && uid != null && auth.currentUser?.uid == uid
    private var directoryJob: kotlinx.coroutines.Job? = null

    init {
        uid?.let { viewer ->
            // Presence and the compact directory are independent and begin together.
            viewModelScope.launch {
                repository.ownPresence(viewer).collect { data ->
                    if (auth.currentUser?.uid == viewer) mutable.update {
                        it.copy(ownCity = data?.let(MapCity::decode), ownPresenceReady = true)
                    }
                }
            }
            refresh()
        }
    }

    fun refresh() {
        val viewer = uid ?: return
        if (directoryJob?.isActive == true) return
        directoryJob?.cancel()
        directoryJob = viewModelScope.launch {
                try {
                    val cities = repository.previewCities()
                    if (auth.currentUser?.uid == viewer) mutable.update {
                        it.copy(cities = cities, directoryReady = true)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The base map remains useful even when the optional cluster request fails.
                    if (auth.currentUser?.uid == viewer) mutable.update { it.copy(directoryReady = true) }
                }
        }
    }
}
