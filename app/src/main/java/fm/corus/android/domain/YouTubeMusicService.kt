package fm.corus.android.domain

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.data.model.MusicService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfig: RemoteConfigService,
    private val musicPreference: MusicServicePreference,
) {
    private val auth get() = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance("us-central1")
    private val preferences = context.getSharedPreferences("youtube_music", Context.MODE_PRIVATE)
    private val client get() = Identity.getAuthorizationClient(context)
    private var launch: ((IntentSenderRequest) -> Unit)? = null
    private var pending: CompletableDeferred<AuthorizationResult>? = null
    private val mutex = Mutex()
    private var token: String? = null
    private var tokenUid: String? = null
    private var expires = 0L
    val enabled get() = remoteConfig.youtubeMusicIntegrationEnabled
    val syncEnabled get() = auth.currentUser?.uid?.let { preferences.getBoolean("$it.sync", false) } ?: false

    fun attach(activity: ComponentActivity) {
        val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            try {
                val data: Intent = result.data ?: error("Google connection was cancelled.")
                pending?.complete(client.getAuthorizationResultFromIntent(data))
            } catch (e: Exception) { pending?.completeExceptionally(e) }
        }
        launch = { launcher.launch(it) }
    }
    fun detach() { launch = null; pending?.cancel(); pending = null }

    suspend fun accessToken(interactive: Boolean, force: Boolean = false): String = withContext(Dispatchers.Main) {
        check(enabled) { "YouTube Music is not available yet." }
        val uid = auth.currentUser?.uid ?: error("Sign in to Corus first.")
        if (!force && tokenUid == uid && expires > System.currentTimeMillis()) return@withContext token!!
        val linked = preferences.getString("$uid.channel", null)
        check(interactive || linked != null) { "Reconnect YouTube Music in Settings." }
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/youtube.force-ssl"))).build()
        var result = client.authorize(request).await()
        if (result.hasResolution()) {
            check(interactive && launch != null && pending == null) { "Reconnect YouTube Music in Settings." }
            val deferred = CompletableDeferred<AuthorizationResult>()
            pending = deferred
            try {
                launch!!(IntentSenderRequest.Builder(result.pendingIntent!!).build())
                result = deferred.await()
            } finally { pending = null }
        }
        val value = result.accessToken ?: error("YouTube permission was not granted.")
        check(uid == auth.currentUser?.uid) { "Your Corus account changed. Try again." }
        val data = functions.getHttpsCallable("connectYouTubeMusic").call(mapOf("accessToken" to value)).await().getData() as Map<*, *>
        val channel = data["channelId"] as String
        check(interactive || linked == channel) { "Your YouTube account changed. Reconnect in Settings." }
        check(uid == auth.currentUser?.uid) { "Your Corus account changed. Try again." }
        preferences.edit().putString("$uid.channel", channel).apply()
        token = value; tokenUid = uid; expires = System.currentTimeMillis() + 45 * 60000
        value
    }

    suspend fun setSync(value: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        if (value) accessToken(true)
        check(uid == auth.currentUser?.uid) { "Your account changed." }
        preferences.edit().putBoolean("$uid.sync", value).apply()
        if (value) flush()
    }
    fun disconnect() {
        val uid = auth.currentUser?.uid ?: return
        preferences.edit().remove("$uid.sync").remove("$uid.channel").remove("$uid.queue").apply()
        token = null; tokenUid = null; expires = 0
    }
    suspend fun generate(name: String, params: Map<String, Any>): Map<*, *> {
        val uid = auth.currentUser?.uid
        val value = accessToken(true)
        check(uid == auth.currentUser?.uid) { "Your account changed." }
        return functions.getHttpsCallable(name).apply { setTimeout(540, TimeUnit.SECONDS) }
            .call(params + mapOf("youtubeAccessToken" to value, "supportsPlaylistGating" to true)).await().getData() as Map<*, *>
    }
    private fun queue(uid: String): List<String> = try {
        val a = JSONArray(preferences.getString("$uid.queue", "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (_: Exception) { emptyList() }
    suspend fun saved(postId: String) {
        if (!enabled || !syncEnabled || musicPreference.current.value != MusicService.YOUTUBE_MUSIC) return
        val uid = auth.currentUser?.uid ?: return
        mutex.withLock { preferences.edit().putString("$uid.queue", JSONArray((queue(uid) + postId).distinct()).toString()).apply() }
        flush()
    }
    suspend fun flush() = mutex.withLock {
        if (!enabled || !syncEnabled || musicPreference.current.value != MusicService.YOUTUBE_MUSIC) return@withLock
        val uid = auth.currentUser?.uid ?: return@withLock
        val value = try { accessToken(false) } catch (_: Exception) { return@withLock }
        for (postId in queue(uid)) {
            if (uid != auth.currentUser?.uid || !syncEnabled) break
            try { functions.getHttpsCallable("addSavedSongToYouTubeMusic").call(mapOf("postId" to postId, "accessToken" to value)).await() }
            catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
                if (e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.UNAUTHENTICATED) { token = null; expires = 0 }
                if (e.code != com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND && e.code != com.google.firebase.functions.FirebaseFunctionsException.Code.PERMISSION_DENIED) break
            } catch (_: Exception) { break }
            preferences.edit().putString("$uid.queue", JSONArray(queue(uid).filter { it != postId }).toString()).apply()
        }
    }
}
