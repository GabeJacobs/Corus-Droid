package fm.corus.android.service

import android.content.Context
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import fm.corus.android.domain.ForYouTuningMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.mockito.Mockito.mockStatic
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ForYouPrototypeStoreTest {
    @Test fun `concurrent visits keep a pending notice and fresh devices respect the account claim`() = runTest(dispatcher) {
        val firestore = mock<FirebaseFirestore>()
        val collection = mock<CollectionReference>()
        val ref = mock<DocumentReference>()
        val transaction = mock<Transaction>()
        val snapshot = mock<DocumentSnapshot>()
        var serverSeen = false
        whenever(firestore.collection("users_v2")).thenReturn(collection)
        whenever(collection.document("gabe")).thenReturn(ref)
        whenever(transaction.get(ref)).thenReturn(snapshot)
        whenever(snapshot.exists()).thenReturn(true)
        whenever(snapshot.getBoolean("settings.yourMixPreviewEndedSeen")).thenAnswer { serverSeen }
        whenever(transaction.update(ref, "settings.yourMixPreviewEndedSeen", true)).thenAnswer { serverSeen = true; transaction }
        val transactions = mutableListOf<Pair<Transaction.Function<Boolean>, TaskCompletionSource<Boolean>>>()
        whenever(firestore.runTransaction(any<Transaction.Function<Boolean>>())).thenAnswer {
            val completion = TaskCompletionSource<Boolean>()
            transactions.add(it.getArgument<Transaction.Function<Boolean>>(0) to completion)
            completion.task
        }
        mockStatic(FirebaseFirestore::class.java).use { factory ->
            factory.`when`<FirebaseFirestore> { FirebaseFirestore.getInstance() }.thenReturn(firestore)
            val store = store(); runCurrent(); complete(0, true); runCurrent()
            val first = async { store.claimPreviewEndedNotice("gabe") }
            val second = async { store.claimPreviewEndedNotice("gabe") }
            runCurrent()
            assertEquals("Concurrent visits must share the claim still waiting to display", 1, transactions.size)
            transactions[0].let { (body, completion) -> completion.setResult(body.apply(transaction)) }
            runCurrent()
            assertTrue(first.await()); assertTrue(second.await())
            assertTrue(store.claimPreviewEndedNotice("gabe"))
            store.markPreviewEndedNoticeHandled()
            assertFalse(store.claimPreviewEndedNotice("gabe"))
            prefs.edit().clear().commit() // Fresh device retains only the server account state.
            val fresh = store(); runCurrent(); complete(1, true); runCurrent()
            val next = async { fresh.claimPreviewEndedNotice("gabe") }
            runCurrent()
            transactions[1].let { (body, completion) -> completion.setResult(body.apply(transaction)) }
            runCurrent()
            assertFalse(next.await())
        }
    }

    @Test fun `expired preview queues notice once while preserving Eclectic and account isolation`() = runTest(dispatcher) {
        val store = store(); runCurrent(); complete(0, true); runCurrent()
        assertFalse(store.state.value.needsPreviewEndedNotice)
        val expired = fm.corus.android.domain.ForYouStayCloseProgress(5, trialEndsAt = 1)
        store.updateStayCloseProgress(expired, "gabe")
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
        assertTrue("Expiry must explain the switch to Eclectic", store.state.value.needsPreviewEndedNotice)
        assertTrue("Fixture must be a new installation", store.needsIntroduction)
        store.markPreviewEndedNoticeHandled()
        assertFalse("Continue must not be followed by the Your Mix introduction", store.needsIntroduction)
        store.updateStayCloseProgress(expired, "gabe")
        assertFalse(store.state.value.needsPreviewEndedNotice)
        switch("other"); runCurrent(); complete(1, true); runCurrent()
        store.updateStayCloseProgress(expired.copy(hasFullAccess = true), "other")
        assertFalse(store.state.value.needsPreviewEndedNotice)
        store.updateStayCloseProgress(expired, "gabe")
        assertFalse(store.state.value.needsPreviewEndedNotice)
        store.updateStayCloseProgress(expired, "other")
        assertTrue(store.state.value.needsPreviewEndedNotice)
    }
    private val dispatcher = StandardTestDispatcher()
    private val auth = mock<FirebaseAuth>()
    private val functions = mock<FirebaseFunctions>()
    private val remote = mock<RemoteConfigService>()
    private val revision = MutableStateFlow(0)
    private val requests = mutableListOf<TaskCompletionSource<HttpsCallableResult>>()
    private lateinit var listener: FirebaseAuth.AuthStateListener
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("corus_for_you_prototype", Context.MODE_PRIVATE)

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        prefs.edit().clear().commit()
        whenever(remote.forYouDefaultMode).thenReturn(ForYouTuningMode.BALANCED)
        whenever(remote.debugOverride(any())).thenReturn(null)
        whenever(remote.revision).thenReturn(revision)
        val gabe = user("gabe")
        whenever(auth.currentUser).thenReturn(gabe)
        doAnswer { listener = it.getArgument(0); listener.onAuthStateChanged(auth); null }
            .whenever(auth).addAuthStateListener(any())
        val callable = mock<HttpsCallableReference>()
        whenever(functions.getHttpsCallable("getForYouPrototypeAccess")).thenReturn(callable)
        whenever(callable.withTimeout(any(), any())).thenReturn(callable)
        whenever(callable.call()).thenAnswer {
            TaskCompletionSource<HttpsCallableResult>().also { requests.add(it) }.task
        }
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun user(uid: String): FirebaseUser = mock { on { this.uid } doReturn uid }
    private fun complete(index: Int, enabled: Boolean, postCount: Int = 5) {
        requests[index].setResult(mock { on { getData() } doReturn mapOf("enabled" to enabled, "stayClose" to mapOf("postCount" to postCount, "threshold" to 5)) })
    }
    private fun switch(uid: String?) {
        val next = uid?.let(::user)
        whenever(auth.currentUser).thenReturn(next)
        listener.onAuthStateChanged(auth)
    }
    private fun store() = ForYouPrototypeStore(auth, functions, remote, context)

    @Test fun `existing saved selections migrate without assuming a new users default access`() = runTest(dispatcher) {
        prefs.edit().putBoolean("access.v1.gabe", true).putString("mode.gabe", "balanced").commit()
        val returning = store(); runCurrent()
        assertEquals(ForYouTuningMode.BALANCED, returning.state.value.mode)
        assertNull(returning.state.value.stayCloseProgress)
        switch("other"); runCurrent()
        assertEquals(ForYouTuningMode.ECLECTIC, returning.state.value.mode)
    }

    @Test fun `returning users request their confirmed mix before slow access resolves`() = runTest(dispatcher) {
        prefs.edit().putBoolean("access.v1.gabe", true).putString("confirmedMode.v1.gabe", "balanced").commit()
        val store = store(); runCurrent()
        assertEquals(ForYouTuningMode.BALANCED, store.state.value.mode)
        assertNull(store.state.value.stayCloseProgress)
        store.select(ForYouTuningMode.STAY_CLOSE)
        assertEquals(ForYouTuningMode.BALANCED, store.state.value.mode)
        advanceTimeBy(1000); runCurrent()
        assertEquals(ForYouTuningMode.BALANCED, store.state.value.mode)
        assertFalse(store.isResolvingAccess)
        complete(0, true, postCount = 4); runCurrent()
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
        assertEquals("tasteMatches", prefs.getString("confirmedMode.v1.gabe", null))
    }

    @Test fun `confirmed routing follows selections and never leaks across accounts`() = runTest(dispatcher) {
        val store = store(); runCurrent(); complete(0, true); runCurrent()
        store.select(ForYouTuningMode.STAY_CLOSE)
        assertEquals("close", prefs.getString("confirmedMode.v1.gabe", null))
        prefs.edit().putString("confirmedMode.v1.other", "balanced").commit()
        switch("other"); runCurrent()
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
        store.updateStayCloseProgress(fm.corus.android.domain.ForYouStayCloseProgress(50), "gabe")
        assertNull(store.state.value.stayCloseProgress)
        switch("gabe"); runCurrent()
        assertEquals(ForYouTuningMode.STAY_CLOSE, store.state.value.mode)
        assertNull(store.state.value.stayCloseProgress)
    }

    @Test fun `posting progress unlocks at five and stale account progress cannot leak`() = runTest(dispatcher) {
        val store = store(); runCurrent(); complete(0, true); runCurrent()
        store.updateStayCloseProgress(fm.corus.android.domain.ForYouStayCloseProgress(4), "gabe")
        store.select(ForYouTuningMode.BALANCED)
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
        store.select(ForYouTuningMode.STAY_CLOSE)
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
        store.updateStayCloseProgress(fm.corus.android.domain.ForYouStayCloseProgress(5), "gabe")
        store.select(ForYouTuningMode.STAY_CLOSE)
        assertEquals(ForYouTuningMode.STAY_CLOSE, store.state.value.mode)
        store.updateStayCloseProgress(fm.corus.android.domain.ForYouStayCloseProgress(4), "gabe")
        assertFalse(store.state.value.stayCloseProgress!!.unlocked)
        switch("other"); runCurrent()
        store.updateStayCloseProgress(fm.corus.android.domain.ForYouStayCloseProgress(50), "gabe")
        assertNull(store.state.value.stayCloseProgress)
    }

    @Test fun `trial expires while open and paid access cancels the expiry`() = runTest(dispatcher) {
        val store = store(); runCurrent(); complete(0, true); runCurrent()
        val preview = fm.corus.android.domain.ForYouStayCloseProgress(5,
            trialEndsAt = System.currentTimeMillis() + 1000)
        store.updateStayCloseProgress(preview, "gabe")
        store.select(ForYouTuningMode.STAY_CLOSE); runCurrent()
        advanceTimeBy(1001); runCurrent()
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
        store.select(ForYouTuningMode.BALANCED)
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
        assertTrue(store.state.value.stayCloseProgress!!.paywallLocked)
        store.updateStayCloseProgress(preview.copy(hasFullAccess = true), "gabe")
        store.select(ForYouTuningMode.STAY_CLOSE); runCurrent()
        advanceTimeBy(2000); runCurrent()
        assertEquals(ForYouTuningMode.STAY_CLOSE, store.state.value.mode)
        assertFalse(store.state.value.stayCloseProgress!!.paywallLocked)
    }

    @Test fun `deletion below the posting threshold keeps expiry finite`() = runTest(dispatcher) {
        val store = store(); runCurrent(); complete(0, true); runCurrent()
        store.updateStayCloseProgress(fm.corus.android.domain.ForYouStayCloseProgress(4,
            trialEndsAt = System.currentTimeMillis() + 1000), "gabe")
        runCurrent(); advanceTimeBy(1001); runCurrent()
        assertTrue(store.state.value.stayCloseProgress!!.serverPaywallLocked)
        assertFalse(store.state.value.stayCloseProgress!!.canAccess)
        advanceTimeBy(2000); runCurrent()
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
    }

    @Test fun `fast access resolves before the first feed presentation`() = runTest(dispatcher) {
        val store = store(); runCurrent()
        assertFalse(store.state.value.hasPresentation)
        complete(0, true); runCurrent()
        assertTrue(store.isAvailable)
        assertTrue(store.state.value.hasPresentation)
        assertEquals(ForYouTuningMode.BALANCED, store.state.value.mode)
        verify(functions).getHttpsCallable("getForYouPrototypeAccess")
    }

    @Test fun `debug visibility changes immediately and reset restores confirmed server access`() = runTest(dispatcher) {
        val store = store(); runCurrent(); complete(0, true); runCurrent()
        whenever(remote.debugOverride("for_you_prototype_enabled")).thenReturn(false)
        revision.value++; runCurrent()
        assertFalse(store.isAvailable)
        assertTrue(prefs.getBoolean("access.v1.gabe", false))
        assertFalse(store().isAvailable) // Survives a new store without altering server cache.
        whenever(remote.debugOverride("for_you_prototype_enabled")).thenReturn(null)
        revision.value++; runCurrent()
        assertTrue(store.isAvailable)
    }

    @Test fun `debug ON stays visible after server denial and reset restores OFF`() = runTest(dispatcher) {
        val store = store(); runCurrent()
        whenever(remote.debugOverride("for_you_prototype_enabled")).thenReturn(true)
        revision.value++; runCurrent()
        assertTrue(store.isAvailable)
        assertTrue(store.state.value.hasPresentation)
        complete(0, false); runCurrent()
        assertTrue(store.isAvailable)
        assertFalse(prefs.getBoolean("access.v1.gabe", true))
        whenever(remote.debugOverride("for_you_prototype_enabled")).thenReturn(null)
        revision.value++; runCurrent()
        assertFalse(store.isAvailable)
    }

    @Test fun `debug ON cannot make a signed out session available`() = runTest(dispatcher) {
        whenever(remote.debugOverride("for_you_prototype_enabled")).thenReturn(true)
        val store = store(); runCurrent()
        assertTrue(store.isAvailable)
        switch(null); runCurrent()
        assertFalse(store.isAvailable)
    }

    @Test fun `late ON is cached for next launch without moving visible tabs`() = runTest(dispatcher) {
        val store = store(); runCurrent(); advanceTimeBy(1000); runCurrent()
        assertTrue(store.state.value.hasPresentation); assertFalse(store.isAvailable)
        complete(0, true); runCurrent(); assertFalse(store.isAvailable)
        assertTrue(prefs.getBoolean("access.v1.gabe", false))
        assertTrue(store().isAvailable)
    }

    @Test fun `late OFF preserves current cached layout and disables next launch`() = runTest(dispatcher) {
        prefs.edit().putBoolean("access.v1.gabe", true).commit()
        val store = store(); runCurrent(); advanceTimeBy(1000); runCurrent()
        complete(0, false); runCurrent(); assertTrue(store.isAvailable)
        assertFalse(store().isAvailable)
    }

    @Test fun `offline startup retains confirmed access and settles immediately on failure`() = runTest(dispatcher) {
        prefs.edit().putBoolean("access.v1.gabe", true).commit()
        val store = store(); runCurrent()
        requests[0].setException(IllegalStateException("offline")); runCurrent()
        assertTrue(store.isAvailable); assertTrue(store.state.value.hasPresentation)
    }

    @Test fun `account switches isolate saved modes and ignore obsolete generations`() = runTest(dispatcher) {
        val store = store(); runCurrent()
        switch("other"); runCurrent()
        complete(0, true); runCurrent(); assertFalse(store.isAvailable)
        switch("gabe"); runCurrent(); complete(1, true); runCurrent()
        assertFalse(store.isAvailable)
        complete(2, true); runCurrent(); store.select(ForYouTuningMode.STAY_CLOSE)
        switch("other"); runCurrent(); assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
        assertFalse(store.isAvailable)
        switch("gabe"); runCurrent(); assertEquals(ForYouTuningMode.STAY_CLOSE, store.state.value.mode)
        assertNull(store.state.value.stayCloseProgress) // Routing does not restore access evidence.
        complete(4, true); runCurrent(); assertEquals(ForYouTuningMode.STAY_CLOSE, store.state.value.mode)
        switch(null); runCurrent(); assertFalse(store.isAvailable)
    }

    @Test fun `auth refresh does not restart access and resolved defaults stay stable`() = runTest(dispatcher) {
        val store = store(); runCurrent(); complete(0, true); runCurrent()
        switch("gabe"); runCurrent(); assertEquals(1, requests.size)
        whenever(remote.forYouDefaultMode).thenReturn(ForYouTuningMode.ECLECTIC)
        revision.value++; runCurrent()
        assertEquals(ForYouTuningMode.BALANCED, store.state.value.defaultMode)
        assertEquals(ForYouTuningMode.BALANCED, store.state.value.mode)
    }

    @Test fun `saved choice overrides remote default and introduction is once per account`() = runTest(dispatcher) {
        whenever(remote.forYouDefaultMode).thenReturn(ForYouTuningMode.ECLECTIC)
        prefs.edit().putString("mode.gabe", "close").commit()
        val store = store(); runCurrent(); complete(0, true); runCurrent()
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.defaultMode)
        assertEquals(ForYouTuningMode.STAY_CLOSE, store.state.value.mode)
        assertTrue(store.needsIntroduction); store.markIntroductionShown(); assertFalse(store.needsIntroduction)
        assertFalse(prefs.getBoolean("introduction.v1.other", false))
    }

    @Test fun `unknown defaults fail safely and endpoint mapping matches iOS`() {
        assertEquals(ForYouTuningMode.BALANCED, ForYouTuningMode.configured("bad"))
        assertEquals(ForYouTuningMode.STAY_CLOSE, ForYouTuningMode.configured(" close "))
        assertEquals("getForYouEclecticFeed", ForYouTuningMode.ECLECTIC.callableName)
        assertEquals("getForYouPrototypeFeed", ForYouTuningMode.BALANCED.callableName)
        assertEquals("getForYouPrototypeFeed", ForYouTuningMode.STAY_CLOSE.callableName)
    }

    @Test fun `viewed history is shared across modes bounded and isolated by account`() = runTest(dispatcher) {
        val store = store(); runCurrent(); complete(0, true); runCurrent()
        store.recordViewedPostIds((0 until 510).map { "post-$it" }, "gabe")
        assertEquals(500, store.viewedPostIds("gabe").size)
        assertEquals("post-10", store.viewedPostIds("gabe").first())
        store.select(ForYouTuningMode.ECLECTIC)
        store.recordViewedPostIds(listOf("post-509", "new-post", ""), "gabe")
        assertEquals(500, store.viewedPostIds("gabe").size)
        assertEquals("new-post", store.viewedPostIds("gabe").last())
        switch("other"); runCurrent()
        assertTrue(store.viewedPostIds("other").isEmpty())
        store.recordViewedPostIds(listOf("stale-account"), "gabe")
        assertFalse(store.viewedPostIds("gabe").contains("stale-account"))
        assertEquals("new-post", store.viewedPostIds("gabe").last())
    }

    @Test fun `unknown and below five posting progress override saved paid modes and remote defaults`() = runTest(dispatcher) {
        prefs.edit().putString("mode.gabe", "balanced").commit()
        val store = store(); runCurrent()
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
        complete(0, true, 4); runCurrent()
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
        store.select(ForYouTuningMode.BALANCED)
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
        store.updateStayCloseProgress(fm.corus.android.domain.ForYouStayCloseProgress(5), "gabe")
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
        store.select(ForYouTuningMode.BALANCED)
        assertEquals(ForYouTuningMode.BALANCED, store.state.value.mode)
        store.updateStayCloseProgress(fm.corus.android.domain.ForYouStayCloseProgress(4, hasFullAccess = true), "gabe")
        assertEquals(ForYouTuningMode.ECLECTIC, store.state.value.mode)
    }

}
