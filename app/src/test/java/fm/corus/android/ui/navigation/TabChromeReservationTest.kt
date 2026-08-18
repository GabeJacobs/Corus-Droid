package fm.corus.android.ui.navigation

import androidx.compose.ui.unit.dp
import fm.corus.android.ui.components.liftAboveReservedChrome
import org.junit.Assert.assertEquals
import org.junit.Test

class TabChromeReservationTest {

    @Test
    fun frostedContentReservesMeasuredChromeNotEmptyScaffoldBottom() {
        assertEquals(
            96.dp,
            tabContentBottomPadding(
                frosted = true,
                chromeHeight = 96.dp,
                scaffoldBottom = 0.dp,
            ),
        )
    }

    @Test
    fun frostedContentGrowsWhenMiniPlayerJoinsTheChrome() {
        val tabOnly = tabContentBottomPadding(
            frosted = true,
            chromeHeight = 72.dp,
            scaffoldBottom = 0.dp,
        )
        val tabAndMini = tabContentBottomPadding(
            frosted = true,
            chromeHeight = 128.dp,
            scaffoldBottom = 0.dp,
        )
        assertEquals(56.dp, tabAndMini - tabOnly)
    }

    @Test
    fun unfrostedContentUsesScaffoldBottom() {
        assertEquals(
            48.dp,
            tabContentBottomPadding(
                frosted = false,
                chromeHeight = 96.dp,
                scaffoldBottom = 48.dp,
            ),
        )
    }

    @Test
    fun closedKeyboardAddsNoLiftOnTopOfReservedChrome() {
        assertEquals(0.dp, liftAboveReservedChrome(ime = 0.dp, reservedChrome = 96.dp))
    }

    @Test
    fun keyboardShorterThanChromeAddsNoLift() {
        assertEquals(0.dp, liftAboveReservedChrome(ime = 40.dp, reservedChrome = 96.dp))
    }

    @Test
    fun keyboardMatchingChromeAddsNoLift() {
        assertEquals(0.dp, liftAboveReservedChrome(ime = 96.dp, reservedChrome = 96.dp))
    }

    @Test
    fun keyboardTallerThanChromeLiftsOnlyTheOverlap() {
        assertEquals(204.dp, liftAboveReservedChrome(ime = 300.dp, reservedChrome = 96.dp))
    }
}
