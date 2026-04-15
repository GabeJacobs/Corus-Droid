package fm.corus.android.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.service.DeepLinkDestination
import fm.corus.android.ui.navigation.MainTabScreen
import fm.corus.android.ui.screens.auth.AuthScreen
import fm.corus.android.ui.screens.auth.AuthViewModel
import fm.corus.android.ui.screens.auth.OnboardingScreen
import fm.corus.android.ui.screens.auth.SocialSetupFlow
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CorusApp(
    deepLinkIntent: Intent? = null,
    pendingNotificationDestination: StateFlow<DeepLinkDestination?>? = null,
    onNotificationDestinationConsumed: () -> Unit = {},
) {
    CorusTheme {
        val viewModel: AuthViewModel = hiltViewModel()
        val authState by viewModel.authState.collectAsState()

        LaunchedEffect(Unit) {
            viewModel.observeAuthState()
        }

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
                // Notification permission is now requested during onboarding (music service screen)
                MainTabScreen(
                    pendingNotificationDestination = pendingNotificationDestination,
                    onNotificationDestinationConsumed = onNotificationDestinationConsumed,
                )
            }
        }
    }
}
