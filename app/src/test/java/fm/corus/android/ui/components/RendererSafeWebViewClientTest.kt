package fm.corus.android.ui.components

import android.app.Application
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.ui.screens.map.MapPreviewWebViewCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RendererSafeWebViewClientTest {
    @Test fun rendererMemoryKillIsHandledWithoutTerminatingTheApp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val parent = FrameLayout(context)
        val view = WebView(context)
        parent.addView(view)
        val detail = mock<RenderProcessGoneDetail>()
        whenever(detail.didCrash()).thenReturn(false)

        assertTrue(RendererSafeWebViewClient().onRenderProcessGone(view, detail))

        assertNull(view.parent)
        assertTrue(shadowOf(view).wasDestroyCalled())
    }

    @Test fun cachedMapRendererIsEvictedAndCannotBeRetainedAgain() {
        val view = WebView(ApplicationProvider.getApplicationContext<Application>())
        MapPreviewWebViewCache.put("preview:test-recovery", view)
        val client = RendererSafeWebViewClient { MapPreviewWebViewCache.remove(it) }

        assertTrue(client.onRenderProcessGone(view, mock()))

        assertNull(MapPreviewWebViewCache.take("preview:test-recovery"))
        MapPreviewWebViewCache.put("preview:test-recovery", view)
        assertNull(MapPreviewWebViewCache.take("preview:test-recovery"))
        assertTrue(view.rendererGone)
    }

    @Test fun sharedRendererFailureCleansUpEachViewAndNotifiesOnce() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val views = listOf(WebView(context), WebView(context))
        val notified = mutableListOf<WebView>()
        val client = RendererSafeWebViewClient { view ->
            assertNull(view.parent)
            assertTrue(shadowOf(view).wasDestroyCalled())
            notified += view
        }
        val detail = mock<RenderProcessGoneDetail>()
        whenever(detail.didCrash()).thenReturn(true)

        views.forEach { assertTrue(client.onRenderProcessGone(it, detail)) }
        assertTrue(client.onRenderProcessGone(views.first(), detail))

        assertEquals(views, notified)
    }
}
