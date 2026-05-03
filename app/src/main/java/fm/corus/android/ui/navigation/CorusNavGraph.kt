package fm.corus.android.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import fm.corus.android.ui.screens.explore.HashtagFeedScreen
import fm.corus.android.ui.screens.feed.CommentLikesScreen
import fm.corus.android.ui.screens.feed.CommentsBottomSheet
import fm.corus.android.ui.screens.feed.CommentsSheet
import fm.corus.android.ui.screens.feed.EditCaptionSheet
import fm.corus.android.ui.screens.feed.FeedScreen
import fm.corus.android.ui.screens.feed.FilmDetailScreen
import fm.corus.android.ui.screens.feed.LikesBottomSheet
import fm.corus.android.ui.screens.feed.PostDetailScreen
import fm.corus.android.ui.screens.feed.SinglePostCommentsScreen
import fm.corus.android.ui.screens.feed.SongDetailScreen
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.ui.screens.subscription.CymbalClubOfferScreen
import fm.corus.android.ui.screens.notifications.NotificationsScreen
import fm.corus.android.ui.screens.profile.EditProfileScreen
import fm.corus.android.ui.screens.profile.FollowListScreen
import fm.corus.android.ui.screens.profile.OtherProfileScreen
import fm.corus.android.ui.screens.profile.ProfileFeedScreen
import fm.corus.android.ui.screens.profile.ProfileScreen
import fm.corus.android.ui.screens.settings.BlockedUsersScreen
import fm.corus.android.ui.screens.settings.MutedUsersScreen
import fm.corus.android.ui.screens.settings.ChangePhoneNumberScreen
import fm.corus.android.ui.screens.settings.ChangeUsernameScreen
import fm.corus.android.ui.screens.settings.FeedbackFormScreen
import fm.corus.android.ui.screens.search.BotListScreen
import fm.corus.android.ui.screens.search.SearchScreen
import fm.corus.android.ui.screens.messaging.MessageThreadScreen
import fm.corus.android.ui.screens.messaging.ThreadListScreen
import fm.corus.android.ui.screens.settings.NotificationSettingsScreen
import fm.corus.android.ui.screens.settings.SettingsScreen
import fm.corus.android.ui.screens.search.SuggestedUsersListScreen
import fm.corus.android.ui.screens.search.SuggestedUsersListViewModel
import fm.corus.android.ui.screens.search.ContactFriendsListScreen
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.screens.search.ContactFriendsListViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.ui.screens.profile.OtherProfileViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UserRepositoryEntryPoint {
    fun userRepository(): UserRepository
}

/**
 * Returns a callback that resolves a username to a user via [UserRepository] and
 * then navigates to the resolved profile. Resolving before navigation (matching
 * iOS) avoids transitioning into a blank screen while the lookup is in flight.
 */
@Composable
private fun rememberNavigateToUserByUsername(navController: NavHostController): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userRepository = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            UserRepositoryEntryPoint::class.java,
        ).userRepository()
    }
    return { username ->
        scope.launch {
            val user = userRepository.fetchUserByUsername(username) ?: return@launch
            navController.navigate(OtherProfileRoute(user.id))
        }
    }
}

