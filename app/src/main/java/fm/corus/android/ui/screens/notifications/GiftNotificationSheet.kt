package fm.corus.android.ui.screens.notifications

import fm.corus.android.ui.components.CorusModalBottomSheet
import android.animation.ValueAnimator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import fm.corus.android.data.model.NotificationType
import fm.corus.android.ui.components.emphasizedGiftAttribution
import fm.corus.android.ui.theme.CorusFont
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.File as RiveFile
import app.rive.runtime.kotlin.core.Rive
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import app.rive.runtime.kotlin.core.Fit
import fm.corus.android.R
import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.GiftDefinition
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.components.LocalContainingTabSelected

/**
 * The Gift animations live in one 4 MB .riv. Handing each RiveAnimationView the raw
 * resource made every view re-read and re-parse it on the main thread (~230 ms each),
 * so opening the picker with four tiles froze the UI for about a second. Parse it once,
 * off the main thread, and share the parsed file across every view.
 */
internal object GiftRiveFile {
    @Volatile private var file: RiveFile? = null
    private val lock = Mutex()

    fun peek(): RiveFile? = file

    suspend fun get(context: Context): RiveFile? {
        file?.let { return it }
        return withContext(Dispatchers.Default) {
            lock.withLock {
                file ?: runCatching {
                    val app = context.applicationContext
                    Rive.init(app)
                    val bytes = app.resources.openRawResource(R.raw.corus_gifts).use { it.readBytes() }
                    RiveFile(bytes)
                }.getOrNull()?.also { file = it }
            }
        }
    }
}

@Composable
internal fun GiftNotificationArtwork(type: String?, size: Dp = 44.dp) {
    val gift = GiftDefinition.from(type)
    val context = LocalContext.current
    val localizedName = gift.name(context)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val active = LocalContainingTabSelected.current
    var view by remember { mutableStateOf<RiveAnimationView?>(null) }
    var failed by remember(type) { mutableStateOf(false) }
    var riveFile by remember { mutableStateOf(GiftRiveFile.peek()) }
    LaunchedEffect(gift.artboard) {
        if (gift.artboard != null && riveFile == null) riveFile = GiftRiveFile.get(context)
    }
    DisposableEffect(lifecycle, view, active) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && active && ValueAnimator.areAnimatorsEnabled()) view?.play()
            if (event == Lifecycle.Event.ON_PAUSE) view?.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); view?.pause() }
    }
    val sharedFile = riveFile
    if (gift.artboard == null || failed || sharedFile == null) {
        // Emoji stands in until the shared file is parsed (or if it never can be).
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { Text(gift.emoji, fontSize = (size.value * .65f).sp) }
    } else key(type) {
        AndroidView(
            modifier = Modifier.size(size),
            factory = { ctx ->
                RiveAnimationView(ctx).also { player ->
                    view = player
                    player.contentDescription = localizedName
                    runCatching {
                        player.setRiveFile(sharedFile, artboardName = gift.artboard,
                            stateMachineName = "Gift loop", fit = Fit.CONTAIN,
                            autoplay = active && ValueAnimator.areAnimatorsEnabled() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    }.onFailure { failed = true }
                }
            },
            update = { player ->
                if (active && ValueAnimator.areAnimatorsEnabled() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) player.play()
                else player.pause()
            },
            onRelease = { it.stop(); view = null },
        )
    }
}

