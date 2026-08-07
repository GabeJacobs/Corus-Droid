package fm.corus.android.ui.components

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
 * Solid card modal over a dimmed scrim — the standard Corus prompt for multi-line
 * copy and stacked outlined actions. Material [androidx.compose.material3.AlertDialog]
 * remains for short destructive confirms.
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

    if (!visible && !revealed) return

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
                                onClick = { dismissThen(button.onClick) },
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
