package fm.corus.android.ui.screens.map

internal const val MAP_PLAYBACK_CARD_CORNER_RADIUS_DP = 18
internal const val MAP_PLAYBACK_CARD_SURFACE_ALPHA = 1f
internal const val MAP_WATCH_CAPTION_MAX_LINES = 2
internal const val MAP_WATCH_HEADER_CLEARANCE_DP = 8
internal const val MAP_WATCH_CARD_MAX_HEIGHT_DP = 520
internal const val MAP_LISTEN_CARD_MAX_HEIGHT_DP = 480
internal const val MAP_WATCH_NAVIGATION_TOP_PADDING_DP = 2
internal const val MAP_WATCH_NAVIGATION_TARGET_DP = 48
internal const val MAP_PLAYBACK_CLOSE_TARGET_DP = 48
internal const val MAP_PLAYBACK_CLOSE_VISUAL_DP = 32
internal const val MAP_PLAYBACK_CLOSE_CARD_GAP_DP = 6
internal const val MAP_PLAYBACK_CLOSE_RESERVE_DP = MAP_PLAYBACK_CLOSE_VISUAL_DP + MAP_PLAYBACK_CLOSE_CARD_GAP_DP
internal const val MAP_PLAYBACK_CARD_HORIZONTAL_SCREEN_INSET_DP = 12
internal const val MAP_PLAYBACK_CLOSE_TARGET_TRAILING_OVERHANG_DP =
    (MAP_PLAYBACK_CLOSE_TARGET_DP - MAP_PLAYBACK_CLOSE_VISUAL_DP) / 2
internal const val MAP_PLAYBACK_CLOSE_TARGET_SCREEN_INSET_DP =
    MAP_PLAYBACK_CARD_HORIZONTAL_SCREEN_INSET_DP - MAP_PLAYBACK_CLOSE_TARGET_TRAILING_OVERHANG_DP
internal const val MAP_FILTER_SELECTED_HEIGHT_DP = 34
internal const val MAP_FILTER_UNSELECTED_HEIGHT_DP = 32

internal enum class MapWatchCardSection { PREVIEW_BANNER, SCROLLABLE_CONTENT, PINNED_NAVIGATION_FOOTER }
internal val MAP_WATCH_CARD_SECTIONS = listOf(
    MapWatchCardSection.PREVIEW_BANNER,
    MapWatchCardSection.SCROLLABLE_CONTENT,
    MapWatchCardSection.PINNED_NAVIGATION_FOOTER,
)

internal fun mapPlaybackCardMaxHeight(viewportHeightDp: Int, headerHeightDp: Int, capDp: Int): Int =
    (viewportHeightDp - headerHeightDp - MAP_WATCH_HEADER_CLEARANCE_DP)
        .coerceAtLeast(0).coerceAtMost(capDp)

internal fun mapWatchCardMaxHeight(viewportHeightDp: Int, headerHeightDp: Int): Int =
    mapPlaybackCardMaxHeight(viewportHeightDp, headerHeightDp, MAP_WATCH_CARD_MAX_HEIGHT_DP)
