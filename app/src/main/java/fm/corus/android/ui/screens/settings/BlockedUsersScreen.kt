package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.launch

@Composable
fun BlockedUsersScreen(
    viewModel: BlockedUsersViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val blockedUsers by viewModel.blockedUsers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val unblockingIds by viewModel.unblockingIds.collectAsState()
    val unblockError by viewModel.unblockError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(unblockError) {
        unblockError?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearUnblockError()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Blocked Users", style = CorusFont.screenTitle, color = CorusColors.Text)
            }

            HorizontalDivider(color = CorusColors.Divider)

            when {
                isLoading -> {
                    Column(modifier = Modifier.padding(CorusSpacing.lg)) {
                        repeat(3) {
                            SkeletonUserRow()
                            Spacer(modifier = Modifier.height(CorusSpacing.md))
                        }
                    }
                }

                blockedUsers.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = CorusColors.Tertiary,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(modifier = Modifier.height(CorusSpacing.md))
                            Text(
                                text = "No blocked users",
                                style = CorusFont.bodyMedium,
                                color = CorusColors.Secondary,
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(blockedUsers, key = { it.id }) { user ->
                            BlockedUserRow(
                                modifier = Modifier.animateItem(),
                                user = user,
                                isUnblocking = unblockingIds.contains(user.id),
                                onUnblock = { viewModel.unblock(user.id) },
                            )
                            HorizontalDivider(
                                color = CorusColors.Divider,
                                modifier = Modifier.padding(horizontal = CorusSpacing.lg),
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BlockedUserRow(
    modifier: Modifier = Modifier,
    user: CymbalUser,
    isUnblocking: Boolean,
    onUnblock: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(avatarURL = user.avatarURL, size = CorusSpacing.avatarMedium)

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username,
                style = CorusFont.username,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.displayName.isNotEmpty()
                && user.displayName.lowercase() != user.username.lowercase()
            ) {
                Text(
                    text = user.displayName,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        // Matches iOS: Capsule with Divider fill, Secondary text
        Button(
            onClick = onUnblock,
            enabled = !isUnblocking,
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = CorusColors.Divider,
                contentColor = CorusColors.Secondary,
                disabledContainerColor = CorusColors.Divider,
                disabledContentColor = CorusColors.Tertiary,
            ),
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        ) {
            if (isUnblocking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = CorusColors.Secondary,
                )
            } else {
                Text("Unblock", style = CorusFont.buttonSmall)
            }
        }
    }
}
