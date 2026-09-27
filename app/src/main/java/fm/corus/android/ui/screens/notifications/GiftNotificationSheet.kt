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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GiftNotificationSheet(notification: CymbalNotification, onDismiss: () -> Unit, onViewCorus: () -> Unit) {
    val context = LocalContext.current
    val gift = GiftDefinition.from(notification.giftType)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CorusColors.Background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(gift.name(context), style = MaterialTheme.typography.titleLarge, color = CorusColors.Text)
            GiftNotificationArtwork(notification.giftType, 164.dp)
            Text(context.getString(R.string.gift_sender_sent_you, notification.fromUser.username, gift.sentPhrase(context)), color = CorusColors.Text)
            Text(GiftDefinition.context(context, notification.postTitle), color = CorusColors.Secondary)
            notification.giftNote?.takeIf { it.isNotBlank() }?.let { Text(it, color = CorusColors.Text) }
            Text(java.text.DateFormat.getDateTimeInstance().format(notification.timestamp), color = CorusColors.Secondary)
            if (notification.postId != null) Button(onClick = onViewCorus) { Text(stringResource(R.string.gift_view_corus)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.gift_close)) }
        }
    }
}
