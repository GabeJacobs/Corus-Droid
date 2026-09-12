package fm.corus.android.ui.screens.map

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.ui.components.SharePostSheet
import fm.corus.android.ui.screens.feed.PostDetailViewModel
import fm.corus.android.ui.theme.CorusColors

/** Uses the app's shared optimistic store, save limits, recipient search and repost flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPostEngagement(post: CymbalPost, onComments: () -> Unit, onRepost: (CymbalPost) -> Unit, onPaywall: () -> Unit, model: PostDetailViewModel = hiltViewModel()) {
    LaunchedEffect(model) { model.saveCapEvents.collect { event -> when(event) {
        is fm.corus.android.domain.SaveCapEvent.PaywallRequested -> onPaywall()
        is fm.corus.android.domain.SaveCapEvent.WarningToast -> fm.corus.android.ui.components.ToastManager.show(event.message)
    } } }
    val states by model.engagementStates.collectAsState()
    val engagement = states[post.id]
    val recent by model.recentShareContacts.collectAsState()
    val results by model.shareSearchResults.collectAsState()
    val searching by model.isShareSearching.collectAsState()
    val loading by model.isLoadingShareContacts.collectAsState()
    var shareTarget by remember { mutableStateOf<CymbalPost?>(null) }
    LaunchedEffect(post.id) { model.loadPost(post.id, includeComments = false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = { model.toggleLike(post.id) }) { Icon(if (engagement?.isLiked == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = if (engagement?.isLiked == true) CorusColors.Accent else CorusColors.Secondary); Text(" ${engagement?.likeCount ?: post.likeCount}") }
        TextButton(onClick = onComments) { Icon(Icons.Default.ChatBubbleOutline, "Comments"); Text(" ${engagement?.commentCount ?: post.commentCount}") }
        IconButton(onClick = { onRepost(post) }) { Icon(Icons.Default.Repeat, "Repost") }
        IconButton(onClick = { model.loadRecentShareContacts(); shareTarget = post }) { Icon(Icons.Default.Share, "Share") }
        IconButton(onClick = { model.toggleSave(post.id) }) { Icon(if (engagement?.isSaved == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Save") }
    }
    shareTarget?.let { tapped -> SharePostSheet(post = tapped, recentContacts = recent, searchResults = results, isSearching = searching, isLoadingContacts = loading, instagramShareEnabled = model.remoteConfig.instagramShareEnabled, sheetState = rememberModalBottomSheetState(), onSearchQueryChange = model::searchShareUsers, onSendToUser = { user, message -> model.sendPostToUser(user, tapped, message); shareTarget = null }, onRepost = { shareTarget = null; onRepost(tapped) }, onDismiss = { shareTarget = null }) }
}