/** Keep the localized article/connector regular and emphasize only sender and Gift name. */
internal fun giftReceiptSenderLine(
    context: android.content.Context,
    notification: CymbalNotification,
    sentToYou: Boolean = false,
): AnnotatedString {
    val senderToken = "__GIFT_SENDER__"
    val nameToken = "__GIFT_NAME__"
    val gift = GiftDefinition.from(notification.giftType)
    val sender = notification.fromUser.username.ifBlank {
        notification.fromUser.displayName.ifBlank { context.getString(R.string.gift_someone) }
    }
    val phrase = gift.sentPhrase(context).replace(gift.name(context), nameToken)
    val text = if (notification.type == NotificationType.GIFT_THANKS) emphasizedGiftAttribution(
        context.getString(R.string.gift_thanks_sender, senderToken),
        mapOf(senderToken to sender),
    ) else emphasizedGiftAttribution(
        context.getString(if (sentToYou) R.string.gift_sender_sent_you else R.string.gift_sender_sent,
            senderToken, phrase),
        linkedMapOf(senderToken to sender, nameToken to gift.name(context)),
    )
    return buildAnnotatedString {
        append(text)
        // Match the app's Nunito username weight and preserve wrapping in every language.
        text.spanStyles.forEach { addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold), it.start, it.end) }
        val start = text.text.indexOf(sender)
        if (start >= 0 && notification.fromUser.id.isNotBlank()) {
            addStringAnnotation("USER", notification.fromUser.id, start, start + sender.length)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GiftNotificationSheet(
    notification: CymbalNotification,
    onDismiss: () -> Unit,
    onViewCorus: () -> Unit,
    onSenderTap: () -> Unit,
    thanksState: GiftThanksState,
    onThank: () -> Unit,
) {
    val context = LocalContext.current
    val gift = GiftDefinition.from(notification.giftType)
    val isThanksReceipt = notification.type == NotificationType.GIFT_THANKS
    val isThanked = notification.giftThankedAt != null || thanksState.thankedAt != null
    val showsThankAction = !isThanksReceipt && (notification.canThankGift || isThanked)
    val sender = notification.fromUser.username.ifBlank {
        notification.fromUser.displayName.ifBlank { stringResource(R.string.gift_someone) }
    }
    CorusModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // Intrinsic content height opens fully. Oversized notes and large text can scroll;
        // ModalBottomSheet supplies the navigation-bar inset below this bottom padding.
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.gift_sheet_title_one), style = CorusFont.screenTitle, color = CorusColors.Text)
            Spacer(Modifier.height(24.dp))
            GiftNotificationArtwork(notification.giftType, 164.dp)
            Spacer(Modifier.height(22.dp))
            if (isThanksReceipt) {
                val senderLine = giftReceiptSenderLine(context, notification)
                ClickableText(
                    text = senderLine,
                    style = CorusFont.body.copy(color = CorusColors.Text, textAlign = TextAlign.Center),
                    onClick = { offset ->
                        if (senderLine.getStringAnnotations("USER", offset, offset).isNotEmpty()) onSenderTap()
                    },
                )
            } else {
                Text(gift.name(context), style = CorusFont.custom(800, 28), color = CorusColors.Text,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(7.dp))
                val senderLine = giftReceiptSenderLine(context, notification)
                ClickableText(
                    text = senderLine,
                    style = CorusFont.body.copy(color = CorusColors.Text, textAlign = TextAlign.Center),
                    onClick = { offset ->
                        if (senderLine.getStringAnnotations("USER", offset, offset).isNotEmpty()) onSenderTap()
                    },
                )
                notification.giftNote?.trim()?.takeIf { it.isNotEmpty() }?.let { note ->
                    Spacer(Modifier.height(22.dp))
                    Column(
                        Modifier.fillMaxWidth().background(CorusColors.CardBackground, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Text(stringResource(R.string.gift_note_from, sender), style = CorusFont.captionMedium,
                            color = CorusColors.Secondary)
                        Text(note, style = CorusFont.body, color = CorusColors.Text)
                    }
                }
            }
            if (showsThankAction || !notification.postId.isNullOrBlank()) {
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (showsThankAction) {
                        Button(
                            onClick = onThank,
                            enabled = !isThanked && !thanksState.sending,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CorusColors.Accent, contentColor = Color.White,
                                disabledContainerColor = CorusColors.Accent, disabledContentColor = Color.White,
                            ),
                        ) {
                            Text(stringResource(when {
                                isThanked -> R.string.gift_thanked
                                thanksState.sending -> R.string.gift_thanks_sending
                                else -> R.string.gift_say_thanks
                            }), style = CorusFont.button, textAlign = TextAlign.Center)
                        }
                    }
                    if (!notification.postId.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = onViewCorus,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(50),
                            border = if (isThanksReceipt) null else BorderStroke(1.dp, CorusColors.Divider),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 13.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isThanksReceipt) CorusColors.Accent else Color.Transparent,
                                contentColor = if (isThanksReceipt) Color.White else CorusColors.Text,
                            ),
                        ) { Text(stringResource(R.string.gift_open_post), style = CorusFont.button, textAlign = TextAlign.Center) }
                    }
                }
            }
            if (thanksState.error) {
                Spacer(Modifier.height(22.dp))
                Text(stringResource(R.string.gift_thanks_error), style = CorusFont.caption,
                    color = CorusColors.Secondary, textAlign = TextAlign.Center)
            }
        }
    }
}
