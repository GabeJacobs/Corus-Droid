package fm.corus.android.ui.screens.subscription

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.models.Period
import fm.corus.android.R
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.VennDiagramIcon
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

// Inside a ModalBottomSheet, LocalContext.current is a ContextWrapper around the
// Activity, not the Activity itself, so a direct `as? Activity` cast returns null
// and purchase() never fires. Walk the wrapper chain to find the host Activity.
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// --- Trial detection helpers ---

private fun trialDurationText(context: Context, pkg: Package?): String? {
    val freeTrialOption = pkg?.product?.subscriptionOptions?.freeTrial ?: return null
    val freePhase = freeTrialOption.freePhase ?: return null
    return formatPeriod(context, freePhase.billingPeriod)
}

private fun formatPeriod(context: Context, period: Period): String? {
    val v = period.value
    if (v <= 0) return null
    return when (period.unit) {
        Period.Unit.YEAR -> if (v == 1) context.getString(R.string.club_period_year_one) else context.getString(R.string.club_period_year_format, v)
        Period.Unit.MONTH -> if (v == 1) context.getString(R.string.club_period_month_one) else context.getString(R.string.club_period_month_format, v)
        Period.Unit.WEEK -> if (v == 1) context.getString(R.string.club_period_week_one) else context.getString(R.string.club_period_week_format, v)
        Period.Unit.DAY -> if (v == 1) context.getString(R.string.club_period_day_one) else context.getString(R.string.club_period_day_format, v)
        Period.Unit.UNKNOWN -> null
    }
}

private fun ctaText(context: Context, selectedPackage: Package?, isClubMember: Boolean): String {
    if (isClubMember) return context.getString(R.string.club_cta_member)
    val trial = trialDurationText(context, selectedPackage)
    return if (trial != null) context.getString(R.string.club_cta_try_free_format, trial) else context.getString(R.string.club_cta_join)
}

private fun monthlyDetailText(context: Context, pkg: Package?, price: String): String {
    val trial = trialDurationText(context, pkg)
    return if (trial != null) context.getString(R.string.club_monthly_detail_trial_format, trial, price)
    else context.getString(R.string.club_monthly_detail_billed_format, price)
}

private fun yearlyDetailText(context: Context, pkg: Package?, price: String, monthlyEquivalent: String): String {
    val trial = trialDurationText(context, pkg)
    return if (trial != null) context.getString(R.string.club_yearly_detail_trial_format, trial, price)
    else context.getString(R.string.club_yearly_detail_only_format, monthlyEquivalent)
}

// Percent saved by the yearly plan vs. 12× the monthly plan, computed live from
// the RevenueCat prices so remote price changes are reflected. Returns null when
// prices aren't loaded yet or the discount is negligible (<5%), so the caller
// shows no badge rather than a stale hardcoded percentage.
private fun savingsBadge(monthly: Package?, yearly: Package?): String? {
    val m = monthly?.product?.price?.amountMicros ?: return null
    val y = yearly?.product?.price?.amountMicros ?: return null
    if (m <= 0L) return null
    val yearlyAsMonthly = y / 12.0
    if (yearlyAsMonthly >= m) return null
    val pct = Math.round((1.0 - yearlyAsMonthly / m) * 100).toInt()
    if (pct < 5) return null
    return "SAVE $pct%"
}

/**
 * The FULL-SCREEN paywall's flexible middle region: the header + feature list,
 * sitting between the close button and the pinned plan cards / CTA.
 *
 * A full-screen paywall can't shrink to its content the way the bottom sheet does,
 * so its leftover space is CENTERED rather than pinned to the top — matching iOS
 * (`.frame(minHeight: proxy.size.height, alignment: .center)`). A plain scrolling
 * Column top-aligns instead, so on a tall phone every spare pixel pools into one
 * dead gap between the disclaimer and the plan cards. The min-height match is what
 * gives Arrangement.Center something to center within — a scrolling Column is
 * otherwise only as tall as its content. Taller-than-viewport content still
 * scrolls normally, so short screens are unaffected.
 *
 * (The sheet variant instead wraps its height to the content, so it needs no
 * centering — see CymbalClubOfferSheet.)
 */
