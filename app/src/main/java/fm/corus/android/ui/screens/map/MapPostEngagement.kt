package fm.corus.android.ui.screens.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import fm.corus.android.R
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.ui.components.SharePostSheet
import fm.corus.android.ui.components.VennDiagramIcon
import fm.corus.android.ui.screens.feed.PostDetailViewModel
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont

internal const val MAP_ENGAGEMENT_SCROLL_THRESHOLD_WITH_CATALOG_DP = 360
internal const val MAP_ENGAGEMENT_SCROLL_THRESHOLD_DP = 310
internal const val MAP_ENGAGEMENT_ITEM_GAP_DP = 12
internal const val MAP_ENGAGEMENT_ICON_COUNT_GAP_DP = 5
internal enum class MapEngagementWidthMode { ANCHORED, HORIZONTAL_SCROLL }
internal fun mapEngagementWidthMode(widthDp: Int, hasCatalog: Boolean, visibleCountCharacters: Int = 0) =
    if (widthDp < (if (hasCatalog) MAP_ENGAGEMENT_SCROLL_THRESHOLD_WITH_CATALOG_DP else MAP_ENGAGEMENT_SCROLL_THRESHOLD_DP) + visibleCountCharacters * 8)
        MapEngagementWidthMode.HORIZONTAL_SCROLL else MapEngagementWidthMode.ANCHORED
internal fun visibleMapEngagementCount(value: Int): String? = value.takeIf { it > 0 }?.toString()
internal fun mapEngagementKeepsTrailingSaveFixed(mode: MapEngagementWidthMode): Boolean = true
internal fun mapEngagementVisibleItemGapDp(count: Int): Int = MAP_ENGAGEMENT_ITEM_GAP_DP

/** Uses the app's shared optimistic store, save limits, recipient search and repost flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPostEngagement(post: CymbalPost, onComments: () -> Unit, onRepost: (CymbalPost) -> Unit, onPaywall: () -> Unit, onCatalog: () -> Unit = {}, onAnalytics: (String) -> Unit = {}, model: PostDetailViewModel = hiltViewModel()) {
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

    val hasCatalog = (post.trackPostCount ?: 0) > 1
    val likeCount = engagement?.likeCount ?: post.likeCount
    val commentCount = engagement?.commentCount ?: post.commentCount
    val repostCount = engagement?.repostCount ?: post.repostCount
    val saveCount = engagement?.saveCount ?: post.saveCount

    @Composable fun leadingActions() {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MAP_ENGAGEMENT_ITEM_GAP_DP.dp)) {
            MapEngagementChip(if (engagement?.isLiked == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.map_cd_like), likeCount, if (engagement?.isLiked == true) CorusColors.Like else CorusColors.Text) { onAnalytics("like"); model.toggleLike(post.id) }
            MapEngagementChip(Icons.Default.ChatBubbleOutline, stringResource(R.string.map_cd_comments), commentCount) { onAnalytics("comments"); onComments() }
            MapEngagementChip(Icons.Default.Repeat, stringResource(R.string.map_cd_repost), repostCount) { onAnalytics("repost"); onRepost(post) }
            MapEngagementChip(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.map_cd_share), post.sendCount) { onAnalytics("share"); model.loadRecentShareContacts(); shareTarget = post }
            if (hasCatalog) MapCatalogEngagementChip(post.trackPostCount ?: 0) { onAnalytics("catalog"); onCatalog() }
        }
    }

    @Composable fun saveAction() {
        MapEngagementChip(if (engagement?.isSaved == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, stringResource(if (engagement?.isSaved == true) R.string.map_cd_remove_saved else R.string.map_cd_save), saveCount, countBeforeIcon = true) { onAnalytics("save"); model.toggleSave(post.id) }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Treat larger accessibility text as less available horizontal space,
        // mirroring iOS ViewThatFits before falling back to scrolling.
        val effectiveWidth = maxWidth.value / androidx.compose.ui.platform.LocalDensity.current.fontScale
        val visibleCountCharacters = listOf(likeCount, commentCount, repostCount, post.sendCount, saveCount)
            .sumOf { visibleMapEngagementCount(it)?.length ?: 0 } + if (hasCatalog) (post.trackPostCount ?: 0).toString().length else 0
        val widthMode = mapEngagementWidthMode(effectiveWidth.toInt(), hasCatalog, visibleCountCharacters)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                when (widthMode) {
                    MapEngagementWidthMode.ANCHORED -> leadingActions()
                    MapEngagementWidthMode.HORIZONTAL_SCROLL -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { leadingActions() }
                }
            }
            Spacer(Modifier.width(MAP_ENGAGEMENT_ITEM_GAP_DP.dp))
            saveAction()
        }
    }
    shareTarget?.let { tapped -> SharePostSheet(post = tapped, recentContacts = recent, searchResults = results, isSearching = searching, isLoadingContacts = loading, instagramShareEnabled = model.remoteConfig.instagramShareEnabled, sheetState = rememberModalBottomSheetState(), onSearchQueryChange = model::searchShareUsers, onSendToUser = { user, message -> model.sendPostToUser(user, tapped, message); shareTarget = null }, onRepost = { shareTarget = null; onRepost(tapped) }, onDismiss = { shareTarget = null }) }
}

@Composable
private fun MapEngagementChip(icon: ImageVector, label: String, count: Int, tint: androidx.compose.ui.graphics.Color = CorusColors.Text, countBeforeIcon: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.height(48.dp).clickable(onClickLabel = label, role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MAP_ENGAGEMENT_ICON_COUNT_GAP_DP.dp)) {
        val value = visibleMapEngagementCount(count)
        if (countBeforeIcon && value != null) Text(value, style = CorusFont.bodyMedium, color = CorusColors.Text)
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        if (!countBeforeIcon && value != null) Text(value, style = CorusFont.bodyMedium, color = CorusColors.Text)
    }
}

@Composable
private fun MapCatalogEngagementChip(count: Int, onClick: () -> Unit) {
    Row(Modifier.height(48.dp).clickable(onClickLabel = stringResource(R.string.map_cd_catalog), role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MAP_ENGAGEMENT_ICON_COUNT_GAP_DP.dp)) {
        VennDiagramIcon(size = 18.dp, color = CorusColors.Text)
        Text(count.toString(), style = CorusFont.bodyMedium, color = CorusColors.Text)
    }
}
