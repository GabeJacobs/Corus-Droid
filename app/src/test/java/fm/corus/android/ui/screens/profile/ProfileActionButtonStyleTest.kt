package fm.corus.android.ui.screens.profile

import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Own-profile EDIT / SHARE pills: narrow phones (≤375dp) start at 11sp; wider
 * phones at 13sp (one step under CorusFont.button). Both labels then share one
 * fitted size at runtime.
 */
class ProfileActionButtonStyleTest {

    @Test
    fun miniAndSmallAndroidWidthsAreNarrow() {
        assertTrue(CorusSpacing.isNarrowProfileActionRow(320))
        assertTrue(CorusSpacing.isNarrowProfileActionRow(360))
        assertTrue(CorusSpacing.isNarrowProfileActionRow(375))
    }

    @Test
    fun standardAndLargePhonesAreNotNarrow() {
        assertFalse(CorusSpacing.isNarrowProfileActionRow(376))
        assertFalse(CorusSpacing.isNarrowProfileActionRow(390))
        assertFalse(CorusSpacing.isNarrowProfileActionRow(411))
    }

    @Test
    fun narrowWidthUsesElevenSp() {
        assertEquals(11f, profileActionButtonBaseStyle(360).fontSize.value)
    }

    @Test
    fun wideWidthUsesThirteenSp() {
        assertEquals(13f, profileActionButtonBaseStyle(390).fontSize.value)
        assertTrue(
            profileActionButtonBaseStyle(390).fontSize.value
                < CorusFont.button.fontSize.value,
        )
    }
}
