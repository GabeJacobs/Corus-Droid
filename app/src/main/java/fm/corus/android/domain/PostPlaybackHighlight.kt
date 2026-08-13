package fm.corus.android.domain

/**
 * Decides whether a feed card's play/pause (or loading) overlay reflects the
 * *active* playback — correctly, when two posts share the same song.
 *
 * The trending feed can show the same track posted by different people. Each
 * post is a distinct card sharing [postTrackId] but with its own [postId].
 * Matching the overlay on track id alone lit every duplicate at once, so the
 * pause icon (and the loading spinner) appeared on a post the user never
 * tapped. When the player knows which post started playback
 * ([activeSourcePostId]), require it to match this card. When it's unknown
 * (null — e.g. single-track playback with no originating post) fall back to a
 * track-id match so nothing regresses.
 *
 * Mirrors iOS `PostPlaybackHighlight`.
 */
object PostPlaybackHighlight {
    /**
     * @param activeTrackId the id of the track the player considers active
     *   (currently playing, or loading).
     * @param activeSourcePostId the id of the post that started that playback,
     *   or null when playback wasn't started from a specific post.
     * @param playbackActive whether the relevant state is live (playing /
     *   loading). When false the overlay is never shown.
     * @param postTrackId this card's `post.track.id`.
     * @param postId this card's `post.id`.
     */
    fun shouldHighlight(
        activeTrackId: String?,
        activeSourcePostId: String?,
        playbackActive: Boolean,
        postTrackId: String,
        postId: String,
    ): Boolean {
        if (!playbackActive || activeTrackId != postTrackId) return false
        return activeSourcePostId == null || activeSourcePostId == postId
    }

    /** Full-song playback overlay — hidden while Spotify connect/auth is in flight. */
    fun shouldShowPlayingOverlay(
        activeTrackId: String?,
        activeSourcePostId: String?,
        isPlaying: Boolean,
        isResolvingFullSong: Boolean,
        postTrackId: String,
        postId: String,
    ): Boolean = shouldHighlight(
        activeTrackId = activeTrackId,
        activeSourcePostId = activeSourcePostId,
        playbackActive = isPlaying && !isResolvingFullSong,
        postTrackId = postTrackId,
        postId = postId,
    )

    /** Preview ExoPlayer load and/or Spotify Connect auth+connect in flight. */
    fun isAlbumArtLoading(
        loadingTrackId: String?,
        loadingSourcePostId: String?,
        fullSongTrackId: String?,
        fullSongSourcePostId: String?,
        isResolvingFullSong: Boolean,
        postTrackId: String,
        postId: String,
        /** Mini Next: pill target armed on tap before identity/scroll. */
        stagedSkipPillTrackId: String? = null,
        stagedSkipPillSourcePostId: String? = null,
    ): Boolean {
        if (shouldHighlight(
                stagedSkipPillTrackId,
                stagedSkipPillSourcePostId,
                true,
                postTrackId,
                postId,
            )
        ) {
            return true
        }
        if (shouldHighlight(loadingTrackId, loadingSourcePostId, true, postTrackId, postId)) {
            return true
        }
        return shouldHighlight(
            fullSongTrackId,
            fullSongSourcePostId,
            isResolvingFullSong,
            postTrackId,
            postId,
        )
    }

    /**
     * Mini/full-player play slot: spinner from staged Next tap until audio is
     * live. Mirrors iOS `MiniPlayerBar.isResolvingCurrentTrack` — spinner wins
     * even if the outgoing track is still marked playing, so transport never
     * flashes play.fill mid-handoff.
     */
    fun showsTransportSpinner(
        stagedFeedSkipLoading: Boolean,
        isPlaying: Boolean,
        isPreviewMode: Boolean,
        loadingTrackId: String?,
        currentTrackId: String?,
        isResolvingFullSong: Boolean,
    ): Boolean {
        if (stagedFeedSkipLoading) return true
        if (isPlaying && !isPreviewMode) return false
        if (isPlaying && loadingTrackId == currentTrackId) return false
        if (loadingTrackId != null && loadingTrackId == currentTrackId) return true
        return isResolvingFullSong && !isPlaying
    }
}
