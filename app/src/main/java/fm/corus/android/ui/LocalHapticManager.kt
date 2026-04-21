package fm.corus.android.ui

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fm.corus.android.domain.HapticManager

/**
 * CompositionLocal that exposes the singleton [HapticManager] to Composables
 * that can't conveniently go through a Hilt-injected ViewModel (e.g. pure
 * UI gestures like double-tap-to-like, pull-to-refresh, button taps).
 *
 * Populated in [CorusApp] from the Hilt SingletonComponent via [hapticManagerFromContext].
 */
val LocalHapticManager = compositionLocalOf<HapticManager> {
    error("LocalHapticManager not provided. Wrap your Composable tree in CorusApp or ProvideHapticManager.")
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HapticManagerEntryPoint {
    fun hapticManager(): HapticManager
}

fun hapticManagerFromContext(context: Context): HapticManager =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        HapticManagerEntryPoint::class.java,
    ).hapticManager()
