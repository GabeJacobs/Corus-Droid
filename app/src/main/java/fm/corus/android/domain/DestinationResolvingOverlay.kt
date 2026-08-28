package fm.corus.android.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Search destination resolve (trending album / artist). Painted on
 * [fm.corus.android.ui.navigation.MainTabScreen] via [fm.corus.android.ui.components.ChromeLoadingHud]
 * so the dim + card covers the mini-player and tab bar. Mirrors iOS
 * `DestinationResolvingOverlayHandle`.
 */
object DestinationResolvingOverlay {
    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    fun setResolving(active: Boolean) {
        _isResolving.value = active
    }

    /** Flip the chrome HUD on the tap frame, before any coroutine hop. */
    fun arm() {
        _isResolving.value = true
    }
}
