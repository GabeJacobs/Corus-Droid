@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package fm.corus.android.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fm.corus.android.localization.CorusStrings as S
import fm.corus.android.service.*
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.CymbalClubVinyl
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

private fun headline(s: ReferralStatus): Int =
    when (s.rewardStatus) {
        "active" -> S.referral_claim_applied_title
        "waiting_for_subscription" -> S.referral_waiting
        "claiming" -> S.referral_claim_pending_title
        "completed" -> S.referral_paid_title
        "available" -> S.referral_ready
        else -> if (s.eligible || s.earned) S.referral_reward else S.referral_paid_title
    }

private fun Context.copyCode(value: String) =
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
        ClipData.newPlainText("Corus", value)
    )

private fun date(ms: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

@Composable
internal fun ReferralActionButton(
    text: String,
    busy: Boolean = false,
    enabled: Boolean = true,
    textStyle: TextStyle = CorusFont.button.copy(fontSize = 18.sp),
    onClick: () -> Unit,
) {
    Button(
        onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = CorusColors.Accent,
            contentColor = Color.White,
            disabledContainerColor = if (busy) CorusColors.Accent else CorusColors.Accent.copy(alpha = .35f),
            disabledContentColor = Color.White,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Keep the label's measured size while the centered spinner replaces it.
            Text(
                text,
                style = textStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp).alpha(if (busy) 0f else 1f),
            )
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun ReferralSuccessBadge() {
    Box(
        Modifier.size(40.dp)
            .background(CorusColors.Background, CircleShape)
            .padding(3.dp)
            .background(CorusColors.Accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Check, null, Modifier.size(26.dp), tint = Color.White)
    }
}

@Composable
fun ReferralCodePill(code: String, source: String = "settings_header") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember(code) { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(S.referral_your_code),
            style = CorusFont.captionMedium,
            color = CorusColors.Secondary,
        )
        OutlinedButton(
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = CorusColors.Accent.copy(alpha = .05f)
                ),
            border =
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    CorusColors.Accent.copy(alpha = .25f),
                ),
            onClick = {
                context.copyCode(code)
                copied = true
                scope.launch { ReferralService.trackInBackground("link_copied", source = source) }
            },
            shape = CircleShape,
            modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp),
        ) {
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                stringResource(if (copied) S.referral_code_copied else S.referral_copy_code),
                Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun ReferralSettingsHeader(code: String, onExplain: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ReferralCodePill(code)
        TextButton(onExplain) {
            Icon(Icons.Default.HelpOutline, null, Modifier.size(14.dp), tint = CorusColors.Secondary)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(S.referral_how_it_works),
                style = CorusFont.captionMedium,
                color = CorusColors.Secondary,
            )
        }
    }
}

@Composable
fun ReferralSettingsCard(status: ReferralStatus, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.PersonAdd,
            null,
            tint = CorusColors.Accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(S.referral_title), style = CorusFont.body, color = CorusColors.Text)
            Text(
                stringResource(
                    if (status.rewardStatus in listOf("active", "completed") || !status.eligible)
                        S.referral_gift_friends
                    else headline(status)
                ),
                style = CorusFont.caption,
                color =
                    if (status.eligible && status.rewardStatus !in listOf("active", "completed"))
                        CorusColors.Accent
                    else CorusColors.Secondary,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = CorusColors.Tertiary,
            modifier = Modifier.size(20.dp),
        )
    }
    HorizontalDivider(
        color = CorusColors.Divider,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
internal fun ReferralHeadline(title: String) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val text = title.replace("Corus Club", "Corus\u00a0Club")
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val availableWidth = with(density) { maxWidth.toPx() }
        val allowWrap = density.fontScale > 1.3f
        val fontSize = if (allowWrap) 26 else (26 downTo 18).firstOrNull { size ->
            measurer.measure(
                text,
                style = CorusFont.body.copy(fontSize = size.sp, lineHeight = (size * 1.2f).sp, fontWeight = FontWeight.ExtraBold),
                softWrap = false,
            ).size.width <= availableWidth
        } ?: 18
        Text(
            text,
            modifier = Modifier.fillMaxWidth(),
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.2f).sp,
            fontWeight = FontWeight.ExtraBold,
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
            style = CorusFont.body.copy(lineBreak = LineBreak.Heading),
        )
    }
}

