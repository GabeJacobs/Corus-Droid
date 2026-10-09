package fm.corus.android.ui.screens.profile

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.domain.ProfileTrophySummary
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.*

class ProfileCollectionSummaryTest {
    private val viewer = "collection-summary-viewer"
    private val profile = "collection-summary-owner"
    private val user = mock<FirebaseUser> { on { uid } doReturn viewer }
    private val auth = mock<FirebaseAuth> { on { currentUser } doReturn user }
    private val flags = mock<RemoteConfigService> { on { profileCollectionEnabled } doReturn true }
    private val callable = mock<HttpsCallableReference>()
    private val functions = mock<FirebaseFunctions> { on { getHttpsCallable("getProfileData") } doReturn callable }
    private val source = CloudFunctionsDataSource(functions, flags, auth)
    private val model = ProfileCollectionViewModel(auth, flags, functions, mock(), mock())

    @After fun clearSummary() { ProfileTrophySummary.remember(viewer, profile, null) }

    private fun respond(data: Map<String, Any>) {
        val response = mock<HttpsCallableResult> { on { getData() } doReturn data }
        whenever(callable.call(any())).thenReturn(Tasks.forResult(response))
    }

    @Test fun `profile load supplies both counts and opening refresh replaces cached zero`() = runTest {
        respond(mapOf("trophyCount" to 0, "profileGiftCount" to 0))
        source.getProfileData(profile)
        assertEquals(CollectionCounts(0, 0), model.cachedSummary(profile))

        respond(mapOf("trophyCount" to 1, "profileGiftCount" to 2))
        assertEquals(CollectionCounts(1, 2), model.summary(profile))
        assertEquals(CollectionCounts(1, 2), model.cachedSummary(profile))
        assertEquals(1, ProfileTrophySummary.count(viewer, profile))
        assertNull(ProfileTrophySummary.counts("another-viewer", profile))
        assertNull(ProfileTrophySummary.counts(viewer, "another-profile"))
    }

    @Test fun `missing or invalid gift counts remain unknown after a profile reload`() = runTest {
        respond(mapOf("trophyCount" to 0, "profileGiftCount" to 0))
        source.getProfileData(profile)
        respond(mapOf("trophyCount" to 0))
        source.getProfileData(profile)
        assertEquals(CollectionCounts(0, null), model.cachedSummary(profile))
        respond(mapOf("trophyCount" to 0, "profileGiftCount" to -1))
        source.getProfileData(profile)
        assertEquals(CollectionCounts(0, null), model.cachedSummary(profile))
    }
}
