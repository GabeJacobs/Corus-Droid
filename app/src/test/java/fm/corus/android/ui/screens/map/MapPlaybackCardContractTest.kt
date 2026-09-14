package fm.corus.android.ui.screens.map

import org.junit.Assert.*
import org.junit.Test

class MapPlaybackCardContractTest {
    @Test fun engagementUsesIntrinsicLeadingGroupAndTrailingSave() {
        assertEquals(MapEngagementWidthMode.ANCHORED, mapEngagementWidthMode(400, hasCatalog = true))
        assertEquals(MapEngagementWidthMode.ANCHORED, mapEngagementWidthMode(320, hasCatalog = false))
        assertNull(visibleMapEngagementCount(0))
        assertEquals("19", visibleMapEngagementCount(19))
        assertEquals(MapEngagementWidthMode.HORIZONTAL_SCROLL, mapEngagementWidthMode(400, hasCatalog = false, visibleCountCharacters = 12))
        assertTrue(mapEngagementKeepsTrailingSaveFixed(MapEngagementWidthMode.ANCHORED))
        assertTrue(mapEngagementKeepsTrailingSaveFixed(MapEngagementWidthMode.HORIZONTAL_SCROLL))
    }

    @Test fun engagementSpacingIsIndependentOfCountsAndCatalog() {
        assertEquals(MAP_ENGAGEMENT_ITEM_GAP_DP, mapEngagementVisibleItemGapDp(0))
        assertEquals(MAP_ENGAGEMENT_ITEM_GAP_DP, mapEngagementVisibleItemGapDp(7))
        assertEquals(MAP_ENGAGEMENT_ITEM_GAP_DP, mapEngagementVisibleItemGapDp(112))
        assertEquals(12, MAP_ENGAGEMENT_ITEM_GAP_DP)
        assertEquals(5, MAP_ENGAGEMENT_ICON_COUNT_GAP_DP)
    }

    @Test fun narrowRowsFallBackToHorizontalScrolling() {
        assertEquals(MapEngagementWidthMode.HORIZONTAL_SCROLL, mapEngagementWidthMode(350, hasCatalog = true))
        assertEquals(MapEngagementWidthMode.HORIZONTAL_SCROLL, mapEngagementWidthMode(300, hasCatalog = false))
    }

    @Test fun largerTextCanUseTheSameEffectiveWidthFallback() {
        val physicalWidth = 400
        val effectiveWidthAtTwoXText = (physicalWidth / 2f).toInt()
        assertEquals(MapEngagementWidthMode.HORIZONTAL_SCROLL, mapEngagementWidthMode(effectiveWidthAtTwoXText, hasCatalog = false))
    }

    @Test fun cardIsOpaqueWithReducedRadius() {
        assertEquals(1f, MAP_PLAYBACK_CARD_SURFACE_ALPHA)
        assertEquals(18, MAP_PLAYBACK_CARD_CORNER_RADIUS_DP)
        assertTrue(MAP_PLAYBACK_CARD_CORNER_RADIUS_DP < 22)
    }

    @Test fun watchCardFitsBelowHeaderAndUsesCompactNavigation() {
        assertEquals(2, MAP_WATCH_CAPTION_MAX_LINES)
        assertEquals(2, MAP_WATCH_NAVIGATION_TOP_PADDING_DP)
        assertEquals(520, mapWatchCardMaxHeight(800, 160))
        assertEquals(432, mapWatchCardMaxHeight(600, 160))
        assertTrue(mapWatchCardMaxHeight(600, 160) <= 600 - 160 - MAP_WATCH_HEADER_CLEARANCE_DP)
        assertEquals(48, MAP_WATCH_NAVIGATION_TARGET_DP)
        assertEquals(
            listOf(MapWatchCardSection.PREVIEW_BANNER, MapWatchCardSection.SCROLLABLE_CONTENT, MapWatchCardSection.PINNED_NAVIGATION_FOOTER),
            MAP_WATCH_CARD_SECTIONS,
        )
    }

    @Test fun closeTargetIsFullySeparatedAboveBothPlaybackCards() {
        assertEquals(48, MAP_PLAYBACK_CLOSE_TARGET_DP)
        assertEquals(32, MAP_PLAYBACK_CLOSE_VISUAL_DP)
        assertEquals(6, MAP_PLAYBACK_CLOSE_CARD_GAP_DP)
        assertEquals(MAP_PLAYBACK_CLOSE_VISUAL_DP + MAP_PLAYBACK_CLOSE_CARD_GAP_DP, MAP_PLAYBACK_CLOSE_RESERVE_DP)
        assertEquals(8, MAP_PLAYBACK_CLOSE_TARGET_TRAILING_OVERHANG_DP)
        assertEquals(4, MAP_PLAYBACK_CLOSE_TARGET_SCREEN_INSET_DP)
        assertEquals(
            MAP_PLAYBACK_CARD_HORIZONTAL_SCREEN_INSET_DP,
            MAP_PLAYBACK_CLOSE_TARGET_TRAILING_OVERHANG_DP + MAP_PLAYBACK_CLOSE_TARGET_SCREEN_INSET_DP,
        )
        assertEquals(480, mapPlaybackCardMaxHeight(900, 160, MAP_LISTEN_CARD_MAX_HEIGHT_DP))
        assertTrue(mapPlaybackCardMaxHeight(600, 160, MAP_LISTEN_CARD_MAX_HEIGHT_DP) <= 600 - 160 - MAP_WATCH_HEADER_CLEARANCE_DP)
    }

    @Test fun mapFilterPillsAreSlightlyShorterWithoutLosingSelectionHierarchy() {
        assertEquals(34, MAP_FILTER_SELECTED_HEIGHT_DP)
        assertEquals(32, MAP_FILTER_UNSELECTED_HEIGHT_DP)
        assertTrue(MAP_FILTER_SELECTED_HEIGHT_DP > MAP_FILTER_UNSELECTED_HEIGHT_DP)
        assertTrue(MAP_FILTER_SELECTED_HEIGHT_DP < 38)
    }
}
