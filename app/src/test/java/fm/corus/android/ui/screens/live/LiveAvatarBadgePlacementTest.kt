package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w393dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveAvatarBadgePlacementTest {
    @get:Rule val compose = createComposeRule()
    @Test fun liveBadgeFollowsGuestDJInSharedRoomPill() {
        val room = LiveRoom("room", mapOf("hostId" to "host", "hostUsername" to "host",
            "currentDJ" to mapOf("uid" to "guest", "displayName" to "guest"),
            "djs" to listOf(mapOf("uid" to "guest", "displayName" to "guest"))))
        var view: android.view.View? = null
        compose.setContent { view = LocalView.current; CorusTheme { Box(Modifier.size(300.dp, 60.dp)) {
            LiveDiscoveryPill(room, 3, false, {}, {})
        } } }
        val pixels = compose.runOnIdle { Bitmap.createBitmap(view!!.width, view!!.height, Bitmap.Config.ARGB_8888).also { view!!.draw(Canvas(it)) } }
        var sum = 0f; var count = 0
        // Below the avatar rings, only the blue badge fill occupies this strip.
        for (y in 39 until minOf(46, pixels.height)) for (x in 0 until minOf(80, pixels.width)) {
            val c = pixels.getPixel(x,y)
            if (Color.red(c) in 50..95 && Color.green(c) in 85..145 && Color.blue(c) in 170..230) { sum += x; count++ }
        }
        assertTrue("The LIVE badge must be rendered", count > 5)
        assertEquals("Badge must sit under the guest, not the group center", 48f, sum / count, 5f)
    }
}
