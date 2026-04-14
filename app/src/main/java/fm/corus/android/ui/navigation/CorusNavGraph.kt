package fm.corus.android.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import fm.corus.android.ui.screens.explore.ExploreScreen
import fm.corus.android.ui.screens.explore.HashtagFeedScreen
import fm.corus.android.ui.screens.feed.CommentsBottomSheet
import fm.corus.android.ui.screens.feed.CommentsSheet
import fm.corus.android.ui.screens.feed.EditCaptionSheet
import fm.corus.android.ui.screens.feed.FeedScreen
import fm.corus.android.ui.screens.feed.FilmDetailScreen
import fm.corus.android.ui.screens.feed.LikesSheet
import fm.corus.android.ui.screens.feed.PostDetailScreen
import fm.corus.android.ui.screens.feed.SinglePostCommentsScreen
import fm.corus.android.ui.screens.feed.SongDetailScreen
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.ui.screens.subscription.CymbalClubOfferScreen
import fm.corus.android.ui.screens.notifications.NotificationsScreen
import fm.corus.android.ui.screens.profile.EditProfileScreen
import fm.corus.android.ui.screens.profile.FollowListScreen
import fm.corus.android.ui.screens.profile.OtherProfileScreen
import fm.corus.android.ui.screens.profile.ProfileScreen
import fm.corus.android.ui.screens.settings.BlockedUsersScreen
import fm.corus.android.ui.screens.settings.MutedUsersScreen
import fm.corus.android.ui.screens.settings.ChangePhoneNumberScreen
import fm.corus.android.ui.screens.settings.ChangeUsernameScreen
import fm.corus.android.ui.screens.settings.FeedbackFormScreen
import fm.corus.android.ui.screens.findpeople.BotListScreen
import fm.corus.android.ui.screens.findpeople.FindPeopleScreen
import fm.corus.android.ui.screens.messaging.MessageThreadScreen
import fm.corus.android.ui.screens.messaging.ThreadListScreen
import fm.corus.android.ui.screens.settings.AppearanceSettingsScreen
import fm.corus.android.ui.screens.settings.NotificationSettingsScreen
import fm.corus.android.ui.screens.settings.SettingsScreen
import fm.corus.android.ui.screens.findpeople.SuggestedUsersListScreen
import fm.corus.android.ui.screens.findpeople.SuggestedUsersListViewModel
import fm.corus.android.ui.screens.findpeople.ContactFriendsListScreen
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.screens.findpeople.ContactFriendsListViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.ui.screens.profile.OtherProfileViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun FeedNavGraph(navController: NavHostController, mainTabViewModel: MainTabViewModel, scrollToTopTrigger: Int = 0) {
    var commentPostId by remember { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = FeedTabRoute,
        enterTransition = { slideInHorizontally(tween(400), initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { -it / 3 }) },
        popEnterTransition = { slideInHorizontally(tween(400), initialOffsetX = { -it / 3 }) },
        popExitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { it }) },
    ) {
        composable<FeedTabRoute> {
            FeedScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
                onNavigateToUser = { userId, avatarURL, avatarThumbURL ->
                    navController.navigate(OtherProfileRoute(userId, avatarURL, avatarThumbURL))
                },
                onNavigateToUserByUsername = { username -> navController.navigate(ProfileByUsernameRoute(username)) },
                onNavigateToComments = { postId -> commentPostId = postId },
                onNavigateToLikes = { postId -> navController.navigate(LikesRoute(postId)) },
                onNavigateToHashtag = { hashtag -> navController.navigate(HashtagFeedRoute(hashtag)) },
                onNavigateToSong = { track -> navController.navigate(track.toSongDetailRoute()) },
                onNavigateToFilm = { movieId -> navController.navigate(FilmDetailRoute(movieId)) },
            )
        }
        sharedDestinations(navController, mainTabViewModel, onShowComments = { commentPostId = it })
    }

    commentPostId?.let { postId ->
        CommentsBottomSheet(
            postId = postId,
            onDismiss = { commentPostId = null },
            onNavigateToUser = { userId ->
                commentPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
        )
    }
}

