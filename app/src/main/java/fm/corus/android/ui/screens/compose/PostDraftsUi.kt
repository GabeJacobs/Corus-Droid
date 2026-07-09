package fm.corus.android.ui.screens.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.PostDraft
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils
import java.util.Date

/**
 * Body of the "Drafts" bottom sheet opened from the pick-a-song/film step.
 * Tap a row to resume it; left-swipe (Material [SwipeToDismissBox]) deletes it.
 * Rows rely on spacing rather than dividers, mirroring the finalized iOS
 * DraftsSheetView. Keeps the 30-cap enforced by the repository.
 */
@Composable
fun DraftsSheetContent(
    drafts: List<PostDraft>,
    onResume: (PostDraft) -> Unit,
    onDelete: (PostDraft) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.compose_drafts_title),
            style = CorusFont.displayName,
            color = CorusColors.Text,
            modifier = Modifier.padding(
                horizontal = CorusSpacing.lg,
                vertical = CorusSpacing.md,
            ),
        )
        if (drafts.isEmpty()) {
            Text(
                text = stringResource(R.string.compose_drafts_empty),
                style = CorusFont.body,
                color = CorusColors.Secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CorusSpacing.xxl),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(drafts, key = { it.id }) { draft ->
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            onDelete(draft)
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        // Error-colored reveal with an icon-only trash (Material).
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    // Fill the whole row (full height + width) so the
                                    // destructive reveal is edge-to-edge, not a skinny
                                    // strip the height of the icon.
                                    .fillMaxSize()
                                    .background(CorusColors.Error)
                                    .padding(horizontal = CorusSpacing.lg),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.compose_draft_delete_aria),
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    ) {
                        DraftRow(
                            draft = draft,
                            onResume = { onResume(draft) },
                        )
                    }
                    // No dividers — the finalized design leans on row spacing.
                }
            }
        }
        Spacer(modifier = Modifier.height(CorusSpacing.lg))
    }
}

@Composable
private fun DraftRow(
    draft: PostDraft,
    onResume: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CorusColors.Background)
            .clickable(onClick = onResume)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .defaultMinSize(minHeight = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val artURL = if (draft.mediaType == MediaType.TRACK) {
            draft.track?.albumArtURL
        } else {
            draft.movie?.posterURL
        }
        // Movie posters are 2:3; album art is square.
        val isMovie = draft.mediaType == MediaType.MOVIE
        Box(
            modifier = Modifier
                .then(if (isMovie) Modifier.size(width = 36.dp, height = 54.dp) else Modifier.size(44.dp))
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                .background(CorusColors.CardBackground),
            contentAlignment = Alignment.Center,
        ) {
            if (artURL != null) {
                AsyncImage(
                    model = artURL,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(if (isMovie) 54.dp else 44.dp),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = draftTitle(draft),
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = draftSubtitle(draft, context),
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Text(
            text = DateUtils.relativeTime(context, Date(draft.updatedAt)),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
        )
    }
}

private fun draftTitle(draft: PostDraft): String {
    if (draft.mediaType == MediaType.TRACK && draft.track != null) {
        return "${draft.track.name} · ${draft.track.artistName}"
    }
    if (draft.mediaType == MediaType.MOVIE && draft.movie != null) {
        return draft.movie.title
    }
    return "Untitled draft"
}

private fun draftSubtitle(draft: PostDraft, context: android.content.Context): String {
    val caption = draft.caption.trim()
    if (caption.isNotEmpty()) return caption
    return if (draft.captionMode == "voice") {
        context.getString(R.string.compose_draft_voice_note)
    } else {
        context.getString(R.string.compose_draft_no_caption)
    }
}

/**
 * Body of the save-on-exit confirmation sheet. Material-idiomatic flat, full-
 * width, ~56dp tappable rows in order Discard / Save draft / Cancel on an opaque
 * surface, with a small muted "Save this as a draft?" title. Discard uses the
 * error color; Save draft + Cancel use the normal on-surface color. Mirrors the
 * finalized iOS ExitDraftSheet, adapted to Material. The sheet hugs its content
 * (no dead space below the last row).
 */
@Composable
fun ExitDraftSheetContent(
    saving: Boolean,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.compose_exit_save_title),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CorusSpacing.sm),
        )
        HorizontalDivider(color = CorusColors.Divider)
        ExitRow(
            label = stringResource(R.string.compose_draft_discard),
            color = CorusColors.Error,
            enabled = !saving,
            onClick = onDiscard,
        )
        HorizontalDivider(color = CorusColors.Divider)
        ExitRow(
            label = if (saving) {
                stringResource(R.string.compose_draft_saving)
            } else {
                stringResource(R.string.compose_draft_save)
            },
            color = CorusColors.Text,
            enabled = !saving,
            showSpinner = saving,
            onClick = onSave,
        )
        HorizontalDivider(color = CorusColors.Divider)
        ExitRow(
            label = stringResource(R.string.compose_draft_cancel),
            color = CorusColors.Text,
            enabled = !saving,
            onClick = onCancel,
        )
    }
}

/** Flat, full-width, ~56dp tappable row (the whole width is the touch target). */
@Composable
private fun ExitRow(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    showSpinner: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = color,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = label,
                style = CorusFont.body,
                color = color,
            )
        }
    }
}
