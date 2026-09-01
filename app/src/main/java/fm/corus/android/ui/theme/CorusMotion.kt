package fm.corus.android.ui.theme

object CorusMotion {
    const val DURATION_FAST = 150
    const val DURATION_NORMAL = 200
    const val DURATION_SLOW = 500
    const val SHIMMER_DURATION = 750
    const val SHIMMER_STAGGER = 80
    /** Album-art / poster fade over a still-shimmering bone. Matches iOS. */
    const val IMAGE_REVEAL_MS = 200
    /**
     * After Search becomes the selected tab, wait this long before the
     * post-preview fan-out so the tab-switch frame stays smooth. Matches iOS.
     */
    const val SEARCH_LIVE_LOAD_DELAY_MS = 350L
}
