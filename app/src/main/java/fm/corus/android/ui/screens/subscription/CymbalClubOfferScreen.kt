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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.models.Period
import fm.corus.android.R
import fm.corus.android.ui.components.ToastManager
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

private fun trialDurationText(pkg: Package?): String? {
    val freeTrialOption = pkg?.product?.subscriptionOptions?.freeTrial ?: return null
    val freePhase = freeTrialOption.freePhase ?: return null
    return formatPeriod(freePhase.billingPeriod)
}

private fun formatPeriod(period: Period): String? {
    val v = period.value
    if (v <= 0) return null
    return when (period.unit) {
        Period.Unit.YEAR -> if (v == 1) "1 year" else "$v years"
        Period.Unit.MONTH -> if (v == 1) "1 month" else "$v months"
        Period.Unit.WEEK -> if (v == 1) "1 week" else "$v weeks"
        Period.Unit.DAY -> if (v == 1) "1 day" else "$v days"
        Period.Unit.UNKNOWN -> null
    }
}

private fun ctaText(selectedPackage: Package?, isClubMember: Boolean): String {
    if (isClubMember) return "You're a member!"
    val trial = trialDurationText(selectedPackage)
    return if (trial != null) "Try Free for $trial" else "Join the Club"
}

private fun monthlyDetailText(pkg: Package?, price: String): String {
    val trial = trialDurationText(pkg)
    return if (trial != null) "$trial free, then $price/mo" else "Billed at $price/mo."
}

