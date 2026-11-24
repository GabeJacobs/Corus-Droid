package fm.corus.android.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import fm.corus.android.ui.components.CorusModalBottomSheet
import fm.corus.android.ui.components.ExpandedPhoto
import fm.corus.android.ui.components.FullScreenPhotoViewer
import fm.corus.android.ui.components.LocalImmersiveHeaderInSheet
import fm.corus.android.ui.components.rememberGuardedSheetState
import fm.corus.android.ui.screens.feed.LikesBottomSheet
import fm.corus.android.ui.screens.feed.RepostersBottomSheet
import fm.corus.android.ui.theme.CorusSystemBars
import kotlinx.coroutines.launch

/** The ordinary profile and destinations, scoped to a sheet above the Live room. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveProfileSheet(
    userId: String,
    initialProfile: OtherProfileRoute = OtherProfileRoute(userId),
    mainTabViewModel: MainTabViewModel,
    onDismiss: () -> Unit,
) {
    val profileRoute = remember(userId) { initialProfile }
    val navController = rememberNavController()
    val navigateToUsername = rememberNavigateToUserByUsername(navController)
    val artistPagesEnabled = rememberArtistPagesEnabled()
    var likesPostId by remember { mutableStateOf<String?>(null) }
    var repostersPostId by remember { mutableStateOf<String?>(null) }
    var photo by remember { mutableStateOf<ExpandedPhoto?>(null) }
    var dismissing by remember { mutableStateOf(false) }
    val sheetState = rememberGuardedSheetState(
        skipPartiallyExpanded = true,
        allowProgrammaticDismiss = { dismissing },
    )
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        }
    }

    CorusModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Keep the ordinary profile palette, including its distinct featured-post colors.
        useSheetTheme = false,
        // Let the profile grid draw behind the transparent gesture navigation area.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        CorusSystemBars()
        CompositionLocalProvider(LocalImmersiveHeaderInSheet provides true) {
            Box(Modifier.fillMaxHeight(.94f)) {
                NavHost(
                    navController = navController,
                    startDestination = profileRoute,
                    enterTransition = { slideInHorizontally(tween(400), initialOffsetX = { it }) },
                    exitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { -it / 3 }) },
                    popEnterTransition = { slideInHorizontally(tween(400), initialOffsetX = { -it / 3 }) },
                    popExitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { it }) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    sharedDestinations(
                        navController = navController,
                        mainTabViewModel = mainTabViewModel,
                        navigateToUserByUsername = navigateToUsername,
                        onShowComments = { navController.navigate(SinglePostCommentsRoute(it)) },
                        onShowPhoto = { photo = it },
                        onShowLikes = { likesPostId = it },
                        onShowReposters = { repostersPostId = it },
                        artistPagesEnabled = artistPagesEnabled,
                        onProfileSheetDismiss = close,
                    )
                }
                FullScreenPhotoViewer(photo = photo, onDismiss = { photo = null })
            }
        }
        likesPostId?.let { postId ->
            LikesBottomSheet(
                postId = postId,
                onDismiss = { likesPostId = null },
                onNavigateToUser = { uid ->
                    likesPostId = null
                    navController.navigate(OtherProfileRoute(uid))
                },
            )
        }
        repostersPostId?.let { postId ->
            RepostersBottomSheet(
                postId = postId,
                onDismiss = { repostersPostId = null },
                onNavigateToUser = { uid ->
                    repostersPostId = null
                    navController.navigate(OtherProfileRoute(uid))
                },
                onNavigateToPost = { id ->
                    repostersPostId = null
                    navController.navigate(PostDetailRoute(id))
                },
            )
        }
    }
}