@Composable
internal fun ReferralClaimContent(
    outcome: ReferralClaimOutcome?,
    rewardEndAtMs: Long?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val confirmation = outcome.takeIf { it == ReferralClaimOutcome.Applied || it == ReferralClaimOutcome.Saved }
    Crossfade(
        targetState = confirmation,
        modifier = modifier.fillMaxWidth(),
        animationSpec = tween(250),
        label = "referral_claim_confirmation",
    ) { result ->
        if (result == null) content()
        else Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    CymbalClubVinyl(180.dp)
                    if (result == ReferralClaimOutcome.Applied) ReferralSuccessBadge()
                }
                ReferralHeadline(stringResource(
                    if (result == ReferralClaimOutcome.Applied) fm.corus.android.R.string.referral_reward_active_title
                    else S.referral_waiting
                ))
                Text(
                    if (result == ReferralClaimOutcome.Applied && rewardEndAtMs != null)
                        stringResource(S.referral_gift_until, date(rewardEndAtMs))
                    else stringResource(if (result == ReferralClaimOutcome.Applied) S.referral_claim_applied_body else S.referral_claim_saved_body),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                )
            }
            ReferralActionButton(stringResource(S.common_done), onClick = onDone)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
fun ReferralScreen(
    onClose: () -> Unit,
    onRewardApplied: () -> Unit = {},
    previewStatus: ReferralStatus? = null,
) {
    val liveStatus by ReferralService.status.collectAsState()
    val loaded by ReferralService.loaded.collectAsState()
    val failed by ReferralService.failed.collectAsState()
    val latestStatus = previewStatus ?: liveStatus
    // The service publishes its response before claim() returns. Hold the pre-claim
    // content until the result is ready so that intermediate states cannot flash.
    var claimingStatus by remember { mutableStateOf<ReferralStatus?>(null) }
    val status = claimingStatus ?: latestStatus
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticManager.current
    var history by remember { mutableStateOf(false) }
    var friends by remember { mutableStateOf(emptyList<ReferralFriend>()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loadingHistory by remember { mutableStateOf(false) }
    var historyLoaded by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<ReferralClaimOutcome?>(null) }
    var displayed by remember { mutableIntStateOf(previewStatus?.count?.coerceIn(0, 5) ?: 0) }
    var username by remember { mutableStateOf("") }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val reward = status.eligible || status.earned
    val confirmed = outcome == ReferralClaimOutcome.Applied || outcome == ReferralClaimOutcome.Saved
    val source = ReferralService.inviteSource
    fun load(next: Boolean = false) {
        scope.launch {
            if (previewStatus != null) {
                friends = listOf(ReferralFriend("preview-friend", "Test Friend"))
                historyLoaded = true
                return@launch
            }
            if (loadingHistory) return@launch
            loadingHistory = true
            try {
                val owner = FirebaseAuth.getInstance().currentUser?.uid
                val result =
                    ReferralService.call(
                        "getReferralHistory",
                        if (next && cursor != null) mapOf("cursor" to cursor!!) else emptyMap(),
                    )
                if (owner != FirebaseAuth.getInstance().currentUser?.uid) return@launch
                val items =
                    (result["items"] as? List<*>)?.mapNotNull {
                        (it as? Map<*, *>)?.let { m ->
                            ReferralFriend(
                                m["uid"] as? String ?: "",
                                m["displayName"] as? String ?: "",
                            )
                        }
                    } ?: emptyList()
                friends = if (next) (friends + items).distinctBy { it.uid } else items
                cursor = result["nextCursor"] as? String
                historyLoaded = true
                historyError = false
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                historyError = true
            } finally {
                loadingHistory = false
            }
        }
    }
    LaunchedEffect(uid) {
        if (previewStatus != null) return@LaunchedEffect
        if (uid != null)
            try {
                username =
                    FirebaseFirestore.getInstance()
                        .collection("users_v2")
                        .document(uid)
                        .get()
                        .await()
                        .getString("username")
                        .orEmpty()
            } catch (_: Exception) {}
        ReferralService.reload()
        ReferralService.trackInBackground("sheet_view", source = source)
        while (true) {
            delay(30000)
            ReferralService.reload()
        }
    }
    LaunchedEffect(uid, status.enabled, status.count) {
        if (previewStatus != null || !status.enabled || uid == null) return@LaunchedEffect
        val prefs = context.getSharedPreferences("corus_referrals_v1", 0)
        val previous = prefs.getInt("seenProgress_$uid", 0).coerceIn(0, 5)
        val target = status.count.coerceIn(0, 5)
        displayed = minOf(previous, target)
        if (reward && target > previous) {
            delay(450)
            displayed = target
            delay(650)
            if (FirebaseAuth.getInstance().currentUser?.uid == uid) {
                haptics.notification()
                prefs.edit().putInt("seenProgress_$uid", target).apply()
            }
        } else displayed = target
    }
    LaunchedEffect(confirmed) {
        if (confirmed) {
            haptics.notification()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = CorusColors.Background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        BackHandler(enabled = history) { history = false }
        ReferralHistoryNavigation(history) { showingHistory ->
            Column(
                Modifier.fillMaxWidth()
                    .fillMaxHeight(.86f)
                    .navigationBarsPadding()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton({ if (showingHistory) history = false else onClose() }) {
                        Icon(
                            if (showingHistory) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                            stringResource(if (showingHistory) S.common_back else S.referral_close),
                            tint = CorusColors.Secondary,
                        )
                    }
                    Text(
                        stringResource(
                            if (showingHistory) S.referral_history else S.settings_row_invite_friends
                        ),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        color = CorusColors.Text,
                        style = CorusFont.body.copy(fontSize = 17.sp),
                    )
                    if (!showingHistory)
                        IconButton({
                            history = true
                            load()
                            scope.launch { ReferralService.trackInBackground("history_view", source = source) }
                        }) {
                            Icon(
                                Icons.Default.History,
                                stringResource(S.referral_history),
                                tint = CorusColors.Secondary,
                            )
                        }
                    else Spacer(Modifier.width(48.dp))
                }
                if (showingHistory) {
                    ReferralHistoryRefreshBox(
                        loading = loadingHistory,
                        onRefresh = { load() },
                        modifier = Modifier.weight(1f),
                    ) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val historyHeight = maxHeight
                            Column(
                                Modifier.fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .heightIn(min = historyHeight)
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                if (historyLoaded && friends.isEmpty() && !historyError)
                                    ReferralHistoryEmptyState(Modifier.weight(1f))
                                friends.forEach {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(it.displayName, color = CorusColors.Text, style = CorusFont.body)
                                        Text(
                                            stringResource(S.referral_joined),
                                            style = CorusFont.caption,
                                            color = CorusColors.Secondary,
                                        )
                                    }
                                }
                                if (cursor != null)
                                    TextButton({ load(true) }, enabled = !loadingHistory) {
                                        Text(stringResource(S.referral_history), style = CorusFont.body)
                                    }
                                if (historyError)
                                    Text(
                                        stringResource(S.referral_error),
                                        color = CorusColors.Secondary,
                                        style = CorusFont.body,
                                    )
                                if (historyError)
                                    TextButton({ load() }, enabled = !loadingHistory) {
                                        Text(
                                            stringResource(
                                                if (historyError) S.referral_retry
                                                else S.referral_history
                                            ),
                                            style = CorusFont.body,
                                        )
                                    }
                            }
                        }
                    }
                } else if (!status.enabled)
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (!loaded && !failed) CircularProgressIndicator()
                        else {
                            Text(
                                stringResource(S.referral_error),
                                textAlign = TextAlign.Center,
                                color = CorusColors.Secondary,
                                style = CorusFont.body,
                            )
                            TextButton({ scope.launch { ReferralService.reload() } }) {
                                Text(stringResource(S.referral_retry), style = CorusFont.body)
                            }
                        }
                    }
                else {
                    ReferralClaimContent(
                        outcome = outcome,
                        rewardEndAtMs = latestStatus.rewardEndAtMs,
                        onDone = onClose,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                val space =
                                    minOf(
                                        220.dp,
                                        maxOf(40.dp, maxWidth - 80.dp),
                                        maxOf(40.dp, maxHeight - 60.dp),
                                    )
                                val recordSize by
                                    animateDpAsState(
                                        referralRecordSize(space, displayed, reward),
                                        spring(dampingRatio = .82f),
                                        label = "referral_record",
                                    )
                                Column(
                                    Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        CymbalClubVinyl(recordSize)
                                    }
                                    if (reward) {
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            stringResource(
                                                when (displayed) {
                                                    1 -> S.referral_record_growing
                                                    2 -> S.referral_three_more
                                                    3 -> S.referral_lp_taking_shape
                                                    4 -> S.referral_one_more
                                                    5 -> S.referral_lp_complete
                                                    else -> S.referral_grow_record
                                                }
                                            ),
                                            style = CorusFont.captionMedium,
                                            color = CorusColors.Secondary,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                            Column(
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 0.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                ReferralHeadline(stringResource(headline(status)))
                                val body =
                                    when (status.rewardStatus) {
                                        "active" -> S.referral_claim_applied_body
                                        "waiting_for_subscription" -> S.referral_claim_saved_body
                                        "claiming" -> S.referral_claim_pending_body
                                        else ->
                                            if (!status.earned)
                                                if (reward) S.referral_explanation else S.referral_paid_body
                                            else null
                                    }
                                body?.let {
                                    Text(
                                        stringResource(it),
                                        style = CorusFont.body,
                                        color = CorusColors.Secondary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                if (reward) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(top = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            stringResource(S.referral_progress, displayed),
                                            style = CorusFont.caption,
                                            color = CorusColors.Secondary,
                                        )
                                        Box(
                                            Modifier.fillMaxWidth()
                                                .height(4.dp)
                                                .background(CorusColors.Accent.copy(alpha = .12f), CircleShape)
                                        ) {
                                            Box(
                                                Modifier.fillMaxWidth(displayed / 5f)
                                                    .fillMaxHeight()
                                                    .background(CorusColors.Accent, CircleShape)
                                            )
                                        }
                                    }
                                }
                                if (failed) {
                                    Text(
                                        stringResource(S.referral_error),
                                        color = MaterialTheme.colorScheme.error,
                                        style = CorusFont.body,
                                    )
                                    TextButton({ scope.launch { ReferralService.reload() } }) {
                                        Text(stringResource(S.referral_retry), style = CorusFont.body)
                                    }
                                }
                                if (status.shortCode.isNotBlank())
                                    ReferralCodePill(status.shortCode, source)
                                val label =
                                    when (outcome) {
                                        ReferralClaimOutcome.Pending -> S.referral_claim_pending_button
                                        else ->
                                            if (status.rewardStatus == "available") S.referral_claim
                                            else if (status.rewardStatus == "claiming")
                                                S.referral_claim_pending_button
                                            else S.referral_share
                                    }
                                val shareText =
                                    stringResource(
                                        if (username.isBlank()) S.referral_share_plain
                                        else S.referral_share_personal,
                                        username.trimStart('@'),
                                    ) + "\n\n" + status.url
                                ReferralActionButton(
                                    stringResource(label),
                                    busy,
                                    !confirmed && (status.rewardStatus == "available" || status.url.isNotBlank()),
                                ) {
                                    if (confirmed) onClose()
                                    else if (
                                        status.rewardStatus == "available" ||
                                            outcome == ReferralClaimOutcome.Pending ||
                                            status.rewardStatus == "claiming"
                                    )
                                        scope.launch {
                                            claimingStatus = status
                                            busy = true
                                            if (previewStatus == null) scope.launch {
                                                ReferralService.trackInBackground("claim_attempt", source = "invite_sheet")
                                            }
                                            val result =
                                                if (previewStatus != null) {
                                                    delay(1200) // Replay a realistic network wait, without making a request.
                                                    ReferralClaimOutcome.Applied
                                                } else ReferralService.claim()
                                            outcome =
                                                if (result == ReferralClaimOutcome.Failed) null else result
                                            busy = false
                                            if (result != ReferralClaimOutcome.Applied && result != ReferralClaimOutcome.Saved)
                                                claimingStatus = null
                                            if (
                                                result == ReferralClaimOutcome.Applied ||
                                                    result == ReferralClaimOutcome.Saved
                                            )
                                                onRewardApplied()
                                        }
                                    else {
                                        scope.launch { ReferralService.trackInBackground("share_started", source = source) }
                                        context.shareReferral(shareText, source)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReferralCodeInput(redeemImmediately: Boolean = false, onRedeemed: () -> Unit = {}) {
    val status by ReferralService.status.collectAsState()
    val enabled by ReferralService.enabled.collectAsState()
    val invite by ReferralService.invitation.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var receiptPending by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        ReferralService.reload()
        ReferralService.refreshInvitation(context)
    }
    if (
        !busy &&
            !receiptPending &&
            (!enabled || !status.canAcceptInvite || (!redeemImmediately && invite != null))
    )
        return
    if (receiptPending) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(S.referral_error), color = CorusColors.Secondary, style = CorusFont.body)
            TextButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        focus.clearFocus()
                        keyboard?.hide()
                        ReferralService.completePending(context)
                        ReferralService.reload()
                        busy = false
                    }
                },
            ) {
                Text(stringResource(S.referral_retry), style = CorusFont.body)
            }
        }
        return
    }
    Column(
        Modifier.fillMaxWidth()
            .then(if (redeemImmediately) Modifier.fillMaxHeight() else Modifier)
            .padding(if (redeemImmediately) 24.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (redeemImmediately) {
            Icon(
                Icons.Default.CardGiftcard,
                null,
                Modifier.size(60.dp)
                    .background(CorusColors.Accent.copy(alpha = .08f), RoundedCornerShape(18.dp))
                    .padding(15.dp),
                tint = CorusColors.Accent,
            )
            Text(
                stringResource(S.referral_gift),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CorusColors.Text,
                textAlign = TextAlign.Center,
                style = CorusFont.body,
            )
        } else
            Text(
                stringResource(S.referral_code_label),
                Modifier.fillMaxWidth(),
                color = CorusColors.Text,
                style = CorusFont.body,
            )
        Text(
            stringResource(S.referral_code_help),
            Modifier.fillMaxWidth(),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
        )
        OutlinedTextField(
            code,
            {
                code = it
                error = false
                applied = false
            },
            Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
            isError = error,
            label = { Text(stringResource(S.referral_code_label), style = CorusFont.body) },
            shape = RoundedCornerShape(14.dp),
            textStyle = CorusFont.body.copy(fontFamily = FontFamily.Monospace, fontSize = 19.sp),
        )
        if (redeemImmediately) Spacer(Modifier.weight(1f).heightIn(min = 20.dp))
        ReferralActionButton(stringResource(S.referral_apply), busy, code.isNotBlank(), textStyle = CorusFont.bodyMedium) {
            scope.launch {
                busy = true
                scope.launch {
                    ReferralService.trackInBackground("code_apply_attempt", source = "settings_redeem")
                }
                val valid = ReferralService.validateAndCapture(context, code)
                val accepted =
                    valid && (!redeemImmediately || ReferralService.completePending(context, true))
                if (accepted && redeemImmediately) {
                    ReferralService.reload()
                    receiptPending = ReferralService.receipt.value?.redeemed != true
                    onRedeemed()
                }
                applied = accepted && !redeemImmediately
                error = !accepted
                busy = false
                scope.launch {
                    ReferralService.trackInBackground(
                        "code_apply_result",
                        source = "settings_redeem",
                        outcome =
                            if (accepted) ReferralService.receipt.value?.giftStatus ?: "pending"
                            else "rejected",
                    )
                }
            }
        }
        if (error || applied)
            Text(
                stringResource(if (error) S.referral_invalid else S.referral_applied),
                color = if (error) MaterialTheme.colorScheme.error else CorusColors.Secondary,
                style = CorusFont.caption,
            )
    }
}

@Composable
fun ReferralRedemptionSheet(onClose: () -> Unit) {
    val receipt by ReferralService.receipt.collectAsState()
    val welcome by ReferralService.welcome.collectAsState()
    val issue by ReferralService.completionIssue.collectAsState()
    DisposableEffect(Unit) {
        ReferralService.presentingRedemption = true
        onDispose {
            ReferralService.presentingRedemption = false
            ReferralService.dismissWelcome()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = CorusColors.Background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.75f).navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(S.referral_redeem_code),
                    Modifier.weight(1f),
                    color = CorusColors.Text,
                    fontWeight = FontWeight.Bold,
                    style = CorusFont.body,
                )
                IconButton(onClose) { Icon(Icons.Default.Close, stringResource(S.referral_close)) }
            }
            if (receipt?.redeemed == true) ReferralReceiptContent(receipt!!)
            else ReferralCodeInput(true)
        }
    }
}