private fun yearlyDetailText(pkg: Package?, price: String, monthlyEquivalent: String): String {
    val trial = trialDurationText(pkg)
    return if (trial != null) "$trial free, then $price/yr" else "Only $monthlyEquivalent/mo"
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

    // Log paywall shown on first composition
    LaunchedEffect(Unit) {
        viewModel.logPaywallShown()
    }

    // Handle purchase result
    LaunchedEffect(purchaseResult) {
        when (purchaseResult) {
            CymbalClubViewModel.PurchaseResult.Success -> {
                ToastManager.show("Welcome to the Club!")
                viewModel.clearResult()
                onBack()
            }
            CymbalClubViewModel.PurchaseResult.Restored -> {
                ToastManager.show("Purchases restored!")
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
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = CorusColors.Secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(CorusSpacing.xxl))

            // Spinning vinyl record
            fm.corus.android.ui.components.CymbalClubVinyl(size = 140.dp)

            Spacer(modifier = Modifier.height(CorusSpacing.xl))

            Text(
                text = "Join the Corus Club",
                style = CorusFont.appTitle,
                color = CorusColors.Text,
            )

            Spacer(modifier = Modifier.height(CorusSpacing.sm))

            Text(
                text = viewModel.source.subtitle,
                style = CorusFont.body,
                color = CorusColors.Secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = CorusSpacing.xl),
            )

            Spacer(modifier = Modifier.height(CorusSpacing.xxxl))

            // Features
            Column(
                modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                FeatureRow(icon = Icons.Filled.AllInclusive, text = "Unlimited posts")
                FeatureRow(icon = Icons.Filled.Verified, text = "Verified badge")
                FeatureRow(icon = Icons.Filled.Person, text = "Profile customization")
                FeatureRow(icon = Icons.Filled.QueueMusic, text = "Generate Spotify playlists")
                FeatureRow(icon = Icons.Filled.Favorite, text = "Help keep Corus running")
            }

            Spacer(modifier = Modifier.height(CorusSpacing.md))

            Text(
                text = "Corus is built by a small team. Club members help keep Corus ad-free, independent, and growing.",
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
            )

            Spacer(modifier = Modifier.height(CorusSpacing.xxxl))

            // Plan selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.xl),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                val monthlyPrice = monthlyPackage?.product?.price?.formatted ?: "$2.99"
                val yearlyPrice = yearlyPackage?.product?.price?.formatted ?: "$19.99"
                val yearlyMonthly = "${"$"}${String.format("%.2f", (yearlyPackage?.product?.price?.amountMicros?.let { it / 1_000_000.0 } ?: 19.99) / 12)}"

                PlanCard(
                    label = "Monthly",
                    price = "$monthlyPrice/mo",
                    detail = monthlyDetailText(monthlyPackage, monthlyPrice),
                    isSelected = selectedPlan == "monthly",
                    onClick = { selectedPlan = "monthly" },
                    modifier = Modifier.weight(1f),
                )
                PlanCard(
                    label = "Yearly",
                    price = "$yearlyPrice/yr",
                    detail = yearlyDetailText(yearlyPackage, yearlyPrice, yearlyMonthly),
                    isSelected = selectedPlan == "yearly",
                    onClick = { selectedPlan = "yearly" },
                    modifier = Modifier.weight(1f),
                    badge = "SAVE 58%",
                )
            }

            Spacer(modifier = Modifier.height(CorusSpacing.xxl))

            // Inline error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    style = CorusFont.caption,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                )
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
            }

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
                        text = ctaText(selectedPackage, isClubMember),
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
                    Text("Restore Purchases", style = CorusFont.caption, color = CorusColors.Secondary)
                }
                TextButton(onClick = {
                    try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://corus.fm/terms"))) } catch (_: Exception) { }
                }) {
                    Text("Terms", style = CorusFont.caption, color = CorusColors.Secondary)
                }
                TextButton(onClick = {
                    try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://corus.fm/privacy"))) } catch (_: Exception) { }
                }) {
                    Text("Privacy", style = CorusFont.caption, color = CorusColors.Secondary)
                }
            }

            Spacer(modifier = Modifier.height(CorusSpacing.xxxl))
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

    // Log paywall shown on first composition
    LaunchedEffect(Unit) {
        viewModel.logPaywallShown()
    }

    LaunchedEffect(purchaseResult) {
        when (purchaseResult) {
            CymbalClubViewModel.PurchaseResult.Success -> {
                ToastManager.show("Welcome to the Club!")
                viewModel.clearResult()
                onDismiss()
            }
            CymbalClubViewModel.PurchaseResult.Restored -> {
                ToastManager.show("Purchases restored!")
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.96f)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Close button
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
                    contentDescription = "Close",
                    tint = CorusColors.Secondary,
                )
            }
        }

        // Spinning vinyl record
        fm.corus.android.ui.components.CymbalClubVinyl(size = 120.dp)

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        Text(
            text = "Join the Corus Club",
            style = CorusFont.appTitle,
            color = CorusColors.Text,
        )

        Spacer(modifier = Modifier.height(CorusSpacing.xs))

        Text(
            text = source.subtitle,
            style = CorusFont.body,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.xl))

        Column(
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        ) {
            FeatureRow(icon = Icons.Filled.AllInclusive, text = "Unlimited posts")
            FeatureRow(icon = Icons.Filled.Verified, text = "Verified badge")
            FeatureRow(icon = Icons.Filled.Person, text = "Profile customization")
            FeatureRow(icon = Icons.Filled.QueueMusic, text = "Generate Spotify playlists")
            FeatureRow(icon = Icons.Filled.Favorite, text = "Help keep Corus running")
        }

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        Text(
            text = "Corus is built by a small team. Club members help keep Corus ad-free, independent, and growing.",
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.xl))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            val monthlyPrice = monthlyPackage?.product?.price?.formatted ?: "$2.99"
            val yearlyPrice = yearlyPackage?.product?.price?.formatted ?: "$19.99"
            val yearlyMonthly = "${"$"}${String.format("%.2f", (yearlyPackage?.product?.price?.amountMicros?.let { it / 1_000_000.0 } ?: 19.99) / 12)}"

            PlanCard(
                label = "Monthly",
                price = "$monthlyPrice/mo",
                detail = monthlyDetailText(monthlyPackage, monthlyPrice),
                isSelected = selectedPlan == "monthly",
                onClick = { selectedPlan = "monthly" },
                modifier = Modifier.weight(1f),
            )
            PlanCard(
                label = "Yearly",
                price = "$yearlyPrice/yr",
                detail = yearlyDetailText(yearlyPackage, yearlyPrice, yearlyMonthly),
                isSelected = selectedPlan == "yearly",
                onClick = { selectedPlan = "yearly" },
                modifier = Modifier.weight(1f),
                badge = "SAVE 58%",
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // Inline error message
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                style = CorusFont.caption,
                color = Color.Red,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = CorusSpacing.xl),
            )
            Spacer(modifier = Modifier.height(CorusSpacing.sm))
        }

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
                    text = ctaText(selectedPackage, isClubMember),
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
                Text("Restore Purchases", style = CorusFont.caption, color = CorusColors.Secondary)
            }
            TextButton(onClick = {
                try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://corus.fm/terms"))) } catch (_: Exception) { }
            }) {
                Text("Terms", style = CorusFont.caption, color = CorusColors.Secondary)
            }
            TextButton(onClick = {
                try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://corus.fm/privacy"))) } catch (_: Exception) { }
            }) {
                Text("Privacy", style = CorusFont.caption, color = CorusColors.Secondary)
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = CorusColors.Accent,
            modifier = Modifier.size(20.dp),
        )
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
