package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusSpacing

/** The card's own proportions (1200×630), so it is never letterboxed. */
private const val CARD_ASPECT = 1200f / 630f

/**
 * The Open Graph card corus.fm draws for a share link — the same image an
 * iMessage, X or WhatsApp unfurl shows once the link lands. Sitting at the top
 * of a share sheet, it answers the only question the sheet couldn't: what the
 * person on the other end is about to see.
 *
 * Every share link this app builds is `corus.fm/{segment}/{key}`, and the card
 * for it is that same path plus `/preview` — the URL the preview functions
 * already stamp into `og:image`, served through corus.fm's edge and cached
 * there. Nothing new is generated for the sheet; it asks for the card the
 * unfurlers ask for.
 *
 * The card is drawn from the poster's latest posts, so a stale one is only ever
 * a card that is a few posts behind, never a wrong one. If it 404s or the
 * network is down this takes its own row away rather than holding an empty box.
 */
@Composable
fun ShareLinkPreviewCard(
    shareableLink: String,
    modifier: Modifier = Modifier,
    version: String? = null,
    theme: ShareCardTheme = ShareCardTheme.DARK,
) {
    if (shareableLink.isBlank()) return

    val previewUrl = shareCardPreviewUrl(shareableLink, version, theme)
    var failed by remember(previewUrl) { mutableStateOf(false) }
    var loaded by remember(previewUrl) { mutableStateOf(false) }
    if (failed) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = CorusSpacing.lg,
                end = CorusSpacing.lg,
                top = CorusSpacing.sm,
                bottom = CorusSpacing.xs,
            )
            // Full width at the card's own proportions, the same shape iOS and
            // web draw, so a share sheet is one design everywhere.
            .aspectRatio(CARD_ASPECT)
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .then(
                if (loaded) Modifier
                else Modifier.background(CorusColors.Skeleton).shimmer(),
            ),
    ) {
        AsyncImage(
            model = previewUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            onError = { failed = true },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
