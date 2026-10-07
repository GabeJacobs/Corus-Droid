package fm.corus.android.ui.screens.auth

import fm.corus.android.data.model.OnboardingFollowSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Called on the main dispatcher. Each target's writes keep tap order. */
internal class OnboardingFollowMeasurement(
    private val scope: CoroutineScope,
    private val write: suspend (String, Boolean) -> Unit,
    private val save: suspend (OnboardingFollowSession) -> Unit,
    private val emit: (String, Map<String, Any>) -> Unit,
) {
    private val _followedIds = MutableStateFlow<Set<String>>(emptySet())
    val followedIds = _followedIds.asStateFlow()
    private val _session = MutableStateFlow<OnboardingFollowSession?>(null)
    val session = _session.asStateFlow()
    private val tasks = mutableMapOf<String, Job>()
    private val versions = mutableMapOf<String, Int>()
    private val mutex = Mutex()
    private var confirmedIds = emptySet<String>()
    private var finishing = false

    suspend fun prepare(value: OnboardingFollowSession) {
        if (_session.value != null) return
        confirmedIds = value.confirmedIds
        _followedIds.value = confirmedIds
        _session.value = value
        persist(value)
    }

    suspend fun expose() = mutex.withLock {
        val value = _session.value ?: return@withLock
        if (!value.exposed) {
            val next = value.copy(exposed = true)
            _session.value = next
            persist(next)
            emit("onboarding_follow_exposed", next.parameters())
        }
    }

    fun toggleFollow(target: String) {
        if (finishing) return
        val following = target !in _followedIds.value
        _followedIds.value = if (following) _followedIds.value + target else _followedIds.value - target
        val version = (versions[target] ?: 0) + 1
        versions[target] = version
        val predecessor = tasks[target]
        tasks[target] = scope.launch {
            predecessor?.join()
            try {
                write(target, following)
                mutex.withLock {
                    val nextIds = if (following) confirmedIds + target else confirmedIds - target
                    if (nextIds != confirmedIds) {
                        confirmedIds = nextIds
                        _session.value?.let { previous ->
                            val next = previous.copy(confirmedIds = nextIds, revision = previous.revision + 1)
                            _session.value = next
                            persist(next)
                            if (next.exposed) emit("onboarding_follow_count_updated", next.parameters())
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (versions[target] == version) {
                    _followedIds.value = if (target in confirmedIds) _followedIds.value + target else _followedIds.value - target
                }
            }
        }
    }

    suspend fun finish(): Int {
        finishing = true
        try {
            tasks.values.toList().joinAll()
            tasks.clear()
            _followedIds.value = confirmedIds
            mutex.withLock {
                _session.value?.let { value ->
                    if (value.exposed && !value.completed) {
                        val next = value.copy(completed = true)
                        _session.value = next
                        persist(next)
                        emit("onboarding_follow_completed", next.parameters())
                    }
                }
            }
            return confirmedIds.size
        } finally { finishing = false }
    }

    private suspend fun persist(value: OnboardingFollowSession) {
        // Local persistence is best effort and must not revert a successful follow.
        try { save(value) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
    }
}
