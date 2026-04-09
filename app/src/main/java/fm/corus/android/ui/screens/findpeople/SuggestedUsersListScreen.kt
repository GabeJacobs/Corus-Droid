package fm.corus.android.ui.screens.findpeople

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestedUsersListScreen(
    matches: List<SuggestedUserMatch>,
    title: String = "Suggested Users",
    useRowLayout: Boolean = false,
    isFollowed: (String) -> Boolean = { false },
    onFollow: (CymbalUser) -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CorusColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        if (matches.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No suggestions available", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
            }
        } else if (useRowLayout) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(top = CorusSpacing.md),
            ) {
                items(matches, key = { it.id }) { match ->
                    SuggestedUserRow(
                        user = match.user,
                        isFollowed = isFollowed(match.user.id),
                        onTap = { onNavigateToUser(match.user.id) },
                        onFollow = { onFollow(match.user) },
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(CorusSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                itemsIndexed(matches, key = { _, m -> m.id }) { _, match ->
                    TasteMatchCard(
                        match = match,
                        isFollowing = isFollowed(match.user.id),
                        onUserTap = { onNavigateToUser(match.user.id) },
                        onFollowTap = { onFollow(match.user) },
                    )
                }
            }
        }
    }
}
