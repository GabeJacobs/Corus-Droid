package fm.corus.android.domain

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** OAuth secrets and user access tokens stay in the existing authenticated backend. */
@Singleton
class AudiomackAuthService @Inject constructor(
    @ApplicationContext context: Context,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val remote: RemoteConfigService,
) {
    private val preferences = context.getSharedPreferences("audiomack_connection", Context.MODE_PRIVATE)
    private val mutableConnected = MutableStateFlow(false)
    val connected = mutableConnected.asStateFlow()
    init { auth.addAuthStateListener { mutableConnected.value = false } }
    @Suppress("UNCHECKED_CAST")
    private suspend fun call(name: String, payload: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val uid = auth.currentUser?.uid ?: error("Please sign in.")
        val result = functions.getHttpsCallable(name).call(payload).await().getData() as? Map<String, Any?> ?: error("Invalid Audiomack response.")
        check(auth.currentUser?.uid == uid) { "Your Corus account changed." }
        return result
    }
    suspend fun refresh() { if(!remote.audiomackStreamingEnabled) return; try { mutableConnected.value = call("audiomackSessionStatus")["connected"] == true } catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException) throw e } }
    suspend fun connect(context: Context) {
        check(remote.audiomackStreamingEnabled) { "Audiomack is not available yet." }
        val uid = auth.currentUser?.uid ?: error("Please sign in.")
        val result = call("audiomackOAuthStart")
        val url = Uri.parse(result["authorizeUrl"] as? String ?: error("Missing sign-in URL."))
        check(url.scheme == "https" && url.host == "audiomack.com") { "Invalid sign-in URL." }
        val token = url.getQueryParameter("oauth_token") ?: error("Missing request token.")
        preferences.edit().putString("uid",uid).putString("token",token).putLong("started",System.currentTimeMillis()).apply()
        CustomTabsIntent.Builder().build().launchUrl(context,url)
    }
    suspend fun finish(uri: Uri) {
        check(remote.audiomackStreamingEnabled) { "Audiomack is not available yet." }
        val uid = auth.currentUser?.uid ?: error("Please sign in.")
        val token = uri.getQueryParameter("oauth_token")
        val verifier = uri.getQueryParameter("oauth_verifier")
        check(uri.scheme == "corus" && uri.host == "audiomack-auth" && !token.isNullOrEmpty() && !verifier.isNullOrEmpty() && preferences.getString("uid",null) == uid && preferences.getString("token",null) == token && System.currentTimeMillis()-preferences.getLong("started",0) in 0..600_000) { "Audiomack sign-in expired. Connect again in Settings." }
        call("audiomackOAuthFinish",mapOf("oauthToken" to token,"oauthVerifier" to verifier))
        preferences.edit().clear().apply(); mutableConnected.value = true
    }
    suspend fun disconnect() { call("audiomackOAuthDisconnect"); preferences.edit().clear().apply(); mutableConnected.value = false }
    suspend fun stream(track: QueuedTrack): String? {
        check(remote.audiomackStreamingEnabled) { "Audiomack is not available yet." }
        val mapping = call("audiomackLookup", mapOf("audiomackId" to track.trackId.takeIf { it.startsWith("amk:") }?.removePrefix("amk:"),"audiomackUrl" to track.audiomackUrl,"trackId" to track.trackId,"name" to track.trackName,"artist" to track.artistName,"isrc" to track.isrc))
        val id = mapping["audiomackId"] as? String ?: return null
        val result = call("resolveAudiomackPlay",mapOf("audiomackId" to id))
        if(result["needsReauth"] == true) { mutableConnected.value = false; error("Connect Audiomack in Settings to play full songs.") }
        val stream = result["streamUrl"] as? String ?: return null
        return stream.takeIf { result["playable"] == true && Uri.parse(it).scheme == "https" }
    }
}
