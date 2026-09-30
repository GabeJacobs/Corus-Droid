package fm.corus.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import fm.corus.android.ui.components.CorusModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.GiftDefinition
import fm.corus.android.data.repository.GiftInventory
import fm.corus.android.data.repository.GiftSendResult
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.screens.notifications.GiftNotificationArtwork
import fm.corus.android.ui.screens.subscription.CymbalClubOfferSheet
import fm.corus.android.ui.screens.subscription.PaywallSource
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.bottomSheetMaxHeight
import com.valentinilk.shimmer.shimmer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.delay
import kotlin.math.ceil

internal fun giftRefillCountdown(nextRefillAtMs: Long, nowMs: Long): String {
    val hours = ceil((nextRefillAtMs - nowMs).coerceAtLeast(0) / 3_600_000.0).toInt()
    return "${hours / 24}d ${hours % 24}h"
}

internal fun shouldShowGiftClubOffer(capacity: Int): Boolean = capacity < 3
internal fun areGiftControlsEnabled(available: Int): Boolean = available > 0
internal fun giftPickerIntroResource(available: Int?): Int =
    if (available == 0) R.string.gift_picker_empty_intro else R.string.gift_picker_intro

/** Sheet state for the gift picker hosts, with two guards against accidental
 *  dismissal (Instagram-style):
 *  1. While the keyboard is up, a swipe-down only closes the keyboard.
 *  2. Releasing after a short drag springs the sheet back up instead of
 *     dismissing it. A deliberate drag past ~a quarter of the screen, a tap on
 *     the dimmed area, or the back gesture still dismiss. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun rememberGuardedSheetState(): SheetState {
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val dismissDragPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() } * DISMISS_DRAG_FRACTION
    val dismissDrag by rememberUpdatedState(dismissDragPx)
    // Top edge of the sheet when it is at rest (updated whenever it settles).
    val restingTop = remember { mutableFloatStateOf(Float.NaN) }
    val holder = remember { arrayOfNulls<SheetState>(1) }
    val confirm = remember {
        { target: SheetValue ->
            if (target != SheetValue.Hidden) {
                true
            } else {
                val top = runCatching { holder[0]?.requireOffset() }.getOrNull()
                val dragged = if (top != null && !restingTop.floatValue.isNaN()) top - restingTop.floatValue else 0f
                // dragged ~ 0 means scrim tap / back / a programmatic hide(): always allow.
                // A real drag with the keyboard up only closes the keyboard; a real but
                // short drag springs back instead of dismissing.
                when {
                    dragged <= 4f -> true
                    imeVisible -> { keyboard?.hide(); false }
                    else -> dragged >= dismissDrag
                }
            }
        }
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = confirm)
    holder[0] = state
    LaunchedEffect(state) {
        snapshotFlow { runCatching { state.requireOffset() }.getOrNull() }
            .filterNotNull()
            .debounce(500)
            .collect { if (state.currentValue == SheetValue.Expanded) restingTop.floatValue = it }
    }
    return state
}

/** Hides the keyboard when the user starts scrolling and leaves the gesture
 *  untouched, so the list still scrolls and the sheet is never dismissed. */
internal class HideKeyboardOnScrollConnection(
    private val hideKeyboard: () -> Unit,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) hideKeyboard()
        return Offset.Zero
    }
}

@Composable
fun Modifier.hideKeyboardOnScroll(): Modifier {
    val keyboard: SoftwareKeyboardController? = LocalSoftwareKeyboardController.current
    val connection = remember(keyboard) { HideKeyboardOnScrollConnection { keyboard?.hide() } }
    return nestedScroll(connection)
}

