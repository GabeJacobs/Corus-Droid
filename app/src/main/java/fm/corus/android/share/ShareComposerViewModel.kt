package fm.corus.android.share

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CommentsAudience
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.components.ToastManager
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State machine for the share-to-Corus sheet:
 * shared text → parse link → resolve → caption → createPost → confirmation.
 *
 * KEEP IN SYNC with the iOS extension's ShareComposerModel (same phases,
 * same copy, same never-block rules) and with ComposeViewModel's createPost
 * payload (the wire shape MUST stay key-identical so share posts are
 * indistinguishable from in-app posts).
 */
@HiltViewModel
class ShareComposerViewModel @Inject constructor(
    private val resolver: ShareResolver,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val remoteConfigService: RemoteConfigService,
    private val analyticsService: AnalyticsService,
    private val postCreationEvent: PostCreationEvent,
) : ViewModel() {

    enum class BlockedReason { NOT_SIGNED_IN, UNSUPPORTED_LINK, SONG_UNAVAILABLE, ALBUM_UNAVAILABLE, NOT_ON_CORUS, UNRELEASED }

    sealed interface Phase {
        data object Loading : Phase
        data object Resolving : Phase
        data object LoadingAlbum : Phase
        data object AlbumPicker : Phase
        data object Ready : Phase
        data object Posting : Phase
        data class Posted(val isFirstPoster: Boolean) : Phase
        data class Blocked(val reason: BlockedReason) : Phase
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Loading)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _track = MutableStateFlow<CymbalTrack?>(null)
    val track: StateFlow<CymbalTrack?> = _track.asStateFlow()

    private val _album = MutableStateFlow<ShareAlbum?>(null)
    val album: StateFlow<ShareAlbum?> = _album.asStateFlow()

    private val _caption = MutableStateFlow("")
    val caption: StateFlow<String> = _caption.asStateFlow()

    private val _commentsAudience = MutableStateFlow(CommentsAudience.EVERYONE)
    val commentsAudience: StateFlow<CommentsAudience> = _commentsAudience.asStateFlow()

    /** Synthetic post backing the reused TrophyCelebrationView. */
    private val _trophyPost = MutableStateFlow<CymbalPost?>(null)
    val trophyPost: StateFlow<CymbalPost?> = _trophyPost.asStateFlow()

    val commentControlsEnabled: Boolean get() = remoteConfigService.commentControlsOnPosts

    /** True when the composer was reached from an album picker (back chevron). */
    val cameFromAlbum: Boolean get() = _album.value != null

    private var pendingText: String? = null
    private var link: SharedMusicLink? = null
    private var started = false

    /**
     * Apple songs paint instantly from a provisional iTunes lookup while the
     * full canonical resolution finishes here in the background; post()
     * awaits it only if still in flight (usually it isn't).
     */
    /** Set for TIDAL/Deezer provisionals: the payload's trackSource + id the
     * BACKEND resolves from (CymbalTrack's enum can't hold these sources). */
    private var shareInputSource: String? = null
    private var shareInputId: String? = null

    companion object {
        const val CAPTION_LIMIT = 1000
        const val CAPTION_COUNTER_THRESHOLD = 950
    }

    fun setCaption(value: String) {
        _caption.value = if (value.length > CAPTION_LIMIT) value.take(CAPTION_LIMIT) else value
    }

    fun setCommentsAudience(value: CommentsAudience) {
        _commentsAudience.value = value
    }

    /** Entry point — called once with the ACTION_SEND text. */
    fun start(sharedText: String?) {
        if (started) return
        started = true
        pendingText = sharedText
        // Funnel top: how many share sheets open (pair with postedVia counts
        // for the open → post conversion).
        analyticsService.logEvent("share_sheet_opened")
        begin()
    }

    /** Re-runs the whole flow; recovers from any blocked state. */
    fun retry() = begin()

    private fun begin() {
        val text = pendingText
        shareInputSource = null
        shareInputId = null
        if (authRepository.currentUserId == null) {
            _phase.value = Phase.Blocked(BlockedReason.NOT_SIGNED_IN)
            return
        }
        val url = text?.let { SharedMusicLink.firstUrlIn(it) }
        if (url == null) {
            _phase.value = Phase.Blocked(BlockedReason.UNSUPPORTED_LINK)
            return
        }
        val parsed = SharedMusicLink.parse(url)
        when {
            parsed != null -> route(parsed)
            SharedMusicLink.isShortLink(url) -> {
                _phase.value = Phase.Resolving
                viewModelScope.launch {
                    val expanded = resolver.expandShortLink(url)?.let { SharedMusicLink.parse(it) }
                    if (expanded != null) route(expanded) else {
                        _phase.value = Phase.Blocked(BlockedReason.UNSUPPORTED_LINK)
                    }
                }
            }
            else -> _phase.value = Phase.Blocked(BlockedReason.UNSUPPORTED_LINK)
        }
    }

    private fun route(parsed: SharedMusicLink) {
        link = parsed
        when (parsed) {
            is SharedMusicLink.AppleMusicAlbum -> loadCatalogAlbum("am:${parsed.albumId}")
            is SharedMusicLink.SpotifyAlbum -> loadCatalogAlbum(parsed.albumId)
            is SharedMusicLink.DeezerAlbum -> loadDeezerAlbum(parsed.id)
            is SharedMusicLink.TidalAlbum -> loadTidalAlbum(parsed.id)
            is SharedMusicLink.AppleMusicSong -> resolveAppleInstant(parsed)
            is SharedMusicLink.DeezerTrack ->
                startProvisional(source = "deezer", externalId = parsed.id) { resolver.deezerTrackMetadata(parsed.id) }
            is SharedMusicLink.TidalTrack ->
                startProvisional(source = "tidal", externalId = parsed.id) { resolver.tidalTrackMetadata(parsed.id) }
            else -> resolveBlocking(parsed)
        }
    }

    // ── Songs ──────────────────────────────────────────────────────────────

    /** Apple: card paints from ONE public lookup; the full canonical
     * resolution runs in the background while the caption is typed. */
    private fun resolveAppleInstant(song: SharedMusicLink.AppleMusicSong) {
        _phase.value = Phase.Resolving
        viewModelScope.launch {
            val provisional = resolver.itunesLookup(song.id, song.storefront)
            if (provisional != null) {
                // Directly postable am: track — the backend's born-with-spotify
                // resolve canonicalizes it at post time.
                presentReady(provisional)
            } else {
                resolveBlockingSuspend(song)
            }
        }
    }

    /** TIDAL/Deezer: the card is the provisional the sharer is looking at
     * (their service's metadata + art). The BACKEND resolves the canonical
     * identity at post time; the sheet never cross-references catalogs. */
    private fun startProvisional(
        source: String,
        externalId: String,
        fetch: suspend () -> CymbalTrack?,
    ) {
        _phase.value = Phase.Resolving
        viewModelScope.launch {
            val provisional = fetch()
            if (provisional == null) {
                _phase.value = Phase.Blocked(BlockedReason.SONG_UNAVAILABLE)
                return@launch
            }
            shareInputSource = source
            shareInputId = externalId
            presentReady(provisional)
        }
    }

    /** Single gate every track passes on its way into the composer: Corus
     * posts released material only, so a future release date blocks the
     * sheet right here (the createPost gate remains the server backstop). */
    private fun presentReady(candidate: CymbalTrack) {
        if (isFutureRelease(candidate.releaseDate)) {
            _phase.value = Phase.Blocked(BlockedReason.UNRELEASED)
            return
        }
        _track.value = candidate
        _phase.value = Phase.Ready
    }

    /** True when dateString (YYYY-MM-DD…) is comfortably in the future (48h
     * buffer absorbs timezone skew) — mirrors the server gate. */
    private fun isFutureRelease(dateString: String?): Boolean {
        if (dateString == null || dateString.length < 10) return false
        return try {
            val date = java.time.LocalDate.parse(dateString.take(10))
            val threshold = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(2)
            date.isAfter(threshold)
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveBlocking(parsed: SharedMusicLink) {
        _phase.value = Phase.Resolving
        viewModelScope.launch { resolveBlockingSuspend(parsed) }
    }

    private suspend fun resolveBlockingSuspend(parsed: SharedMusicLink) {
        val resolved = resolver.resolveSong(parsed)
        if (resolved != null) {
            presentReady(resolved)
        } else {
            _phase.value = Phase.Blocked(BlockedReason.SONG_UNAVAILABLE)
        }
    }

    // ── Album picker ───────────────────────────────────────────────────────

    private fun loadCatalogAlbum(albumId: String) {
        _phase.value = Phase.LoadingAlbum
        viewModelScope.launch {
            val loaded = resolver.fetchCatalogAlbum(albumId)
            if (loaded != null) {
                _album.value = loaded
                _phase.value = Phase.AlbumPicker
            } else {
                _phase.value = Phase.Blocked(BlockedReason.ALBUM_UNAVAILABLE)
            }
        }
    }

    private fun loadDeezerAlbum(id: String) {
        _phase.value = Phase.LoadingAlbum
        viewModelScope.launch {
            val loaded = resolver.fetchDeezerAlbum(id)
            if (loaded != null) {
                if (isFutureRelease(loaded.releaseDate)) {
                    _phase.value = Phase.Blocked(BlockedReason.UNRELEASED)
                    return@launch
                }
                _album.value = loaded
                _phase.value = Phase.AlbumPicker
            } else {
                _phase.value = Phase.Blocked(BlockedReason.ALBUM_UNAVAILABLE)
            }
        }
    }

    /** TIDAL albums via the tidalGetAlbum callable; rows carry ISRCs and
     * cross-resolve on tap with no extra fetch. */
    private fun loadTidalAlbum(id: String) {
        _phase.value = Phase.LoadingAlbum
        viewModelScope.launch {
            val loaded = resolver.fetchTidalAlbum(id)
            if (loaded != null) {
                if (isFutureRelease(loaded.releaseDate)) {
                    _phase.value = Phase.Blocked(BlockedReason.UNRELEASED)
                    return@launch
                }
                _album.value = loaded
                _phase.value = Phase.AlbumPicker
            } else {
                _phase.value = Phase.Blocked(BlockedReason.ALBUM_UNAVAILABLE)
            }
        }
    }

    /** Row tap on the album page. Catalog rows (Apple/Spotify) are already
     * postable; Deezer/TIDAL rows become the provisional the sharer is
     * looking at (row metadata + this album's art) and the BACKEND resolves
     * their identity at post time. Every row enters the composer instantly. */
    fun selectAlbumTrack(albumTrack: ShareAlbumTrack) {
        albumTrack.preResolved?.let {
            presentReady(it)
            return
        }
        val album = _album.value ?: return
        _track.value = CymbalTrack(
            id = albumTrack.id,
            name = albumTrack.name,
            artistName = albumTrack.artistName,
            albumName = album.title,
            albumArtURL = album.coverUrl,
            albumArtLargeURL = album.coverUrl,
            durationMs = albumTrack.durationMs,
            isrc = albumTrack.lazyIsrc?.ifEmpty { null },
        )
        if (albumTrack.deezerTrackId != null) {
            shareInputSource = "deezer"
            shareInputId = albumTrack.deezerTrackId
        } else {
            shareInputSource = "tidal"
            shareInputId = albumTrack.id
        }
        _phase.value = Phase.Ready
    }

    /** Back chevron from the composer: return to the album page to pick a
     * different song. Keeps any caption already written. */
    fun backToAlbum() {
        if (!cameFromAlbum || _phase.value !is Phase.Ready) return
        _track.value = null
        _phase.value = Phase.AlbumPicker
    }

    // ── Post ───────────────────────────────────────────────────────────────

    fun post(limitMessage: String, hardCapMessage: String, bannedMessage: String, genericMessage: String) {
        val current = _track.value ?: return
        if (_phase.value != Phase.Ready) return
        _phase.value = Phase.Posting
        val userId = authRepository.currentUserId
        if (userId == null) {
            _phase.value = Phase.Blocked(BlockedReason.NOT_SIGNED_IN)
            return
        }
        val captionText = _caption.value

        viewModelScope.launch {
            // Land the background canonical resolution: use its result if it
            // finished (or wait under the posting spinner if not). A failed
            // resolution posts the provisional apple-only track rather than
            // blocking — the display never swaps (no flicker).
            val postTrack = current

            try {
                // KEEP IN SYNC with ComposeViewModel.createPost's track branch —
                // key-identical payload so share posts are indistinguishable
                // from in-app posts. (timeZone is stamped by the data source.)
                val payload = mutableMapOf<String, Any?>()
                payload["mediaType"] = MediaType.TRACK.value
                payload["caption"] = captionText
                // Attribution: backend stamps postedVia on the post doc so
                // share-sheet posts are countable (server allowlists values).
                payload["entryPoint"] = "share_android"
                val hashtagRegex = Regex("#(\\w+)")
                payload["hashtags"] = hashtagRegex.findAll(captionText).map { it.groupValues[1] }.toList()

                val pickedAudience = _commentsAudience.value
                if (remoteConfigService.commentControlsOnPosts && pickedAudience != CommentsAudience.EVERYONE) {
                    payload["commentsAudience"] = pickedAudience.wire
                }

                val trackMap = mutableMapOf<String, Any?>(
                    "trackId" to postTrack.id,
                    "trackName" to postTrack.name,
                    "artistName" to postTrack.artistName,
                    "artistIds" to postTrack.artistIds,
                    "albumName" to postTrack.albumName,
                    "albumArtURL" to (postTrack.albumArtURL ?: ""),
                    "albumArtLargeURL" to (postTrack.albumArtLargeURL ?: ""),
                    "durationMs" to postTrack.durationMs,
                    // TIDAL/Deezer provisionals ship their real source; the
                    // backend converts to a canonical identity before writing.
                    "trackSource" to (shareInputSource ?: postTrack.source.raw),
                )
                when (shareInputSource) {
                    "tidal" -> trackMap["tidalId"] = shareInputId ?: ""
                    "deezer" -> trackMap["deezerId"] = shareInputId ?: ""
                }
                postTrack.isrc?.let { trackMap["isrc"] = it }
                postTrack.releaseDate?.let { trackMap["trackReleaseDate"] = it }
                postTrack.releaseDatePrecision?.let { trackMap["trackReleaseDatePrecision"] = it }
                postTrack.previewUrl?.let { trackMap["previewUrl"] = it }
                if (postTrack.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD) {
                    trackMap["soundcloudId"] = postTrack.soundcloudId ?: ""
                    trackMap["soundcloudPermalinkUrl"] = postTrack.soundcloudPermalinkUrl ?: ""
                }
                if (postTrack.source == fm.corus.android.data.model.TrackSource.AUDIOMACK) {
                    trackMap["audiomackId"] = postTrack.audiomackId ?: ""
                    trackMap["audiomackUrl"] = postTrack.audiomackUrl ?: ""
                    trackMap["audiomackArtistUrl"] = postTrack.audiomackArtistUrl ?: ""
                    trackMap["audiomackAlbumUrl"] = postTrack.audiomackAlbumUrl ?: ""
                }
                payload["track"] = trackMap

                val result = postRepository.createPost(payload, null)
                analyticsService.logPostCreated(
                    MediaType.TRACK.value,
                    trackId = postTrack.id,
                    hasHashtags = (payload["hashtags"] as List<*>).isNotEmpty(),
                    isFirstPoster = result.isFirstPoster,
                )
                analyticsService.logEvent("share_sheet_posted")
                subscriptionRepository.incrementPostCount()
                authRepository.bumpCymbalCount(1)
                // Same signal ComposeViewModel fires: Feed + Profile listen on
                // this singleton bus and refresh themselves, so the new post is
                // already there when the user lands in the app (no manual pull).
                postCreationEvent.notifyPostCreated(MediaType.TRACK)

                if (result.isFirstPoster) {
                    _trophyPost.value = CymbalPost(
                        id = "",
                        user = CymbalUser(id = userId, username = "", displayName = ""),
                        track = postTrack,
                        mediaType = MediaType.TRACK,
                        isFirstPoster = true,
                    )
                }
                _phase.value = Phase.Posted(isFirstPoster = result.isFirstPoster)
            } catch (e: CloudFunctionsDataSource.PostLimitReachedException) {
                _phase.value = Phase.Ready
                ToastManager.show(if (e.hardCap) hardCapMessage else limitMessage)
            } catch (e: CloudFunctionsDataSource.PostingBannedException) {
                _phase.value = Phase.Ready
                ToastManager.show(bannedMessage)
            } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
                when {
                    e.message?.contains("no_catalog_match") == true ->
                        // Server ran the full Spotify + Apple chain, nothing postable.
                        _phase.value = Phase.Blocked(BlockedReason.NOT_ON_CORUS)
                    e.message?.contains("unreleased") == true ->
                        // Server backstop for the sheet-open unreleased gate.
                        _phase.value = Phase.Blocked(BlockedReason.UNRELEASED)
                    else -> {
                        Log.e("ShareComposerVM", "createPost failed", e)
                        _phase.value = Phase.Ready
                        ToastManager.show(genericMessage)
                    }
                }
            } catch (e: Exception) {
                Log.e("ShareComposerVM", "createPost failed", e)
                _phase.value = Phase.Ready
                ToastManager.show(genericMessage)
            }
        }
    }
}
