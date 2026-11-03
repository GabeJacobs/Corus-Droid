package fm.corus.android.ui.screens.subscription

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import fm.corus.android.R
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.models.PricingPhase
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.models.SubscriptionOption
import com.revenuecat.purchases.models.SubscriptionOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ClubTrialCopyTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun option(duration: String?): SubscriptionOption {
        val option = mock<SubscriptionOption>()
        val phase = duration?.let {
            mock<PricingPhase>().also { phase ->
                whenever(phase.billingPeriod).thenReturn(Period.create(it))
            }
        }
        whenever(option.freePhase).thenReturn(phase)
        return option
    }

    private fun pkg(selected: SubscriptionOption, other: SubscriptionOption? = null): Package {
        val product = mock<StoreProduct>()
        whenever(product.defaultOption).thenReturn(selected)
        whenever(product.subscriptionOptions).thenReturn(SubscriptionOptions(listOfNotNull(other, selected)))
        return mock<Package>().also { whenever(it.product).thenReturn(product) }
    }

    // Exercise the actual paywall formatter, including its non-onboarding path.
    private fun duration(pkg: Package?): String? {
        val method = Class.forName("fm.corus.android.ui.screens.subscription.CymbalClubOfferScreenKt")
            .getDeclaredMethod("trialDurationText", Context::class.java, Package::class.java)
        method.isAccessible = true
        return method.invoke(null, context, pkg) as String?
    }

    @Test fun `copy follows the purchased one month option rather than the first trial`() {
        assertEquals("1 month", duration(pkg(option("P1M"), option("P2W"))))
    }

    @Test fun `no trial selected option never advertises another offer trial`() {
        assertNull(duration(pkg(option(null), option("P2W"))))
    }

    @Test fun `control and unloaded offerings render their actual duration`() {
        assertEquals("2 weeks", duration(pkg(option("P2W"))))
        assertNull(duration(null))
    }

    private fun copy(name: String, ctx: Context, vararg args: Any?): String {
        val method = Class.forName("fm.corus.android.ui.screens.subscription.CymbalClubOfferScreenKt")
            .declaredMethods.single { it.name == name }
        method.isAccessible = true
        return method.invoke(null, ctx, *args) as String
    }

    @Test fun `buttons and both plan details match all three variants`() {
        for ((period, duration) in listOf("P2W" to "2 weeks", "P1M" to "1 month")) {
            val pkg = pkg(option(period))
            assertEquals("Try Free for $duration", copy("ctaText", context, pkg, false))
            assertEquals("$duration free, then $3.99/mo", copy("monthlyDetailText", context, pkg, "$3.99"))
            assertEquals("$duration free, then $29.99/yr", copy("yearlyDetailText", context, pkg, "$29.99", "$2.50"))
            assertEquals("$duration free", copy("onboardingPlanDetailText", context, pkg, "Billed now"))
        }
        val noTrial = pkg(option(null))
        assertEquals(context.getString(R.string.club_cta_join), copy("ctaText", context, noTrial, false))
        assertEquals("Billed at $3.99/mo.", copy("monthlyDetailText", context, noTrial, "$3.99"))
        assertEquals("Only $2.50/mo", copy("yearlyDetailText", context, noTrial, "$29.99", "$2.50"))
        assertEquals("Billed now", copy("onboardingPlanDetailText", context, noTrial, "Billed now"))
    }

    @Test fun `Portuguese buttons and feed copy use the selected month duration`() {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("pt-BR")) }
        val pt = context.createConfigurationContext(config)
        val pkg = pkg(option("P1M"), option("P2W"))
        assertEquals("Teste grátis por 1 mês", copy("ctaText", pt, pkg, false))
        assertEquals("1 mês grátis, depois R$ 9,90/mês", copy("monthlyDetailText", pt, pkg, "R$ 9,90"))
        assertEquals(
            "Experimente o Corus Club grátis por 1 mês e veja posts de pessoas que compartilham o seu gosto.",
            pt.getString(R.string.corus_feed_taste_matches_paywall_trial_body_format, "1 mês"),
        )
    }
}
