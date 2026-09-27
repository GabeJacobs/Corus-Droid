package fm.corus.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.GiftDefinition
import fm.corus.android.data.repository.GiftInventory
import fm.corus.android.data.repository.GiftSendResult
import fm.corus.android.ui.screens.notifications.GiftNotificationArtwork
import fm.corus.android.ui.screens.subscription.CymbalClubOfferSheet
import fm.corus.android.ui.screens.subscription.PaywallSource
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftSelectionSheet(
    post: CymbalPost,
    onDismiss: () -> Unit,
    onSent: (GiftSendResult) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: GiftSelectionViewModel = hiltViewModel(key = "gift-${post.id}")
    val state by viewModel.state.collectAsState()
    val hasClubIntroTrial by viewModel.hasClubIntroTrial.collectAsState()
    var showClubOffer by remember(post.id) { mutableStateOf(false) }

    LaunchedEffect(post.id) { viewModel.open() }
    LaunchedEffect(state.sentGiftId) {
        if (state.sentGiftId != null) {
            delay(1_150)
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CorusSpacing.lg)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(44.dp))
            Text(
                text = stringResource(R.string.gift_send_action),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = CorusColors.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.background(CorusColors.CardBackground, CircleShape),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.gift_close), tint = CorusColors.Secondary)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = if (state.inventory?.isEmpty == true) {
                stringResource(giftPickerIntroResource(state.inventory?.available))
            } else {
                stringResource(giftPickerIntroResource(state.inventory?.available), post.user.username)
            },
            style = CorusFont.bodyMedium,
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        when {
            state.sentGiftId != null -> SentGiftConfirmation(state.sentGiftId!!)
            state.loading && state.inventory == null -> GiftPickerSkeleton()
            state.inventory == null -> GiftLoadError(viewModel::refresh)
            else -> {
                val controlsEnabled = areGiftControlsEnabled(state.inventory!!.available)
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
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::updateNote,
                    modifier = Modifier.fillMaxWidth().alpha(if (controlsEnabled) 1f else .5f),
                    enabled = controlsEnabled,
                    label = { Text(stringResource(R.string.gift_note_optional)) },
                    supportingText = { Text(stringResource(R.string.gift_note_count, state.note.length)) },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(18.dp))
                InventorySection(state.inventory!!)
                Spacer(Modifier.height(14.dp))

                if (state.inventory!!.isEmpty) {
                    if (shouldShowGiftClubOffer(state.inventory!!.capacity)) {
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
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val selected = GiftDefinition.from(state.selectedGiftId)
                    Button(
                        onClick = {
                            viewModel.send(post.id) { result ->
                                GiftPresentationStore.recordSend(post.id, post.giftCount, result.alreadySent)
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
                            Text(stringResource(R.string.gift_send_selected, selected.sentPhrase(context)))
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
        ModalBottomSheet(
            onDismissRequest = { showClubOffer = false },
            containerColor = CorusColors.Background,
        ) {
            CymbalClubOfferSheet(
                source = PaywallSource.GIFT,
                onDismiss = { showClubOffer = false },
                onPurchaseSuccess = {
                    showClubOffer = false
                    viewModel.refresh()
                },
            )
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
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(((gifts.size + 1) / 2 * 142).dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(gifts, key = { it.id }) { gift ->
            val selected = enabled && selectedGiftId == gift.id
            Surface(
                onClick = { onSelect(gift.id) },
                enabled = enabled,
                modifier = Modifier.alpha(if (enabled) 1f else .5f),
                shape = RoundedCornerShape(16.dp),
                color = if (selected) CorusColors.Accent.copy(alpha = .12f) else CorusColors.CardBackground,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) CorusColors.Accent else CorusColors.Divider),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GiftNotificationArtwork(gift.id, 76.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(gift.name(context), color = CorusColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(gift.shortMeaning(context), color = CorusColors.Secondary, fontSize = 11.sp, maxLines = 1)
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
    Spacer(Modifier.height(5.dp))
    Text(
        inventory.nextRefillAtMs?.let {
            stringResource(R.string.gift_refills_in, giftRefillCountdown(it, nowMs))
        } ?: stringResource(R.string.gift_reset_cycle),
        style = CorusFont.caption,
        color = CorusColors.Secondary,
        textAlign = TextAlign.Center,
    )
    if (inventory.capacity <= 1 && !inventory.isEmpty) {
        Text(stringResource(R.string.gift_club_slots), style = CorusFont.caption, color = CorusColors.Secondary)
    }
}

@Composable
private fun SentGiftConfirmation(giftId: String) {
    Column(
        modifier = Modifier.fillMaxWidth().height(330.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GiftNotificationArtwork(giftId, 132.dp)
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.gift_sent), style = MaterialTheme.typography.headlineSmall, color = CorusColors.Text)
    }
}

@Composable
private fun GiftPickerSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = CorusColors.Accent)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.gift_loading), color = CorusColors.Secondary)
    }
}

@Composable
private fun GiftLoadError(retry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.gift_load_error), color = CorusColors.Secondary, textAlign = TextAlign.Center)
        TextButton(onClick = retry) { Text(stringResource(R.string.gift_try_again)) }
    }
}
