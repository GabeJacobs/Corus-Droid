package fm.corus.android.data.local

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Use the stock Application (not CorusApplication, whose onCreate configures
// RevenueCat and throws in the test JVM); we only need a Context for SharedPreferences.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppearanceDefaultMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun prefs() = context.getSharedPreferences("corus_prefs", Context.MODE_PRIVATE)

    @After
    fun tearDown() {
        prefs().edit().clear().apply()
    }

    @Test
    fun `fresh install with no prior data resolves to system`() {
        assertEquals("system", AppearanceDefaultMigration.unsetThemeDefault(context))
    }

    @Test
    fun `existing install that completed onboarding is pinned to light`() {
        prefs().edit().putBoolean("completed_onboarding_uid123", true).apply()
        assertEquals("light", AppearanceDefaultMigration.unsetThemeDefault(context))
    }

    @Test
    fun `decision is frozen on first call and never re-evaluated`() {
        // First call as a fresh install freezes "system".
        assertEquals("system", AppearanceDefaultMigration.unsetThemeDefault(context))

        // A later onboarding flag must NOT flip a brand-new user to light.
        prefs().edit().putBoolean("completed_onboarding_uid123", true).apply()
        assertEquals("system", AppearanceDefaultMigration.unsetThemeDefault(context))
    }

    @Test
    fun `existing-install decision is frozen too`() {
        prefs().edit().putBoolean("completed_onboarding_uid123", true).apply()
        assertEquals("light", AppearanceDefaultMigration.unsetThemeDefault(context))

        // Even after clearing the onboarding flag, the frozen decision stands.
        prefs().edit().remove("completed_onboarding_uid123").apply()
        assertEquals("light", AppearanceDefaultMigration.unsetThemeDefault(context))
    }
}
