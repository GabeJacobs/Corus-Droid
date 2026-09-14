package fm.corus.android.ui.screens.map

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.ui.components.SharePostSheet
import fm.corus.android.ui.components.VennDiagramIcon
import fm.corus.android.ui.screens.feed.PostDetailViewModel
import fm.corus.android.ui.theme.CorusColors

/** Uses the app's shared optimistic store, save limits, recipient search and repost flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPostEngagement(post: CymbalPost, onComments: () -> Unit, onRepost: (CymbalPost) -> Unit, onPaywall: () -> Unit, onCatalog: () -> Unit = {}, model: PostDetailViewModel = hiltViewModel()) {
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
    // Match iOS Listen Mode: one evenly distributed action row, with Save
    // anchored at the trailing edge. A nested leading row can consume Save's
    // width on narrower Android screens and shrink its icon almost to nothing.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(
            onClick = { model.toggleLike(post.id) },
            modifier = Modifier.weight(1f).heightIn(min = 36.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = CorusColors.Text),
        ) {
            Icon(
                if (engagement?.isLiked == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "Like",
                modifier = Modifier.size(21.dp),
                tint = if (engagement?.isLiked == true) CorusColors.Like else CorusColors.Text,
            )
            Text(" ${engagement?.likeCount ?: post.likeCount}")
        }
        TextButton(
            onClick = onComments,
            modifier = Modifier.weight(1f).heightIn(min = 36.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = CorusColors.Text),
        ) {
            Icon(Icons.Default.ChatBubbleOutline, "Comments", modifier = Modifier.size(21.dp), tint = CorusColors.Text)
            (engagement?.commentCount ?: post.commentCount).takeIf { it > 0 }?.let { Text(" $it") }
        }
        IconButton(onClick = { onRepost(post) }, modifier = Modifier.weight(1f).height(36.dp)) {
            Icon(Icons.Default.Repeat, "Repost", modifier = Modifier.size(21.dp), tint = CorusColors.Text)
        }
        IconButton(onClick = { model.loadRecentShareContacts(); shareTarget = post }, modifier = Modifier.weight(1f).height(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, "Share", modifier = Modifier.size(21.dp), tint = CorusColors.Text)
        }
        if ((post.trackPostCount ?: 0) > 1) {
            TextButton(
                onClick = onCatalog,
                modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = CorusColors.Text),
            ) {
                VennDiagramIcon(size = 18.dp, color = CorusColors.Text)
                Text(" ${post.trackPostCount}")
            }
        }
        TextButton(
            onClick = { model.toggleSave(post.id) },
            modifier = Modifier.weight(1f).heightIn(min = 36.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = CorusColors.Text),
        ) {
            post.saveCount.takeIf { it > 0 }?.let { Text("$it ") }
            Icon(if (engagement?.isSaved == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Save", modifier = Modifier.size(21.dp), tint = CorusColors.Text)
        }
    }
    shareTarget?.let { tapped -> SharePostSheet(post = tapped, recentContacts = recent, searchResults = results, isSearching = searching, isLoadingContacts = loading, instagramShareEnabled = model.remoteConfig.instagramShareEnabled, sheetState = rememberModalBottomSheetState(), onSearchQueryChange = model::searchShareUsers, onSendToUser = { user, message -> model.sendPostToUser(user, tapped, message); shareTarget = null }, onRepost = { shareTarget = null; onRepost(tapped) }, onDismiss = { shareTarget = null }) }
}
