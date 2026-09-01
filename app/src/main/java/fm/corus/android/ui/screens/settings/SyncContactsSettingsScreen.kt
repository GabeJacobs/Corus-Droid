package fm.corus.android.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.screens.search.ContactFriendsListScreen
import fm.corus.android.ui.screens.search.ContactFriendsListViewModel
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Settings entry point for contact sync. Lets users who dismissed the
 * onboarding/search prompts sync later, and lets already-synced users
 * re-sync to pick up new contacts. After syncing it shows the standard
 * contact-friends list (or its empty state).
 */
@Composable
fun SyncContactsSettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToUser: (CymbalUser) -> Unit = {},
    viewModel: ContactFriendsListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    var showResults by rememberSaveable { mutableStateOf(false) }

    val permissionDeniedMessage = stringResource(R.string.sync_contacts_permission_denied)
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.syncContacts(context.contentResolver)
            showResults = true
        } else {
            ToastManager.show(permissionDeniedMessage)
        }
    }

    // Already-synced users skip the intro: refresh from the device when
    // permission is still granted, otherwise show the matches the ViewModel
    // loads from the stored contacts.
    LaunchedEffect(Unit) {
        if (viewModel.currentSyncStatus() == "synced") {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.syncContacts(context.contentResolver)
            }
            showResults = true
        }
    }

    if (showResults) {
        ContactFriendsListScreen(
            users = contacts,
            isLoading = isLoading || isSyncing,
            isFollowed = { viewModel.isFollowed(it) },
            onFollow = { viewModel.toggleFollow(it) },
            onUserTapped = { userId -> viewModel.logUserTapped(userId) },
            onNavigateToUser = onNavigateToUser,
            onBack = onBack,
        )
    } else {
        SyncContactsIntro(
            onBack = onBack,
            onSync = {
                viewModel.logSyncContactsTapped()
                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncContactsIntro(
    onBack: () -> Unit,
    onSync: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_row_sync_contacts),
                        style = CorusFont.screenTitle,
                        color = CorusColors.Text,
                    )
                },
                navigationIcon = {
                    CorusHeaderIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(CorusColors.Accent.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Contacts,
                        contentDescription = null,
                        tint = CorusColors.Accent,
                        modifier = Modifier.size(40.dp),
                    )
                }

                Spacer(modifier = Modifier.height(CorusSpacing.xl))

                Text(
                    stringResource(R.string.search_contacts_card_title),
                    style = CorusFont.songTitleLarge,
                    color = CorusColors.Text,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(CorusSpacing.xs))

                Text(
                    stringResource(R.string.search_contacts_card_subtitle),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(CorusSpacing.xl))

                Button(
                    onClick = onSync,
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
                    Text(stringResource(R.string.search_contacts_card_sync), style = CorusFont.buttonSmall)
                }
            }
        }
    }
}
