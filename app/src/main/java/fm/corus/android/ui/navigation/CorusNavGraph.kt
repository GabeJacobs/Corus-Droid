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
import fm.corus.android.ui.screens.explore.HashtagPeopleListScreen
import fm.corus.android.ui.screens.feed.CommentLikesScreen
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
import fm.corus.android.ui.screens.settings.SyncContactsSettingsScreen
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
fun FeedNavGraph(
    navController: NavHostController,
    mainTabViewModel: MainTabViewModel,
    scrollToTopTrigger: Int = 0,
    isFeedTabSelected: Boolean = true,
    onShowComments: (String) -> Unit = {},
    onNavigateToCompose: () -> Unit = {},
) {
    var likesPostId by remember { mutableStateOf<String?>(null) }
    val navigateToUserByUsername = rememberNavigateToUserByUsername(navController)
    // Synchronous access to the cached following set so feed → profile
    // navigation can seed the correct follow state on the first frame (no
    // Follow→Following flash for authors the viewer already follows).
    val followStateContext = LocalContext.current
    val userRepository = remember(followStateContext) {
        EntryPointAccessors.fromApplication(
            followStateContext.applicationContext,
            UserRepositoryEntryPoint::class.java,
        ).userRepository()
    }

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
                isAtRoot = isFeedTabSelected,
                onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
                onNavigateToUser = { user ->
                    navController.navigate(OtherProfileRoute(
                        userId = user.id,
                        avatarURL = user.avatarURL,
                        avatarThumbURL = user.avatarThumbURL,
                        initialDisplayName = user.displayName,
                        initialUsername = user.username,
                        initialBio = user.bio,
                        // The feed post's author is denormalized preview data
                        // (fromAuthorPreview) that never carries counts, so these
                        // are always 0 here. Leave them null so the loading header
                        // shimmers the stats instead of flashing "0 coruses/
                        // followers/following" until the live profile lands.
                        initialIsVerified = user.isVerified,
                        initialIsClubMember = user.isClubMember,
                        // Seed the real follow state from the cached following set
                        // (synchronous, same source as the feed's Follow pill) so
                        // the profile opens in the correct state with no
                        // Follow→Following flash. Hardcoding `true` here was a
                        // Following-feed-era assumption that showed "Following" for
                        // unfollowed Trending/For You authors.
                        initialIsFollowing = userRepository.isFollowing(user.id),
                    ))
                },
                onNavigateToUserById = { userId -> navController.navigate(OtherProfileRoute(userId)) },
                onNavigateToUserByUsername = navigateToUserByUsername,
                onNavigateToComments = onShowComments,
                onNavigateToLikes = { postId -> likesPostId = postId },
                onNavigateToHashtag = { hashtag -> navController.navigate(HashtagFeedRoute(hashtag)) },
                onNavigateToSong = { track -> navController.navigate(track.toSongDetailRoute()) },
                onNavigateToFilm = { movieId -> navController.navigate(FilmDetailRoute(movieId)) },
                onRepost = { post -> mainTabViewModel.setRepostOriginalPost(post) },
                onNavigateToCompose = onNavigateToCompose,
            )
        }
        sharedDestinations(navController, mainTabViewModel, navigateToUserByUsername = navigateToUserByUsername, onShowComments = onShowComments, onShowLikes = { likesPostId = it }, isContainingTabSelected = isFeedTabSelected)
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
fun SearchNavGraph(
    navController: NavHostController,
    mainTabViewModel: MainTabViewModel,
    scrollToTopTrigger: Int = 0,
    isContainingTabSelected: Boolean = true,
    onShowComments: (String) -> Unit = {},
) {
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
                onNavigateToSuggestedUsers = { title, useRowLayout, source -> navController.navigate(SuggestedUsersListRoute(title, useRowLayout, source)) },
                onNavigateToContactFriends = { navController.navigate(ContactFriendsListRoute) },
                onNavigateToHashtag = { hashtag -> navController.navigate(HashtagFeedRoute(hashtag)) },
            )
        }
        sharedDestinations(navController, mainTabViewModel, navigateToUserByUsername = navigateToUserByUsername, onShowComments = onShowComments, onShowLikes = { likesPostId = it }, isContainingTabSelected = isContainingTabSelected)
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
fun NotificationsNavGraph(
    navController: NavHostController,
    mainTabViewModel: MainTabViewModel,
    scrollToTopTrigger: Int = 0,
    tabActivationTrigger: Int = 0,
    isContainingTabSelected: Boolean = true,
    onShowComments: (String) -> Unit = {},
) {
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
                tabActivationTrigger = tabActivationTrigger,
                unreadMessageCount = unreadMessageCount,
                onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
                onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
                onNavigateToMessages = { navController.navigate(ThreadListRoute) },
                onNavigateToPostComments = { postId, commentId ->
                    navController.navigate(SinglePostCommentsRoute(postId, commentId))
                },
            )
        }
        sharedDestinations(navController, mainTabViewModel, navigateToUserByUsername = navigateToUserByUsername, onShowComments = onShowComments, onShowLikes = { likesPostId = it }, isContainingTabSelected = isContainingTabSelected)
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
fun ProfileNavGraph(
    navController: NavHostController,
    mainTabViewModel: MainTabViewModel,
    scrollToTopTrigger: Int = 0,
    tabActivationTrigger: Int = 0,
    onOpenCompose: (String) -> Unit = {},
    isContainingTabSelected: Boolean = true,
    onShowComments: (String) -> Unit = {},
) {
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
                tabActivationTrigger = tabActivationTrigger,
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
        sharedDestinations(navController, mainTabViewModel, navigateToUserByUsername = navigateToUserByUsername, onShowComments = onShowComments, onShowLikes = { likesPostId = it }, isContainingTabSelected = isContainingTabSelected)
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
    /**
     * True when the tab that owns this NavGraph is currently selected. Used
     * by feed-style destinations (ProfileFeedScreen) to gate registration
     * of the mini-player tap-to-scroll handler so a background tab's
     * ProfileFeedScreen doesn't claim a tap meant for the visible feed.
     */
    isContainingTabSelected: Boolean = true,
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
            isContainingTabSelected = isContainingTabSelected,
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
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
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
                onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
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
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
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
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
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
            onNavigateToHashtagFollowers = { tag ->
                navController.navigate(HashtagPeopleRoute(tag, isFollowers = true))
            },
            onNavigateToHashtagContributors = { tag ->
                navController.navigate(HashtagPeopleRoute(tag, isFollowers = false))
            },
        )
    }

    composable<HashtagPeopleRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<HashtagPeopleRoute>()
        HashtagPeopleListScreen(
            hashtag = route.hashtag,
            isFollowers = route.isFollowers,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { uid -> navController.navigate(OtherProfileRoute(uid)) },
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
            onSyncContacts = { navController.navigate(SyncContactsSettingsRoute) },
        )
    }

    composable<SyncContactsSettingsRoute> {
        SyncContactsSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
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
            onNavigateToFilm = { route -> navController.navigate(route) },
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
            onNavigateToProfile = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToSong = { track ->
                navController.navigate(track.toSongDetailRoute())
            },
            onNavigateToFilm = { movie ->
                navController.navigate(movie.toFilmDetailRoute())
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
            followedIds = followedIds,
            onFollow = { viewModel.toggleFollow(it) },
            onUserTapped = { userId -> viewModel.logUserTapped(userId) },
            onVisibleRangeChange = { start, end -> viewModel.ensureClubMembersEnriched(start, end) },
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
        )
    }

    composable<ContactFriendsListRoute> {
        val viewModel: ContactFriendsListViewModel = hiltViewModel()
        val contacts by viewModel.contacts.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()

        ContactFriendsListScreen(
            users = contacts,
            isLoading = isLoading,
            isFollowed = { viewModel.isFollowed(it) },
            onFollow = { viewModel.toggleFollow(it) },
            onUserTapped = { userId -> viewModel.logUserTapped(userId) },
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
        )
    }

}
