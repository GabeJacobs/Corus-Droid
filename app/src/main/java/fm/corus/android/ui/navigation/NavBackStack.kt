package fm.corus.android.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * In-app back (profile chevron, etc.) must not pop a tab's start destination.
 * [NavHostController.popBackStack] with no args pops the *current* destination,
 * so a double-tap after Search → profile leaves the NavHost empty and the
 * Search tab paints a blank body under the still-selected tab bar.
 */
internal fun NavHostController.safePopBackStack(): Boolean {
    if (!canPopTabBackStack(previousBackStackEntry != null)) return false
    val previousId = previousBackStackEntry?.destination?.id ?: return false
    // Pop *to* the previous destination, not the current one. Two rapid
    // popBackStack() calls both see a previous entry and the second removes
    // the tab root (blank Search). popBackStack(previousId, inclusive=false)
    // never pops that destination, even when both taps land in the same frame.
    return popBackStack(previousId, inclusive = false)
}

/**
 * If a tab NavHost has no destination (start route was popped), remount the
 * tab root. No-op when the graph isn't ready or already has a current screen.
 */
internal fun NavHostController.restoreStartIfEmpty(): Boolean {
    if (!shouldRestoreTabStart(currentDestination != null)) return false
    val startId = runCatching { graph.startDestinationId }.getOrNull() ?: return false
    if (startId == 0) return false
    return runCatching {
        Log.w(TAB_NAV_TAG, "Tab NavHost was empty; remounting start destination")
        navigate(startId)
        true
    }.getOrDefault(false)
}

internal fun NavHostController.popToStart(): Boolean {
    if (restoreStartIfEmpty()) return true
    val startId = runCatching { graph.startDestinationId }.getOrNull() ?: return false
    return popBackStack(startId, inclusive = false)
}

/** True when an in-app back can pop without removing the tab root. */
internal fun canPopTabBackStack(hasPreviousEntry: Boolean): Boolean = hasPreviousEntry

/** True when a tab NavHost has no destination and must remount its start route. */
internal fun shouldRestoreTabStart(hasCurrentDestination: Boolean): Boolean =
    !hasCurrentDestination

/**
 * Watches a tab [NavHost] and remounts its start destination if the back stack
 * is emptied (system-back double-fire, or an unguarded pop of the tab root).
 */
@Composable
internal fun RestoreTabNavIfEmpty(navController: NavHostController) {
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry) {
        if (currentEntry == null) {
            navController.restoreStartIfEmpty()
        }
    }
}

private const val TAB_NAV_TAG = "TabNav"
