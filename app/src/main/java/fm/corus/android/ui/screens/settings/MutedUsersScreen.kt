package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.launch

@Composable
fun MutedUsersScreen(
    viewModel: MutedUsersViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
) {
    val mutedUsers by viewModel.mutedUsers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val unmutingIds by viewModel.unmutingIds.collectAsState()
    val unmuteError by viewModel.unmuteError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(unmuteError) {
        unmuteError?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearUnmuteError()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CorusColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CorusHeaderIconButton(
                    onClick = onNavigateBack,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
                Text(stringResource(R.string.muted_users_screen_title), style = CorusFont.screenTitle, color = CorusColors.Text)
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

                mutedUsers.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.VolumeOff,
                                contentDescription = null,
                                tint = CorusColors.Tertiary,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(CorusSpacing.md))
                            Text(
                                text = stringResource(R.string.muted_users_empty),
                                style = CorusFont.bodyMedium,
                                color = CorusColors.Secondary,
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(mutedUsers, key = { it.id }) { user ->
                            MutedUserRow(
                                modifier = Modifier.animateItem(),
                                user = user,
                                isUnmuting = unmutingIds.contains(user.id),
                                onUnmute = { viewModel.unmute(user.id) },
                                onUserTap = { onNavigateToUser(user.id) },
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
private fun MutedUserRow(
    modifier: Modifier = Modifier,
    user: CymbalUser,
    isUnmuting: Boolean,
    onUnmute: () -> Unit,
    onUserTap: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onUserTap)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(avatarURL = user.avatarURL, displayName = user.displayName, size = CorusSpacing.avatarMedium)

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
            onClick = onUnmute,
            enabled = !isUnmuting,
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = CorusColors.Divider,
                contentColor = CorusColors.Secondary,
                disabledContainerColor = CorusColors.Divider,
                disabledContentColor = CorusColors.Tertiary,
            ),
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        ) {
            if (isUnmuting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = CorusColors.Secondary,
                )
            } else {
                Text(stringResource(R.string.common_unmute), style = CorusFont.buttonSmall)
            }
        }
    }
}
