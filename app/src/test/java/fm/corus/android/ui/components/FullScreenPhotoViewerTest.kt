package fm.corus.android.ui.components

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import fm.corus.android.R
import org.junit.After
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

    private val artistPhoto = ExpandedPhoto(url = "https://i.scdn.co/image/ab6761610000e5eb", subjectName = "Tame Impala")

    private val directorPhoto = ExpandedPhoto(url = "https://image.tmdb.org/t/p/h632/denis.jpg", subjectName = "Denis Villeneuve")

    private val closeLabel: String
        get() = context.getString(R.string.full_screen_image_cd_close)

    private val errorText: String
        get() = context.getString(R.string.photo_viewer_error)

    private val touchSlop: Float
        get() = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val spinner = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate,
    )

    private fun setViewer(photo: ExpandedPhoto? = this.photo, onDismiss: () -> Unit = {}) {
        composeRule.setContent {
            FullScreenPhotoViewer(photo = photo, onDismiss = onDismiss)
        }
        composeRule.waitForIdle()
    }

    @OptIn(DelicateCoilApi::class)
    private fun installSynchronousLoader(result: (ImageRequest) -> ImageResult) {
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components {
                    add(object : Interceptor {
                        override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
                            result(chain.request)
                    })
                }
                .build(),
        )
    }

    private fun photoImage() =
        Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).asImage()

    @OptIn(DelicateCoilApi::class)
    @After
    fun resetImageLoader() {
        SingletonImageLoader.reset()
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

    @Test
    fun `artist photo load leaves no spinner`() {
        installSynchronousLoader { request ->
            SuccessResult(photoImage(), request, DataSource.MEMORY_CACHE)
        }

        setViewer(photo = artistPhoto)

        composeRule.onNode(spinner).assertDoesNotExist()
        composeRule.onNodeWithText(errorText).assertDoesNotExist()
    }

    @Test
    fun `director photo and upgrade load leaves no spinner`() {
        installSynchronousLoader { request ->
            SuccessResult(photoImage(), request, DataSource.MEMORY_CACHE)
        }

        setViewer(photo = directorPhoto)

        composeRule.onNode(spinner).assertDoesNotExist()
        composeRule.onNodeWithText(errorText).assertDoesNotExist()
    }

    @Test
    fun `director base photo with failed upgrade leaves no spinner`() {
        installSynchronousLoader { request ->
            if (request.data.toString().contains("/original/")) {
                ErrorResult(null, request, Exception("original unavailable"))
            } else {
                SuccessResult(photoImage(), request, DataSource.MEMORY_CACHE)
            }
        }

        setViewer(photo = directorPhoto)

        composeRule.onNode(spinner).assertDoesNotExist()
        composeRule.onNodeWithText(errorText).assertDoesNotExist()
    }

    @Test
    fun `terminal failure shows the error text and no spinner`() {
        installSynchronousLoader { request ->
            ErrorResult(null, request, Exception("nothing loadable"))
        }

        setViewer(photo = artistPhoto)

        composeRule.onNode(spinner).assertDoesNotExist()
        composeRule.onNodeWithText(errorText).assertExists()
    }

    @Test
    fun `reopen during the exit fade keeps a delivered photo spinner-free`() {
        installSynchronousLoader { request ->
            SuccessResult(photoImage(), request, DataSource.MEMORY_CACHE)
        }
        var photoState by mutableStateOf<ExpandedPhoto?>(artistPhoto)
        composeRule.setContent {
            FullScreenPhotoViewer(photo = photoState, onDismiss = {})
        }
        composeRule.waitForIdle()
        composeRule.onNode(spinner).assertDoesNotExist()

        composeRule.mainClock.autoAdvance = false
        photoState = null
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        photoState = artistPhoto
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(closeLabel).assertExists()
        composeRule.onNode(spinner).assertDoesNotExist()
        composeRule.onNodeWithText(errorText).assertDoesNotExist()
    }

    @Test
    fun `reopen during the exit fade keeps the terminal error state`() {
        installSynchronousLoader { request ->
            ErrorResult(null, request, Exception("nothing loadable"))
        }
        var photoState by mutableStateOf<ExpandedPhoto?>(artistPhoto)
        composeRule.setContent {
            FullScreenPhotoViewer(photo = photoState, onDismiss = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(errorText).assertExists()

        composeRule.mainClock.autoAdvance = false
        photoState = null
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        photoState = artistPhoto
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNode(spinner).assertDoesNotExist()
        composeRule.onNodeWithText(errorText).assertExists()
    }
}
