package fm.corus.android.ui.components

import android.app.Application
import android.content.Context
import android.view.ViewConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FullScreenPhotoViewerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val photo = ExpandedPhoto(url = "file:///nonexistent/photo.jpg", subjectName = "Tame Impala")

    private val closeLabel: String
        get() = context.getString(R.string.full_screen_image_cd_close)

    private val touchSlop: Float
        get() = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private fun setViewer(onDismiss: () -> Unit = {}) {
        composeRule.setContent {
            FullScreenPhotoViewer(photo = photo, onDismiss = onDismiss)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `null photo renders nothing`() {
        composeRule.setContent {
            FullScreenPhotoViewer(photo = null, onDismiss = {})
        }

        composeRule.onNodeWithContentDescription(closeLabel).assertDoesNotExist()
    }

    @Test
    fun `close button invokes onDismiss`() {
        var dismissed = false
        setViewer(onDismiss = { dismissed = true })

        composeRule.onNodeWithContentDescription(closeLabel).performClick()

        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun `single tap dismisses once the double-tap window elapses`() {
        var dismissed = false
        setViewer(onDismiss = { dismissed = true })
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput {
            down(center)
            up()
        }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertTrue(dismissed)
    }

    @Test
    fun `jittered tap with sub-slop wobble still dismisses`() {
        var dismissed = false
        setViewer(onDismiss = { dismissed = true })
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(touchSlop / 4f, 0f))
            up()
        }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertTrue(dismissed)
    }

    @Test
    fun `past-slop drag never dismisses`() {
        var dismissed = false
        setViewer(onDismiss = { dismissed = true })
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(touchSlop * 6f, 0f))
            up()
        }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertFalse(dismissed)
    }

    @Test
    fun `sub-slop pinch never dismisses`() {
        var dismissed = false
        setViewer(onDismiss = { dismissed = true })
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput {
            down(0, center - Offset(60f, 0f))
            down(1, center + Offset(60f, 0f))
            moveBy(0, Offset(touchSlop / 4f, 0f))
            up(0)
            up(1)
        }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertFalse(dismissed)
    }

    @Test
    fun `zero-move pinch never dismisses`() {
        var dismissed = false
        setViewer(onDismiss = { dismissed = true })
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput {
            down(0, center - Offset(60f, 0f))
            down(1, center + Offset(60f, 0f))
            up(0)
            up(1)
        }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertFalse(dismissed)
    }

    @Test
    fun `double tap never dismisses`() {
        var dismissed = false
        setViewer(onDismiss = { dismissed = true })
        composeRule.mainClock.autoAdvance = false

        composeRule.onRoot().performTouchInput { doubleClick() }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertFalse(dismissed)
    }

    @Test
    fun `image node carries the Photo of subject description`() {
        setViewer()

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.photo_viewer_photo_of, "Tame Impala"))
            .assertExists()
    }

    @Test
    fun `image stack fills the viewer root`() {
        setViewer()

        val imageBounds = composeRule
            .onNodeWithContentDescription(context.getString(R.string.photo_viewer_photo_of, "Tame Impala"))
            .getUnclippedBoundsInRoot()
        val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()

        assertEquals(rootBounds, imageBounds)
    }
}
