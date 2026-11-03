package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import fm.corus.android.localization.CorusStrings as S
import fm.corus.android.service.ReferralService
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.launch

@Composable
fun ReferralInviteBanner(surface: String) {
    val enabled by ReferralService.enabled.collectAsState()
    val status by ReferralService.status.collectAsState()
    val store by ReferralService.storeSubscribed.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val prefs = remember(context) { context.getSharedPreferences("corus_referrals_v1", 0) }
    var dismissed by
        remember(uid, surface) {
            mutableStateOf(prefs.getBoolean("dismissed_${surface}_$uid", false))
        }
    LaunchedEffect(enabled, uid) { if (enabled) ReferralService.reload() }
    if (
        !enabled ||
            !status.enabled ||
            !status.eligible ||
            status.earned ||
            status.rewardStatus in listOf("claiming", "waiting_for_subscription", "active", "completed") ||
            store == true ||
            dismissed
    )
        return
    LaunchedEffect(uid, surface) {
        ReferralService.trackInBackground("${surface}_banner_shown", source = surface)
    }
    Row(
        Modifier.fillMaxWidth().background(CorusColors.Accent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f)
                .clickable {
                    ReferralService.openInvite(surface)
                    scope.launch {
                        ReferralService.trackInBackground("${surface}_banner_tapped", source = surface)
                    }
                }
                .padding(start = 18.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(S.settings_row_invite_friends),
                    color = Color.White,
                    style = CorusFont.body.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    null,
                    Modifier.size(14.dp),
                    tint = Color.White,
                )
            }
            Text(
                stringResource(S.referral_feed_banner_subtitle),
                color = Color.White,
                style = CorusFont.caption,
            )
        }
        IconButton({
            dismissed = true
            prefs.edit().putBoolean("dismissed_${surface}_$uid", true).apply()
            scope.launch { ReferralService.trackInBackground("${surface}_banner_dismissed", source = surface) }
        }) {
            Icon(
                Icons.Default.Close,
                stringResource(S.referral_feed_banner_dismiss),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