private const val DISMISS_DRAG_FRACTION = 0.25f
private const val GIFT_SENT_HOLD_MS = 2_000L
private const val GIFT_ALREADY_SENT_HOLD_MS = 2_600L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftSelectionSheet(
    post: CymbalPost,
    onDismiss: () -> Unit,
    onSent: (GiftSendResult) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticManager.current
    val viewModel: GiftSelectionViewModel = hiltViewModel(key = "gift-${post.id}")
    val state by viewModel.state.collectAsState()
    val hasClubIntroTrial by viewModel.hasClubIntroTrial.collectAsState()
    var showClubOffer by remember(post.id) { mutableStateOf(false) }

    LaunchedEffect(post.id) { viewModel.open(post.id) }
    // iOS holds for 1.15s; Android holds longer so the sent gift is actually seen (Gabe's call).
    LaunchedEffect(state.sentGiftId) {
        if (state.sentGiftId != null) {
            haptics.play(HapticManager.Pattern.GIFT_SENT)
            delay(GIFT_SENT_HOLD_MS)
            onDismiss()
        }
    }
    LaunchedEffect(state.alreadySentGiftId) {
        if (state.alreadySentGiftId != null) {
            delay(GIFT_ALREADY_SENT_HOLD_MS)
            onDismiss()
        }
    }

    // Capped like the other sheets so it never rides under the status bar /
    // camera cutout. Content scrolls in the weighted region; the CTA is pinned
    // below it so it stays reachable on short screens.
    val inventory = state.inventory
    val resultShown = state.sentGiftId != null || state.alreadySentGiftId != null
    val showActions = !resultShown && inventory != null
    val keyboardOnSend = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier
            .animateContentSize(animationSpec = tween(320, easing = FastOutSlowInEasing))
            .fillMaxWidth()
            .heightIn(max = bottomSheetMaxHeight())
            .padding(horizontal = CorusSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .hideKeyboardOnScroll()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        if (!resultShown) Text(
            text = stringResource(R.string.gift_send_action),
            style = CorusFont.custom(700, 22),
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!resultShown) {
            Spacer(Modifier.height(10.dp))
            val introText = if (state.inventory?.isEmpty == true) {
                stringResource(giftPickerIntroResource(state.inventory?.available))
            } else {
                stringResource(giftPickerIntroResource(state.inventory?.available), post.user.username)
            }
            // Username in bold (matches iOS) without splitting the localized string.
            val introAnnotated = remember(introText, post.user.username) {
                buildAnnotatedString {
                    append(introText)
                    val start = introText.indexOf(post.user.username)
                    if (start >= 0 && state.inventory?.isEmpty != true) {
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + post.user.username.length)
                    }
                }
            }
            Text(
                text = introAnnotated,
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                textAlign = TextAlign.Center,
            )

        }
        Spacer(Modifier.height(20.dp))

        when {
            state.sentGiftId != null -> SentGiftConfirmation(state.sentGiftId!!)
            state.alreadySentGiftId != null -> AlreadySentNotice(state.alreadySentGiftId!!)
            state.inventory == null && !state.loading -> GiftLoadError(viewModel::refresh)
            else -> {
                val controlsEnabled = state.inventory?.let { areGiftControlsEnabled(it.available) } ?: true
                val availableGifts = GiftDefinition.selectable.filter {
                    state.catalogIds.isEmpty() || it.id in state.catalogIds
                }
                GiftGrid(
                    gifts = availableGifts,
                    selectedGiftId = state.selectedGiftId,
                    enabled = controlsEnabled,
                    onSelect = viewModel::select,
                )
                Spacer(Modifier.height(18.dp))
                GiftNoteField(
                    note = state.note,
                    enabled = controlsEnabled,
                    onNoteChange = viewModel::updateNote,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (!resultShown && inventory == null && state.loading) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).shimmer(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(180.dp).height(20.dp).background(CorusColors.CardBackground, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(5.dp))
            Box(Modifier.width(120.dp).height(14.dp).background(CorusColors.CardBackground, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(52.dp).background(CorusColors.CardBackground, RoundedCornerShape(26.dp)))
        }
    } else if (showActions && inventory != null) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
                // Pinned with the button/upsell (iOS footer), so the count and
                // refill time stay visible however far the picker is scrolled.
                InventorySection(inventory)
                Spacer(Modifier.height(14.dp))
                if (inventory.isEmpty) {
                    if (shouldShowGiftClubOffer(inventory.capacity)) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = CorusColors.CardBackground,
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    stringResource(R.string.gift_club_offer_title),
                                    style = CorusFont.bodyMedium,
                                    color = CorusColors.Text,
                                )
                                Text(
                                    stringResource(R.string.gift_club_offer_body),
                                    style = CorusFont.body,
                                    color = CorusColors.Secondary,
                                    textAlign = TextAlign.Center,
                                )
                                Button(
                                    onClick = { showClubOffer = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        stringResource(
                                            if (hasClubIntroTrial) R.string.gift_club_offer_cta_trial
                                            else R.string.gift_club_offer_cta_standard,
                                        ),
                                        style = CorusFont.button,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val selected = GiftDefinition.from(state.selectedGiftId)
                    Button(
                        onClick = {
                            keyboardOnSend?.hide()
                            viewModel.send(post.id) { result ->
                                GiftPresentationStore.recordSend(
                                    post.id, post.giftCount, result.alreadySent,
                                    result.recentGifts.map {
                                        fm.corus.android.data.model.PostGiftPreview(
                                            it.senderId, it.senderUsername, it.senderDisplayName,
                                            it.senderAvatarUrl, it.giftType, it.note, it.sentAtMs,
                                        )
                                    },
                                )
                                onSent(result)
                            }
                        },
                        enabled = !state.sending,
                        colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (state.sending) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.CardGiftcard, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.gift_send_selected, selected.sentPhrase(context)), style = CorusFont.button)
                        }
                    }
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.gift_send_error), color = CorusColors.Error, textAlign = TextAlign.Center, style = CorusFont.caption)
                }
        }
    }
    }

    if (showClubOffer) {
        CorusModalBottomSheet(
            onDismissRequest = { showClubOffer = false },
            sheetState = rememberGuardedSheetState(),
        ) {
            CymbalClubOfferSheet(
                source = PaywallSource.GIFT,
                onDismiss = { showClubOffer = false },
                onPurchaseSuccess = {
                    showClubOffer = false
                    viewModel.refreshAfterPurchase()
                },
            )
        }
    }
}

