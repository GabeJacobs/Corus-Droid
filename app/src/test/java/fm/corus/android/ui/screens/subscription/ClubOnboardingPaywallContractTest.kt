package fm.corus.android.ui.screens.subscription

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
class ClubOnboardingPaywallContractTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `supported tall layout shows export in approved order`() {
        assertEquals(
            listOf(
                R.string.club_feature_unlock_all_taste_matches,
                R.string.club_feature_customization,
                R.string.club_feature_unlimited_saves,
                R.string.club_feature_unlimited_playlists,
                R.string.club_feature_support,
            ),
            ClubOnboardingPaywallContract.benefitStringResources(true, 500.dp),
        )
    }

    @Test fun `compact or unsupported layout omits export without replacement`() {
        val expected = listOf(
            R.string.club_feature_unlock_all_taste_matches,
            R.string.club_feature_customization,
            R.string.club_feature_unlimited_saves,
            R.string.club_feature_support,
        )
        assertEquals(expected, ClubOnboardingPaywallContract.benefitStringResources(true, 400.dp))
        assertEquals(expected, ClubOnboardingPaywallContract.benefitStringResources(false, 500.dp))
    }

    @Test fun `artwork and close target meet layout contract`() {
        assertEquals(112.dp, ClubOnboardingPaywallContract.vinylSize)
        assertEquals(56.dp, ClubOnboardingPaywallContract.headerVerticalPadding)
        assertTrue(ClubOnboardingPaywallContract.vinylSize < 140.dp)

        composeRule.setContent { Box { ClubCloseButton(onClick = {}) } }
        val bounds = composeRule.onNodeWithContentDescription("Close").getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left >= 48.dp)
        assertTrue(bounds.bottom - bounds.top >= 48.dp)
    }

    @Test fun `close overlay keeps system and trailing insets without an app bar`() {
        val source = File(
            "src/main/java/fm/corus/android/ui/screens/subscription/CymbalClubOfferScreen.kt",
        ).readText()
        assertFalse(source.contains("TopAppBar("))
        assertTrue(source.contains(".statusBarsPadding()"))
        assertTrue(source.contains("end = CorusSpacing.lg"))
        assertFalse(source.contains("background(CorusColors.CardBackground)"))
    }

    @Test fun `onboarding trial remains RevenueCat eligibility driven`() {
        assertEquals(
            R.string.club_onboarding_renewal,
            ClubOnboardingPaywallContract.renewalStringResource(hasTrial = true),
        )
        assertEquals(
            R.string.club_onboarding_renewal_no_trial,
            ClubOnboardingPaywallContract.renewalStringResource(hasTrial = false),
        )

        val source = File(
            "src/main/java/fm/corus/android/ui/screens/subscription/CymbalClubOfferScreen.kt",
        ).readText()
        assertTrue(source.contains("pkg?.product?.defaultOption"))
        assertTrue(source.contains("trialDurationText(context, monthlyPackage, true)"))
        assertTrue(source.contains("trialDurationText(context, yearlyPackage, true)"))
        assertTrue(source.contains("R.string.club_cta_try_free_format"))
        assertTrue(source.contains("trial != null"))
        assertFalse(source.contains("forceDebugTrial"))
        assertFalse(source.contains("BuildConfig.DEBUG"))
    }

    @Test fun `onboarding analytics cover the iOS funnel without duplicate skips`() {
        val screenSource = File(
            "src/main/java/fm/corus/android/ui/screens/subscription/CymbalClubOfferScreen.kt",
        ).readText()
        val viewModelSource = File(
            "src/main/java/fm/corus/android/ui/screens/subscription/CymbalClubViewModel.kt",
        ).readText()

        assertTrue(screenSource.contains("didLogOnboardingSkip"))
        assertTrue(screenSource.contains("logDismissal(\"system_back\")"))
        assertTrue(screenSource.contains("logDismissal(\"maybe_later\")"))
        assertTrue(screenSource.contains("logDismissal(if (isOnboarding) \"close\" else null)"))
        assertTrue(viewModelSource.contains("logOnboardingClubOfferShown()"))
        assertTrue(viewModelSource.contains("logOnboardingClubOfferSkipped(method)"))
        assertTrue(viewModelSource.contains("logOnboardingClubOfferSubscribed(planName)"))
        assertTrue(viewModelSource.contains("logPurchaseStarted(planName, source.analyticsName)"))
        assertTrue(viewModelSource.contains("logPurchaseCompleted(planName, source.analyticsName)"))
    }
}
