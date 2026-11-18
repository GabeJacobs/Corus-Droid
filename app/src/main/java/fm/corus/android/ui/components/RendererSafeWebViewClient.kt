package fm.corus.android.ui.components

import android.util.Log
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient

private const val RENDERER_GONE_TAG = 0x43575247

internal val WebView.rendererGone: Boolean
    get() = getTag(RENDERER_GONE_TAG) == true

/** A dead web renderer must not take down native playback or be reused. */
internal open class RendererSafeWebViewClient(
    private val onRendererGone: (WebView) -> Unit = {},
) : WebViewClient() {
    final override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        if (!view.rendererGone) {
            view.setTag(RENDERER_GONE_TAG, true)
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            onRendererGone(view)
            Log.w("WebViewRecovery", "Recovered renderer exit; crashed=${detail.didCrash()}, priority=${detail.rendererPriorityAtExit()}")
        }
        return true
    }
}
