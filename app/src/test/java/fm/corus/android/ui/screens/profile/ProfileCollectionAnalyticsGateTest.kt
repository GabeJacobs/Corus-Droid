package fm.corus.android.ui.screens.profile

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import org.junit.Test
import org.mockito.kotlin.*

class ProfileCollectionAnalyticsGateTest {
    @Test fun legacyTrophiesRespectKillSwitchViewerAndReplacementGate() {
        val auth = mock<FirebaseAuth>()
        val user = mock<FirebaseUser>()
        whenever(auth.currentUser).thenReturn(user)
        whenever(user.uid).thenReturn("FUQZIrZR08T2Ux2vYpPzWx7B1rv1")
        val flags = mock<RemoteConfigService>()
        val analytics = mock<AnalyticsService>()
        val model = TrophyCaseViewModel(auth, flags, mock<FirebaseFunctions>(), mock<PostRepository>(), analytics)
        fun opened() = model.track("owner", "00112233-4455-6677-8899-aabbccddeeff", "opened", "track")
        opened()
        verify(analytics).logEvent(eq("profile_collection_event"), any())
        clearInvocations(analytics)
        whenever(flags.trophyCaseDisabled).thenReturn(true)
        opened()
        verifyNoInteractions(analytics)
        whenever(flags.trophyCaseDisabled).thenReturn(false)
        whenever(flags.profileCollectionEnabled).thenReturn(true)
        opened()
        verifyNoInteractions(analytics)
        whenever(flags.profileCollectionEnabled).thenReturn(false)
        whenever(user.uid).thenReturn("other")
        opened()
        verifyNoInteractions(analytics)
    }

    @Test fun disabledViewsEmitNothingAndFlagEnabledNewAccountsEmitEvents() {
        val auth = mock<FirebaseAuth>()
        val user = mock<FirebaseUser>()
        whenever(auth.currentUser).thenReturn(user)
        whenever(user.uid).thenReturn("FUQZIrZR08T2Ux2vYpPzWx7B1rv1")
        val flags = mock<RemoteConfigService>()
        val analytics = mock<AnalyticsService>()
        val model = ProfileCollectionViewModel(auth, flags, mock<FirebaseFunctions>(), mock<PostRepository>(), analytics)
        model.track("owner", "00112233-4455-6677-8899-aabbccddeeff", "opened", "trophies", "track")
        verifyNoInteractions(analytics)
        whenever(flags.profileCollectionEnabled).thenReturn(true)
        model.track("owner", "00112233-4455-6677-8899-aabbccddeeff", "opened", "trophies", "track")
        verify(analytics).logEvent(eq("profile_collection_event"), any())
        clearInvocations(analytics)
        whenever(user.uid).thenReturn("other")
        model.track("owner", "00112233-4455-6677-8899-aabbccddeeff", "opened", "trophies", "track")
        verify(analytics).logEvent(eq("profile_collection_event"), any())
    }
}
