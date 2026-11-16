package fm.corus.android.ui.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.data.model.FlairStyle
import fm.corus.android.data.model.TrackSource
import fm.corus.android.domain.QueuedTrack
import fm.corus.android.domain.toQueuedTrack
import fm.corus.android.localization.CorusStrings
import fm.corus.android.ui.components.CorusModalBottomSheet
import fm.corus.android.ui.components.FeedPlayingPill
import fm.corus.android.ui.components.PostActionMenuContent
import fm.corus.android.ui.components.PostActionMenuRow
import fm.corus.android.ui.components.PostActionMenuSheet
import fm.corus.android.ui.components.ShareMediaSheet
import fm.corus.android.ui.components.ShareMediaSubject
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.components.rememberBackCoverFlipState
import fm.corus.android.ui.components.launchBackCoverFlip
import fm.corus.android.ui.navigation.AlbumPageRoute
import fm.corus.android.ui.navigation.ArtistPageRoute
import fm.corus.android.ui.screens.live.LiveAvatar
import fm.corus.android.ui.screens.live.LiveIconButton
import fm.corus.android.ui.screens.live.LiveSaveItem
import fm.corus.android.ui.screens.live.LiveText
import fm.corus.android.ui.screens.live.liveDisplayArtwork
import fm.corus.android.ui.screens.live.withLiveDisplayArtwork
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun LiveSaveItem.matches(filter: ProfileMediaFilter): Boolean = when (this) {
    is LiveSaveItem.Track -> filter != ProfileMediaFilter.FILM
    is LiveSaveItem.Post -> filter.mediaType == null || post.mediaType.value == filter.mediaType
}

