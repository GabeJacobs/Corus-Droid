package fm.corus.android.ui.screens.messaging

import android.content.Context
import fm.corus.android.R
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MessageSendStatus
import fm.corus.android.data.model.MessageType
import fm.corus.android.data.model.ShareRecipient
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.localization.CorusStrings
import fm.corus.android.ui.components.ToastManager
import java.util.UUID

internal fun MessageRepository.queueSongShare(
    context: Context, sender: String, recipient: ShareRecipient, track: CymbalTrack, caption: String,
) {
    val message = CymbalMessage(
        id = UUID.randomUUID().toString(),
        threadId = "",
        fromUserId = sender,
        text = caption,
        type = MessageType.SHARED_TRACK,
        sendStatus = MessageSendStatus.SENDING,
        trackId = track.id,
        trackName = track.name,
        artistName = track.artistName,
        albumName = track.albumName,
        albumArtURL = track.albumArtURL,
        albumArtLargeURL = track.albumArtLargeURL,
        spotifyURI = track.spotifyURI,
        spotifyURL = track.spotifyWebURL,
        previewUrl = track.previewUrl,
        isrc = track.isrc,
        durationMs = track.durationMs,
        trackSource = track.source.raw,
        soundcloudId = track.soundcloudId,
        soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
    )
    queueShareWithFeedback(context, recipient, message, track.name) { threadId, messageId ->
        sendSharedTrackMessage(threadId, sender, caption, track, messageId)
    }
}

internal fun MessageRepository.queuePostShare(
    context: Context, sender: String, recipient: ShareRecipient, post: CymbalPost, caption: String,
) {
    val message = CymbalMessage(
        id = UUID.randomUUID().toString(),
        threadId = "",
        fromUserId = sender,
        text = caption,
        type = MessageType.SHARED_POST,
        sendStatus = MessageSendStatus.SENDING,
        sharedPostId = post.id,
    )
    queueShareWithFeedback(context, recipient, message, post.movieTitle ?: post.track.name) { threadId, messageId ->
        sendSharedPostMessage(threadId, sender, post.id, caption, messageId)
    }
}

private fun MessageRepository.queueShareWithFeedback(
    context: Context,
    recipient: ShareRecipient,
    message: CymbalMessage,
    preview: String,
    deliver: suspend (String, String) -> Unit,
) {
    var toastId = 0L
    queueShare(
        destinationId = recipient.id,
        recipient = recipient,
        message = message,
        preview = preview,
        deliver = deliver,
        onStart = { toastId = ToastManager.showLoading(context.getString(R.string.messaging_thread_sending)) },
        onSent = {
            ToastManager.dismiss(toastId)
            ToastManager.show(context.getString(CorusStrings.share_toast_message_sent))
        },
        onFailure = { error ->
            ToastManager.dismiss(toastId)
            showDmSendError(context, error, R.string.feed_toast_failed_send_post)
        },
    )
}
