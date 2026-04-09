package fm.corus.android.ui.screens.findpeople

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactFriendsListScreen(
    users: List<CymbalUser>,
    isFollowed: (String) -> Boolean = { false },
    onFollow: (CymbalUser) -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends on Corus", style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CorusColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        if (users.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No contacts found on Corus", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(top = CorusSpacing.md),
            ) {
                items(users, key = { it.id }) { user ->
                    SuggestedUserRow(
                        user = user,
                        subtitle = "From your contacts",
                        isFollowed = isFollowed(user.id),
                        onTap = { onNavigateToUser(user.id) },
                        onFollow = { onFollow(user) },
                    )
                }
            }
        }
    }
}
