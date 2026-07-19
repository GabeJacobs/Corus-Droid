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
import fm.corus.android.ui.screens.feed.RepostersBottomSheet
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
import fm.corus.android.ui.screens.search.TrendingListScreen
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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RemoteConfigServiceEntryPoint {
    fun remoteConfigService(): fm.corus.android.service.RemoteConfigService
}

/**
 * Synchronous read of the `artist_pages_enabled` Remote Config flag for the
 * nav graphs. When off, the artist/director navigation callbacks passed to
 * screens stay null, so every entry point (tappable names, search rows)
 * renders exactly as before and the destination routes are unreachable.
 */
@Composable
internal fun rememberArtistPagesEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            RemoteConfigServiceEntryPoint::class.java,
        ).remoteConfigService().artistPagesEnabled
    }
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
    var repostersPostId by remember { mutableStateOf<String?>(null) }
    val navigateToUserByUsername = rememberNavigateToUserByUsername(navController)
    val artistPagesEnabled = rememberArtistPagesEnabled()
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
                onShowReposters = { repostersPostId = it },
                onNavigateToCompose = onNavigateToCompose,
                onNavigateToArtist = if (artistPagesEnabled) { { route -> navController.navigate(route) } } else null,
                onNavigateToDirector = if (artistPagesEnabled) { { route -> navController.navigate(route) } } else null,
                onNavigateToAlbum = if (artistPagesEnabled) { { route -> navController.navigate(route) } } else null,
            )
        }
        sharedDestinations(navController, mainTabViewModel, navigateToUserByUsername = navigateToUserByUsername, onShowComments = onShowComments, onShowLikes = { likesPostId = it }, onShowReposters = { repostersPostId = it }, isContainingTabSelected = isFeedTabSelected, artistPagesEnabled = artistPagesEnabled)
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

    repostersPostId?.let { postId ->
        RepostersBottomSheet(
            postId = postId,
            onDismiss = { repostersPostId = null },
            onNavigateToUser = { userId ->
                repostersPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
            onNavigateToPost = { pid ->
                repostersPostId = null
                navController.navigate(PostDetailRoute(pid))
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
    var repostersPostId by remember { mutableStateOf<String?>(null) }
    val navigateToUserByUsername = rememberNavigateToUserByUsername(navController)
    val artistPagesEnabled = rememberArtistPagesEnabled()

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
                onNavigateToArtist = { route -> navController.navigate(route) },
                onNavigateToAlbum = { route -> navController.navigate(route) },
                onNavigateToDirector = { route -> navController.navigate(route) },
                onNavigateToTrending = { kind -> navController.navigate(TrendingListRoute(kind)) },
            )
        }
        sharedDestinations(navController, mainTabViewModel, navigateToUserByUsername = navigateToUserByUsername, onShowComments = onShowComments, onShowLikes = { likesPostId = it }, onShowReposters = { repostersPostId = it }, isContainingTabSelected = isContainingTabSelected, artistPagesEnabled = artistPagesEnabled)
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

    repostersPostId?.let { postId ->
        RepostersBottomSheet(
            postId = postId,
            onDismiss = { repostersPostId = null },
            onNavigateToUser = { userId ->
                repostersPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
            onNavigateToPost = { pid ->
                repostersPostId = null
                navController.navigate(PostDetailRoute(pid))
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
    var repostersPostId by remember { mutableStateOf<String?>(null) }
    val navigateToUserByUsername = rememberNavigateToUserByUsername(navController)
    val artistPagesEnabled = rememberArtistPagesEnabled()

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
        sharedDestinations(navController, mainTabViewModel, navigateToUserByUsername = navigateToUserByUsername, onShowComments = onShowComments, onShowLikes = { likesPostId = it }, onShowReposters = { repostersPostId = it }, isContainingTabSelected = isContainingTabSelected, artistPagesEnabled = artistPagesEnabled)
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

    repostersPostId?.let { postId ->
        RepostersBottomSheet(
            postId = postId,
            onDismiss = { repostersPostId = null },
            onNavigateToUser = { userId ->
                repostersPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
            onNavigateToPost = { pid ->
                repostersPostId = null
                navController.navigate(PostDetailRoute(pid))
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
    var repostersPostId by remember { mutableStateOf<String?>(null) }
    val navigateToUserByUsername = rememberNavigateToUserByUsername(navController)
    val artistPagesEnabled = rememberArtistPagesEnabled()

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
                onNavigateToFollowList = { userId, isFollowers, username, followerCount, followingCount ->
                    navController.navigate(FollowListRoute(userId, isFollowers, username, followerCount, followingCount))
                },
                onNavigateToProfileFeed = { userId, username, postId, segment ->
                    navController.navigate(ProfileFeedRoute(userId, username, segment, postId))
                },
                onNavigateToClub = { navController.navigate(CymbalClubOfferRoute()) },
                onOpenCompose = onOpenCompose,
            )
        }
        sharedDestinations(navController, mainTabViewModel, navigateToUserByUsername = navigateToUserByUsername, onShowComments = onShowComments, onShowLikes = { likesPostId = it }, onShowReposters = { repostersPostId = it }, isContainingTabSelected = isContainingTabSelected, artistPagesEnabled = artistPagesEnabled)
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

    repostersPostId?.let { postId ->
        RepostersBottomSheet(
            postId = postId,
            onDismiss = { repostersPostId = null },
            onNavigateToUser = { userId ->
                repostersPostId = null
                navController.navigate(OtherProfileRoute(userId))
            },
            onNavigateToPost = { pid ->
                repostersPostId = null
                navController.navigate(PostDetailRoute(pid))
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
    onShowReposters: (String) -> Unit = {},
    /**
     * True when the tab that owns this NavGraph is currently selected. Used
     * by feed-style destinations (ProfileFeedScreen) to gate registration
     * of the mini-player tap-to-scroll handler so a background tab's
     * ProfileFeedScreen doesn't claim a tap meant for the visible feed.
     */
    isContainingTabSelected: Boolean = true,
    /** Gates every artist/album/director page entry point (tappable names).
     *  The destinations themselves are always registered, but with the flag
     *  off no callback navigates to them, so they're unreachable. */
    artistPagesEnabled: Boolean = false,
) {
    // Nullable-when-flag-off navigation callbacks. Screens receive these and
    // keep their names as plain text whenever they're null.
    val navigateToArtist: ((ArtistPageRoute) -> Unit)? =
        if (artistPagesEnabled) { { route -> navController.navigate(route) } } else null
    val navigateToAlbum: ((AlbumPageRoute) -> Unit)? =
        if (artistPagesEnabled) { { route -> navController.navigate(route) } } else null
    val navigateToDirector: ((DirectorPageRoute) -> Unit)? =
        if (artistPagesEnabled) { { route -> navController.navigate(route) } } else null
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
            onShowReposters = onShowReposters,
            onNavigateToArtist = navigateToArtist,
            onNavigateToDirector = navigateToDirector,
            onNavigateToAlbum = navigateToAlbum,
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
            onShowReposters = onShowReposters,
            onNavigateToArtist = navigateToArtist,
            onNavigateToDirector = navigateToDirector,
            onNavigateToAlbum = navigateToAlbum,
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
            onNavigateToFollowList = { userId, isFollowers, username, followerCount, followingCount ->
                navController.navigate(FollowListRoute(userId, isFollowers, username, followerCount, followingCount))
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
                onNavigateToFollowList = { uid, isFollowers, username, followerCount, followingCount ->
                    navController.navigate(FollowListRoute(uid, isFollowers, username, followerCount, followingCount))
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
            audiomackUrl = route.audiomackUrl,
            isrc = route.isrc,
            artistId = route.artistId,
            artistIdCount = route.artistIdCount,
            albumId = route.albumId,
            releaseDate = route.releaseDate,
            releaseDatePrecision = route.releaseDatePrecision,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
            onNavigateToCompose = { track ->
                mainTabViewModel.setPreSelectedTrack(track)
            },
            onNavigateToArtist = navigateToArtist,
            onNavigateToAlbum = navigateToAlbum,
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
            initialMovieReleaseDate = route.movieReleaseDate,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
            onNavigateToCompose = { movieId ->
                mainTabViewModel.setPreSelectedMovieId(movieId)
            },
            onNavigateToDirector = navigateToDirector,
        )
    }

    // ── Artist / Album / Director destination pages (artist_pages_enabled) ──

    composable<ArtistPageRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ArtistPageRoute>()
        // In-place scroll requests from the mini-player (when this page is already
        // visible) land in this entry's saved state — scoped to exactly this
        // screen instance, so a duplicate page is never pushed.
        val inPlaceScroll by backStackEntry.savedStateHandle
            .getStateFlow<String?>(CATALOG_SCROLL_TO_TRACK_KEY, null)
            .collectAsState()
        fm.corus.android.ui.screens.destination.ArtistPageScreen(
            artistId = route.artistId,
            nameHint = route.name,
            imageUrlHint = route.imageUrl,
            scrollToTrackId = route.scrollToTrackId,
            inPlaceScrollTrackId = inPlaceScroll,
            onInPlaceScrollConsumed = {
                backStackEntry.savedStateHandle[CATALOG_SCROLL_TO_TRACK_KEY] = null
            },
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
            onNavigateToSong = { songRoute -> navController.navigate(songRoute) },
            onNavigateToAlbum = { albumRoute -> navController.navigate(albumRoute) },
            onSeeAllPosts = {
                navController.navigate(ArtistPostsRoute(route.artistId, route.name))
            },
            onSeeAllDiscography = {
                navController.navigate(ArtistDiscographyRoute(route.artistId, route.name))
            },
            onSeeAllVideos = {
                navController.navigate(ArtistMusicVideosRoute(route.artistId, route.name))
            },
        )
    }

    composable<AlbumPageRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<AlbumPageRoute>()
        val inPlaceScroll by backStackEntry.savedStateHandle
            .getStateFlow<String?>(CATALOG_SCROLL_TO_TRACK_KEY, null)
            .collectAsState()
        fm.corus.android.ui.screens.destination.AlbumPageScreen(
            albumId = route.albumId,
            titleHint = route.title,
            artistHint = route.artist,
            coverUrlHint = route.coverUrl,
            yearHint = route.year,
            scrollToTrackId = route.scrollToTrackId,
            inPlaceScrollTrackId = inPlaceScroll,
            onInPlaceScrollConsumed = {
                backStackEntry.savedStateHandle[CATALOG_SCROLL_TO_TRACK_KEY] = null
            },
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
            onNavigateToSong = { songRoute -> navController.navigate(songRoute) },
            onNavigateToArtist = { artistRoute -> navController.navigate(artistRoute) },
        )
    }

    composable<DirectorPageRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<DirectorPageRoute>()
        fm.corus.android.ui.screens.destination.DirectorPageScreen(
            directorId = route.directorId,
            nameHint = route.name,
            imageUrlHint = route.imageUrl,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
            onNavigateToFilm = { filmRoute -> navController.navigate(filmRoute) },
            onSeeAllPosts = {
                navController.navigate(DirectorPostsRoute(route.directorId, route.name))
            },
            onSeeAllFilmography = {
                navController.navigate(DirectorFilmographyRoute(route.directorId, route.name))
            },
            onSeeAllTrailers = {
                navController.navigate(DirectorTrailersRoute(route.directorId, route.name))
            },
        )
    }

    composable<ArtistDiscographyRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ArtistDiscographyRoute>()
        fm.corus.android.ui.screens.destination.ArtistDiscographyScreen(
            artistId = route.artistId,
            nameHint = route.name,
            onBack = { navController.popBackStack() },
            onNavigateToAlbum = { albumRoute -> navController.navigate(albumRoute) },
        )
    }

    composable<ArtistMusicVideosRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ArtistMusicVideosRoute>()
        fm.corus.android.ui.screens.destination.ArtistMusicVideosScreen(
            artistId = route.artistId,
            nameHint = route.name,
            onBack = { navController.popBackStack() },
        )
    }

    composable<DirectorTrailersRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<DirectorTrailersRoute>()
        fm.corus.android.ui.screens.destination.DirectorTrailersScreen(
            directorId = route.directorId,
            nameHint = route.name,
            onBack = { navController.popBackStack() },
        )
    }

    composable<ArtistPostsRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ArtistPostsRoute>()
        fm.corus.android.ui.screens.destination.DestinationPostsScreen(
            kind = fm.corus.android.ui.screens.destination.DestinationPostsViewModel.Kind.ARTIST,
            subjectId = route.artistId,
            subjectName = route.name,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
            onNavigateToFilm = { filmRoute -> navController.navigate(filmRoute) },
        )
    }

    composable<DirectorFilmographyRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<DirectorFilmographyRoute>()
        fm.corus.android.ui.screens.destination.DirectorFilmographyScreen(
            directorId = route.directorId,
            nameHint = route.name,
            onBack = { navController.popBackStack() },
            onNavigateToFilm = { filmRoute -> navController.navigate(filmRoute) },
        )
    }

    composable<DirectorPostsRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<DirectorPostsRoute>()
        fm.corus.android.ui.screens.destination.DestinationPostsScreen(
            kind = fm.corus.android.ui.screens.destination.DestinationPostsViewModel.Kind.DIRECTOR,
            subjectId = route.directorId,
            subjectName = route.name,
            onBack = { navController.popBackStack() },
            onNavigateToUser = { userId -> navController.navigate(OtherProfileRoute(userId)) },
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
            onNavigateToFilm = { filmRoute -> navController.navigate(filmRoute) },
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
            title = route.username,
            followerCount = route.followerCount,
            followingCount = route.followingCount,
            initialShowFollowers = route.isFollowers,
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
            onNavigateToArtist = { route -> navController.navigate(route) },
            onNavigateToAlbum = { route -> navController.navigate(route) },
            onNavigateToDirector = { route -> navController.navigate(route) },
            onNavigateToTrending = { kind -> navController.navigate(TrendingListRoute(kind)) },
        )
    }

    composable<TrendingListRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<TrendingListRoute>()
        TrendingListScreen(
            kind = route.kind,
            onBack = { navController.popBackStack() },
            onNavigateToSong = { track -> navController.navigate(track.toSongDetailRoute()) },
            onNavigateToFilm = { filmRoute -> navController.navigate(filmRoute) },
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
            onShowReposters = onShowReposters,
            onNavigateToArtist = navigateToArtist,
            onNavigateToDirector = navigateToDirector,
            onNavigateToAlbum = navigateToAlbum,
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
            onNavigateToPost = { postId -> navController.navigate(PostDetailRoute(postId)) },
            onNavigateToArtist = { id, name, image ->
                navigateToArtist?.invoke(ArtistPageRoute(id, name, image))
            },
            onNavigateToAlbum = { id, title, artist, cover, year ->
                navigateToAlbum?.invoke(AlbumPageRoute(id, title, artist, cover, year))
            },
            onNavigateToDirector = { id, name, image ->
                navigateToDirector?.invoke(DirectorPageRoute(id, name, image))
            },
        )
    }

    composable<SuggestedUsersListRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<SuggestedUsersListRoute>()
        val viewModel: SuggestedUsersListViewModel = hiltViewModel()
        // Render the filtered view so the tasteMatches "Unfollowed" filter drops
        // already-followed matches; all other sources pass through unchanged.
        val suggestions by viewModel.visibleSuggestions.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val isLoadingMore by viewModel.isLoadingMore.collectAsState()
        val isRefreshing by viewModel.isRefreshing.collectAsState()
        val hasMore by viewModel.hasMore.collectAsState()
        val followedIds by viewModel.followedIds.collectAsState()
        val filterUnfollowed by viewModel.filterUnfollowed.collectAsState()
        val isFilling by viewModel.isFilling.collectAsState()

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
            // Toggle available for the taste-matches list once the viewer follows
            // anyone. Mirrors iOS TasteMatchesListView (toolbar filter shown when
            // currentUserFollowingIds is non-empty).
            showFilterToggle = route.source == "tasteMatches" && followedIds.isNotEmpty(),
            filterUnfollowed = filterUnfollowed,
            onSetFilterUnfollowed = { viewModel.setTasteMatchFilter(it) },
            isFilling = isFilling,
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
