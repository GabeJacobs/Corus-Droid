package fm.corus.android.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.SkeletonTasteMatchCard
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotListScreen(
    botType: String? = null,
    viewModel: BotListViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (CymbalUser) -> Unit = {},
) {
    val bots by viewModel.bots.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val followedIds by viewModel.followedIds.collectAsState()

    LaunchedEffect(botType) {
        viewModel.loadBots(botType)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (botType == "film") stringResource(fm.corus.android.R.string.bot_list_title_film) else stringResource(fm.corus.android.R.string.bot_list_title_music),
                        style = CorusFont.screenTitle,
                        color = CorusColors.Text,
                    )
                },
                navigationIcon = {
                    CorusHeaderIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(fm.corus.android.R.string.common_back),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        if (isLoading) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(CorusSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(6) {
                    SkeletonTasteMatchCard()
                }
            }
        } else if (bots.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(fm.corus.android.R.string.bot_list_empty), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(CorusSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                itemsIndexed(bots, key = { _, match -> match.id }) { _, match ->
                    TasteMatchCard(
                        match = match,
                        isFollowing = followedIds.contains(match.user.id),
                        onUserTap = { onNavigateToUser(match.user) },
                        onFollowTap = { viewModel.toggleFollow(match.user) },
                    )
                }
            }
        }
    }
}
