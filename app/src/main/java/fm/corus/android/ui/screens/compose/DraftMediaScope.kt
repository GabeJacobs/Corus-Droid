package fm.corus.android.ui.screens.compose

import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.PostDraft

internal fun draftsForMediaType(drafts: List<PostDraft>, mediaType: MediaType?): List<PostDraft> =
    if (mediaType == null) emptyList() else drafts.filter { it.mediaType == mediaType }
