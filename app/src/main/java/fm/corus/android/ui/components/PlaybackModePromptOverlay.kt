package fm.corus.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fm.corus.android.R
import fm.corus.android.data.model.MusicService
import fm.corus.android.domain.MusicServiceLinkOut

@Composable
fun PlaybackModePromptOverlay(
    musicService: MusicService,
    onChoosePreviews: () -> Unit,
    onChooseFullSongs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val bodyRes = when (musicService) {
        MusicService.APPLE_MUSIC -> R.string.playback_mode_prompt_body_apple_music
        else -> R.string.playback_mode_prompt_body_spotify
    }

    CorusPromptOverlay(
        visible = visible,
        title = stringResource(R.string.playback_mode_prompt_title),
        message = stringResource(bodyRes),
        footnote = stringResource(R.string.playback_mode_prompt_settings_footnote),
        iconRes = MusicServiceLinkOut.logoRes(musicService),
        buttons = listOf(
            CorusPromptButton(
                label = stringResource(R.string.playback_mode_prompt_previews),
                onClick = onChoosePreviews,
            ),
            CorusPromptButton(
                label = stringResource(R.string.playback_mode_prompt_full_songs),
                emphasized = true,
                onClick = onChooseFullSongs,
            ),
        ),
        modifier = modifier,
    )
}