@Composable
internal fun ColumnScope.CenteredScrollRegion(
    verticalPadding: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(vertical = verticalPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

// --- Full-screen paywall ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CymbalClubOfferScreen(
    viewModel: CymbalClubViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val packages by viewModel.packages.collectAsState()
    val isPurchasing by viewModel.isPurchasing.collectAsState()
    val purchaseResult by viewModel.purchaseResult.collectAsState()
    val isClubMember by viewModel.isClubMember.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    var selectedPlan by remember { mutableStateOf(viewModel.defaultPlan) }

    val monthlyPackage = packages.firstOrNull { it.identifier == "\$rc_monthly" }
    val yearlyPackage = packages.firstOrNull { it.identifier == "\$rc_annual" }
    val selectedPackage = if (selectedPlan == "yearly") yearlyPackage else monthlyPackage

    // Log paywall shown on first composition. The view model is scoped to the
    // host screen, so a stale error from a previous presentation would still
    // be set — clear it so each presentation starts clean.
    LaunchedEffect(Unit) {
        viewModel.clearError()
        viewModel.logPaywallShown()
    }

    // Handle purchase result
    LaunchedEffect(purchaseResult) {
        when (purchaseResult) {
            CymbalClubViewModel.PurchaseResult.Success -> {
                ToastManager.show(context.getString(R.string.club_toast_welcome))
                viewModel.clearResult()
                onBack()
            }
            CymbalClubViewModel.PurchaseResult.Restored -> {
                ToastManager.show(context.getString(R.string.club_toast_restored))
                viewModel.clearResult()
            }
            CymbalClubViewModel.PurchaseResult.Cancelled -> {
                // Silent dismiss — no toast, no error (matches iOS)
                viewModel.clearResult()
            }
            CymbalClubViewModel.PurchaseResult.NothingToRestore -> {
                viewModel.clearResult()
            }
            CymbalClubViewModel.PurchaseResult.Failed -> {
                viewModel.clearResult()
            }
            null -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = {
                        viewModel.logPaywallDismissed()
                        onBack()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.club_cd_close), tint = CorusColors.Secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        // Two-zone layout (mirrors iOS): the header + features scroll in a
        // flexible region, while the plan cards, CTA, and footer links are
        // PINNED to the bottom so "Restore Purchases" / "Terms" / "Privacy" are
        // always visible and can never be pushed under the CTA. The Scaffold's
        // content padding already reserves the system navigation-bar inset.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Scrollable header + features ──
            CenteredScrollRegion(verticalPadding = CorusSpacing.xl) {
                // Spinning vinyl record
                fm.corus.android.ui.components.CymbalClubVinyl(size = 140.dp)

                Spacer(modifier = Modifier.height(CorusSpacing.xl))

                if (viewModel.source == PaywallSource.POST_LIMIT) {
                    Text(
                        text = stringResource(R.string.club_post_limit_eyebrow),
                        style = CorusFont.caption.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
                        ),
                        color = CorusColors.Accent,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.xs))
                }

                // Feature eyebrow when arriving from Taste Matches — names the perk in
                // the brand color, mirroring the cold-start eyebrow.
                if (viewModel.source == PaywallSource.TASTE_MATCHES) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        VennDiagramIcon(
                            size = 15.dp,
                            color = CorusColors.Accent,
                            shadedIntersection = true,
                        )
                        Text(
                            text = stringResource(R.string.search_section_taste_matches),
                            style = CorusFont.sectionHeader,
                            color = CorusColors.Accent,
                        )
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.xs))
                }

                Text(
                    text = stringResource(R.string.club_title),
                    style = CorusFont.appTitle,
                    color = CorusColors.Text,
                )

                Spacer(modifier = Modifier.height(CorusSpacing.sm))

                // Post-limit: surface the trial with its duration when available,
                // otherwise the source's default subtitle ("Remove posting limits").
                val trial = trialDurationText(context, selectedPackage)
                val subtitleText = if (viewModel.source == PaywallSource.POST_LIMIT && trial != null)
                    context.getString(R.string.club_subtitle_post_limit_trial_format, trial)
                else
                    viewModel.source.subtitle

                Text(
                    text = subtitleText,
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                )

                Spacer(modifier = Modifier.height(CorusSpacing.xxxl))

                // Features. When Taste Matches is live, advertise it as the headline
                // perk right under Unlimited posts, taking the badge row's slot so the
                // list stays at 5 (mirrors iOS).
                val tasteMatchesEnabled = viewModel.remoteConfig.tasteMatchesEnabled
                Column(
                    modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    FeatureRow(icon = Icons.Filled.AllInclusive, text = stringResource(R.string.club_feature_unlimited))
                    if (tasteMatchesEnabled) {
                        FeatureRow(text = stringResource(R.string.club_feature_taste_matches)) {
                            VennDiagramIcon(size = 20.dp, color = CorusColors.Accent, shadedIntersection = true)
                        }
                    }
                    FeatureRow(icon = Icons.Filled.Brush, text = stringResource(R.string.club_feature_customization))
                    FeatureRow(icon = Icons.Filled.QueueMusic, text = stringResource(R.string.club_feature_playlists))
                    FeatureRow(icon = Icons.Filled.Favorite, text = stringResource(R.string.club_feature_support))
                    if (!tasteMatchesEnabled) {
                        FeatureRow(icon = Icons.Filled.Verified, text = stringResource(R.string.club_feature_verified))
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))

                Text(
                    text = stringResource(R.string.club_disclaimer),
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
                )
            }

            // ── Pinned bottom: plan cards, CTA, footer (always visible) ──
            // Extra top padding sets the plan cards apart from the features
            // above, while the gap below them (before the CTA) stays tight —
            // grouping the cards with the button (matches iOS).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CorusSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Plan selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.xl),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    val monthlyPrice = monthlyPackage?.product?.price?.formatted ?: "$3.99"
                    val yearlyPrice = yearlyPackage?.product?.price?.formatted ?: "$29.99"
                    val yearlyMonthly = "${"$"}${String.format("%.2f", (yearlyPackage?.product?.price?.amountMicros?.let { it / 1_000_000.0 } ?: 29.99) / 12)}"

                    PlanCard(
                        label = stringResource(R.string.club_plan_monthly),
                        price = stringResource(R.string.club_plan_monthly_price_format, monthlyPrice),
                        detail = monthlyDetailText(context, monthlyPackage, monthlyPrice),
                        isSelected = selectedPlan == "monthly",
                        onClick = { selectedPlan = "monthly" },
                        modifier = Modifier.weight(1f),
                    )
                    PlanCard(
                        label = stringResource(R.string.club_plan_yearly),
                        price = stringResource(R.string.club_plan_yearly_price_format, yearlyPrice),
                        detail = yearlyDetailText(context, yearlyPackage, yearlyPrice, yearlyMonthly),
                        isSelected = selectedPlan == "yearly",
                        onClick = { selectedPlan = "yearly" },
                        modifier = Modifier.weight(1f),
                        badge = savingsBadge(monthlyPackage, yearlyPackage),
                    )
                }

                // Inline purchase error (rare) renders only when present, so it
                // doesn't reserve a permanent gap between the plan cards and the CTA.
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                    Text(
                        text = it,
                        style = CorusFont.caption,
                        color = Color.Red,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                    )
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))

                // CTA button
                Button(
                    onClick = {
                        if (activity != null && selectedPackage != null) {
                            viewModel.purchase(activity, selectedPackage!!, selectedPlan)
                        }
                    },
                    enabled = !isPurchasing && selectedPackage != null && !isClubMember,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.xl)
                        .height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CorusColors.Accent,
                        contentColor = Color.White,
                    ),
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(
                            text = ctaText(context, selectedPackage, isClubMember),
                            style = CorusFont.button,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))

                // Restore purchases + links
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { viewModel.restorePurchases() }) {
                        Text(stringResource(R.string.club_restore_purchases), style = CorusFont.caption, color = CorusColors.Secondary)
                    }
                    TextButton(onClick = {
                        try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://corus.fm/terms"))) } catch (_: Exception) { }
                    }) {
                        Text(stringResource(R.string.club_terms), style = CorusFont.caption, color = CorusColors.Secondary)
                    }
                    TextButton(onClick = {
                        try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://corus.fm/privacy"))) } catch (_: Exception) { }
                    }) {
                        Text(stringResource(R.string.club_privacy), style = CorusFont.caption, color = CorusColors.Secondary)
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.lg))
            }
        }
    }
}

