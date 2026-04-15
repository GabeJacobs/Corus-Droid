package fm.corus.android.ui.screens.subscription

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
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
import fm.corus.android.R
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

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
    val context = LocalContext.current
    val activity = context as? Activity

    var selectedPlan by remember { mutableStateOf("monthly") }

    val monthlyPackage = packages.firstOrNull { it.identifier == "\$rc_monthly" }
    val yearlyPackage = packages.firstOrNull { it.identifier == "\$rc_annual" }
    val selectedPackage = if (selectedPlan == "yearly") yearlyPackage else monthlyPackage

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
            CymbalClubViewModel.PurchaseResult.NothingToRestore -> {
                ToastManager.show("No purchases to restore")
                viewModel.clearResult()
            }
            CymbalClubViewModel.PurchaseResult.Failed -> {
                ToastManager.show("Purchase failed")
                viewModel.clearResult()
            }
            null -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CorusColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
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

            // Club icon
            Image(
                painter = painterResource(R.drawable.vinyl_black),
                contentDescription = null,
                modifier = Modifier.size(140.dp),
                contentScale = ContentScale.Fit,
            )

            Spacer(modifier = Modifier.height(CorusSpacing.xl))

            Text(
                text = "Join the Corus Club",
                style = CorusFont.appTitle,
                color = CorusColors.Text,
            )

            Spacer(modifier = Modifier.height(CorusSpacing.sm))

            Text(
                text = "Support Corus. Get Perks.",
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
                FeatureRow(icon = Icons.Filled.Palette, text = "Custom vinyl colors")
                FeatureRow(icon = Icons.Filled.Person, text = "Profile customization")
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

                PlanCard(
                    label = "Monthly",
                    price = "$monthlyPrice/mo",
                    detail = "Billed at $monthlyPrice/mo.",
                    isSelected = selectedPlan == "monthly",
                    onClick = { selectedPlan = "monthly" },
                    modifier = Modifier.weight(1f),
                )
                PlanCard(
                    label = "Yearly",
                    price = "$yearlyPrice/yr",
                    detail = "Only ${"$"}${String.format("%.2f", (yearlyPackage?.product?.price?.amountMicros?.let { it / 1_000_000.0 } ?: 19.99) / 12)}/mo",
                    isSelected = selectedPlan == "yearly",
                    onClick = { selectedPlan = "yearly" },
                    modifier = Modifier.weight(1f),
                    badge = "SAVE 58%",
                )
            }

            Spacer(modifier = Modifier.height(CorusSpacing.xxl))

            // CTA button
            Button(
                onClick = {
                    if (activity != null && selectedPackage != null) {
                        viewModel.purchase(activity, selectedPackage!!)
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
                        text = if (isClubMember) "You're a member!" else "Join the Club",
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
    onDismiss: () -> Unit = {},
) {
    val packages by viewModel.packages.collectAsState()
    val isPurchasing by viewModel.isPurchasing.collectAsState()
    val purchaseResult by viewModel.purchaseResult.collectAsState()
    val isClubMember by viewModel.isClubMember.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    var selectedPlan by remember { mutableStateOf("monthly") }

    val monthlyPackage = packages.firstOrNull { it.identifier == "\$rc_monthly" }
    val yearlyPackage = packages.firstOrNull { it.identifier == "\$rc_annual" }
    val selectedPackage = if (selectedPlan == "yearly") yearlyPackage else monthlyPackage

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
            CymbalClubViewModel.PurchaseResult.NothingToRestore -> {
                ToastManager.show("No purchases to restore")
                viewModel.clearResult()
            }
            CymbalClubViewModel.PurchaseResult.Failed -> {
                ToastManager.show("Purchase failed")
                viewModel.clearResult()
            }
            null -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Close button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = CorusSpacing.md, top = CorusSpacing.md),
            contentAlignment = Alignment.TopEnd,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = CorusColors.Secondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.md))

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
            text = "Support Corus. Get Perks.",
            style = CorusFont.body,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.xxxl))

        Column(
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            FeatureRow(icon = Icons.Filled.AllInclusive, text = "Unlimited posts")
            FeatureRow(icon = Icons.Filled.Verified, text = "Verified badge")
            FeatureRow(icon = Icons.Filled.Palette, text = "Custom vinyl colors")
            FeatureRow(icon = Icons.Filled.Person, text = "Profile customization")
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            val monthlyPrice = monthlyPackage?.product?.price?.formatted ?: "$2.99"
            val yearlyPrice = yearlyPackage?.product?.price?.formatted ?: "$19.99"

            PlanCard(
                label = "Monthly",
                price = "$monthlyPrice/mo",
                detail = "Billed at $monthlyPrice/mo.",
                isSelected = selectedPlan == "monthly",
                onClick = { selectedPlan = "monthly" },
                modifier = Modifier.weight(1f),
            )
            PlanCard(
                label = "Yearly",
                price = "$yearlyPrice/yr",
                detail = "Only ${"$"}${String.format("%.2f", (yearlyPackage?.product?.price?.amountMicros?.let { it / 1_000_000.0 } ?: 19.99) / 12)}/mo",
                isSelected = selectedPlan == "yearly",
                onClick = { selectedPlan = "yearly" },
                modifier = Modifier.weight(1f),
                badge = "SAVE 58%",
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xxl))

        Button(
            onClick = {
                if (activity != null && selectedPackage != null) {
                    viewModel.purchase(activity, selectedPackage!!)
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
                    text = if (isClubMember) "You're a member!" else "Join the Club",
                    style = CorusFont.button,
                )
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.md))

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
