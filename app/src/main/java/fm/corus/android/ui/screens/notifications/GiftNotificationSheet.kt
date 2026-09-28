package fm.corus.android.ui.screens.notifications

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
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.Fit
import fm.corus.android.R
import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.GiftDefinition
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.components.LocalContainingTabSelected

@Composable
internal fun GiftNotificationArtwork(type: String?, size: Dp = 44.dp) {
    val gift = GiftDefinition.from(type)
    val localizedName = gift.name(LocalContext.current)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val active = LocalContainingTabSelected.current
    var view by remember { mutableStateOf<RiveAnimationView?>(null) }
    var failed by remember(type) { mutableStateOf(false) }
    DisposableEffect(lifecycle, view, active) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && active && ValueAnimator.areAnimatorsEnabled()) view?.play()
            if (event == Lifecycle.Event.ON_PAUSE) view?.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); view?.pause() }
    }
    if (gift.artboard == null || failed) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { Text(gift.emoji, fontSize = (size.value * .65f).sp) }
    } else key(type) {
        AndroidView(
            modifier = Modifier.size(size),
            factory = { context ->
                Rive.init(context)
                RiveAnimationView(context).also { player ->
                    view = player
                    player.contentDescription = localizedName
                    runCatching {
                        player.setRiveResource(R.raw.corus_gifts, artboardName = gift.artboard,
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
    val text = emphasizedGiftAttribution(
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CorusColors.Background,
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
                Text(
                    stringResource(R.string.gift_thanks_sender, sender),
                    style = CorusFont.body, color = CorusColors.Text, textAlign = TextAlign.Center,
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
