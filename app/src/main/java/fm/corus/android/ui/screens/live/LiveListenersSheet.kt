package fm.corus.android.ui.screens.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.localization.CorusStrings
import fm.corus.android.ui.screens.feed.CorusUserListRow
import fm.corus.android.ui.screens.feed.CorusUserListSearchField
import fm.corus.android.ui.theme.CorusColors
import kotlinx.coroutines.CancellationException

@Composable
internal fun LiveListenersSheet(
    vm: LiveViewModel,
    room: LiveRoom,
    listenerCount: Int,
    viewerId: String?,
    close: () -> Unit,
    onProfile: (String) -> Unit,
) {
    var roster by remember(room.id) { mutableStateOf<List<LiveFields>?>(null) }
    var failed by remember(room.id) { mutableStateOf(false) }
    var profiles by remember(room.id) { mutableStateOf<Map<String, LivePerson>>(emptyMap()) }
    var search by remember(room.id) { mutableStateOf("") }
    var followerIds by remember(room.id, viewerId) { mutableStateOf(emptySet<String>()) }
    var followStatusReady by remember(room.id, viewerId) { mutableStateOf(emptySet<String>()) }
    val followingIds by vm.followingIds.collectAsState()
    val followPending by vm.listenerFollowPending.collectAsState()

    LaunchedEffect(room.id, room.revision, room.likes, listenerCount) {
        try {
            roster = vm.listenerRoster(room.id)
            failed = false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = roster == null
        }
    }

    val ids = roster.orEmpty().map { it.text("uid") }
    LaunchedEffect(ids) {
        try {
            profiles = vm.listenerProfiles(ids)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Rows can still use the username and avatar from the roster.
        }
    }
    LaunchedEffect(viewerId, ids) {
        val targets = ids.filter { it != viewerId && it !in followStatusReady }
        if (viewerId == null || targets.isEmpty()) return@LaunchedEffect
        try {
            followerIds += vm.listenerFollowerIds(viewerId, targets)
            followStatusReady += targets
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Keep follow actions hidden until their status is known.
        }
    }

    val query = search.trim()
    val visibleListeners = roster.orEmpty().map { fields ->
        val person = LivePerson.parse(fields)
        val user = profiles[person.uid]?.profile ?: CymbalUser(
            id = person.uid,
            username = person.username,
            displayName = "",
            avatarURL = person.avatar,
        )
        user to (room.current != null && fields["likedRevision"] != null &&
            fields.number("likedRevision") == room.revision)
    }.filter { (user, _) ->
        query.isEmpty() || user.username.contains(query, ignoreCase = true) ||
            user.displayName.contains(query, ignoreCase = true)
    }

    LiveSheet(
        title = stringResource(CorusStrings.corus_live_listeners),
        close = close,
        large = true,
        sheetToolbar = true,
        startsHalfExpanded = true,
        dismissKeyboardFromHeader = true,
        horizontalContentPadding = 0.dp,
        showCloseButton = true,
    ) {
        Column(Modifier.weight(1f)) {
            if (ids.size >= 10 || search.isNotEmpty()) {
                CorusUserListSearchField(search) { search = it }
                HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            }
            LazyColumn(Modifier.weight(1f).padding(top = 4.dp)) {
                if (visibleListeners.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                failed -> LiveText(stringResource(CorusStrings.common_error_7104f711), color = CorusColors.Secondary)
                                roster == null -> CircularProgressIndicator(color = CorusColors.Accent)
                                query.isNotEmpty() -> LiveText(stringResource(CorusStrings.follow_list_no_results), color = CorusColors.Secondary)
                                else -> LiveText(stringResource(CorusStrings.corus_live_no_listeners_yet), color = CorusColors.Secondary)
                            }
                        }
                    }
                }
                items(visibleListeners, key = { (user, _) -> user.id }) { (user, likedSong) ->
                    val id = user.id
                    CorusUserListRow(
                        user = user,
                        isFollowing = id in followingIds,
                        isFollower = id in followerIds,
                        onUserTap = { onProfile(id) },
                        onFollowTap = if (viewerId != null && id != viewerId && id in followStatusReady) {
                            { vm.toggleListenerFollow(id) }
                        } else {
                            null
                        },
                        followPending = id in followPending,
                    ) {
                        if (likedSong) {
                            Icon(
                                Icons.Default.Favorite,
                                stringResource(R.string.live_liked_this_song),
                                tint = LiveRed,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
                // Let the sheet expand by dragging anywhere in a short roster.
                item { Spacer(Modifier.fillParentMaxHeight()) }
            }
        }
    }
}
