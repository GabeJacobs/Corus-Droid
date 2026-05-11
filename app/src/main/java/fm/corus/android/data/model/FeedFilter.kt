package fm.corus.android.data.model

/**
 * Unified feed filter spanning the media-type narrowing AND the
 * "new releases only" toggle. The home feed presents these as five
 * mutually-exclusive options under a single dropdown.
 */
enum class FeedFilter {
    ALL,
    MUSIC,
    FILM,
    MUSIC_NEW_RELEASES,
    FILM_NEW_RELEASES;

    val isAll: Boolean get() = this == ALL

    /** The mediaType param sent to the `getFeedPage` callable, or null for ALL. */
    val mediaType: MediaType?
        get() = when (this) {
            ALL -> null
            MUSIC, MUSIC_NEW_RELEASES -> MediaType.TRACK
            FILM, FILM_NEW_RELEASES -> MediaType.MOVIE
        }

    /** Whether the server-side new-releases filter should be applied. */
    val newReleasesOnly: Boolean
        get() = this == MUSIC_NEW_RELEASES || this == FILM_NEW_RELEASES

    /**
     * Stable, cross-platform value sent as the `filter` parameter of the
     * `feed_filter_changed` analytics event. Must match the iOS `analyticsValue`
     * and the Web `feedFilterToAnalyticsValue` output so GA4 reports can compare
     * platforms.
     */
    val analyticsValue: String
        get() = when (this) {
            ALL -> "all"
            MUSIC -> "music"
            FILM -> "film"
            MUSIC_NEW_RELEASES -> "music_new_releases"
            FILM_NEW_RELEASES -> "film_new_releases"
        }
}
