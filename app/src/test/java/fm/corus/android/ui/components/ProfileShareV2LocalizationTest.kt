package fm.corus.android.ui.components

import android.app.Application
import android.content.res.Configuration
import fm.corus.android.localization.CorusStrings
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProfileShareV2LocalizationTest {
    @Test fun `every locale bundles grid labels and formats exact five by five unlock counts`() {
        val expected = mapOf(
            "en" to "Post 7 more coruses to unlock 5 × 5",
            "pt-BR" to "Publique mais 7 coruses para liberar 5 × 5",
            "es" to "Publica 7 coruses más para desbloquear 5 × 5",
            "ja" to "あと7件のcorusを投稿すると5 × 5が使えます",
            "zh-Hans" to "再发布 7 条 corus 即可解锁 5 × 5",
            "de" to "Poste noch 7 coruses, um 5 × 5 freizuschalten",
            "fr" to "Publiez encore 7 coruses pour débloquer 5 × 5",
            "ko" to "corus를 7개 더 게시하면 5 × 5를 사용할 수 있어요",
            "it" to "Pubblica ancora 7 coruses per sbloccare 5 × 5",
        )
        for ((locale, sentence) in expected) {
            val config = Configuration(RuntimeEnvironment.getApplication().resources.configuration).apply {
                setLocale(Locale.forLanguageTag(locale))
            }
            val context = RuntimeEnvironment.getApplication().createConfigurationContext(config)
            assertEquals(locale, "3 × 3", context.getString(CorusStrings.profile_share_grid_standard))
            assertEquals(locale, "4 × 4", context.getString(CorusStrings.profile_share_grid_large))
            assertEquals(locale, "5 × 5", context.getString(CorusStrings.profile_share_grid_extra_large))
            assertEquals(locale, sentence, profileUnlockText(context, ProfileStoryGridSize.EXTRA_LARGE, 7))
            val one = profileUnlockText(context, ProfileStoryGridSize.EXTRA_LARGE, 1)
            assertTrue(locale, one.contains("1")); assertTrue(locale, one.contains("5 × 5"))
            assertFalse(locale, one.contains("%")); assertFalse(locale, one.contains("{arg"))
        }
    }
}
