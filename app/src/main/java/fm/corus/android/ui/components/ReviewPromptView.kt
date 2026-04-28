package fm.corus.android.ui.components

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.play.core.review.ReviewManagerFactory
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun ReviewPromptView(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = CorusSpacing.xxl)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CorusColors.Background)
                    .padding(CorusSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // App logo
                Text(
                    "c",
                    style = CorusFont.logoLarge,
                    color = CorusColors.Accent,
                )

                Spacer(modifier = Modifier.height(CorusSpacing.lg))

                Text(
                    stringResource(R.string.review_prompt_title),
                    style = CorusFont.songTitleLarge,
                    color = CorusColors.Text,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(CorusSpacing.sm))

                Text(
                    stringResource(R.string.review_prompt_message),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(CorusSpacing.xxl))

                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity != null) {
                            val reviewManager = ReviewManagerFactory.create(context)
                            val request = reviewManager.requestReviewFlow()
                            request.addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    reviewManager.launchReviewFlow(activity, task.result)
                                }
                            }
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(stringResource(R.string.review_prompt_button), style = CorusFont.button, color = Color.White)
                }

                Spacer(modifier = Modifier.height(CorusSpacing.sm))

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.review_prompt_not_now), style = CorusFont.body, color = CorusColors.Secondary)
                }
            }
        }
    }
}
