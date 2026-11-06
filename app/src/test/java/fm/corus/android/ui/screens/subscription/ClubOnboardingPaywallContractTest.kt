package fm.corus.android.ui.screens.subscription

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
class ClubOnboardingPaywallContractTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `onboarding benefits show the same three perks in order`() {
        assertEquals(
            listOf(
                R.string.club_feature_unlock_all_taste_matches,
                R.string.club_onboarding_unlimited_mixes,
                R.string.club_onboarding_customize_profile,
            ),
            ClubOnboardingPaywallContract.benefitStringResources,
        )
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `localized benefits fit narrow screens at default and enlarged text sizes`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val benefits = ClubOnboardingPaywallContract.benefitStringResources
        val localeTags = listOf("en", "pt-BR", "es", "ja", "zh-Hans", "de", "fr", "ko", "it")
        val language = mutableStateOf(localeTags.first())
        val fontScale = mutableStateOf(1f)
        fun localizedContext(tag: String) = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) },
        )

        composeRule.setContent {
            val localized = localizedContext(language.value)
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(density, fontScale = fontScale.value),
            ) {
                Column(Modifier.width(320.dp).padding(horizontal = 20.dp)) {
                    benefits.forEach { OnboardingBenefitRow(it) }
                }
            }
        }

        val failures = mutableListOf<String>()
        for ((scale, maxLines) in listOf(1f to 1, 1.3f to 2)) {
            composeRule.runOnIdle { fontScale.value = scale }
            for (tag in localeTags) {
                composeRule.runOnIdle { language.value = tag }
                val localized = localizedContext(tag)
                for (benefit in benefits) {
                    val label = localized.getString(benefit)
                    if (tag != "en") assertTrue("$tag fell back to English", label != context.getString(benefit))
                    val layouts = mutableListOf<TextLayoutResult>()
                    composeRule.onNodeWithText(label)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    val paragraph = layout.multiParagraph
                    assertFalse("$tag truncated: $label", paragraph.didExceedMaxLines)
                    for (line in 0 until layout.lineCount) {
                        val right = layout.getLineRight(line)
                        val limit = layout.layoutInput.constraints.maxWidth
                        if (right > limit + 1) failures += "$tag $label: $right > $limit"
                    }
                    assertTrue("$tag clipped vertically: $label", paragraph.height <= layout.size.height + 1)
                    if (layout.lineCount > maxLines) {
                        failures += "$tag at ${scale * 100}%: $label took ${layout.lineCount} lines"
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun `reviews appear when the scroll region is tall enough`() {
        assertTrue(ClubOnboardingPaywallContract.showsReviews(440.dp))
        assertFalse(ClubOnboardingPaywallContract.showsReviews(400.dp))
        assertEquals(280.dp, ClubOnboardingPaywallContract.subtitleMaxWidth)
        assertEquals(7, ClubOnboardingPaywallContract.playStoreReviews.size)
    }

    @Test fun `artwork and close target meet layout contract`() {
        assertEquals(112.dp, ClubOnboardingPaywallContract.vinylSize)
        assertEquals(20.dp, ClubOnboardingPaywallContract.headerVerticalPadding(500.dp))
        assertEquals(36.dp, ClubOnboardingPaywallContract.headerVerticalPadding(560.dp))
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
        assertTrue(source.contains("trialDurationText(context, monthlyPackage)"))
        assertTrue(source.contains("trialDurationText(context, yearlyPackage)"))
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
        assertTrue(viewModelSource.contains("logPurchaseStarted(planName, source.analyticsName, offeringID, productID)"))
        assertTrue(viewModelSource.contains("logPurchaseCompleted(planName, source.analyticsName, offeringID, productID)"))
    }
}