/** Compact "Add a note · Optional" row (mirrors iOS) that expands in place into
 *  the text field on tap — no second sheet stacked on the sheet, and it keeps
 *  the picker short on small screens until the sender actually wants a note. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun GiftNoteField(
    note: String,
    enabled: Boolean,
    onNoteChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    // The keyboard shrinks the scroll region; keep the whole field (border and
    // counter included) visible instead of clipped under the pinned footer.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(focused, imeVisible, note) {
        if (focused) {
            delay(80)
            bringIntoView.bringIntoView()
        }
    }
    if (expanded || note.isNotEmpty()) {
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoView)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .alpha(if (enabled) 1f else .5f),
            enabled = enabled,
            textStyle = CorusFont.body,
            label = { Text(stringResource(R.string.gift_note_optional), style = CorusFont.body) },
            supportingText = { Text(stringResource(R.string.gift_note_count, note.length), style = CorusFont.caption) },
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        val keyboardController = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            if (note.isEmpty()) {
                focusRequester.requestFocus()
                // Focus alone does not raise the IME on recent Android versions, and
                // show() is a no-op until the new field has an input connection.
                delay(150)
                keyboardController?.show()
            }
        }
    } else {
        Surface(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(52.dp).alpha(if (enabled) 1f else .5f),
            shape = RoundedCornerShape(16.dp),
            color = CorusColors.CardBackground,
            border = BorderStroke(1.dp, CorusColors.Divider),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = CorusColors.Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.gift_note_add), style = CorusFont.bodyMedium, color = CorusColors.Text)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.gift_note_optional_tag), style = CorusFont.caption, color = CorusColors.Secondary)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CorusColors.Secondary)
            }
        }
    }
}

@Composable
private fun GiftGrid(
    gifts: List<GiftDefinition>,
    selectedGiftId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val rowCount = (gifts.size + 1) / 2
    val gridHeight = (rowCount * 136 + (rowCount - 1).coerceAtLeast(0) * 12).dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(gridHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(gifts, key = { it.id }) { gift ->
            val selected = enabled && selectedGiftId == gift.id
            val dividerColor = CorusColors.Divider
            Surface(
                modifier = Modifier
                    .height(136.dp)
                    .alpha(if (enabled) 1f else .5f),
                shape = RoundedCornerShape(16.dp),
                color = if (selected) CorusColors.Accent.copy(alpha = .12f) else CorusColors.CardBackground,
                border = if (selected) BorderStroke(2.dp, CorusColors.Accent) else null,
            ) {
                Box(
                    // iOS parity: unselected tiles get a dashed outline.
                    if (selected) Modifier else Modifier.drawBehind {
                        drawRoundRect(
                            color = dividerColor,
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())),
                            ),
                        )
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GiftNotificationArtwork(gift.id, 76.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            gift.name(context),
                            color = CorusColors.Text,
                            style = CorusFont.custom(700, 14),
                            maxLines = 1,
                        )
                    }
                    // The Rive artwork is an AndroidView that swallows touches, so a
                    // click on the tile itself never saw taps on the animation. This
                    // transparent layer sits above it and makes the whole tile the target.
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClickLabel = gift.name(context),
                                onClick = { onSelect(gift.id) },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun InventorySection(inventory: GiftInventory) {
    var nowMs by remember(inventory.nextRefillAtMs) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(inventory.nextRefillAtMs) {
        while (inventory.nextRefillAtMs != null) {
            delay(60_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val availableText = if (inventory.available == 1) {
        stringResource(R.string.gift_available_one)
    } else {
        stringResource(R.string.gift_available_many, inventory.available)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (inventory.isEmpty) Icons.Filled.HourglassBottom else Icons.Filled.CardGiftcard,
            contentDescription = null,
            tint = CorusColors.Accent,
        )
        Spacer(Modifier.width(7.dp))
        Text(availableText, style = CorusFont.bodyMedium, color = CorusColors.Text)
    }
    inventory.nextRefillAtMs?.let {
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(R.string.gift_refills_in, giftRefillCountdown(it, nowMs)),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )
    }
    if (inventory.capacity <= 1 && !inventory.isEmpty) {
        Text(stringResource(R.string.gift_club_slots), style = CorusFont.caption, color = CorusColors.Secondary)
    }
}

/** Pops in like iOS (`.scale.combined(with: .opacity)`, spring 0.4 / 0.72). */
@Composable
private fun SentGiftConfirmation(giftId: String) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn() + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GiftNotificationArtwork(giftId, 168.dp)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.gift_sent), style = CorusFont.displayName, color = CorusColors.Text)
        }
    }
}

