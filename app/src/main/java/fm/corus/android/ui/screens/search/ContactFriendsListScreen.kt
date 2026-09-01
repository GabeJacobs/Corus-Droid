package fm.corus.android.ui.screens.search

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactFriendsListScreen(
    users: List<CymbalUser>,
    isLoading: Boolean = false,
    isFollowed: (String) -> Boolean = { false },
    onFollow: (CymbalUser) -> Unit = {},
    /** Fired before [onNavigateToUser] so analytics can attribute taps from
     *  the FriendsOnCorus see-all destination. Default no-op keeps this a
     *  pure UI screen. */
    onUserTapped: (String) -> Unit = {},
    onNavigateToUser: (CymbalUser) -> Unit = {},
    onBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(fm.corus.android.R.string.contacts_list_title), style = CorusFont.screenTitle, color = CorusColors.Text) },
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
        if (isLoading && users.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = CorusColors.Accent)
            }
        } else if (users.isEmpty()) {
            val context = LocalContext.current
            val shareText = stringResource(fm.corus.android.R.string.settings_share_app_text)
            val shareChooser = stringResource(fm.corus.android.R.string.settings_share_app_chooser)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = CorusSpacing.xxl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(fm.corus.android.R.string.search_no_contact_matches_title),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Text,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(CorusSpacing.xs))
                Text(
                    stringResource(fm.corus.android.R.string.search_no_contact_matches_subtitle),
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(CorusSpacing.lg))
                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, shareChooser))
                    },
                    shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CorusColors.Accent,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(
                        horizontal = CorusSpacing.xl,
                        vertical = CorusSpacing.sm,
                    ),
                ) {
                    Text(stringResource(fm.corus.android.R.string.settings_row_invite_friends), style = CorusFont.buttonSmall)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(top = CorusSpacing.md),
            ) {
                items(users, key = { it.id }) { user ->
                    SuggestedUserRow(
                        user = user,
                        subtitle = stringResource(fm.corus.android.R.string.search_subtitle_from_contacts),
                        isFollowed = isFollowed(user.id),
                        onTap = {
                            onUserTapped(user.id)
                            onNavigateToUser(user)
                        },
                        onFollow = { onFollow(user) },
                    )
                }
            }
        }
    }
}
