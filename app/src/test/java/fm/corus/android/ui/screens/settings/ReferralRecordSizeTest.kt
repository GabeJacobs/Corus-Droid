package fm.corus.android.ui.screens.settings

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ReferralRecordSizeTest {
    @Test fun `paid inviter sees a full LP instead of an ineligible growth EP`() {
        assertEquals(220.dp, referralRecordSize(220.dp, 0, reward = false))
    }
    @Test fun `eligible inviter grows from EP to LP within the available space`() {
        assertEquals(116.dp, referralRecordSize(220.dp, 0, reward = true))
        assertEquals(220.dp, referralRecordSize(220.dp, 5, reward = true))
        assertEquals(80.dp, referralRecordSize(80.dp, 0, reward = true))
    }
}
