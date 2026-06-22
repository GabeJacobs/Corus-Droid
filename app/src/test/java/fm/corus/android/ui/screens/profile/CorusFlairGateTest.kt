package fm.corus.android.ui.screens.profile

import fm.corus.android.data.model.FlairStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate logic for the staff-only "Corus" flair picker option
 * ([shouldShowCorusFlairOption]). The flair is shown when the viewer is staff,
 * the open flag is on, OR it's the current selection (grace-period clause).
 */
class CorusFlairGateTest {

    @Test fun `open flag on shows Corus for non-staff`() {
        assertTrue(shouldShowCorusFlairOption(isStaff = false, corusFlairOpen = true, selected = FlairStyle.CHECKMARK))
    }

    @Test fun `staff sees Corus even when flag is off`() {
        assertTrue(shouldShowCorusFlairOption(isStaff = true, corusFlairOpen = false, selected = FlairStyle.CHECKMARK))
    }

    @Test fun `non-staff with flag off does not see Corus`() {
        assertFalse(shouldShowCorusFlairOption(isStaff = false, corusFlairOpen = false, selected = FlairStyle.CHECKMARK))
    }

    @Test fun `existing holder keeps seeing Corus during phase-out`() {
        // Flag off, not staff, but currently selected -> still shown so the
        // selection isn't blanked.
        assertTrue(shouldShowCorusFlairOption(isStaff = false, corusFlairOpen = false, selected = FlairStyle.CORUS_LOGO))
    }

    @Test fun `non-holder switching away cannot get it back once gated off`() {
        // Selection moved to a non-Corus flair; with flag off and non-staff,
        // the option disappears.
        assertFalse(shouldShowCorusFlairOption(isStaff = false, corusFlairOpen = false, selected = FlairStyle.VINYL))
    }

    @Test fun `staff and flag and selection all true is shown`() {
        assertTrue(shouldShowCorusFlairOption(isStaff = true, corusFlairOpen = true, selected = FlairStyle.CORUS_LOGO))
    }
}
