package fm.corus.android.ui.screens.subscription

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Bottom sheet shown when a free user tries to generate a playlist after their free trial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPaywallSheet(
    onDismiss: () -> Unit,
    onNavigateToClub: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xxl)
                .padding(bottom = CorusSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.QueueMusic,
                contentDescription = null,
                tint = CorusColors.Accent,
                modifier = Modifier.size(48.dp),
            )

            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            Text(
                text = "Club members only",
                style = CorusFont.appTitle,
                color = CorusColors.Text,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(CorusSpacing.sm))

            Text(
                text = "Playlist generation is a Corus Club feature. Join the club to create unlimited Spotify playlists from any feed or profile.",
                style = CorusFont.body,
                color = CorusColors.Secondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(CorusSpacing.xxl))

            Button(
                onClick = onNavigateToClub,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CorusColors.Accent,
                    contentColor = Color.White,
                ),
            ) {
                Text("Unlock playlist generation", style = CorusFont.button)
            }

            Spacer(modifier = Modifier.height(CorusSpacing.md))

            TextButton(onClick = onDismiss) {
                Text("Maybe later", style = CorusFont.body, color = CorusColors.Secondary)
            }
        }
    }
}
