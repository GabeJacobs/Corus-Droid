package fm.corus.android.ui.screens.profile

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionFeedSessionTest {
    private fun post(id: String) = CymbalPost(id = id, user = CymbalUser(id = "owner", username = "owner", displayName = "Owner"),
        track = CymbalTrack(id = id, name = id, artistName = "Artist", albumName = "Album"))
    private fun item(id: String, post: CymbalPost? = null) = CollectionItem(id, id, "Artist", "", "track", 0, post)

    @Test fun `post readiness failure and explicit retry report outcomes once`() = runTest {
        val events = mutableListOf<String>()
        var calls = 0
        val session = CollectionFeedSession("owner", false, CollectionPage(listOf(item("a")), null),
            fetchPost = { if (++calls == 1) error("Offline") else post(it) },
            fetchPage = { error("End") }, allowed = { true }, onPostEvent = { events += it })
        session.resolve("a")
        session.resolve("a")
        session.resolve("a", retry = true)
        session.resolve("a")
        assertEquals(listOf("load_failed", "retry_tapped", "load_succeeded"), events)
    }

    @Test fun `cached gift readiness is reported once and revoked sessions stay silent`() = runTest {
        val events = mutableListOf<String>()
        var allowed = true
        val session = CollectionFeedSession("owner", true, CollectionPage(listOf(item("a", post("a"))), null),
            fetchPost = { error("Already cached") }, fetchPage = { error("End") },
            allowed = { allowed }, onPostEvent = { events += it })
        session.resolve("a")
        session.resolve("a")
        allowed = false
        session.resolve("a", retry = true)
        assertEquals(listOf("load_succeeded"), events)
    }

    @Test fun `opening keeps ranked placeholders and hydrates selected post without refetching gallery`() = runTest {
        val next = mapOf("rank" to 24)
        var pageCalls = 0
        val fetchIds = mutableListOf<String>()
        val session = CollectionFeedSession("owner", false, CollectionPage(listOf(item("a"), item("b")), next),
            fetchPost = { fetchIds += it; post(it) }, fetchPage = { pageCalls++; error("Not needed") }, allowed = { true })
        assertEquals(listOf("a", "b"), session.items.value.map { it.id })
        assertTrue(session.items.value.all { it.post == null })
        assertEquals(0, pageCalls)
        session.resolve("b")
        assertEquals(listOf("a", "b"), session.items.value.map { it.id })
        assertNull(session.items.value[0].post)
        assertEquals("b", session.items.value[1].post?.id)
        assertEquals(listOf("b"), fetchIds)
        assertEquals(0, pageCalls)
    }

    @Test fun `pagination resumes gallery cursor and deduplicates overlapping pages in order`() = runTest {
        val next = mapOf("rank" to 24)
        val requests = mutableListOf<Map<*, *>>()
        val session = CollectionFeedSession("owner", false, CollectionPage(listOf(item("b"), item("a")), next),
            fetchPost = { post(it) }, fetchPage = { requests += it; CollectionPage(listOf(item("a"), item("c")), null) }, allowed = { true })
        session.loadMore()
        session.loadMore()
        assertEquals(listOf(next), requests)
        assertEquals(listOf("b", "a", "c"), session.items.value.map { it.id })
        assertFalse(session.hasMore.value)
    }

    @Test fun `gift feed reuses full posts already in the sheet`() = runTest {
        var calls = 0
        val cached = post("gift")
        val session = CollectionFeedSession("owner", true, CollectionPage(listOf(item("gift", cached)), null),
            fetchPost = { calls++; post(it) }, fetchPage = { error("End") }, allowed = { true })
        assertSame(cached, session.resolve("gift"))
        assertEquals(6, session.segment)
        assertEquals(0, calls)
    }

    @Test fun `failed requests wait for explicit retry and retain the cursor`() = runTest {
        val cursor = mapOf("rank" to 24)
        var calls = 0
        val session = CollectionFeedSession("owner", false, CollectionPage(listOf(item("a")), cursor),
            fetchPost = { post(it) }, fetchPage = {
                assertEquals(cursor, it)
                if (++calls == 1) error("Offline")
                CollectionPage(listOf(item("b")), null)
            }, allowed = { true })
        session.loadMore()
        assertTrue(session.pageFailed.value)
        assertTrue(session.hasMore.value)
        session.loadMore()
        assertEquals(1, calls)
        session.loadMore(retry = true)
        assertFalse(session.pageFailed.value)
        assertEquals(listOf("a", "b"), session.items.value.map { it.id })
    }

    @Test fun `concurrent resolution shares a single request`() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val session = CollectionFeedSession("owner", false, CollectionPage(listOf(item("a")), null),
            fetchPost = { calls++; gate.await(); post(it) }, fetchPage = { error("End") }, allowed = { true })
        val first = async { session.resolve("a") }
        val second = async { session.resolve("a") }
        runCurrent()
        assertEquals(1, calls)
        gate.complete(Unit)
        assertEquals("a", first.await()?.id)
        assertEquals("a", second.await()?.id)
        assertEquals(1, calls)
    }

    @Test fun `failed post holds its slot and retries without shifting adjacent posts`() = runTest {
        var calls = 0
        val session = CollectionFeedSession("owner", false, CollectionPage(listOf(item("a"), item("b")), null),
            fetchPost = { if (++calls == 1) error("Offline") else post(it) }, fetchPage = { error("End") }, allowed = { true })
        session.resolve("b")
        session.resolve("b")
        assertEquals(1, calls)
        assertEquals(setOf("b"), session.failedIds.value)
        assertEquals(listOf("a", "b"), session.items.value.map { it.id })
        session.resolve("b", retry = true)
        assertTrue(session.failedIds.value.isEmpty())
        assertEquals("b", session.items.value[1].post?.id)
    }

    @Test fun `empty resumable gift scan and repeated cursor terminate correctly`() = runTest {
        val first = mapOf("beforeMs" to 30)
        val next = mapOf("beforeMs" to 20)
        var calls = 0
        val session = CollectionFeedSession("owner", true, CollectionPage(listOf(item("a")), first),
            fetchPost = { post(it) }, fetchPage = {
                calls++
                CollectionPage(emptyList(), next)
            }, allowed = { true })
        session.loadMore()
        assertTrue(session.hasMore.value)
        session.loadMore()
        assertFalse(session.hasMore.value)
        session.loadMore()
        assertEquals(2, calls)
    }

    @Test fun `navigation awaits sheet animation then removes sheet before pushing`() = runTest {
        val events = mutableListOf<String>()
        val animation = CompletableDeferred<Unit>()
        val transition = async {
            dismissCollectionThenNavigate(
                hide = { events += "hiding"; animation.await(); events += "hidden" },
                isHidden = { animation.isCompleted }, dismiss = { events += "dismissed" }, navigate = { events += "pushed" })
        }
        runCurrent()
        assertEquals(listOf("hiding"), events)
        animation.complete(Unit)
        transition.await()
        assertEquals(listOf("hiding", "hidden", "dismissed", "pushed"), events)
    }

    @Test fun `blocked sheet dismissal never pushes a feed behind the sheet`() = runTest {
        var pushed = false
        dismissCollectionThenNavigate({}, { false }, { fail("Sheet still visible") }, { pushed = true })
        assertFalse(pushed)
    }

    @Test fun `viewer changes during hydration cannot publish post data`() = runTest {
        var allowed = true
        val session = CollectionFeedSession("owner", false, CollectionPage(listOf(item("a")), null),
            fetchPost = { allowed = false; post(it) }, fetchPage = { error("End") }, allowed = { allowed })
        session.resolve("a")
        assertNull(session.items.value.single().post)
    }
}
