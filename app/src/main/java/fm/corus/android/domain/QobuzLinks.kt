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

/** Only confirmed HTTPS tracks can open. Display checks never start catalog matching. */
object QobuzLinks {
    data class Entry(val url: String?, val expiresAtMs: Long, val notFound: Boolean = false)
    private val mutableEntries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val entries = mutableEntries.asStateFlow()
    private val mutableResolving = MutableStateFlow(false)
    val isResolving = mutableResolving.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    fun dismissMessage() { mutableMessage.value = null }
    fun reportFailure() { mutableMessage.value = "Couldn’t check Qobuz. Please try again." }
    private val pending = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val mutex = Mutex()
    private val requestMutex = Mutex()
    private val tapMutex = Mutex()
    val enabled: Boolean get() = runCatching {
        FirebaseAuth.getInstance().currentUser != null &&
            FirebaseRemoteConfig.getInstance().getBoolean("qobuz_enabled")
    }.getOrDefault(false)
    private fun key(track: CymbalTrack) = listOf(FirebaseAuth.getInstance().currentUser?.uid,
        track.isrc, track.name, track.artistName, track.albumName, track.durationMs).joinToString("\u001f")

    fun url(track: CymbalTrack): String? {
        if (!enabled) return null
        if (track.qobuzMatchStatus == "matched") track.qobuzMatchInput?.confirmedUrl(track)?.let { return it }
        return mutableEntries.value[key(track)]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.url
    }

    suspend fun destination(track: CymbalTrack): String? = tapMutex.withLock {
        if (!enabled) return@withLock null
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        mutableMessage.value = null
        url(track)?.let { return@withLock it }
        mutableResolving.value = true
        try {
            resolve(track, onTap = true)
            if (!enabled || FirebaseAuth.getInstance().currentUser?.uid != uid) return@withLock null
            url(track)?.let { return@withLock it }
            val entry = mutableEntries.value[key(track)]
            mutableMessage.value = if (entry?.notFound == true && entry.expiresAtMs > System.currentTimeMillis())
                "This song couldn’t be found on Qobuz." else "Couldn’t check Qobuz. Please try again."
            null
        } finally { mutableResolving.value = false }
    }

    suspend fun resolve(track: CymbalTrack, onTap: Boolean = false) {
        if (!enabled || track.isrc.isNullOrBlank() || track.durationMs <= 0 || url(track) != null) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val identity = key(track)
        // A tap must not inherit an in-flight cache-only pending result.
        val workKey = identity + if (onTap) "\u001ftap" else "\u001fdisplay"
        var ownsRequest = false
        val work = mutex.withLock {
            val entry = mutableEntries.value[identity]
            if (entry != null && entry.expiresAtMs > System.currentTimeMillis() && (!onTap || entry.notFound)) return
            pending[workKey] ?: CompletableDeferred<Unit>().also { pending[workKey] = it; ownsRequest = true }
        }
        if (!ownsRequest) { work.await(); return }
        var ownsSlot = false
        try {
            requestMutex.lock(); ownsSlot = true
            if (!enabled || FirebaseAuth.getInstance().currentUser?.uid != uid || url(track) != null) return
            val response = FirebaseFunctions.getInstance().getHttpsCallable("qobuzLookup").call(mapOf(
                "isrc" to track.isrc, "name" to track.name, "artist" to track.artistName,
                "albumName" to track.albumName, "durationMs" to track.durationMs,
                "cacheOnly" to !onTap, "resolveOnTap" to onTap,
            )).await().getData() as? Map<*, *> ?: return
            if (!enabled || FirebaseAuth.getInstance().currentUser?.uid != uid || response["disabled"] == true) return
            mutableEntries.value = mutableEntries.value + (identity to entryFromResponse(response, System.currentTimeMillis()))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) {
            if (FirebaseAuth.getInstance().currentUser?.uid == uid)
                mutableEntries.value = mutableEntries.value + (identity to Entry(null, System.currentTimeMillis() + 60_000))
        } finally {
            if (ownsSlot) requestMutex.unlock()
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                mutex.withLock { pending.remove(workKey) }; work.complete(Unit)
            }
        }
    }

    internal fun entryFromResponse(response: Map<*, *>, now: Long): Entry {
        val raw = response["qobuzURL"] as? String
        val url = raw?.takeIf { it.matches(Regex("https://open\\.qobuz\\.com/track/[1-9][0-9]*")) }
        val expiry = (response["expiresAtMs"] as? Number)?.toLong() ?: now + 60_000
        val notFound = response.containsKey("qobuzURL") && response["qobuzURL"] == null &&
            response["pending"] != true && response["retryAtMs"] == null && response["disabled"] != true && expiry > now && response["expiresAtMs"] is Number
        return Entry(url, expiry, notFound)
    }
}
