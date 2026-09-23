package fm.corus.android.ui.screens.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapCityPeopleSheetTest {
    @Test
    fun heightUsesPeekUntilOffsetIsReady() {
        assertEquals(520, mapCitySheetHeightPx(Float.NaN, 1000))
    }

    @Test
    fun expandedHeightLeavesATopGapInsteadOfFillingTheMap() {
        assertEquals(120, mapCitySheetExpandedOffsetPx(1000f).toInt())
        assertEquals(880, mapCitySheetHeightPx(offset = 0f, maxHeight = 1000))
        assertEquals(880, mapCitySheetHeightPx(offset = 120f, maxHeight = 1000))
    }

    @Test
    fun leftoverFlingDoesNotMoveSheetAfterListScroll() {
        assertEquals(false, mapCitySheetTakesLeftoverFling(listConsumedVelocityY = 800f))
        assertEquals(true, mapCitySheetTakesLeftoverFling(listConsumedVelocityY = 0f))
    }

    @Test
    fun downwardFlingAdvancesExactlyOneDetent() {
        assertEquals(MapCitySheetValue.Peek, mapCitySheetNextDown(MapCitySheetValue.Expanded))
        assertEquals(MapCitySheetValue.Hidden, mapCitySheetNextDown(MapCitySheetValue.Peek))
    }
}
