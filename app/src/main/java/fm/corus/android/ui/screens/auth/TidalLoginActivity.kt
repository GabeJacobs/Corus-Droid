package fm.corus.android.ui.screens.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.view.ViewGroup
import fm.corus.android.ui.components.RendererSafeWebViewClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import fm.corus.android.domain.TidalAuthService
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts the TIDAL login web page (authorization-code + PKCE) and intercepts the
 * `corus://tidal-auth` redirect, handing it to [TidalAuthService] to finish the
 * OAuth exchange. Mirrors TIDAL's official Android sample (a WebView with
 * redirect interception) rather than Custom Tabs, so we never touch the app's
 * existing `corus://` deep-link routing.
 */
@AndroidEntryPoint
class TidalLoginActivity : ComponentActivity() {

    @Inject lateinit var tidalAuth: TidalAuthService

    private var handled = false
    private var loginWebView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL)
        if (loginUrl.isNullOrEmpty()) {
            finish()
            return
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : RendererSafeWebViewClient({
                loginWebView = null
                finish()
            }) {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    maybeHandleRedirect(request.url)

                @Deprecated("Deprecated in Java")
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    maybeHandleRedirect(Uri.parse(url))
            }
        }
        loginWebView = webView
        setContentView(webView)
        webView.loadUrl(loginUrl)
    }

    private fun maybeHandleRedirect(uri: Uri): Boolean {
        if (handled || !uri.toString().startsWith(TidalAuthService.REDIRECT_URI)) return false
        handled = true
        lifecycleScope.launch {
            tidalAuth.handleRedirect(uri)
            finish()
        }
        return true
    }

    override fun onDestroy() {
        loginWebView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        loginWebView = null
        // Closed without capturing a redirect (back press / dismissed) → release
        // the suspended login() so the caller stops waiting.
        if (!handled) tidalAuth.cancelPendingLogin()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LOGIN_URL = "loginUrl"
    }
}
