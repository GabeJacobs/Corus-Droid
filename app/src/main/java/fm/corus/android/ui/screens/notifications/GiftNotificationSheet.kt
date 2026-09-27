package fm.corus.android.ui.screens.notifications

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import fm.corus.android.data.model.NotificationType
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.components.LocalContainingTabSelected
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GiftNotificationSheet(notification: CymbalNotification, onDismiss: () -> Unit, onViewCorus: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val gift = GiftDefinition.from(notification.giftType)
    val scope = rememberCoroutineScope()
    var thanked by remember(notification.id) { mutableStateOf(notification.giftThankedAt != null) }
    var sendingThanks by remember(notification.id) { mutableStateOf(false) }
    var thankError by remember(notification.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CorusColors.Background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(gift.name(context), style = MaterialTheme.typography.titleLarge, color = CorusColors.Text)
            GiftNotificationArtwork(notification.giftType, 164.dp)
            Text(
                if (notification.type == NotificationType.GIFT_THANKS) {
                    context.getString(R.string.notif_gift_thanks_full, notification.fromUser.username)
                } else {
                    context.getString(R.string.gift_sender_sent_you, notification.fromUser.username, gift.sentPhrase(context))
                },
                color = CorusColors.Text,
            )
            Text(GiftDefinition.context(context, notification.postTitle), color = CorusColors.Secondary)
            if (notification.type == NotificationType.GIFT) {
                notification.giftNote?.takeIf { it.isNotBlank() }?.let { Text(it, color = CorusColors.Text) }
            }
            Text(java.text.DateFormat.getDateTimeInstance().format(notification.timestamp), color = CorusColors.Secondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (notification.type == NotificationType.GIFT && notification.giftId != null) {
                    Button(
                        enabled = !thanked && !sendingThanks,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val postId = notification.postId ?: return@Button
                            val giftId = notification.giftId ?: return@Button
                            thankError = false
                            sendingThanks = true
                            scope.launch {
                                try {
                                    FirebaseFunctions.getInstance("us-central1")
                                        .getHttpsCallable("thankGift")
                                        .call(mapOf("postId" to postId, "giftId" to giftId))
                                        .await()
                                    thanked = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    thankError = true
                                } finally {
                                    sendingThanks = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource(when {
                            thanked -> R.string.gift_thanked
                            sendingThanks -> R.string.gift_sending_thanks
                            else -> R.string.gift_say_thanks
                        }))
                    }
                }
                if (notification.postId != null) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = onViewCorus) {
                        Text(stringResource(R.string.gift_go_to_post))
                    }
                }
            }
            if (thankError) Text(stringResource(R.string.gift_thanks_failed), color = CorusColors.Secondary)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.gift_close)) }
        }
    }
}
