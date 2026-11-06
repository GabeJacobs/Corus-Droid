package fm.corus.android.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReviewPromptManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day = 86_400_000L
    private val today = 20_000 * day
    private val user = "publisher"

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("review_prompt", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun manager(enabled: Boolean = true): ReviewPromptManager {
        val remoteConfig = mock<RemoteConfigService> {
            on { reviewPromptEnabled } doReturn enabled
        }
        return ReviewPromptManager(context, remoteConfig, mock())
    }

    @Test
    fun `three distinct active days and a publication are required`() {
        val manager = manager()
        repeat(3) { manager.beginVisit(user, today - 2 * day) }
        manager.recordPublishedPost(user, false, today - 2 * day)
        assertNull(manager.readyUserId.value)

        manager.beginVisit(user, today - day)
        manager.beginVisit(user, today)
        assertNull(manager.readyUserId.value)

        manager.recordPublishedPost(user, false, today)
        assertEquals(user, manager.readyUserId.value)
    }

    @Test
    fun `deferred post survives until a later visit without starting cooldown`() {
        val manager = manager()
        manager.beginVisit(user, today - 2 * day)
        manager.beginVisit(user, today - day)
        manager.recordPublishedPost(user, true, today)
        assertNull(manager.readyUserId.value)

        manager.recordPublishedPost(user, false, today)
        assertNull(manager.readyUserId.value)
        manager.beginVisit(user, today)
        assertTrue(manager.consumeRequest(user, today))
    }

    @Test
    fun `a request starts a thirty day device cooldown`() {
        val manager = manager()
        manager.beginVisit(user, today - 2 * day)
        manager.beginVisit(user, today - day)
        manager.recordPublishedPost(user, false, today)
        assertTrue(manager.consumeRequest(user, today))
        assertFalse(manager.consumeRequest(user, today))

        manager.recordPublishedPost(user, false, today + 29 * day)
        assertNull(manager.readyUserId.value)
        manager.recordPublishedPost(user, false, today + 30 * day)
        assertEquals(user, manager.readyUserId.value)
    }

    @Test
    fun `pending publication is scoped to the account and survives recreation`() {
        val manager = manager()
        manager.beginVisit(user, today - 2 * day)
        manager.beginVisit(user, today - day)
        manager.recordPublishedPost(user, true, today)

        val recreated = manager()
        recreated.beginVisit("another_account", today)
        assertNull(recreated.readyUserId.value)
        recreated.beginVisit(user, today)
        assertEquals(user, recreated.readyUserId.value)
    }

    @Test
    fun `remote config off prevents a request`() {
        val manager = manager(enabled = false)
        manager.beginVisit(user, today - 2 * day)
        manager.beginVisit(user, today - day)
        manager.recordPublishedPost(user, false, today)
        assertFalse(manager.consumeRequest(user, today))
    }
}
