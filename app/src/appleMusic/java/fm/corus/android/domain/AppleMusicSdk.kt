package fm.corus.android.domain

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.apple.android.music.playback.controller.MediaPlayerController
import com.apple.android.music.playback.controller.MediaPlayerControllerFactory
import com.apple.android.music.playback.model.MediaItemType
import com.apple.android.music.playback.model.MediaPlayerException
import com.apple.android.music.playback.model.PlaybackState
import com.apple.android.music.playback.model.PlayerQueueItem
import com.apple.android.music.playback.queue.CatalogPlaybackQueueItemProvider
import com.apple.android.music.playback.queue.PlaybackModeContainer
import com.apple.android.music.playback.queue.PlaybackQueueInsertionType.INSERTION_TYPE_AFTER_CURRENT_ITEM
import com.apple.android.music.playback.queue.PlaybackQueueItemsAdded
import com.apple.android.sdk.authentication.AuthenticationFactory
import com.apple.android.sdk.authentication.TokenProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

internal fun isAppleMusicSdkAvailable(): Boolean =
    when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a", "armeabi-v7a" -> true
        else -> false
    }

internal fun createAppleMusicSdk(context: Context): AppleMusicPlatform? =
    if (isAppleMusicSdkAvailable()) NativeAppleMusic(context) else null

private class NativeAppleMusic(private val context: Context) : AppleMusicPlatform {
    private val authManager = AuthenticationFactory.createAuthenticationManager(context)
    private val playbackLibraries by lazy {
        System.loadLibrary("c++_shared")
        System.loadLibrary("appleMusicSDK")
    }

    override fun authorizationIntent(developerToken: String): Intent =
        authManager.createIntentBuilder(developerToken).build()

    override fun authorizationResult(data: Intent): AppleMusicAuthorization? {
        val result = authManager.handleTokenResult(data)
        if (result.isError) return null
        val userToken = result.musicUserToken?.takeIf { it.isNotBlank() } ?: return null
        val storefront = result.storefrontId?.lowercase()?.takeIf { it.matches(Regex("[a-z]{2}")) }
            ?: return null
        return AppleMusicAuthorization(userToken, storefront, result.appleMusicStorefrontId)
    }

    override fun createPlayer(
        developerToken: String,
        authorization: AppleMusicAuthorization,
        songId: String,
        mediaItem: MediaItem,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        onAuthorizationLost: () -> Unit,
    ): Player {
        val tokens = object : TokenProvider {
            override fun getDeveloperToken(): String = developerToken
            override fun getUserToken(): String = authorization.userToken
            override fun getAppleMusicStoreFrontId(): String? = authorization.encodedStorefront
        }
        playbackLibraries
        val controller = MediaPlayerControllerFactory.createLocalController(context, tokens)
        return AppleMusicPlayer(controller, songId, mediaItem, onNext, onPrevious, onAuthorizationLost)
    }
}

