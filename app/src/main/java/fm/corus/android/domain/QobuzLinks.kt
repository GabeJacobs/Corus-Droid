package fm.corus.android.domain

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import fm.corus.android.data.model.CymbalTrack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** Shared public-link cache. Cached confirmed links; explicit taps can resolve or open provider search. */
object QobuzLinks {
    data class Entry(val url: String?, val expiresAtMs: Long)
    private val mutableEntries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val entries = mutableEntries.asStateFlow()
    private val pending = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val mutex = Mutex()
    private val requestMutex = Mutex()
    private fun key(track: CymbalTrack) = listOf(track.isrc, track.name, track.artistName, track.albumName, track.durationMs).joinToString("\u001f")
    private val enabled: Boolean get() = FirebaseAuth.getInstance().currentUser != null &&
        FirebaseRemoteConfig.getInstance().getBoolean("qobuz_enabled")

    fun url(track: CymbalTrack): String? {
        if (!enabled) return null
        return mutableEntries.value[key(track)]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.url
    }

    suspend fun destination(track: CymbalTrack): String? {
        if (!enabled) return null
        url(track)?.let { return it }
        resolve(track, onTap = true)
        if (!enabled) return null
        return url(track)
    }

    suspend fun resolve(track: CymbalTrack, onTap: Boolean = false) {
        if (!enabled || track.isrc.isNullOrBlank() || track.durationMs <= 0) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val identity = key(track)
        var ownsRequest = false
        val work = mutex.withLock {
            if ((mutableEntries.value[identity]?.expiresAtMs ?: 0) > System.currentTimeMillis() && (!onTap || mutableEntries.value[identity]?.url != null)) return
            pending[identity] ?: CompletableDeferred<Unit>().also { pending[identity] = it; ownsRequest = true }
        }
        if (!ownsRequest) { work.await(); return }
        var ownsSlot = false
        try {
            requestMutex.lock()
            ownsSlot = true
            if (!enabled || FirebaseAuth.getInstance().currentUser?.uid != uid) return
            val response = FirebaseFunctions.getInstance().getHttpsCallable("qobuzLookup").call(mapOf(
                "isrc" to track.isrc, "name" to track.name, "artist" to track.artistName,
                "albumName" to track.albumName, "durationMs" to track.durationMs,
                "cacheOnly" to !onTap, "resolveOnTap" to onTap,
            )).await().getData() as? Map<*, *> ?: return
            if (!enabled || FirebaseAuth.getInstance().currentUser?.uid != uid || response["disabled"] == true) return
            val url = (response["qobuzURL"] as? String)?.takeIf { it.matches(Regex("https://open\\.qobuz\\.com/track/[1-9][0-9]*")) }
            val expiry = (response["expiresAtMs"] as? Number)?.toLong() ?: (System.currentTimeMillis() + 60_000)
            mutableEntries.value = mutableEntries.value + (identity to Entry(url, expiry))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Retry transport failures soon; never treat one as a catalog absence.
            mutableEntries.value = mutableEntries.value + (identity to Entry(null, System.currentTimeMillis() + 60_000))
        } finally {
            if (ownsSlot) requestMutex.unlock()
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                mutex.withLock { pending.remove(identity) }
                work.complete(Unit)
            }
        }
    }
}
