package fm.corus.android.ui.screens.auth

import fm.corus.android.data.model.OnboardingFollowSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingFollowMeasurementTest {
    private fun session(revised: Boolean = true) = OnboardingFollowSession(revised, false, 3, 8)

    @Test fun `zero follows and repeated appearance complete once`() = runTest {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val metric = OnboardingFollowMeasurement(this, { _, _ -> }, {}, { name, parameters -> events += name to parameters })
        metric.prepare(session(false))
        metric.expose()
        metric.expose()
        assertEquals(0, metric.finish())
        assertEquals(0, metric.finish())
        assertEquals(listOf("onboarding_follow_exposed", "onboarding_follow_completed"), events.map { it.first })
        assertEquals("control", events.last().second["variant"])
        assertEquals(0, events.last().second["followed_count"])
        assertFalse(events.last().second.containsKey("target_user_id"))
    }

    @Test fun `failed requests excluded successful unfollow removes unique user`() = runTest {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val metric = OnboardingFollowMeasurement(this, { target, _ -> if (target == "failed") error("write failed") }, {}, { n,p -> events += n to p })
        metric.prepare(session())
        metric.expose()
        metric.toggleFollow("kept")
        metric.toggleFollow("removed")
        metric.toggleFollow("removed")
        metric.toggleFollow("failed")
        assertEquals(1, metric.finish())
        assertEquals(setOf("kept"), metric.followedIds.value)
        assertEquals(1, events.last().second["followed_count"])
    }

    @Test fun `finish waits for pending writes and target taps stay ordered`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<Boolean>()
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val metric = OnboardingFollowMeasurement(this, { _, following ->
            writes += following
            if (writes.size == 1) gate.await()
        }, {}, { n,p -> events += n to p })
        metric.prepare(session())
        metric.expose()
        metric.toggleFollow("target")
        metric.toggleFollow("target")
        val finished = async { metric.finish() }
        runCurrent()
        assertEquals(listOf(true), writes)
        assertFalse(finished.isCompleted)
        assertFalse(events.any { it.first == "onboarding_follow_completed" })
        gate.complete(Unit)
        assertEquals(0, finished.await())
        assertEquals(listOf(true,false), writes)
        assertEquals(listOf(1,0), events.filter { it.first == "onboarding_follow_count_updated" }.map { it.second["followed_count"] })
    }

    @Test fun `persisted session restores confirmed users and avoids duplicate exposure`() = runTest {
        var saved: OnboardingFollowSession? = null
        val metric = OnboardingFollowMeasurement(this, { _, _ -> }, { saved = it }, { _, _ -> })
        metric.prepare(session(false))
        metric.expose()
        metric.toggleFollow("target")
        runCurrent()
        val restored = Json.decodeFromString<OnboardingFollowSession>(Json.encodeToString(saved!!))
        val events = mutableListOf<String>()
        val resumed = OnboardingFollowMeasurement(this, { _, _ -> }, {}, { n,_ -> events += n })
        resumed.prepare(restored)
        resumed.expose()
        assertEquals(false, resumed.session.value!!.revised)
        assertEquals(setOf("target"), resumed.followedIds.value)
        assertTrue(events.isEmpty())
        assertEquals(1, resumed.finish())
        assertEquals(listOf("onboarding_follow_completed"), events)
    }

    @Test fun `storage failures do not revert successful follows`() = runTest {
        val metric = OnboardingFollowMeasurement(this, { _, _ -> }, { error("disk unavailable") }, { _, _ -> })
        metric.prepare(session())
        metric.expose()
        metric.toggleFollow("target")
        assertEquals(1, metric.finish())
        assertEquals(setOf("target"), metric.followedIds.value)
    }
}