@Composable
fun FeedNavGraph(navController: NavHostController, mainTabViewModel: MainTabViewModel, scrollToTopTrigger: Int = 0) {
    var commentPostId by remember { mutableStateOf<String?>(null) }
    var likesPostId by remember { mutableStateOf<String?>(null) }
    val navigateToUserByUsername = rememberNavigateToUserByUsername(navController)

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
                onNavigateToUser = { user ->
                    navController.navigate(OtherProfileRoute(
                        userId = user.id,
                        avatarURL = user.avatarURL,
                        avatarThumbURL = user.avatarThumbURL,
                        initialDisplayName = user.displayName,
                        initialUsername = user.username,
                        initialBio = user.bio,
                        initialCymbalCount = user.cymbalCount,
                        initialFollowerCount = user.followerCount,
                        initialFollowingCount = user.followingCount,
                        initialIsVerified = user.isVerified,
                        initialIsClubMember = user.isClubMember,
                        initialIsFollowing = true,
                    ))
                },
                onNavigateToUserById = { userId -> navController.navigate(OtherProfileRoute(userId)) },
                onNavigateToUserByUsername = navigateToUserByUsername,
                onNavigateToComments = { postId -> commentPostId = postId },
                onNavigateToLikes = { postId -> likesPostId = postId },
                onNavigateToHashtag = { hashtag -> navController.navigate(HashtagFeedRoute(hashtag)) },
                onNavigateToSong = { track -> navController.navigate(track.toSongDetailRoute()) },
                onNavigateToFilm = { movieId -> navController.navigate(FilmDetailRoute(movieId)) },
                onNavigateToBotList = { botType -> navController.navigate(BotListRoute(botType)) },
                onRepost = { post -> mainTabViewModel.setRepostOriginalPost(post) },
            )
        }
        sharedDestinations(navController, mainTabViewModel, navigateToUserByUsername = navigateToUserByUsername, onShowComments = { commentPostId = it }, onShowLikes = { likesPostId = it })
    }

    commentPostId?.let { postId ->
        CommentsBottomSheet(
            postId = postId,
            onDismiss = { commentPostId = null },
            onNavigateToUser = { userId ->
                commentPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
            onNavigateToSong = { track ->
                commentPostId = null
                navController.navigate(track.toSongDetailRoute())
            },
            onNavigateToFilm = { movie ->
                commentPostId = null
                navController.navigate(FilmDetailRoute(movie.id))
            },
        )
    }

    likesPostId?.let { postId ->
        LikesBottomSheet(
            postId = postId,
            onDismiss = { likesPostId = null },
            onNavigateToUser = { userId ->
                likesPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
        )
    }
}

@Composable
fun SearchNavGraph(navController: NavHostController, mainTabViewModel: MainTabViewModel, scrollToTopTrigger: Int = 0) {
    var commentPostId by remember { mutableStateOf<String?>(null) }
    var likesPostId by remember { mutableStateOf<String?>(null) }
    val navigateToUserByUsername = rememberNavigateToUserByUsername(navController)

    NavHost(
        navController = navController,
        startDestination = SearchTabRoute,
        enterTransition = { slideInHorizontally(tween(400), initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { -it / 3 }) },
        popEnterTransition = { slideInHorizontally(tween(400), initialOffsetX = { -it / 3 }) },
        popExitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { it }) },
    ) {
        composable<SearchTabRoute> {
            SearchScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
                onNavigateToSong = { track -> navController.navigate(track.toSongDetailRoute()) },
                onNavigateToFilm = { route -> navController.navigate(route) },
                onNavigateToBotList = { botType -> navController.navigate(BotListRoute(botType)) },
                onNavigateToSuggestedUsers = { title, useRowLayout, source -> navController.navigate(SuggestedUsersListRoute(title, useRowLayout, source)) },
                onNavigateToContactFriends = { navController.navigate(ContactFriendsListRoute) },
                onNavigateToHashtag = { hashtag -> navController.navigate(HashtagFeedRoute(hashtag)) },
            )
        }
        sharedDestinations(navController, mainTabViewModel, navigateToUserByUsername = navigateToUserByUsername, onShowComments = { commentPostId = it }, onShowLikes = { likesPostId = it })
    }

    commentPostId?.let { postId ->
        CommentsBottomSheet(
            postId = postId,
            onDismiss = { commentPostId = null },
            onNavigateToUser = { userId ->
                commentPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
            onNavigateToSong = { track ->
                commentPostId = null
                navController.navigate(track.toSongDetailRoute())
            },
            onNavigateToFilm = { movie ->
                commentPostId = null
                navController.navigate(FilmDetailRoute(movie.id))
            },
        )
    }

    likesPostId?.let { postId ->
        LikesBottomSheet(
            postId = postId,
            onDismiss = { likesPostId = null },
            onNavigateToUser = { userId ->
                likesPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
        )
    }
}

@Composable
fun NotificationsNavGraph(navController: NavHostController, mainTabViewModel: MainTabViewModel, scrollToTopTrigger: Int = 0) {
    var commentPostId by remember { mutableStateOf<String?>(null) }
    var likesPostId by remember { mutableStateOf<String?>(null) }
    val navigateToUserByUsername = rememberNavigateToUserByUsername(navController)

    NavHost(
        navController = navController,
        startDestination = NotificationsTabRoute,
        enterTransition = { slideInHorizontally(tween(400), initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { -it / 3 }) },
        popEnterTransition = { slideInHorizontally(tween(400), initialOffsetX = { -it / 3 }) },
        popExitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { it }) },
    ) {
        composable<NotificationsTabRoute> {
            val unreadMessageCount by mainTabViewModel.unreadMessageCount.collectAsState()
            NotificationsScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                unreadMessageCount = unreadMessageCount,
                onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
                onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
                onNavigateToMessages = { navController.navigate(ThreadListRoute) },
                onNavigateToPostComments = { postId, commentId ->
                    navController.navigate(SinglePostCommentsRoute(postId, commentId))
                },
            )
        }
        sharedDestinations(navController, mainTabViewModel, navigateToUserByUsername = navigateToUserByUsername, onShowComments = { commentPostId = it }, onShowLikes = { likesPostId = it })
    }

    commentPostId?.let { postId ->
        CommentsBottomSheet(
            postId = postId,
            onDismiss = { commentPostId = null },
            onNavigateToUser = { userId ->
                commentPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
            onNavigateToSong = { track ->
                commentPostId = null
                navController.navigate(track.toSongDetailRoute())
            },
            onNavigateToFilm = { movie ->
                commentPostId = null
                navController.navigate(FilmDetailRoute(movie.id))
            },
        )
    }

    likesPostId?.let { postId ->
        LikesBottomSheet(
            postId = postId,
            onDismiss = { likesPostId = null },
            onNavigateToUser = { userId ->
                likesPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
        )
    }
}

@Composable
fun ProfileNavGraph(navController: NavHostController, mainTabViewModel: MainTabViewModel, scrollToTopTrigger: Int = 0, onOpenCompose: (String) -> Unit = {}) {
    var commentPostId by remember { mutableStateOf<String?>(null) }
    var likesPostId by remember { mutableStateOf<String?>(null) }
    val navigateToUserByUsername = rememberNavigateToUserByUsername(navController)

    NavHost(
        navController = navController,
        startDestination = ProfileTabRoute,
        enterTransition = { slideInHorizontally(tween(400), initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { -it / 3 }) },
        popEnterTransition = { slideInHorizontally(tween(400), initialOffsetX = { -it / 3 }) },
        popExitTransition = { slideOutHorizontally(tween(400), targetOffsetX = { it }) },
    ) {
        composable<ProfileTabRoute> { backStackEntry ->
            val openStylePicker = backStackEntry.savedStateHandle
                .get<Boolean>("open_style_picker") == true
            ProfileScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                openStylePicker = openStylePicker,
                onStylePickerConsumed = {
                    backStackEntry.savedStateHandle.remove<Boolean>("open_style_picker")
                },
                onNavigateToSettings = { navController.navigate(SettingsRoute) },
                onNavigateToEditProfile = { navController.navigate(EditProfileRoute(it)) },
                onNavigateToFollowList = { userId, isFollowers ->
                    navController.navigate(FollowListRoute(userId, isFollowers))
                },
                onNavigateToProfileFeed = { userId, username, postId, segment ->
                    navController.navigate(ProfileFeedRoute(userId, username, segment, postId))
                },
                onNavigateToClub = { navController.navigate(CymbalClubOfferRoute()) },
                onOpenCompose = onOpenCompose,
            )
        }
        sharedDestinations(navController, mainTabViewModel, navigateToUserByUsername = navigateToUserByUsername, onShowComments = { commentPostId = it }, onShowLikes = { likesPostId = it })
    }

    commentPostId?.let { postId ->
        CommentsBottomSheet(
            postId = postId,
            onDismiss = { commentPostId = null },
            onNavigateToUser = { userId ->
                commentPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
            onNavigateToSong = { track ->
                commentPostId = null
                navController.navigate(track.toSongDetailRoute())
            },
            onNavigateToFilm = { movie ->
                commentPostId = null
                navController.navigate(FilmDetailRoute(movie.id))
            },
        )
    }

    likesPostId?.let { postId ->
        LikesBottomSheet(
            postId = postId,
            onDismiss = { likesPostId = null },
            onNavigateToUser = { userId ->
                likesPostId = null
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
    navigateToUserByUsername: (String) -> Unit,
    onShowComments: (String) -> Unit = {},
    onShowLikes: (String) -> Unit = {},
) {
    composable<PostDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<PostDetailRoute>()
        PostDetailScreen(
            postId = route.postId,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToUserByUsername = navigateToUserByUsername,
            onNavigateToComments = onShowComments,
            onNavigateToLikes = onShowLikes,
            onNavigateToSong = { track -> navController.navigate(track.toSongDetailRoute()) },
            onNavigateToFilm = { movieId -> navController.navigate(FilmDetailRoute(movieId)) },
            onNavigateToHashtag = { hashtag -> navController.navigate(HashtagFeedRoute(hashtag)) },
            onRepost = { post -> mainTabViewModel.setRepostOriginalPost(post) },
        )
    }

    composable<ProfileFeedRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ProfileFeedRoute>()
        ProfileFeedScreen(
            userId = route.userId,
            username = route.username,
            segment = route.segment,
            initialPostId = route.initialPostId,
            hashtag = route.hashtag,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToUserByUsername = navigateToUserByUsername,
            onNavigateToComments = onShowComments,
            onNavigateToLikes = onShowLikes,
            onNavigateToSong = { track -> navController.navigate(track.toSongDetailRoute()) },
            onNavigateToFilm = { movieId -> navController.navigate(FilmDetailRoute(movieId)) },
            onNavigateToHashtag = { hashtag -> navController.navigate(HashtagFeedRoute(hashtag)) },
            onRepost = { post -> mainTabViewModel.setRepostOriginalPost(post) },
        )
    }

    composable<OtherProfileRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<OtherProfileRoute>()
        OtherProfileScreen(
            userId = route.userId,
            initialAvatarURL = route.avatarURL,
            initialAvatarThumbURL = route.avatarThumbURL,
            initialDisplayName = route.initialDisplayName,
            initialUsername = route.initialUsername,
            initialBio = route.initialBio,
            initialCymbalCount = route.initialCymbalCount,
            initialFollowerCount = route.initialFollowerCount,
            initialFollowingCount = route.initialFollowingCount,
            initialIsVerified = route.initialIsVerified,
            initialIsClubMember = route.initialIsClubMember,
            initialIsFollowing = route.initialIsFollowing,
            onBack = { navController.popBackStack() },
            onNavigateToProfileFeed = { userId, username, postId, segment ->
                navController.navigate(ProfileFeedRoute(userId, username, segment, postId))
            },
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
                onNavigateToProfileFeed = { uid, uname, postId, segment ->
                    navController.navigate(ProfileFeedRoute(uid, uname, segment, postId))
                },
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
            source = route.source,
            soundcloudId = route.soundcloudId,
            soundcloudPermalinkUrl = route.soundcloudPermalinkUrl,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToCompose = { track ->
                mainTabViewModel.setPreSelectedTrack(track)
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
            onNavigateToHashtagFeed = { postId ->
                navController.navigate(
                    ProfileFeedRoute(
                        userId = "",
                        username = route.hashtag,
                        segment = 4,
                        initialPostId = postId,
                        hashtag = route.hashtag.lowercase(),
                    )
                )
            },
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
            onCustomizeProfile = {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("open_style_picker", true)
                navController.popBackStack()
            },
        )
    }

    composable<EditCaptionRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<EditCaptionRoute>()
        val captionUpdatedMsg = stringResource(fm.corus.android.R.string.nav_toast_caption_updated)
        EditCaptionSheet(
            postId = route.postId,
            initialCaption = route.initialCaption,
            albumArtURL = route.albumArtURL,
            onDismiss = { navController.popBackStack() },
            onSaved = { ToastManager.show(captionUpdatedMsg) },
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

    composable<SearchRoute> {
        SearchScreen(
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToSong = { track -> navController.navigate(track.toSongDetailRoute()) },
            onNavigateToFilm = { route -> navController.navigate(route) },
            onNavigateToBotList = { botType -> navController.navigate(BotListRoute(botType)) },
            onNavigateToSuggestedUsers = { title, useRowLayout, source -> navController.navigate(SuggestedUsersListRoute(title, useRowLayout, source)) },
            onNavigateToContactFriends = { navController.navigate(ContactFriendsListRoute) },
            onNavigateToHashtag = { hashtag -> navController.navigate(HashtagFeedRoute(hashtag)) },
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
            onNavigateToSong = { track -> navController.navigate(track.toSongDetailRoute()) },
            onNavigateToFilm = { movieId -> navController.navigate(FilmDetailRoute(movieId)) },
            onNavigateToHashtag = { hashtag -> navController.navigate(HashtagFeedRoute(hashtag)) },
            onNavigateToLikes = onShowLikes,
            onNavigateToCommentLikes = { commentId ->
                navController.navigate(CommentLikesRoute(route.postId, commentId))
            },
            onRepost = { post -> mainTabViewModel.setRepostOriginalPost(post) },
        )
    }

    composable<CommentLikesRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<CommentLikesRoute>()
        CommentLikesScreen(
            postId = route.postId,
            commentId = route.commentId,
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
            onNavigateToSong = { track ->
                navController.navigate(track.toSongDetailRoute())
            },
            onNavigateToFilm = { movie ->
                navController.navigate(FilmDetailRoute(movie.id))
            },
        )
    }

    composable<SuggestedUsersListRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<SuggestedUsersListRoute>()
        val viewModel: SuggestedUsersListViewModel = hiltViewModel()
        val suggestions by viewModel.suggestions.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val isLoadingMore by viewModel.isLoadingMore.collectAsState()
        val isRefreshing by viewModel.isRefreshing.collectAsState()
        val hasMore by viewModel.hasMore.collectAsState()
        val followedIds by viewModel.followedIds.collectAsState()

        SuggestedUsersListScreen(
            matches = suggestions,
            title = route.title,
            useRowLayout = route.useRowLayout,
            source = route.source,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            onLoadMore = { viewModel.loadMore() },
            isFollowed = { followedIds.contains(it) },
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
