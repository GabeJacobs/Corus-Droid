package fm.corus.android.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * iOS ReviewPromptManager parity: 8 likes in a session shows the sheet;
 * users who have never been asked (no last_prompt_date) are eligible;
 * dismiss / leave-a-review starts the 60-day cooldown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReviewPromptManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("review_prompt", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun manager(enabled: Boolean = true): ReviewPromptManager {
        val remoteConfig = mock<RemoteConfigService> {
            on { reviewPromptEnabled } doReturn enabled
        }
        return ReviewPromptManager(context, remoteConfig)
    }

    @Test
    fun `never-asked user sees the prompt on the 8th like`() {
        val manager = manager()
        repeat(7) { manager.recordLike() }
        assertFalse(manager.shouldShowPrompt.value)

        manager.recordLike()
        assertTrue(manager.shouldShowPrompt.value)
    }

    @Test
    fun `remote config off never shows the prompt`() {
        val manager = manager(enabled = false)
        repeat(8) { manager.recordLike() }
        assertFalse(manager.shouldShowPrompt.value)
    }

    @Test
    fun `dismiss starts cooldown so further likes do not show again`() {
        val manager = manager()
        repeat(8) { manager.recordLike() }
        assertTrue(manager.shouldShowPrompt.value)

        manager.dismiss()
        assertFalse(manager.shouldShowPrompt.value)

        repeat(8) { manager.recordLike() }
        assertFalse(manager.shouldShowPrompt.value)
    }

    @Test
    fun `leave a review starts cooldown so further likes do not show again`() {
        val manager = manager()
        repeat(8) { manager.recordLike() }
        manager.requestReview()
        assertFalse(manager.shouldShowPrompt.value)

        repeat(8) { manager.recordLike() }
        assertFalse(manager.shouldShowPrompt.value)
    }

    @Test
    fun `user prompted more than 60 days ago is eligible again`() {
        val stale = System.currentTimeMillis() - 61L * 24 * 60 * 60 * 1000
        context.getSharedPreferences("review_prompt", Context.MODE_PRIVATE)
            .edit()
            .putLong(ReviewPromptManager.KEY_LAST_PROMPT_DATE, stale)
            .commit()

        val manager = manager()
        repeat(8) { manager.recordLike() }
        assertTrue(manager.shouldShowPrompt.value)
    }
}