@Composable
fun ExploreNavGraph(navController: NavHostController, mainTabViewModel: MainTabViewModel, scrollToTopTrigger: Int = 0) {
    var commentPostId by remember { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = ExploreTabRoute,
        enterTransition = { slideInHorizontally(tween(400), initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { -it / 3 }) },
        popEnterTransition = { slideInHorizontally(tween(400), initialOffsetX = { -it / 3 }) },
        popExitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { it }) },
    ) {
        composable<ExploreTabRoute> {
            FindPeopleScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
                onNavigateToSong = { track -> navController.navigate(track.toSongDetailRoute()) },
                onNavigateToFilm = { movieId -> navController.navigate(FilmDetailRoute(movieId)) },
                onNavigateToBotList = { botType -> navController.navigate(BotListRoute(botType)) },
                onNavigateToSuggestedUsers = { title, useRowLayout -> navController.navigate(SuggestedUsersListRoute(title, useRowLayout)) },
                onNavigateToContactFriends = { navController.navigate(ContactFriendsListRoute) },
            )
        }
        sharedDestinations(navController, mainTabViewModel, onShowComments = { commentPostId = it })
    }

    commentPostId?.let { postId ->
        CommentsBottomSheet(
            postId = postId,
            onDismiss = { commentPostId = null },
            onNavigateToUser = { userId ->
                commentPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
        )
    }
}

@Composable
fun NotificationsNavGraph(navController: NavHostController, mainTabViewModel: MainTabViewModel, scrollToTopTrigger: Int = 0) {
    var commentPostId by remember { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = NotificationsTabRoute,
        enterTransition = { slideInHorizontally(tween(400), initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { -it / 3 }) },
        popEnterTransition = { slideInHorizontally(tween(400), initialOffsetX = { -it / 3 }) },
        popExitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { it }) },
    ) {
        composable<NotificationsTabRoute> {
            NotificationsScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
                onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
                onNavigateToMessages = { navController.navigate(ThreadListRoute) },
                onNavigateToPostComments = { postId, commentId ->
                    navController.navigate(SinglePostCommentsRoute(postId, commentId))
                },
            )
        }
        sharedDestinations(navController, mainTabViewModel, onShowComments = { commentPostId = it })
    }

    commentPostId?.let { postId ->
        CommentsBottomSheet(
            postId = postId,
            onDismiss = { commentPostId = null },
            onNavigateToUser = { userId ->
                commentPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
        )
    }
}

@Composable
fun ProfileNavGraph(navController: NavHostController, mainTabViewModel: MainTabViewModel, scrollToTopTrigger: Int = 0) {
    var commentPostId by remember { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = ProfileTabRoute,
        enterTransition = { slideInHorizontally(tween(400), initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { -it / 3 }) },
        popEnterTransition = { slideInHorizontally(tween(400), initialOffsetX = { -it / 3 }) },
        popExitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { it }) },
    ) {
        composable<ProfileTabRoute> {
            ProfileScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                onNavigateToSettings = { navController.navigate(SettingsRoute) },
                onNavigateToEditProfile = { navController.navigate(EditProfileRoute(it)) },
                onNavigateToFollowList = { userId, isFollowers ->
                    navController.navigate(FollowListRoute(userId, isFollowers))
                },
                onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
                onNavigateToClub = { navController.navigate(CymbalClubOfferRoute) },
            )
        }
        sharedDestinations(navController, mainTabViewModel, onShowComments = { commentPostId = it })
    }

    commentPostId?.let { postId ->
        CommentsBottomSheet(
            postId = postId,
            onDismiss = { commentPostId = null },
            onNavigateToUser = { userId ->
                commentPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
        )
    }
}

/**
 * Shared destinations available from any tab's NavHost.
 */
