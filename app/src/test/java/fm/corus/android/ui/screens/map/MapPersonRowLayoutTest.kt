package fm.corus.android.ui.screens.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapPersonRowLayoutTest {
    @Test fun memberWithoutPostShowsUsernameOnly() {
        assertEquals(MapPersonRowLayout.USERNAME_ONLY, mapPersonRowLayout(hasPost = false))
    }

    @Test fun memberWithRealPostKeepsLatestPostDetails() {
        assertEquals(MapPersonRowLayout.LATEST_POST, mapPersonRowLayout(hasPost = true))
    }
}