@Composable
private fun ReferralReceiptContent(r: ReferralReceipt) {
    LaunchedEffect(r) {
        ReferralService.trackInBackground("receipt_view", source = "settings_redeem", outcome = r.giftStatus)
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(Icons.Default.CardGiftcard, null, Modifier.size(40.dp), tint = CorusColors.Accent)
        Text(
            stringResource(
                when (r.giftStatus) {
                    "applied",
                    "expired" -> S.referral_receipt_applied
                    "waiting_for_subscription" -> S.referral_receipt_claimed
                    else -> S.referral_receipt_redeemed
                }
            ),
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
            style = CorusFont.songTitleLarge,
        )
        r.appliedCode?.let {
            Text(stringResource(S.referral_receipt_code), color = CorusColors.Secondary, style = CorusFont.caption)
            SelectionContainer {
                Text(
                    it,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CorusColors.Text,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(
                                CorusColors.Accent.copy(alpha = .08f),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
        r.inviterLabel?.let {
            Text(stringResource(S.referral_receipt_from, it), color = CorusColors.Secondary, style = CorusFont.bodyMedium)
        }
        Text(
            if (r.giftStatus == "waiting_for_subscription")
                stringResource(S.referral_receipt_waiting)
            else if (r.giftEndAtMs != null && r.giftStatus in listOf("applied", "expired"))
                stringResource(
                    if (r.giftEndAtMs > System.currentTimeMillis()) S.referral_receipt_until
                    else S.referral_receipt_ended,
                    date(r.giftEndAtMs),
                )
            else stringResource(S.referral_receipt_pending),
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
            style = CorusFont.body,
        )
        Text(
            stringResource(S.referral_receipt_limit),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ReferralOutcomeContent(onContinue: () -> Unit) {
    val welcome by ReferralService.welcome.collectAsState()
    val issue by ReferralService.completionIssue.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticManager.current
    var busy by remember { mutableStateOf(false) }
    var followingBusy by remember { mutableStateOf(false) }
    var following by remember(welcome?.inviterId) { mutableStateOf(false) }
    var followFailed by remember { mutableStateOf(false) }
    fun issueOutcome(value: ReferralCompletionIssue?) = when (value) {
        ReferralCompletionIssue.AlreadyRedeemed -> "alreadyRedeemed"
        ReferralCompletionIssue.TemporaryFailure -> "temporaryFailure"
        ReferralCompletionIssue.InvalidInvitation -> "invalidInvitation"
        null -> "unknown"
    }
    LaunchedEffect(welcome?.inviterId, welcome?.giftStatus, issue) {
        if (issue != null) ReferralService.trackInBackground("outcome_view", source = "onboarding", outcome = issueOutcome(issue))
        else if (welcome != null) ReferralService.trackInBackground("gift_confirmation_view", source = "onboarding", outcome = welcome?.giftStatus)
    }
    LaunchedEffect(welcome?.inviterId) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val inviter = welcome?.inviterId
        if (uid != null && !inviter.isNullOrBlank())
            try {
                following =
                    FirebaseFirestore.getInstance()
                        .collection("users_v2")
                        .document(uid)
                        .collection("following")
                        .document(inviter)
                        .get()
                        .await()
                        .exists()
            } catch (_: Exception) {}
    }
    LaunchedEffect(welcome?.isActive) {
        if (welcome?.isActive == true)
            haptics.play(fm.corus.android.domain.HapticManager.Pattern.GIFT_SENT)
    }
    LaunchedEffect(welcome?.inviterId) {
        if (welcome?.pending == true)
            for (seconds in listOf(2L, 4L, 8L)) {
                delay(seconds * 1000)
                if (ReferralService.welcome.value?.pending != true) break
                ReferralService.completePending(context)
            }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(CorusColors.Background)) {
        val minimumHeight = maxHeight
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minimumHeight)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.weight(1f).heightIn(min = 30.dp))
            val value = welcome
            if (issue != null) {
                Icon(
                    if (issue == ReferralCompletionIssue.TemporaryFailure) Icons.Default.WifiOff
                    else Icons.Default.CardGiftcard,
                    null,
                    Modifier.size(44.dp),
                    tint = CorusColors.Accent,
                )
                Text(
                    stringResource(
                        when (issue) {
                            ReferralCompletionIssue.AlreadyRedeemed -> S.referral_issue_used_title
                            ReferralCompletionIssue.TemporaryFailure ->
                                S.referral_issue_network_title
                            else -> S.referral_issue_invalid_title
                        }
                    ),
                    style = CorusFont.body,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CorusColors.Text,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(
                        when (issue) {
                            ReferralCompletionIssue.AlreadyRedeemed -> S.referral_issue_used_body
                            ReferralCompletionIssue.TemporaryFailure ->
                                S.referral_issue_network_body
                            else -> S.referral_issue_invalid_body
                        }
                    ),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                )
            } else if (value != null) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    CymbalClubVinyl(174.dp)
                    if (value.isActive)
                        ReferralSuccessBadge()
                }
                Text(
                    stringResource(
                        if (value.isActive) S.referral_gift_active_title
                        else if (value.waiting) S.referral_gift_waiting_title
                        else S.referral_gift_pending_title
                    ),
                    style = CorusFont.body,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = CorusColors.Text,
                    textAlign = TextAlign.Center,
                )
                if (!value.isActive)
                    Text(
                        stringResource(
                            if (value.waiting) S.referral_gift_waiting_body
                            else S.referral_gift_pending_body
                        ),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Secondary,
                        textAlign = TextAlign.Center,
                    )
                if (value.isActive)
                    value.giftEndAtMs?.let {
                        Text(
                            stringResource(S.referral_gift_until, date(it)),
                            style = CorusFont.bodyMedium,
                            color = CorusColors.Secondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                Text(
                    stringResource(S.referral_gift_from, value.name),
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                )
            }
            Spacer(Modifier.weight(1f).heightIn(min = 40.dp))
            if (issue == null && value != null) {
                TextButton(
                    enabled = !followingBusy && !following,
                    onClick = {
                        scope.launch {
                            followingBusy = true
                            followFailed = false
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            try {
                                followReferralInviter(context, value.inviterId)
                                if (uid == FirebaseAuth.getInstance().currentUser?.uid) {
                                    following = true
                                    haptics.selection()
                                    scope.launch {
                                        ReferralService.trackInBackground(
                                            "follow_inviter_result",
                                            source = "gift_confirmation",
                                            outcome = "followed",
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                followFailed = true
                                scope.launch {
                                    ReferralService.trackInBackground(
                                        "follow_inviter_result",
                                        source = "gift_confirmation",
                                        outcome = "failed",
                                    )
                                }
                            } finally {
                                followingBusy = false
                            }
                        }
                    },
                ) {
                    Icon(if (following) Icons.Default.Check else Icons.Default.PersonAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (following) S.following_status else S.referral_follow,
                            value.name,
                        ),
                        style = CorusFont.bodyMedium,
                    )
                }
                if (followFailed)
                    Text(
                        stringResource(S.referral_gift_follow_error),
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                    )
            }
            val retry: () -> Unit = {
                scope.launch {
                    busy = true
                    if (issue != null) scope.launch { ReferralService.trackInBackground("outcome_retry", source = "onboarding", outcome = issueOutcome(issue)) }
                    ReferralService.completePending(context)
                    busy = false
                }
            }
            val finish: () -> Unit = {
                ReferralService.trackInBackground(
                    if (issue != null) "outcome_continue" else "gift_confirmation_continue",
                    source = "onboarding",
                    outcome = value?.giftStatus ?: issueOutcome(issue),
                )
                ReferralService.dismissWelcome()
                onContinue()
            }
            if (issue == ReferralCompletionIssue.TemporaryFailure)
                ReferralActionButton(stringResource(S.referral_retry), busy, onClick = retry)
            else if (value?.pending == true)
                TextButton(
                    retry,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                ) {
                    Text(stringResource(S.referral_retry), style = CorusFont.body)
                }
            if (issue == ReferralCompletionIssue.TemporaryFailure)
                TextButton(
                    finish,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                ) {
                    Text(stringResource(S.common_continue), style = CorusFont.body)
                }
            else ReferralActionButton(stringResource(S.common_continue), enabled = !busy, textStyle = CorusFont.button, onClick = finish)
        }
    }
}

@Composable
fun ReferralWelcomeDialog() {
    val welcome by ReferralService.welcome.collectAsState()
    val issue by ReferralService.completionIssue.collectAsState()
    if (ReferralService.presentingRedemption || (welcome == null && issue == null)) return
    Dialog(
        onDismissRequest = {},
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(CorusColors.Background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            ReferralOutcomeContent {}
        }
    }
}
