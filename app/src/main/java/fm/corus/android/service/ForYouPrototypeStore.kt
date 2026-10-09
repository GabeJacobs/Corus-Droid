package fm.corus.android.service

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.domain.ForYouPrototypeState
import fm.corus.android.domain.ForYouTuningMode
import fm.corus.android.domain.ForYouStayCloseProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForYouPrototypeStore @Inject constructor(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val remoteConfig: RemoteConfigService,
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("corus_for_you_prototype", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(initialState(auth.currentUser?.uid, 0))
    val state = _state.asStateFlow()
    private var started = false
    private var request: Job? = null
    private var deadline: Job? = null
    private var progressGeneration = 0
    private var trialExpiry: Job? = null
    private var lastDebugOverride = remoteConfig.debugOverride("for_you_prototype_enabled")

    val isAvailable: Boolean
        get() = _state.value.let { it.uid != null && it.uid == auth.currentUser?.uid && it.enabled }

    val isResolvingAccess: Boolean
        get() = auth.currentUser?.uid?.let { it != _state.value.uid || !_state.value.hasPresentation } ?: false

    val needsIntroduction: Boolean
        get() = isAvailable && !prefs.getBoolean("introduction.v1.${_state.value.uid}", false)

    private fun initialState(uid: String?, generation: Int): ForYouPrototypeState {
        val default = remoteConfig.forYouDefaultMode
        val override = remoteConfig.debugOverride("for_you_prototype_enabled")
        val enabled = uid != null && (override ?: prefs.getBoolean("access.v1.$uid", false))
        return ForYouPrototypeState(
            uid = uid, generation = generation,
            enabled = enabled,
            hasPresentation = uid == null || override != null,
            defaultMode = default,
            mode = startupMode(uid, enabled),
        )
    }

    // This restores the request destination, never permission or cached posts.
    private fun startupMode(uid: String?, enabled: Boolean): ForYouTuningMode =
        if (uid != null && enabled) ForYouTuningMode.initial(
            prefs.getString("confirmedMode.v1.$uid", null) ?: prefs.getString("mode.$uid", null),
            ForYouTuningMode.ECLECTIC)
        else ForYouTuningMode.ECLECTIC

    init {
        auth.addAuthStateListener { scope.launch { beginSession(it.currentUser?.uid) } }
        scope.launch {
            remoteConfig.revision.collect {
                val override = remoteConfig.debugOverride("for_you_prototype_enabled")
                if (override != lastDebugOverride) {
                    lastDebugOverride = override
                    val current = _state.value
                    _state.value = current.copy(
                        enabled = current.uid != null && (override ?: prefs.getBoolean("access.v1.${current.uid}", false)),
                        hasPresentation = true,
                    )
                }
                val current = _state.value
                if (!current.hasPresentation) {
                    val default = remoteConfig.forYouDefaultMode
                    _state.value = current.copy(defaultMode = default,
                        mode = if (current.stayCloseProgress == null) startupMode(current.uid, current.enabled) else current.mode)
                }
            }
        }
    }

    private fun beginSession(uid: String?) {
        if (started && _state.value.uid == uid) return
        started = true
        request?.cancel()
        deadline?.cancel()
        trialExpiry?.cancel()
        val generation = _state.value.generation + 1
        progressGeneration++
        _state.value = initialState(uid, generation)
        if (uid == null) return
        deadline = scope.launch {
            delay(1_000)
            _state.value = _state.value.finish(generation)
        }
        request = scope.launch {
            try {
                val callable = functions.getHttpsCallable("getForYouPrototypeAccess")
                    .withTimeout(10, TimeUnit.SECONDS)
                val result = callable.call().await().getData() as? Map<*, *>
                val enabled = result?.get("enabled") == true
                if (_state.value.generation != generation) return@launch
                prefs.edit().putBoolean("access.v1.$uid", enabled).apply()
                _state.value = _state.value.resolve(enabled, generation)
                updateStayCloseProgress(ForYouStayCloseProgress.parse(result?.get("stayClose") as? Map<*, *>), uid)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.finish(generation)
            }
            if (_state.value.generation == generation) deadline?.cancel()
        }
    }

    fun viewedPostIds(uid: String): List<String> {
        val stored = prefs.getString("viewed.v1.$uid", null) ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(stored)
            (0 until array.length()).map { array.getString(it) }.takeLast(500)
        }.getOrDefault(emptyList())
    }

    fun recordViewedPostIds(ids: List<String>, uid: String) {
        if (!isAvailable || _state.value.uid != uid || ids.isEmpty()) return
        val history = viewedPostIds(uid)
        val combined = (history + ids.filter { it.isNotEmpty() }).distinct().takeLast(500)
        if (combined != history) prefs.edit().putString("viewed.v1.$uid", org.json.JSONArray(combined).toString()).apply()
    }

    fun select(mode: ForYouTuningMode) {
        if (!isAvailable) return
        val current = _state.value
        if (mode != ForYouTuningMode.ECLECTIC && current.stayCloseProgress?.canAccessBalanced != true) return
        if (mode == ForYouTuningMode.STAY_CLOSE && current.stayCloseProgress?.unlocked != true) return
        prefs.edit().putString("mode.${current.uid}", mode.value)
            .putString("confirmedMode.v1.${current.uid}", mode.value).apply()
        _state.value = current.copy(mode = mode)
    }

    fun markIntroductionShown() {
        if (isAvailable) prefs.edit().putBoolean("introduction.v1.${_state.value.uid}", true).apply()
    }

    fun updateStayCloseProgress(progress: ForYouStayCloseProgress?, uid: String) {
        val current = _state.value
        if (progress == null || current.uid != uid) return
        val preferredMode = if (current.stayCloseProgress == null)
            ForYouTuningMode.initial(prefs.getString("mode.$uid", null), current.defaultMode) else current.mode
        val mode = if (preferredMode != ForYouTuningMode.ECLECTIC && !progress.canAccess)
            ForYouTuningMode.ECLECTIC else preferredMode
        _state.value = current.copy(stayCloseProgress = progress, mode = mode)
        prefs.edit().putString("confirmedMode.v1.$uid", mode.value).apply()
        trialExpiry?.cancel()
        val endsAt = progress.trialEndsAt
        if (!progress.hasFullAccess && !progress.serverPaywallLocked && !progress.paywallLocked &&
            endsAt != null && endsAt > System.currentTimeMillis()) {
            trialExpiry = scope.launch {
                delay((endsAt - System.currentTimeMillis()).coerceAtLeast(0))
                if (_state.value.uid == uid) {
                    updateStayCloseProgress(progress.copy(serverPaywallLocked = true), uid)
                }
            }
        }
    }

    suspend fun refreshStayCloseProgress() {
        if (!isAvailable) return
        val current = _state.value
        val generation = ++progressGeneration
        try {
            val result = functions.getHttpsCallable("getForYouPrototypeAccess")
                .withTimeout(10, TimeUnit.SECONDS).call().await().getData() as? Map<*, *>
            if (current.uid != _state.value.uid || progressGeneration != generation) return
            current.uid?.let { updateStayCloseProgress(ForYouStayCloseProgress.parse(result?.get("stayClose") as? Map<*, *>), it) }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* Preserve confirmed progress; the server enforces access. */ }
    }

}