/** Adapts the MusicKit stream to Corus's existing Media3 session and system transport. */
@OptIn(UnstableApi::class)
private class AppleMusicPlayer(
    private val controller: MediaPlayerController,
    private var songId: String,
    private var mediaItem: MediaItem,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onAuthorizationLost: () -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()), AppleMusicLiveQueue {
    private val handler = Handler(Looper.getMainLooper())
    private var released = false
    private var prepared = false
    private var ended = false
    private var started = false
    private var playRequested = false
    private var error: PlaybackException? = null
    private data class NextItem(
        val afterQueueId: Long,
        val songId: String,
        val mediaItem: MediaItem,
        val revision: Long,
    )

    private var next: NextItem? = null
    private var playingStaged: NextItem? = null
    private var currentQueueId: Long? = null
    private var ownsLiveQueue = false

    override fun stageNext(songId: String?, revision: Long, mediaItem: MediaItem?) {
        if (released) return
        val current = controller.currentItem ?: return
        val replacement = if (songId != null && mediaItem != null) {
            NextItem(current.playbackQueueId, songId, mediaItem, revision)
        } else null
        if (replacement == next) return

        val queued = controller.queueItems
        val currentIndex = queued.indexOfFirst { it.playbackQueueId == current.playbackQueueId }
        if (currentIndex < 0) return
        val upcoming = queued.drop(currentIndex + 1)
        val alreadyQueued = replacement?.let { desired ->
            upcoming.firstOrNull()?.takeIf { it.matchesSong(desired.songId) }
        }
        upcoming.filter { it.playbackQueueId != alreadyQueued?.playbackQueueId }
            .forEach { controller.removeQueueItemWithId(it.playbackQueueId) }
        next = replacement
        ownsLiveQueue = true
        if (replacement != null && alreadyQueued == null) {
            val queue = CatalogPlaybackQueueItemProvider.Builder().items(MediaItemType.SONG, replacement.songId).build()
            controller.addQueueItems(queue, INSERTION_TYPE_AFTER_CURRENT_ITEM)
        }
    }

    private fun PlayerQueueItem.matchesSong(id: String): Boolean =
        item.playbackStoreId == id || item.subscriptionStoreId == id

    override fun hasStagedNext(mediaId: String, revision: Long): Boolean {
        val staged = next?.takeIf { it.mediaItem.mediaId == mediaId && it.revision == revision } ?: return false
        val items = controller.queueItems
        val index = items.indexOfFirst { it.playbackQueueId == staged.afterQueueId }
        return index >= 0 && items.getOrNull(index + 1)?.matchesSong(staged.songId) == true
    }

    override fun stagedPosition(mediaId: String, revision: Long): Long? =
        controller.currentPosition.coerceAtLeast(0).takeIf {
            !released && playingStaged?.revision == revision && playingStaged?.mediaItem?.mediaId == mediaId &&
                controller.playbackState == PlaybackState.PLAYING && !controller.isBuffering
        }


    private val listener = object : MediaPlayerController.Listener {
        override fun onPlaybackStateChanged(player: MediaPlayerController, previous: Int, current: Int) {
            when (current) {
                PlaybackState.PLAYING -> {
                    started = true
                    playRequested = true
                }
                PlaybackState.PAUSED -> if (started) playRequested = false
                PlaybackState.STOPPED -> if (started && !ended) playRequested = false
            }
            invalidateState()
        }

        override fun onPlaybackStateUpdated(player: MediaPlayerController) = invalidateState()
        override fun onBufferingStateChanged(player: MediaPlayerController, buffering: Boolean) = invalidateState()
        override fun onCurrentItemChanged(player: MediaPlayerController, previous: PlayerQueueItem?, current: PlayerQueueItem?) {
            val earlierId = previous?.playbackQueueId ?: currentQueueId
            if (current != null) currentQueueId = current.playbackQueueId
            val staged = next
            if (current != null && staged != null && earlierId == staged.afterQueueId &&
                current.playbackQueueId != earlierId && current.matchesSong(staged.songId)
            ) {
                playingStaged = staged
                next = null
                mediaItem = staged.mediaItem
                songId = staged.songId
                ended = false
            } else if (ownsLiveQueue && current != null && earlierId != null && current.playbackQueueId != earlierId) {
                // A queue edit can race MusicKit starting an old entry.
                playingStaged = null
                ended = true
                playRequested = false
                controller.pause()
            }
            invalidateState()
        }
        override fun onMetadataUpdated(player: MediaPlayerController, item: PlayerQueueItem) = invalidateState()

        override fun onItemEnded(player: MediaPlayerController, item: PlayerQueueItem, endPosition: Long) {
            if (currentQueueId?.let { it != item.playbackQueueId } == true ||
                next?.let { it.afterQueueId == item.playbackQueueId && hasStagedNext(it.mediaItem.mediaId, it.revision) } == true
            ) return
            ended = true
            playRequested = false
            invalidateState()
        }

        override fun onPlaybackError(player: MediaPlayerController, cause: MediaPlayerException) {
            if (cause.type == MediaPlayerException.TYPE_MUSIC_USER_TOKEN_ERROR ||
                cause.type == MediaPlayerException.TYPE_USER_SUBSCRIPTION_INVALID
            ) onAuthorizationLost()
            error = PlaybackException("Apple Music playback failed", cause, PlaybackException.ERROR_CODE_UNSPECIFIED)
            playRequested = false
            invalidateState()
        }

        override fun onPlayerStateRestored(player: MediaPlayerController) = Unit
        override fun onPlaybackQueueChanged(player: MediaPlayerController, items: MutableList<PlayerQueueItem>) = Unit
        override fun onPlaybackQueueItemsAdded(player: MediaPlayerController, items: PlaybackQueueItemsAdded) = Unit
        override fun onPlaybackModeChanged(player: MediaPlayerController, mode: PlaybackModeContainer) = Unit
        override fun onPlaybackRepeatModeChanged(player: MediaPlayerController, mode: Int) = Unit
        override fun onPlaybackShuffleModeChanged(player: MediaPlayerController, mode: Int) = Unit
    }

    init {
        controller.addListener(listener)
    }

    override fun getState(): State {
        if (released) return State.Builder().build()
        val duration = controller.duration.takeIf { it > 0 } ?: C.TIME_UNSET
        val seekable = controller.canSeek() && duration != C.TIME_UNSET
        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA, Player.COMMAND_RELEASE,
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
            .addIf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, seekable)
            .build()
        val state = when {
            error != null || !prepared -> Player.STATE_IDLE
            ended -> Player.STATE_ENDED
            !started || controller.isBuffering -> Player.STATE_BUFFERING
            else -> Player.STATE_READY
        }
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(listOf(
                MediaItemData.Builder(mediaItem.mediaId)
                    .setMediaItem(mediaItem)
                    .setDurationUs(if (duration == C.TIME_UNSET) C.TIME_UNSET else duration * 1_000)
                    .setIsSeekable(seekable)
                    .build(),
            ))
            .setPlaybackState(state)
            .setPlayWhenReady(playRequested, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlayerError(error)
            .setContentPositionMs(PositionSupplier { controller.currentPosition.coerceAtLeast(0) })
            .build()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        prepared = true
        started = false
        ended = false
        next = null
        playingStaged = null
        currentQueueId = null
        ownsLiveQueue = false
        val queue = CatalogPlaybackQueueItemProvider.Builder().items(MediaItemType.SONG, songId).build()
        controller.prepare(queue, playRequested)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playRequested = playWhenReady
        if (prepared) {
            if (playWhenReady && ended) return handlePrepare()
            if (playWhenReady) controller.play() else controller.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                handler.post { if (!released) onNext() }
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                handler.post { if (!released) onPrevious() }
            else -> controller.seekToPosition(positionMs.coerceAtLeast(0))
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        prepared = false
        playRequested = false
        controller.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        controller.removeListener(listener)
        controller.stop()
        controller.release()
        return Futures.immediateVoidFuture()
    }
}
