package fm.corus.android.share

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import fm.corus.android.MainActivity
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.hapticManagerFromContext
import fm.corus.android.ui.screens.settings.AppearanceSettingsViewModel
import fm.corus.android.ui.theme.AppearanceMode
import fm.corus.android.ui.theme.CorusTheme

/**
 * The share target: receiving end of ACTION_SEND text/plain (song links from
 * Spotify, Apple Music, SoundCloud, Deezer). Launches into the SHARING app's
 * task (manifest: taskAffinity="" + excludeFromRecents) so it overlays
 * Spotify like a sheet — the Android twin of the iOS CorusShare extension.
 * Runs in the app process with the full Hilt graph, so auth, App Check (Play
 * Integrity), and Remote Config all just work.
 *
 * Finish behavior differs by outcome: cancel/close returns to the sharing app
 * (nothing happened, so don't yank the user away), but a SUCCESSFUL post brings
 * Corus forward on whatever screen it was already showing. (iOS can't do this
 * at all — a sandboxed extension may not open its host app — so there the sheet
 * always dismisses back to the sharing app.)
 */
@AndroidEntryPoint
class ShareToCorusActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            null
        }

        setContent {
            // Same theme resolution as CorusApp (System / Light / Dark).
            val appearanceViewModel: AppearanceSettingsViewModel = hiltViewModel()
            val mode by appearanceViewModel.appearanceMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (mode) {
                AppearanceMode.SYSTEM -> systemDark
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }

            CorusTheme(darkTheme = darkTheme) {
                val context = LocalContext.current
                val hapticManager = remember(context) { hapticManagerFromContext(context) }
                CompositionLocalProvider(LocalHapticManager provides hapticManager) {
                    ShareComposerScreen(
                        sharedText = sharedText,
                        onFinish = { finish() },
                        onPosted = ::openCorus,
                    )
                }
            }
        }
    }

    /**
     * Leaves the share overlay and brings Corus forward, deliberately WITHOUT a
     * deep link: MainActivity is singleTask, so a plain launch resumes its task
     * with the in-app navigation exactly where the user left it (its deep-link
     * handler ignores non-ACTION_VIEW intents). The freshly created post is
     * already accounted for — ShareComposerViewModel fires PostCreationEvent,
     * which the Feed and Profile screens listen to. FLAG_ACTIVITY_NEW_TASK
     * because we're starting from an activity hosted in the sharing app's task.
     */
    private fun openCorus() {
        // Cut straight over with no transition on either side. This activity
        // lives in the SHARING app's task, so reaching MainActivity crosses a
        // task boundary — and the sheet's close animation stacked on top of the
        // system's cross-task switch reads as a heavy "switching apps"
        // ceremony. Suppressing both makes it a single instant beat.
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(intent)
        // The two APIs want opposite ordering around finish(): the modern one
        // must be armed before, the legacy one called after.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
            finish()
        } else {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
