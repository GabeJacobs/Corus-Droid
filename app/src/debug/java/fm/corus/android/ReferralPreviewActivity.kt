package fm.corus.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import fm.corus.android.service.*
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.hapticManagerFromContext
import fm.corus.android.ui.screens.settings.*
import fm.corus.android.ui.theme.CorusTheme

/** Debug-only visual harness. The claim replay is isolated from account and grant state. */
class ReferralPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario")
            ?: if (intent.component?.className?.endsWith("ClaimRewardPreview") == true) "ready" else "progress"
        check(scenario == "ready" || FirebaseAuth.getInstance().currentUser == null) {
            "Use a signed-out emulator for referral previews"
        }
        if (scenario != "ready") ReferralService.reset()
        val count = intent.getIntExtra("count", 3).coerceIn(0, 5)
        val state =
            when (scenario) {
                "ready" -> "available"
                "active" -> "active"
                "saved" -> "waiting_for_subscription"
                "claiming" -> "claiming"
                else -> "locked"
            }
        val status =
            ReferralStatus(
                enabled = true,
                code = "1234567890abcdef12345678",
                url = "https://invite.corus.fm/EjRWeJCrze8SNFZ4",
                count = if (scenario == "ready") 5 else count,
                eligible = scenario != "paid",
                earned = scenario in listOf("active", "saved", "claiming"),
                rewardStatus = state,
                shortCode = "7K4N8X2P",
                rewardEndAtMs = System.currentTimeMillis() + 90 * 86400000L,
            )
        if (scenario.startsWith("gift"))
            ReferralService.welcome.value =
                ReferralWelcome(
                    "preview-inviter",
                    "Test Friend",
                    when (scenario) {
                        "giftPending" -> "pending"
                        "giftWaiting" -> "waiting_for_subscription"
                        else -> "applied"
                    },
                    System.currentTimeMillis() + 14 * 86400000L,
                )
        if (scenario.startsWith("issue"))
            ReferralService.completionIssue.value =
                when (scenario) {
                    "issueUsed" -> ReferralCompletionIssue.AlreadyRedeemed
                    "issueNetwork" -> ReferralCompletionIssue.TemporaryFailure
                    else -> ReferralCompletionIssue.InvalidInvitation
                }
        setContent {
            CorusTheme(darkTheme = intent.getBooleanExtra("dark", false)) {
                CompositionLocalProvider(
                    LocalHapticManager provides hapticManagerFromContext(this)
                ) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        if (scenario.startsWith("gift") || scenario.startsWith("issue"))
                            ReferralOutcomeContent { finish() }
                        else ReferralScreen(onClose = { finish() }, previewStatus = status)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        val claimReplay = intent.getStringExtra("scenario") == "ready" ||
            intent.component?.className?.endsWith("ClaimRewardPreview") == true
        if (!claimReplay) ReferralService.dismissWelcome()
        super.onDestroy()
    }
}