/** The server keeps one Gift per sender per post and answers a second send with the
 *  original gift, so say that instead of showing a fake "Gift sent". */
@Composable
private fun AlreadySentNotice(giftId: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GiftNotificationArtwork(giftId, 168.dp)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.gift_already_sent_title), style = CorusFont.displayName, color = CorusColors.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.gift_already_sent_body), style = CorusFont.body, color = CorusColors.Secondary, textAlign = TextAlign.Center)
    }
}

/** Placeholder with the same footprint as the loaded picker (grid, note field,
 *  inventory line) so the sheet opens at its final height and content swaps in
 *  place instead of the sheet growing when the data arrives. */
@Composable
private fun GiftPickerSkeleton() {
    val tiles = GiftDefinition.selectable.size
    val rows = (tiles + 1) / 2
    val block = CorusColors.CardBackground
    Column(modifier = Modifier.fillMaxWidth().shimmer(), horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = if (row == 0) 0.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(2) { col ->
                    val visible = row * 2 + col < tiles
                    Box(
                        Modifier.weight(1f).height(136.dp)
                            .then(if (visible) Modifier.background(block, RoundedCornerShape(16.dp)) else Modifier),
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(52.dp).background(block, RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GiftLoadError(retry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.gift_load_error), style = CorusFont.body, color = CorusColors.Secondary, textAlign = TextAlign.Center)
        TextButton(onClick = retry) { Text(stringResource(R.string.gift_try_again), style = CorusFont.button) }
    }
}
