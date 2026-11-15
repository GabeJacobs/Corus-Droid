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
import fm.corus.android.domain.SpotifyFtuePromptKind

@Composable
fun PlaybackModePromptOverlay(
    kind: SpotifyFtuePromptKind,
    musicService: MusicService,
    onSecondary: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val title = when (kind) {
        SpotifyFtuePromptKind.LINK_SPOTIFY ->
            stringResource(fm.corus.android.localization.CorusStrings.playback_mode_prompt_title_link)
        SpotifyFtuePromptKind.CHOOSE_LISTEN ->
            stringResource(fm.corus.android.localization.CorusStrings.playback_mode_prompt_title)
    }
    val secondary = when (kind) {
        SpotifyFtuePromptKind.LINK_SPOTIFY ->
            stringResource(fm.corus.android.localization.CorusStrings.playback_mode_prompt_not_now)
        SpotifyFtuePromptKind.CHOOSE_LISTEN ->
            stringResource(fm.corus.android.localization.CorusStrings.playback_mode_prompt_previews)
    }

    val isAppleMusic = musicService == MusicService.APPLE_MUSIC
    CorusPromptOverlay(
        visible = visible,
        title = title,
        message = stringResource(
            if (isAppleMusic) R.string.playback_mode_prompt_body_apple_music
            else fm.corus.android.localization.CorusStrings.playback_mode_prompt_body_spotify_link,
        ),
        footnote = if (kind == SpotifyFtuePromptKind.LINK_SPOTIFY) {
            stringResource(fm.corus.android.localization.CorusStrings.playback_mode_prompt_link_footnote)
        } else {
            null
        },
        iconRes = MusicServiceLinkOut.logoRes(musicService),
        buttons = listOf(
            CorusPromptButton(
                label = secondary,
                onClick = onSecondary,
            ),
            CorusPromptButton(
                label = stringResource(
                    if (isAppleMusic) R.string.settings_connect_apple_music
                    else fm.corus.android.localization.CorusStrings.playback_mode_prompt_link_spotify,
                ),
                emphasized = true,
                onClick = onConnect,
            ),
        ),
        modifier = modifier,
    )
}
