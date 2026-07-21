package fm.corus.android.share

import android.content.Intent
import android.net.Uri
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
 * (nothing happened, so don't yank the user away), but a SUCCESSFUL post opens
 * Corus on the post the user just made (unlike iOS, where the sandboxed
 * extension can't open its host app, Android can — the user asked to land in
 * Corus rather than bounce back to Spotify).
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
                        onPosted = ::openPostInCorus,
                    )
                }
            }
        }
    }

    /**
     * Leaves the share overlay and opens the post the user just made in the
     * Corus app. Routes through the existing `corus://post/{id}` deep link, so
     * MainActivity (singleTask) either comes forward and handles it via
     * onNewIntent or cold-starts and handles it in onCreate — the same path a
     * push-notification tap uses. FLAG_ACTIVITY_NEW_TASK because we're starting
     * from an activity hosted in the sharing app's task.
     */
    private fun openPostInCorus(postId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("corus://post/$postId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
    }
}
