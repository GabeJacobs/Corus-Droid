package fm.corus.android.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import fm.corus.android.R
import fm.corus.android.service.DeepLinkDestination
import fm.corus.android.ui.navigation.MainTabScreen
import fm.corus.android.ui.screens.auth.AuthScreen
import fm.corus.android.ui.screens.auth.AuthViewModel
import fm.corus.android.ui.screens.auth.OnboardingScreen
import fm.corus.android.ui.screens.auth.SocialSetupFlow
import fm.corus.android.ui.screens.settings.AppearanceSettingsViewModel
import fm.corus.android.ui.theme.AppearanceMode
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CorusApp(
    deepLinkIntent: Intent? = null,
    pendingNotificationDestination: StateFlow<DeepLinkDestination?>? = null,
    onNotificationDestinationConsumed: () -> Unit = {},
) {
    val appearanceViewModel: AppearanceSettingsViewModel = hiltViewModel()
    val mode by appearanceViewModel.appearanceMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (mode) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }

    CorusTheme(darkTheme = darkTheme) {
        val viewModel: AuthViewModel = hiltViewModel()
        val authState by viewModel.authState.collectAsState()
        val isConnected by viewModel.networkConnected.collectAsState()

        LaunchedEffect(Unit) {
            viewModel.observeAuthState()
        }

        val context = LocalContext.current
        val hapticManager = remember(context) { hapticManagerFromContext(context) }

        CompositionLocalProvider(
            LocalHapticManager provides hapticManager,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (authState) {
                    AuthViewModel.AuthState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = CorusColors.Accent)
                        }
                    }
                    AuthViewModel.AuthState.SignedOut -> {
                        AuthScreen()
                    }
                    AuthViewModel.AuthState.NeedsOnboarding -> {
                        OnboardingScreen()
                    }
                    AuthViewModel.AuthState.NeedsSocialSetup -> {
                        SocialSetupFlow(onFinished = { viewModel.finishSocialSetup() })
                    }
                    AuthViewModel.AuthState.SignedIn -> {
                        MainTabScreen(
                            pendingNotificationDestination = pendingNotificationDestination,
                            onNotificationDestinationConsumed = onNotificationDestinationConsumed,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !isConnected,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    OfflineBanner()
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.85f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.WifiOff,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(end = 0.dp),
        )
        Text(
            text = stringResource(R.string.offline_banner_no_connection),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
