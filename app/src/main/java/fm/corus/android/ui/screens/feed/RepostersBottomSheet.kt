package fm.corus.android.ui.screens.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars

/**
 * "Reposts" — the people who reposted a post, each row their own repost of it.
 * A repost is a real post, so rows reuse the song page's [PostedByRow]: the row
 * taps through to that person's repost, the avatar/name to their profile.
 * Presented as a ModalBottomSheet mirroring [LikesBottomSheet] (drag handle,
 * opens partially expanded, drags up; row-tap dismisses + navigates). Opened by
 * long-pressing a post's repost count (gated behind `reposters_list_enabled`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepostersBottomSheet(
    postId: String,
    onDismiss: () -> Unit,
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    // Animate the sheet closed, THEN navigate — so the sheet visibly dismisses
    // before the destination transitions in, matching iOS. Navigating first (or
    // concurrently) runs the NavHost's slide behind the still-visible sheet, so
    // by the time the sheet is gone the push already looks finished.
    val closeThen: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
                action()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CorusColors.Background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        CorusSystemBars()
        BackHandler { onDismiss() }
        RepostersSheetContent(
            postId = postId,
            onNavigateToUser = { userId -> closeThen { onNavigateToUser(userId) } },
            onNavigateToPost = { pid -> closeThen { onNavigateToPost(pid) } },
        )
    }
}

@Composable
private fun RepostersSheetContent(
    postId: String,
    viewModel: RepostersViewModel = hiltViewModel(),
    /** Already wrapped by the caller to close the sheet first, then navigate. */
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
) {
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadError by viewModel.loadError.collectAsState()

    LaunchedEffect(postId) {
        viewModel.load(postId)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 400.dp),
    ) {
        Text(
            text = stringResource(R.string.reposters_title),
            style = CorusFont.screenTitle,
            color = CorusColors.Text,
            modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        )

        HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)

        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading && posts.isEmpty()) {
                items(8) { SkeletonUserRow() }
            } else if (loadError && posts.isEmpty()) {
                item(key = "error") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.reposters_load_error),
                            style = CorusFont.bodyMedium,
                            color = CorusColors.Secondary,
                        )
                    }
                }
            } else if (posts.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.reposters_empty),
                            style = CorusFont.bodyMedium,
                            color = CorusColors.Secondary,
                        )
                    }
                }
            } else {
                items(posts, key = { it.id }) { post ->
                    // Same package's song-page row: avatar + @username + display
                    // name + caption + timestamp/chevron, vertically centred.
                    // Deliberately NOT DestinationPostRow — its media chip is
                    // redundant here (every repost is of the same song) and it
                    // threw the row's vertical alignment off.
                    PostedByRow(
                        post = post,
                        onUserTap = { onNavigateToUser(post.user.id) },
                        onPostTap = { onNavigateToPost(post.id) },
                    )

                    if (post.id == posts.lastOrNull()?.id && hasMore && !isLoadingMore) {
                        LaunchedEffect(post.id) { viewModel.loadMore() }
                    }
                }

                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = CorusSpacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = CorusColors.Accent,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }

            // Invisible spacer so short content still overflows the visible area —
            // required for ModalBottomSheet drag-to-expand to work (mirrors LikesSheet).
            item(key = "expand_spacer") {
                Spacer(modifier = Modifier.fillParentMaxHeight())
            }
        }
    }
}