/**
 * Bottom-sheet content for the Corus Club offer / paywall.
 * Shown as a ModalBottomSheet from ProfileScreen (matches iOS sheet presentation).
 */
@Composable
fun CymbalClubOfferSheet(
    viewModel: CymbalClubViewModel = hiltViewModel(),
    source: PaywallSource = PaywallSource.DEFAULT,
    onDismiss: () -> Unit = {},
    /** Called on [CymbalClubViewModel.PurchaseResult.Success] (never on a plain
     *  dismiss), before [onDismiss] — so the host can complete the action this
     *  paywall interrupted (e.g. the feed switching to Taste Matches).
     *  Membership is already synced to Firestore by then (the repository's
     *  purchase path awaits the sync before reporting success), so gated
     *  server calls made from the callback see the member status. */
    onPurchaseSuccess: () -> Unit = {},
) {
    val packages by viewModel.packages.collectAsState()
    val isPurchasing by viewModel.isPurchasing.collectAsState()
    val purchaseResult by viewModel.purchaseResult.collectAsState()
    val isClubMember by viewModel.isClubMember.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    var selectedPlan by remember { mutableStateOf(viewModel.defaultPlan) }

    val monthlyPackage = packages.firstOrNull { it.identifier == "\$rc_monthly" }
    val yearlyPackage = packages.firstOrNull { it.identifier == "\$rc_annual" }
    val selectedPackage = if (selectedPlan == "yearly") yearlyPackage else monthlyPackage

    // Log paywall shown on first composition. The view model is scoped to the
    // host screen, so a stale error from a previous presentation would still
    // be set — clear it so each presentation starts clean.
    LaunchedEffect(Unit) {
        viewModel.clearError()
        viewModel.logPaywallShown()
    }

    LaunchedEffect(purchaseResult) {
        when (purchaseResult) {
            CymbalClubViewModel.PurchaseResult.Success -> {
                ToastManager.show(context.getString(R.string.club_toast_welcome))
                viewModel.clearResult()
                onPurchaseSuccess()
                onDismiss()
            }
            CymbalClubViewModel.PurchaseResult.Restored -> {
                ToastManager.show(context.getString(R.string.club_toast_restored))
                viewModel.clearResult()
            }
            CymbalClubViewModel.PurchaseResult.Cancelled -> {
                // Silent dismiss — no toast, no error (matches iOS)
                viewModel.clearResult()
            }
            CymbalClubViewModel.PurchaseResult.NothingToRestore -> {
                viewModel.clearResult()
            }
            CymbalClubViewModel.PurchaseResult.Failed -> {
                viewModel.clearResult()
            }
            null -> {}
        }
    }

    // Sheet height WRAPS the content (vinyl, ~5 feature rows, two plan cards,
    // CTA, footer ≈ 690dp), capped just shy of the screen. A fixed 94%-height
    // sheet looked right on a normal phone but on a tall device (Pixel 9 Pro,
    // ~950dp) it stretched far past the content, leaving a dead band of empty
    // space — so the sheet now shrinks to a right-sized card there while short
    // phones still get a near-full, scrollable sheet (see the fill=false weight
    // on the scroll region below). The cap keeps it below the status bar /
    // camera cutout and away from an awkward in-between height.
    //
    // Two-zone layout (mirrors iOS): the header + features scroll in a flexible
    // region, while the plan cards, CTA, and footer links are PINNED to the
    // bottom. This guarantees "Restore Purchases" / "Terms" / "Privacy" are
    // always visible — they can never be pushed under the CTA or the system
    // navigation bar (navigationBarsPadding reserves the gesture/button-bar
    // inset, since a ModalBottomSheet only insets its top edge).
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.94f).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Close button (fixed at the top)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = CorusSpacing.md),
            contentAlignment = Alignment.TopEnd,
        ) {
            IconButton(onClick = {
                viewModel.logPaywallDismissed()
                onDismiss()
            }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.club_cd_close),
                    tint = CorusColors.Secondary,
                )
            }
        }

        // ── Header + features ──
        // `weight(1f, fill = false)` lets this region SHRINK to its content so the
        // whole sheet can wrap (see maxSheetHeight above) — yet still get a bounded
        // height and scroll when content would overflow a short screen's cap.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = CorusSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Spinning vinyl record
            fm.corus.android.ui.components.CymbalClubVinyl(size = 120.dp)

            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            if (source == PaywallSource.POST_LIMIT) {
                Text(
                    text = stringResource(R.string.club_post_limit_eyebrow),
                    style = CorusFont.caption.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                    color = CorusColors.Accent,
                )
                Spacer(modifier = Modifier.height(CorusSpacing.xs))
            }

            // Feature eyebrow when arriving from Taste Matches — names the perk
            // in the brand color (mirrors the full-screen paywall + iOS).
            if (source == PaywallSource.TASTE_MATCHES) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    VennDiagramIcon(
                        size = 15.dp,
                        color = CorusColors.Accent,
                        shadedIntersection = true,
                    )
                    Text(
                        text = stringResource(R.string.search_section_taste_matches),
                        style = CorusFont.sectionHeader,
                        color = CorusColors.Accent,
                    )
                }
                Spacer(modifier = Modifier.height(CorusSpacing.xs))
            }

            Text(
                text = stringResource(R.string.club_title),
                style = CorusFont.appTitle,
                color = CorusColors.Text,
            )

            Spacer(modifier = Modifier.height(CorusSpacing.xs))

            // Post-limit: surface the trial with its duration when available,
            // otherwise the source's default subtitle ("Remove posting limits").
            val trial = trialDurationText(context, selectedPackage)
            val subtitleText = if (source == PaywallSource.POST_LIMIT && trial != null)
                context.getString(R.string.club_subtitle_post_limit_trial_format, trial)
            else
                source.subtitle

            Text(
                text = subtitleText,
                style = CorusFont.body,
                color = CorusColors.Secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = CorusSpacing.xl),
            )

            Spacer(modifier = Modifier.height(CorusSpacing.xl))

            // Features. When Taste Matches is live, advertise it as the headline
            // perk right under Unlimited posts, taking the badge row's slot so
            // the list stays compact (mirrors the full-screen paywall + iOS).
            val tasteMatchesEnabled = viewModel.remoteConfig.tasteMatchesEnabled
            Column(
                modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                if (source != PaywallSource.FIRST_POST) {
                    FeatureRow(icon = Icons.Filled.AllInclusive, text = stringResource(R.string.club_feature_unlimited))
                }
                if (tasteMatchesEnabled) {
                    FeatureRow(text = stringResource(R.string.club_feature_taste_matches)) {
                        VennDiagramIcon(size = 20.dp, color = CorusColors.Accent, shadedIntersection = true)
                    }
                }
                // Source-specific perk sits right under posts so it's in the list,
                // not just the subtitle.
                if (source == PaywallSource.FAVORITE_LIMIT) {
                    FeatureRow(icon = Icons.Filled.Star, text = stringResource(R.string.club_feature_favorites))
                }
                FeatureRow(icon = Icons.Filled.Brush, text = stringResource(R.string.club_feature_customization))
                FeatureRow(icon = Icons.Filled.QueueMusic, text = stringResource(R.string.club_feature_playlists))
                FeatureRow(icon = Icons.Filled.Favorite, text = stringResource(R.string.club_feature_support))
                // Drop the badge line on the favorites or Taste Matches paywalls
                // to keep the list compact on phones (room is tight).
                if (source != PaywallSource.FAVORITE_LIMIT && !tasteMatchesEnabled) {
                    FeatureRow(icon = Icons.Filled.Verified, text = stringResource(R.string.club_feature_verified))
                }
            }

            Spacer(modifier = Modifier.height(CorusSpacing.sm))

            Text(
                text = stringResource(R.string.club_disclaimer),
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
            )
        }

        // ── Pinned bottom: plan cards, CTA, footer (always visible) ──
        // Extra top padding sets the plan cards apart from the features above,
        // while the gap below them (before the CTA) stays tight — grouping the
        // cards with the button (matches iOS).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CorusSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.xl),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                val monthlyPrice = monthlyPackage?.product?.price?.formatted ?: stringResource(R.string.club_plan_monthly_default_price)
                val yearlyPrice = yearlyPackage?.product?.price?.formatted ?: stringResource(R.string.club_plan_yearly_default_price)
                val yearlyMonthly = "${"$"}${String.format("%.2f", (yearlyPackage?.product?.price?.amountMicros?.let { it / 1_000_000.0 } ?: 29.99) / 12)}"

                PlanCard(
                    label = stringResource(R.string.club_plan_monthly),
                    price = stringResource(R.string.club_plan_monthly_price_format, monthlyPrice),
                    detail = monthlyDetailText(context, monthlyPackage, monthlyPrice),
                    isSelected = selectedPlan == "monthly",
                    onClick = { selectedPlan = "monthly" },
                    modifier = Modifier.weight(1f),
                )
                PlanCard(
                    label = stringResource(R.string.club_plan_yearly),
                    price = stringResource(R.string.club_plan_yearly_price_format, yearlyPrice),
                    detail = yearlyDetailText(context, yearlyPackage, yearlyPrice, yearlyMonthly),
                    isSelected = selectedPlan == "yearly",
                    onClick = { selectedPlan = "yearly" },
                    modifier = Modifier.weight(1f),
                    badge = savingsBadge(monthlyPackage, yearlyPackage),
                )
            }

            // Inline purchase error (rare) renders only when present, so it
            // doesn't reserve a permanent gap between the plan cards and the CTA.
            errorMessage?.let {
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                Text(
                    text = it,
                    style = CorusFont.caption,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                )
            }

            Spacer(modifier = Modifier.height(CorusSpacing.md))

            Button(
                onClick = {
                    if (activity != null && selectedPackage != null) {
                        viewModel.purchase(activity, selectedPackage!!, selectedPlan)
                    }
                },
                enabled = !isPurchasing && selectedPackage != null && !isClubMember,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.xl)
                    .height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CorusColors.Accent,
                    contentColor = Color.White,
                ),
            ) {
                if (isPurchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        text = ctaText(context, selectedPackage, isClubMember),
                        style = CorusFont.button,
                    )
                }
            }

            Spacer(modifier = Modifier.height(CorusSpacing.xs))

            Row(
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { viewModel.restorePurchases() }) {
                    Text(stringResource(R.string.club_restore_purchases), style = CorusFont.caption, color = CorusColors.Secondary)
                }
                TextButton(onClick = {
                    try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://corus.fm/terms"))) } catch (_: Exception) { }
                }) {
                    Text(stringResource(R.string.club_terms), style = CorusFont.caption, color = CorusColors.Secondary)
                }
                TextButton(onClick = {
                    try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://corus.fm/privacy"))) } catch (_: Exception) { }
                }) {
                    Text(stringResource(R.string.club_privacy), style = CorusFont.caption, color = CorusColors.Secondary)
                }
            }

            Spacer(modifier = Modifier.height(CorusSpacing.sm))
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    FeatureRow(text = text) {
        Icon(
            icon,
            contentDescription = null,
            tint = CorusColors.Accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Feature row with a custom leading icon (e.g. the Taste Matches Venn). */
@Composable
private fun FeatureRow(text: String, icon: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        icon()
        Text(text = text, style = CorusFont.bodyMedium, color = CorusColors.Text)
    }
}

@Composable
private fun PlanCard(
    label: String,
    price: String,
    detail: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val borderColor = if (isSelected) CorusColors.Accent else CorusColors.Secondary.copy(alpha = 0.2f)

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .border(borderWidth, borderColor, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .clickable(onClick = onClick)
                .padding(CorusSpacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, style = CorusFont.bodyMedium, color = CorusColors.Text)
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (isSelected) CorusColors.Accent else CorusColors.Secondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(CorusSpacing.xs))
            Text(text = price, style = CorusFont.songTitleLarge, color = CorusColors.Text)
            Spacer(modifier = Modifier.height(CorusSpacing.xs))
            Text(text = detail, style = CorusFont.caption, color = CorusColors.Secondary)
        }

        // Badge overlay on top of card
        if (badge != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-10).dp),
                shape = RoundedCornerShape(50),
                color = CorusColors.Accent,
            ) {
                Text(
                    text = badge,
                    style = CorusFont.caption.copy(
                        fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = CorusSpacing.sm, vertical = 4.dp),
                )
            }
        }
    }
}
