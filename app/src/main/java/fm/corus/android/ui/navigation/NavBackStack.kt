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

/** True when Map is already under this profile, so the city line should not
 *  push another Map (iOS `suppressMapCityNavigation`). */
internal fun shouldOpenMapFromProfileCity(mapAlreadyOnStack: Boolean): Boolean =
    !mapAlreadyOnStack

internal fun destinationIsMapExplore(route: String?): Boolean {
    val name = MapExploreRoute::class.qualifiedName ?: return false
    return route?.startsWith(name) == true
}

internal fun tabStartRouteName(tab: CorusTab): String? = when (tab) {
    CorusTab.FEED -> FeedTabRoute::class.qualifiedName
    CorusTab.EXPLORE -> SearchTabRoute::class.qualifiedName
    CorusTab.NOTIFICATIONS -> NotificationsTabRoute::class.qualifiedName
    CorusTab.PROFILE -> ProfileTabRoute::class.qualifiedName
    CorusTab.MESSAGES -> ThreadListRoute::class.qualifiedName
    CorusTab.COMPOSE -> null
}

/** Map from a profile city is never the tab root — Profile retap must pop it. */
internal fun isAtTabStartDestination(
    currentRoute: String?,
    startRouteName: String?,
    currentId: Int?,
    startId: Int,
): Boolean {
    if (destinationIsMapExplore(currentRoute)) return false
    if (!currentRoute.isNullOrBlank() && !startRouteName.isNullOrBlank()) {
        return currentRoute == startRouteName ||
            currentRoute.startsWith("$startRouteName/") ||
            currentRoute.startsWith("$startRouteName?")
    }
    return startId != 0 && currentId == startId
}

internal fun NavHostController.hasMapExploreInBackStack(): Boolean =
    currentBackStack.value.any { destinationIsMapExplore(it.destination.route) }

/** True when a tab NavHost has no destination and must remount its start route. */
internal fun shouldRestoreTabStart(hasCurrentDestination: Boolean): Boolean =
    !hasCurrentDestination

internal enum class TabReselectAction { SELECT_ONLY, RESTORE_START, POP_TO_START, SCROLL_TO_TOP }

/** A graph entry is not a screen above root; compare destination IDs instead. */
internal fun tabReselectAction(
    alreadySelected: Boolean,
    hasCurrentDestination: Boolean,
    isAtStartDestination: Boolean,
): TabReselectAction = when {
    !alreadySelected -> TabReselectAction.SELECT_ONLY
    !hasCurrentDestination -> TabReselectAction.RESTORE_START
    !isAtStartDestination -> TabReselectAction.POP_TO_START
    else -> TabReselectAction.SCROLL_TO_TOP
}

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