internal fun LiveSaveItem.queuedTrack(): QueuedTrack? = when (this) {
    is LiveSaveItem.Track -> track.song.withLiveDisplayArtwork().toQueuedTrack().copy(sourcePostId = feedId)
    is LiveSaveItem.Post -> post.takeIf { it.isTrack }?.toQueuedTrack()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveSavedTrackCard(
    saved: LiveSaveItem.Track,
    viewModel: ProfileFeedViewModel,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlay: () -> Unit,
    onSong: () -> Unit,
    onUser: (String) -> Unit,
    onViewSet: () -> Unit,
    onUnsave: () -> Unit,
    onArtist: ((ArtistPageRoute) -> Unit)?,
    onAlbum: ((AlbumPageRoute) -> Unit)?,
) {
    val track = saved.track.song
    val service by viewModel.musicServicePreference.current.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backCover = rememberBackCoverFlipState()
    var showMenu by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var actionLoading by remember { mutableStateOf(false) }
    val actionUnavailable = stringResource(R.string.live_save_action_unavailable)
    val queued = stringResource(CorusStrings.post_menu_toast_added_to_queue)

    fun runSongAction(block: suspend () -> Boolean) {
        if (actionLoading) return
        actionLoading = true
        scope.launch {
            try {
                if (!block()) ToastManager.show(actionUnavailable)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ToastManager.show(actionUnavailable)
            } finally {
                actionLoading = false
            }
        }
    }

    fun openArtist() {
        val navigate = onArtist ?: return
        if (actionLoading) return
        val knownId = track.artistIds.firstOrNull { it.isNotBlank() }
        if (knownId != null) {
            navigate(ArtistPageRoute(knownId, name = track.artistName))
            return
        }
        runSongAction {
            val id = viewModel.resolveArtistIdForTrack(track) ?: return@runSongAction false
            navigate(ArtistPageRoute(id, name = track.artistName))
            true
        }
    }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.lg, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dj = saved.pickedBy
            if (dj != null) {
                LiveAvatar(
                    person = dj,
                    size = CorusSpacing.avatarSmall,
                    modifier = Modifier.clickable(enabled = dj.uid.isNotBlank()) { onUser(dj.uid) },
                )
            }
            Column(Modifier.weight(1f)) {
                if (dj != null) {
                    val profile = dj.profile
                    UsernameWithFlair(
                        username = dj.username,
                        isVerified = profile?.isVerified == true,
                        isClubMember = profile?.isClubMember == true,
                        isBot = profile?.isBot == true,
                        flairStyle = profile?.flairStyle ?: FlairStyle.NONE,
                        modifier = Modifier.clickable(enabled = dj.uid.isNotBlank()) { onUser(dj.uid) },
                    )
                }
                val attribution = saved.roomTitle.takeIf { it.isNotBlank() }?.let {
                    stringResource(R.string.live_save_played_in, it)
                } ?: stringResource(R.string.live_save_played_on)
                LiveText(attribution, CorusFont.caption, CorusColors.Secondary, lines = 1)
            }
            Box {
                if (actionLoading || backCover.isLoading) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                } else {
                    LiveIconButton(Icons.Default.MoreHoriz, stringResource(R.string.live_save_more), { showMenu = true })
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds().clickable(
                onClickLabel = stringResource(when {
                    backCover.isFlipped -> R.string.live_save_view_front_cover
                    isPlaying -> R.string.live_save_pause_song
                    else -> R.string.live_save_play_song
                }),
                role = Role.Button,
                onClick = { if (backCover.isFlipped) backCover.flipBack() else onPlay() },
            ),
        ) {
            ShimmerAsyncImage(
                model = if (backCover.isFlipped) backCover.backCoverURL else track.liveDisplayArtwork,
                contentDescription = track.name,
                modifier = Modifier.fillMaxSize(),
            )
            if (!backCover.isFlipped) {
                FeedPlayingPill(isPlaying = isPlaying, isLoading = isLoading)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                LiveText(track.name, CorusFont.songTitle, modifier = Modifier.clickable(onClick = onSong), lines = 2)
                LiveText(
                    track.artistName, CorusFont.artistName, CorusColors.Secondary,
                    modifier = Modifier.clickable(enabled = onArtist != null, onClick = ::openArtist), lines = 2,
                )
            }
            LiveIconButton(Icons.Default.Bookmark, stringResource(R.string.live_save_unsave), onUnsave, tint = CorusColors.Text)
        }
        HorizontalDivider(color = CorusColors.Divider)
    }
    if (showMenu) {
        PostActionMenuSheet(onDismiss = { showMenu = false }) {
            PostActionMenuContent {
                @Composable
                fun entry(icon: ImageVector, label: String, action: () -> Unit) {
                    PostActionMenuRow(icon = icon, label = label, onClick = {
                        showMenu = false
                        action()
                    })
                }

                @Composable
                fun divider() {
                    HorizontalDivider(
                        color = CorusColors.Divider,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg),
                    )
                }

                if (saved.roomId.isNotBlank()) {
                    entry(Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(R.string.live_save_view_full_set), onViewSet)
                    divider()
                }
                entry(Icons.AutoMirrored.Filled.Send, stringResource(R.string.live_save_share)) { showShare = true }
                divider()
                entry(Icons.Default.MusicNote, stringResource(R.string.live_save_go_to_song), onSong)
                if (onArtist != null) entry(Icons.Default.Person, stringResource(R.string.live_save_go_to_artist), ::openArtist)
                if (onAlbum != null) entry(Icons.Default.Album, stringResource(CorusStrings.post_kebab_go_to_album)) {
                    runSongAction {
                        val album = viewModel.resolveTrackDestinationsForTrack(track)
                        if (album.goToAlbumAsSong) onSong()
                        else {
                            val id = album.albumId ?: return@runSongAction false
                            onAlbum(AlbumPageRoute(id, track.albumName, track.artistName, track.liveDisplayArtwork))
                        }
                        true
                    }
                }
                if (!saved.track.playsFromSource) {
                    entry(
                        Icons.Outlined.Style,
                        stringResource(if (backCover.isFlipped) R.string.live_save_view_front_cover else CorusStrings.post_kebab_view_back_cover),
                    ) {
                        if (backCover.isFlipped) backCover.flipBack()
                        else scope.launchBackCoverFlip(backCover, saved.feedId) {
                            try {
                                viewModel.savedSongBackCover(track)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                }
                divider()
                val serviceLabel = when (track.source) {
                    TrackSource.SOUNDCLOUD -> stringResource(R.string.live_save_open_in, "SoundCloud")
                    TrackSource.BANDCAMP -> stringResource(R.string.live_save_open_in, "Bandcamp")
                    else -> stringResource(R.string.live_save_play_in, service.displayLabel)
                }
                entry(Icons.Default.PlayArrow, serviceLabel) {
                    runSongAction {
                        val url = viewModel.resolveServiceLinkUrl(track)
                            ?: track.appleMusicURL?.takeIf { track.source == TrackSource.APPLEMUSIC }
                            ?: track.spotifyWebURL.takeIf { it.isNotBlank() }
                            ?: track.spotifyURI.takeIf { it.isNotBlank() }
                            ?: return@runSongAction false
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        true
                    }
                }
                entry(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(CorusStrings.post_menu_add_to_queue)) {
                    saved.queuedTrack()?.let {
                        if (viewModel.nowPlayingManager.addToUserQueue(it)) ToastManager.show(queued)
                    }
                }
            }
        }
    }
    LaunchedEffect(backCover.showNotFound) {
        if (backCover.showNotFound) ToastManager.show(context.getString(R.string.live_save_no_back_cover))
    }
    if (showShare) {
        val contacts by viewModel.recentShareContacts.collectAsState()
        val results by viewModel.shareSearchResults.collectAsState()
        val searching by viewModel.isShareSearching.collectAsState()
        val loadingContacts by viewModel.isLoadingShareContacts.collectAsState()
        LaunchedEffect(Unit) {
            viewModel.searchShareUsers("")
            viewModel.loadRecentShareContacts(includeCachedFallbacks = true)
        }
        CorusModalBottomSheet(onDismissRequest = { showShare = false }, dragHandle = null) {
            ShareMediaSheet(
                subject = ShareMediaSubject.Track(track.withLiveDisplayArtwork()),
                recentContacts = contacts,
                searchResults = results,
                isSearching = searching,
                isLoadingContacts = loadingContacts,
                onSearchQueryChange = viewModel::searchShareUsers,
                onSendToUser = { recipient, message ->
                    viewModel.sendSavedSongToUser(recipient, track.withLiveDisplayArtwork(), message)
                    showShare = false
                },
                onDismiss = { showShare = false },
            )
        }
    }
}