private fun androidx.navigation.NavGraphBuilder.sharedDestinations(
    navController: NavHostController,
    mainTabViewModel: MainTabViewModel,
    onShowComments: (String) -> Unit = {},
) {
    composable<PostDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<PostDetailRoute>()
        PostDetailScreen(
            postId = route.postId,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToComments = onShowComments,
            onNavigateToLikes = { postId -> navController.navigate(LikesRoute(postId)) },
            onNavigateToSong = { track -> navController.navigate(track.toSongDetailRoute()) },
            onNavigateToFilm = { movieId -> navController.navigate(FilmDetailRoute(movieId)) },
            onNavigateToHashtag = { hashtag -> navController.navigate(HashtagFeedRoute(hashtag)) },
        )
    }

    composable<OtherProfileRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<OtherProfileRoute>()
        OtherProfileScreen(
            userId = route.userId,
            initialAvatarURL = route.avatarURL,
            initialAvatarThumbURL = route.avatarThumbURL,
            onBack = { navController.popBackStack() },
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToFollowList = { userId, isFollowers ->
                navController.navigate(FollowListRoute(userId, isFollowers))
            },
            onNavigateToMessages = { threadId, otherUserId ->
                navController.navigate(MessageThreadRoute(threadId, otherUserId))
            },
        )
    }

    composable<ProfileByUsernameRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ProfileByUsernameRoute>()
        val viewModel: OtherProfileViewModel = hiltViewModel()
        var resolvedUserId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(route.username) {
            val userId = viewModel.fetchUserIdByUsername(route.username)
            if (userId != null) {
                resolvedUserId = userId
            } else {
                navController.popBackStack()
            }
        }
        resolvedUserId?.let { userId ->
            OtherProfileScreen(
                userId = userId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
                onNavigateToUser = { uid -> navController.navigate(OtherProfileRoute(uid)) },
                onNavigateToFollowList = { uid, isFollowers ->
                    navController.navigate(FollowListRoute(uid, isFollowers))
                },
                onNavigateToMessages = { threadId, otherUserId ->
                    navController.navigate(MessageThreadRoute(threadId, otherUserId))
                },
            )
        }
    }

    composable<SongDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<SongDetailRoute>()
        SongDetailScreen(
            trackId = route.trackId,
            albumArtURL = route.albumArtURL,
            albumArtLargeURL = route.albumArtLargeURL,
            songName = route.songName,
            artistName = route.artistName,
            spotifyURI = route.spotifyURI,
            spotifyWebURL = route.spotifyWebURL,
            previewUrl = route.previewUrl,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToCompose = { trackId ->
                mainTabViewModel.setPreSelectedTrackId(trackId)
            },
        )
    }

    composable<FilmDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<FilmDetailRoute>()
        FilmDetailScreen(
            movieId = route.movieId,
            initialMovieTitle = route.movieTitle,
            initialDirectorName = route.directorName,
            initialReleaseYear = route.releaseYear,
            initialPosterURL = route.posterURL,
            initialPosterLargeURL = route.posterLargeURL,
            initialTrailerURL = route.trailerURL,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToCompose = { movieId ->
                mainTabViewModel.setPreSelectedMovieId(movieId)
            },
        )
    }

    composable<HashtagFeedRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<HashtagFeedRoute>()
        HashtagFeedScreen(
            hashtag = route.hashtag,
            onBack = { navController.popBackStack() },
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
        )
    }

    composable<FollowListRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<FollowListRoute>()
        FollowListScreen(
            userId = route.userId,
            isFollowers = route.isFollowers,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
        )
    }

    composable<EditProfileRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<EditProfileRoute>()
        EditProfileScreen(
            onBack = { navController.popBackStack() },
            onNavigateToClub = { navController.navigate(CymbalClubOfferRoute) },
        )
    }

    composable<LikesRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<LikesRoute>()
        LikesSheet(
            postId = route.postId,
            onDismiss = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
        )
    }

    composable<EditCaptionRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<EditCaptionRoute>()
        EditCaptionSheet(
            postId = route.postId,
            initialCaption = route.initialCaption,
            albumArtURL = route.albumArtURL,
            onDismiss = { navController.popBackStack() },
            onSaved = { ToastManager.show("Caption updated") },
        )
    }

    composable<CymbalClubOfferRoute> {
        CymbalClubOfferScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable<SettingsRoute> {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onChangeUsername = { navController.navigate(ChangeUsernameRoute) },
            onChangePhoneNumber = { navController.navigate(ChangePhoneNumberRoute) },
            onBlockedUsers = { navController.navigate(BlockedUsersRoute) },
            onMutedUsers = { navController.navigate(MutedUsersRoute) },
            onSendFeedback = { navController.navigate(FeedbackFormRoute) },
            onNotificationSettings = { navController.navigate(NotificationSettingsRoute) },
        )
    }

    composable<NotificationSettingsRoute> {
        NotificationSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable<AppearanceSettingsRoute> {
        AppearanceSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable<ChangeUsernameRoute> {
        ChangeUsernameScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable<BlockedUsersRoute> {
        BlockedUsersScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable<MutedUsersRoute> {
        MutedUsersScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
        )
    }

    composable<FeedbackFormRoute> {
        FeedbackFormScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable<ChangePhoneNumberRoute> {
        ChangePhoneNumberScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable<FindPeopleRoute> {
        FindPeopleScreen(
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToSong = { track -> navController.navigate(track.toSongDetailRoute()) },
            onNavigateToFilm = { movieId -> navController.navigate(FilmDetailRoute(movieId)) },
            onNavigateToBotList = { botType -> navController.navigate(BotListRoute(botType)) },
            onNavigateToSuggestedUsers = { title, useRowLayout -> navController.navigate(SuggestedUsersListRoute(title, useRowLayout)) },
            onNavigateToContactFriends = { navController.navigate(ContactFriendsListRoute) },
        )
    }

    composable<BotListRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<BotListRoute>()
        BotListScreen(
            botType = route.botType,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
        )
    }

    composable<SinglePostCommentsRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<SinglePostCommentsRoute>()
        SinglePostCommentsScreen(
            postId = route.postId,
            highlightCommentId = route.commentId,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
        )
    }

    composable<ThreadListRoute> {
        ThreadListScreen(
            onBack = { navController.popBackStack() },
            onThreadTap = { threadId, otherUserId ->
                navController.navigate(MessageThreadRoute(threadId, otherUserId))
            },
        )
    }

    composable<MessageThreadRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<MessageThreadRoute>()
        MessageThreadScreen(
            threadId = route.threadId,
            otherUserId = route.otherUserId,
            onBack = { navController.popBackStack() },
        )
    }

    composable<SuggestedUsersListRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<SuggestedUsersListRoute>()
        val viewModel: SuggestedUsersListViewModel = hiltViewModel()
        val suggestions by viewModel.suggestions.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()

        SuggestedUsersListScreen(
            matches = suggestions,
            title = route.title,
            useRowLayout = route.useRowLayout,
            isLoading = isLoading,
            isFollowed = { viewModel.isFollowed(it) },
            onFollow = { viewModel.toggleFollow(it) },
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
        )
    }

    composable<ContactFriendsListRoute> {
        val viewModel: ContactFriendsListViewModel = hiltViewModel()
        val contacts by viewModel.contacts.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()

        if (isLoading) {
            ContactFriendsListScreen(
                users = emptyList(),
                onBack = { navController.popBackStack() },
                onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            )
        } else {
            ContactFriendsListScreen(
                users = contacts,
                isFollowed = { viewModel.isFollowed(it) },
                onFollow = { viewModel.toggleFollow(it) },
                onBack = { navController.popBackStack() },
                onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            )
        }
    }

}
