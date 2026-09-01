package fm.corus.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CorusPromptButton(
    val label: String,
    val emphasized: Boolean = false,
    val onClick: () -> Unit,
)

private val CardCornerRadius = 20.dp
private val ButtonCornerRadius = 14.dp
private val CardPaddingH = 24.dp
private val CardPaddingTop = 28.dp
private val CardPaddingBottom = 24.dp
private val ScreenInset = 32.dp

/**
 * App-root slot for [CorusPromptOverlay]. A Compose [androidx.compose.ui.window.Dialog]
 * window does not draw behind the status bar (even with `decorFitsSystemWindows = false`),
 * so the card would sit low and the status strip would stay undimmed. Rendering in
 * [fm.corus.android.ui.CorusApp] covers the status strip, tab bar, and mini-player, and
 * centers the card on the real screen. System status-bar icons (clock, chips) stay
 * above every app window — that layer cannot be dimmed.
 */
@Stable
class PromptOverlayHostState {
    internal var request by mutableStateOf<PromptOverlayRequest?>(null)
}

internal data class PromptOverlayRequest(
    val visible: Boolean,
    val revealed: Boolean,
    val title: String,
    val message: String,
    val buttons: List<CorusPromptButton>,
    val iconRes: Int?,
    val footnote: String?,
    val modifier: Modifier,
    val onBack: () -> Unit,
)

val LocalPromptOverlayHost = staticCompositionLocalOf<PromptOverlayHostState?> { null }

@Composable
fun rememberPromptOverlayHostState(): PromptOverlayHostState = remember { PromptOverlayHostState() }

/**
 * Draws the hosted prompt, if any. Call from the app-root box so the scrim is
 * a sibling of [fm.corus.android.ui.navigation.MainTabScreen], not a child of
 * the inset tab scaffold.
 */
@Composable
fun PromptOverlayHost(state: PromptOverlayHostState) {
    val request = state.request ?: return
    PromptOverlayContent(
        visible = request.visible,
        revealed = request.revealed,
        title = request.title,
        message = request.message,
        buttons = request.buttons,
        modifier = request.modifier,
        iconRes = request.iconRes,
        footnote = request.footnote,
        onBack = request.onBack,
    )
}

/**
 * Solid card modal over a dimmed scrim — the standard Corus prompt for multi-line
 * copy and stacked outlined actions. Material [androidx.compose.material3.AlertDialog]
 * remains for short destructive confirms.
 *
 * When a [LocalPromptOverlayHost] is in scope (the signed-in app), the overlay is
 * drawn at the window root so it covers chrome and the status-bar strip. Previews
 * and tests without a host still render in place.
 */
@Composable
fun CorusPromptOverlay(
    visible: Boolean,
    title: String,
    message: String,
    buttons: List<CorusPromptButton>,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    footnote: String? = null,
    onDismiss: () -> Unit = {},
) {
    var revealed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            revealed = true
        } else {
            revealed = false
        }
    }

    fun dismissThen(action: () -> Unit) {
        scope.launch {
            revealed = false
            delay(240)
            onDismiss()
            action()
        }
    }

    val wrappedButtons = buttons.map { button ->
        button.copy(onClick = { dismissThen(button.onClick) })
    }

    val host = LocalPromptOverlayHost.current
    if (host != null) {
        SideEffect {
            host.request = if (!visible && !revealed) {
                null
            } else {
                PromptOverlayRequest(
                    visible = visible,
                    revealed = revealed,
                    title = title,
                    message = message,
                    buttons = wrappedButtons,
                    iconRes = iconRes,
                    footnote = footnote,
                    modifier = modifier,
                    onBack = { dismissThen {} },
                )
            }
        }
        DisposableEffect(host) {
            onDispose { host.request = null }
        }
        return
    }

    if (!visible && !revealed) return

    PromptOverlayContent(
        visible = visible,
        revealed = revealed,
        title = title,
        message = message,
        buttons = wrappedButtons,
        modifier = modifier,
        iconRes = iconRes,
        footnote = footnote,
        onBack = { dismissThen {} },
    )
}

@Composable
private fun PromptOverlayContent(
    visible: Boolean,
    revealed: Boolean,
    title: String,
    message: String,
    buttons: List<CorusPromptButton>,
    modifier: Modifier,
    @DrawableRes iconRes: Int?,
    footnote: String?,
    onBack: () -> Unit,
) {
    BackHandler(enabled = visible || revealed, onBack = onBack)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible || revealed,
            enter = fadeIn(animationSpec = tween(320)),
            exit = fadeOut(animationSpec = tween(240)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = revealed,
                    enter = fadeIn(animationSpec = tween(320)) +
                        scaleIn(
                            initialScale = 0.97f,
                            transformOrigin = TransformOrigin(0.5f, 0.5f),
                            animationSpec = tween(320),
                        ),
                    exit = fadeOut(animationSpec = tween(240)) +
                        scaleOut(
                            targetScale = 0.97f,
                            transformOrigin = TransformOrigin(0.5f, 0.5f),
                            animationSpec = tween(240),
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenInset)
                            .shadow(24.dp, RoundedCornerShape(CardCornerRadius))
                            .clip(RoundedCornerShape(CardCornerRadius))
                            .background(CorusColors.CardBackground)
                            .border(
                                0.5.dp,
                                CorusColors.Divider.copy(alpha = 0.65f),
                                RoundedCornerShape(CardCornerRadius),
                            )
                            .padding(
                                start = CardPaddingH,
                                end = CardPaddingH,
                                top = CardPaddingTop,
                                bottom = CardPaddingBottom,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (iconRes != null) {
                            Image(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(modifier = Modifier.height(CorusSpacing.sm))
                        }
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CorusColors.Text,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = message,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = CorusColors.Text.copy(alpha = 0.72f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        buttons.forEachIndexed { index, button ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                            }
                            OutlinedPromptButton(
                                label = button.label,
                                emphasized = button.emphasized,
                                onClick = { button.onClick() },
                            )
                        }
                        if (!footnote.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(CorusSpacing.md))
                            Text(
                                text = footnote,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = CorusColors.Secondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OutlinedPromptButton(
    label: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (emphasized) CorusColors.Accent else CorusColors.Divider
    val textColor = if (emphasized) CorusColors.Accent else CorusColors.Text

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ButtonCornerRadius))
            .border(1.5.dp, borderColor, RoundedCornerShape(ButtonCornerRadius))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 15.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}
