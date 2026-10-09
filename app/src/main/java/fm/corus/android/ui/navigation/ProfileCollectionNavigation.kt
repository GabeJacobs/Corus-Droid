package fm.corus.android.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fm.corus.android.ui.screens.profile.CollectionPostSkeleton
import fm.corus.android.domain.CollectionPlaybackQueue
import fm.corus.android.ui.screens.feed.LikesBottomSheet
import fm.corus.android.ui.screens.feed.RepostersBottomSheet
import kotlinx.serialization.Serializable

// Use the existing app-level compose coordinator, not a second MainTabViewModel.
internal val LocalCollectionMainTabViewModel = staticCompositionLocalOf<MainTabViewModel?> { null }
@Serializable private data object CollectionRootRoute

/** A local stack inside one sheet. Collection data/grid state live above this
 * host, so popping a post does not refetch or reset the collection. */
@Composable
internal fun ProfileCollectionNavigation(
    modifier: Modifier,
    postMedia: String,
    postTitle: String,
    onDismiss: () -> Unit,
    onReturnedToCollection: () -> Unit,
    onPostLoadState: (Boolean) -> Unit,
    playbackQueue: CollectionPlaybackQueue?,
    collection: @Composable ((String) -> Unit) -> Unit,
) {
    val coordinator = checkNotNull(LocalCollectionMainTabViewModel.current)
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val atRoot = entry == null || entry?.destination?.hasRoute<CollectionRootRoute>() == true
    var leftRoot by remember { mutableStateOf(false) }
    var likes by remember { mutableStateOf<String?>(null) }
    var reposters by remember { mutableStateOf<String?>(null) }
    val navigateToUsername = rememberNavigateToUserByUsername(nav)
    val artistPages = rememberArtistPagesEnabled()
    // Material's sheet lives in a ComponentDialog window. A BackHandler that
    // inherits the activity dispatcher cannot receive the dialog's Back events.
    val dialogBackOwner = LocalView.current.findViewTreeOnBackPressedDispatcherOwner()
        ?: checkNotNull(LocalOnBackPressedDispatcherOwner.current)
    LaunchedEffect(atRoot) {
        if (!atRoot) {
            leftRoot = true
        } else if (leftRoot) {
            leftRoot = false
            onReturnedToCollection()
        }
    }
    CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides dialogBackOwner) {
        NavHost(
            navController = nav,
            startDestination = CollectionRootRoute,
            modifier = modifier,
            enterTransition = { slideInHorizontally(tween(300)) { it } },
            exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } },
        ) {
            composable<CollectionRootRoute> {
                collection { postId ->
                    if (nav.currentBackStackEntry?.destination?.hasRoute<CollectionRootRoute>() == true) {
                        nav.navigate(PostDetailRoute(postId))
                    }
                }
            }
            sharedDestinations(
                navController = nav,
                mainTabViewModel = coordinator,
                navigateToUserByUsername = navigateToUsername,
                onShowComments = { nav.navigate(SinglePostCommentsRoute(it)) },
                onShowLikes = { likes = it },
                onShowReposters = { reposters = it },
                artistPagesEnabled = artistPages,
                postLoadingContent = { LazyColumn { item { CollectionPostSkeleton(postMedia) } } },
                postNavigationTitle = postTitle,
                onPostLoadState = onPostLoadState,
                collectionPlaybackQueue = playbackQueue,
            )
        }
        likes?.let { id ->
            LikesBottomSheet(postId = id, onDismiss = { likes = null }, onNavigateToUser = { user ->
                likes = null
                nav.navigate(OtherProfileRoute(user))
            })
        }
        reposters?.let { id ->
            RepostersBottomSheet(postId = id, onDismiss = { reposters = null }, onNavigateToUser = { user ->
                reposters = null
                nav.navigate(OtherProfileRoute(user))
            }, onNavigateToPost = { post ->
                reposters = null
                nav.navigate(PostDetailRoute(post))
            })
        }
        // Register after NavHost so this owns the root/pop decision and rapid taps
        // use the same protected pop behavior as the rest of the app.
        BackHandler { if (atRoot) onDismiss() else nav.safePopBackStack() }
    }
}
