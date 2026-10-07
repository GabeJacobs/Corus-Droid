package fm.corus.android.ui.components

import fm.corus.android.service.GiftAnalytics

import fm.corus.android.ui.components.CorusModalBottomSheet
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.valentinilk.shimmer.shimmer
import fm.corus.android.R
import fm.corus.android.data.model.GiftDefinition
import fm.corus.android.data.model.GiftSender
import fm.corus.android.data.model.PostGiftSummary
import fm.corus.android.data.model.PostGiftPreview
import fm.corus.android.ui.screens.notifications.GiftNotificationArtwork
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class GiftReceipt(
    val id: String, val senderId: String, val sender: String, val type: String, val note: String?,
    val canThank: Boolean = false, val wasThanked: Boolean = false,
)

internal fun shouldShowGiftNote(note: String?): Boolean = !note.isNullOrBlank()

private fun PostGiftPreview.toReceipt() = GiftReceipt(
    id = "preview_${senderId}_${sentAtMs}_$giftType",
    senderId = senderId,
    sender = senderUsername.ifBlank { senderDisplayName.ifBlank { "Someone" } },
    type = giftType,
    note = note,
)

private fun previewSummary(gifts: List<PostGiftPreview>, total: Int): PostGiftSummary {
    val senders = gifts.distinctBy { it.senderId }.map {
        GiftSender(it.senderId, it.senderUsername.ifBlank { it.senderDisplayName.ifBlank { "Someone" } })
    }
    return PostGiftSummary(total.coerceAtLeast(gifts.size), if (gifts.size >= total) senders.size else null, senders)
}

private data class GiftReceiptPage(
    val receipts: List<GiftReceipt>,
    val summary: PostGiftSummary?,
    val cursor: Map<*, *>?,
)

private suspend fun fetchGiftReceiptPage(
    postId: String,
    fallbackTotal: Int,
    cursor: Map<*, *>? = null,
): GiftReceiptPage {
    val args = mutableMapOf<String, Any>("postId" to postId)
    cursor?.let { args["cursor"] = it }
    val data = FirebaseFunctions.getInstance("us-central1")
        .getHttpsCallable("getPostGifts")
        .call(args).await().getData() as? Map<*, *> ?: error("Invalid Gift response")
    val receipts = (data["gifts"] as? List<*>)?.mapNotNull { value ->
        val gift = value as? Map<*, *> ?: return@mapNotNull null
        val id = gift["giftId"] as? String ?: return@mapNotNull null
        GiftReceipt(
            id = id,
            senderId = gift["senderId"] as? String ?: "",
            sender = (gift["senderUsername"] as? String)?.takeIf { it.isNotBlank() }
                ?: gift["senderDisplayName"] as? String ?: "Someone",
            type = gift["giftType"] as? String ?: "",
            note = gift["note"] as? String,
            canThank = gift["canThank"] as? Boolean ?: false,
            wasThanked = gift["wasThanked"] as? Boolean ?: false,
        )
    }.orEmpty()
    val summary = (data["summary"] as? Map<*, *>)?.let { raw ->
        PostGiftSummary(
            (raw["total"] as? Number)?.toInt() ?: fallbackTotal,
            (raw["senderCount"] as? Number)?.toInt(),
            (raw["senders"] as? List<*>)?.mapNotNull { value ->
                val sender = value as? Map<*, *> ?: return@mapNotNull null
                val id = sender["senderId"] as? String ?: return@mapNotNull null
                GiftSender(id, (sender["senderUsername"] as? String)?.takeIf { it.isNotBlank() }
                    ?: sender["senderDisplayName"] as? String ?: "Someone")
            }.orEmpty(),
        )
    }
    return GiftReceiptPage(receipts, summary, data["nextCursor"] as? Map<*, *>)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostGiftRow(
    postId: String,
    giftCount: Int,
    recentGifts: List<PostGiftPreview> = emptyList(),
    recipientId: String,
    onSenderTap: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(start = 12.5.dp, end = 16.dp, top = 3.dp, bottom = 9.dp),
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val previewReceipts = remember(recentGifts) { recentGifts.map(PostGiftPreview::toReceipt) }
    val initialSummary = remember(recentGifts, giftCount) { previewSummary(recentGifts, giftCount) }
    var rowReceipts by remember(postId, uid, giftCount, recentGifts) { mutableStateOf(previewReceipts) }
    var rowSummary by remember(postId, uid, giftCount, recentGifts) {
        mutableStateOf(initialSummary.takeIf { previewReceipts.isNotEmpty() })
    }
    var open by remember(postId, uid) { mutableStateOf(false) }
    var index by remember(postId, uid) { mutableIntStateOf(0) }
    var sheetReceipts by remember(postId, uid) { mutableStateOf(emptyList<GiftReceipt>()) }
    var sheetSummary by remember(postId, uid) { mutableStateOf<PostGiftSummary?>(null) }
    var sheetCursor by remember(postId, uid) { mutableStateOf<Map<*, *>?>(null) }
    var sheetLoading by remember(postId, uid) { mutableStateOf(false) }
    var sheetError by remember(postId, uid) { mutableStateOf(false) }
    var thankingId by remember(postId, uid) { mutableStateOf<String?>(null) }
    var thankErrorId by remember(postId, uid) { mutableStateOf<String?>(null) }
    var thankedIds by remember(postId, uid) { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    suspend fun loadSheet(reset: Boolean): Boolean {
        if (sheetLoading) return false
        sheetLoading = true
        sheetError = false
        return try {
            val page = fetchGiftReceiptPage(postId, giftCount, if (reset) null else sheetCursor)
            sheetReceipts = if (reset) page.receipts else (sheetReceipts + page.receipts).distinctBy { it.id }
            sheetSummary = page.summary ?: sheetSummary
            sheetCursor = page.cursor
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            sheetError = true
            false
        } finally {
            sheetLoading = false
        }
    }

    LaunchedEffect(postId, uid, giftCount) {
        if (giftCount <= 0 || uid == null || previewReceipts.isNotEmpty()) return@LaunchedEffect
        try {
            val page = fetchGiftReceiptPage(postId, giftCount)
            rowReceipts = page.receipts
            rowSummary = page.summary
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The bounded feed row is optional; a later post refresh can retry it.
        }
    }

    val currentRowSummary = rowSummary ?: return
    val firstRowGift = rowReceipts.firstOrNull() ?: return
    val rowGiftTypes = rowReceipts.map { it.type }.distinct().take(3)
    // Match iOS: repeated gifts do not shrink a row displaying only two types.
    val rowArtworkSize = if (rowGiftTypes.size <= 2) 34.dp else 30.dp
    val rowArtworkSpacing = if (currentRowSummary.total == 2) 1.dp else 3.dp
    // The feed preview is bounded. Only show a multiplier when it contains every gift.
    val giftTypeCounts = if (rowReceipts.size >= currentRowSummary.total) {
        rowReceipts.groupingBy { it.type }.eachCount()
    } else emptyMap()
    val openGiftSheet = {
        index = 0
        sheetReceipts = rowReceipts
        sheetSummary = rowSummary
        sheetCursor = null
        sheetError = false
        open = true
    }
    val currentOpenGiftSheet by rememberUpdatedState(openGiftSheet)
    Row(
        Modifier.fillMaxWidth()
            .then(if (firstRowGift.type == "corus_heart") Modifier.offset(x = (-1).dp) else Modifier)
            .clickable(onClick = openGiftSheet).padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.5.dp),
    ) {
        Box {
            Row(horizontalArrangement = Arrangement.spacedBy(rowArtworkSpacing)) {
                rowGiftTypes.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Adjust the Boombox artwork without moving the attribution text.
                        Box(
                            if (type == "boombox") Modifier.offset(x = (-3).dp).scale(1.06f)
                            else Modifier,
                        ) {
                            GiftNotificationArtwork(type, rowArtworkSize)
                        }
                        val typeCount = giftTypeCounts[type] ?: 0
                        if (typeCount > 1) {
                            Text("×$typeCount", style = CorusFont.captionMedium, color = CorusColors.Secondary)
                        }
                    }
                }
            }
            // Rive's AndroidView consumes taps before the parent row can see
            // them. Keep the artwork animated and forward its touch area to
            // the same receipt sheet action as the text.
            Spacer(Modifier.matchParentSize().pointerInput(postId) {
                detectTapGestures { currentOpenGiftSheet() }
            })
        }
        Text(
            if (currentRowSummary.total == 1) localizedGiftReceiptTitle(context, firstRowGift)
            else localizedGiftAttribution(context, currentRowSummary),
            style = CorusFont.body,
            color = CorusColors.Text,
            modifier = Modifier.weight(1f),
        )
    }

    val viewedReceipt = sheetReceipts.getOrNull(index)
    LaunchedEffect(open, viewedReceipt?.id) {
        if (open && viewedReceipt != null) GiftAnalytics.log(context, "receipt_viewed", "post_receipt", viewedReceipt.type, !viewedReceipt.note.isNullOrBlank())
    }
    if (open) {
        LaunchedEffect(Unit) { loadSheet(reset = true) }
        val displayedTotal = sheetSummary?.total ?: giftCount
        val actionGift = sheetReceipts.getOrNull(index) ?: sheetReceipts.firstOrNull()
        val actionIsThanked = actionGift?.let { it.wasThanked || it.id in thankedIds } == true
        val sheetState = rememberGuardedSheetState()
        CorusModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(if (displayedTotal > 1) fm.corus.android.localization.CorusStrings.activity_filter_gifts else fm.corus.android.localization.CorusStrings.gift_name_generic), style = CorusFont.screenTitle, color = CorusColors.Text)
                // iOS: single gift gets room between title and artwork; with a pager it sits close
                Spacer(Modifier.height(if (displayedTotal > 1) 0.dp else 14.dp))
                AnimatedContent(
                    targetState = sheetReceipts.isNotEmpty(),
                    transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(100)) },
                    label = "giftReceiptContent",
                ) { hasReceipt ->
                    if (!hasReceipt) {
                        if (sheetError) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(stringResource(fm.corus.android.localization.CorusStrings.gift_load_details_error), style = CorusFont.body, color = CorusColors.Secondary)
                                TextButton(onClick = { scope.launch { loadSheet(reset = true) } }) { Text(stringResource(fm.corus.android.localization.CorusStrings.gift_try_again), style = CorusFont.button) }
                            }
                        } else GiftReceiptSkeleton(displayedTotal)
                    } else {
                        val first = sheetReceipts.firstOrNull()
                        if (first == null) {
                            Text(stringResource(fm.corus.android.localization.CorusStrings.gift_no_gifts), color = CorusColors.Secondary, style = CorusFont.body, modifier = Modifier.padding(48.dp))
                        } else {
                            val gift = sheetReceipts.getOrElse(index) { first }
                            Column(
                                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(22.dp),
                            ) {
                                if (displayedTotal > 1) GiftPager(
                                    index, displayedTotal, index > 0,
                                    index + 1 < sheetReceipts.size || (!sheetLoading && sheetCursor != null),
                                    { index-- },
                                    {
                                        if (index + 1 < sheetReceipts.size) index++
                                        else scope.launch { if (loadSheet(false) && index + 1 < sheetReceipts.size) index++ }
                                    },
                                )
                                GiftNotificationArtwork(gift.type, 164.dp)
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(GiftDefinition.from(gift.type).name(context), style = CorusFont.custom(800, 28), color = CorusColors.Text)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        gift.sender,
                                        color = CorusColors.Text,
                                        style = CorusFont.username,
                                        modifier = Modifier.clickable(enabled = gift.senderId.isNotBlank()) {
                                            scope.launch {
                                                sheetState.hide()
                                                open = false
                                                onSenderTap(gift.senderId)
                                            }
                                        },
                                    )
                                    Text(
                                        context.getString(
                                            R.string.gift_sender_sent,
                                            "",
                                            GiftDefinition.from(gift.type).sentPhrase(context),
                                        ).trimEnd(),
                                        color = CorusColors.Text,
                                        style = CorusFont.body,
                                    )
                                }
                                }
                                AnimatedVisibility(
                                    visible = shouldShowGiftNote(gift.note),
                                    enter = fadeIn(tween(240)) + expandVertically(tween(240)),
                                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                                ) {
                                    gift.note?.takeIf { it.isNotBlank() }?.let { note ->
                                        Column(
                                            Modifier.fillMaxWidth()
                                                .background(CorusColors.CardBackground, RoundedCornerShape(16.dp))
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(9.dp),
                                        ) {
                                            Text(
                                                stringResource(R.string.gift_note_from, gift.sender),
                                                style = CorusFont.captionMedium, color = CorusColors.Secondary,
                                            )
                                            Text(note, style = CorusFont.body, color = CorusColors.Text)
                                        }
                                    }
                                }
                                AnimatedVisibility(
                                    visible = uid != null && uid == recipientId && actionGift != null && (actionGift.canThank || actionIsThanked),
                                    enter = fadeIn(tween(240)) + expandVertically(tween(240)),
                                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                                ) {
                                    if (actionGift != null) {
                Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(50),
                enabled = !actionIsThanked && thankingId != actionGift.id,
                onClick = {
                    thankErrorId = null
                    thankedIds = thankedIds + actionGift.id
                    thankingId = actionGift.id
                    scope.launch {
                        GiftAnalytics.log(context, "thanks_started", "post_receipt")
                        try {
                            val response = FirebaseFunctions.getInstance("us-central1")
                                .getHttpsCallable("thankGift")
                                .call(mapOf("postId" to postId, "giftId" to actionGift.id))
                                .await()
                            val alreadyThanked = (response.getData() as? Map<*, *>)?.get("alreadyThanked") == true
                            GiftAnalytics.log(context, "thanks_completed", "post_receipt", result = if (alreadyThanked) "already_thanked" else "thanked")
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            GiftAnalytics.log(context, "thanks_failed", "post_receipt", errorCode = GiftAnalytics.errorCode(error))
                            thankedIds = thankedIds - actionGift.id
                            thankErrorId = actionGift.id
                        } finally {
                            thankingId = null
                        }
                    }
                },
            ) {
                Text(stringResource(when {
                    actionIsThanked -> fm.corus.android.localization.CorusStrings.gift_thanked
                    thankingId == actionGift.id -> fm.corus.android.localization.CorusStrings.auth_button_sending
                    else -> fm.corus.android.localization.CorusStrings.gift_say_thanks
                }), style = CorusFont.button)
            }

                                    }
                                }
                                if (thankErrorId == gift.id) {
                                    Text(stringResource(fm.corus.android.localization.CorusStrings.gift_thanks_failed), style = CorusFont.caption, color = CorusColors.Secondary)
                                }
                                if (sheetError) {
                                    Text(stringResource(fm.corus.android.localization.CorusStrings.gift_refresh_details_error), style = CorusFont.caption, color = CorusColors.Secondary)
                                    TextButton(onClick = { scope.launch { loadSheet(reset = true) } }) { Text(stringResource(fm.corus.android.localization.CorusStrings.gift_try_again), style = CorusFont.button) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GiftPager(
    index: Int, total: Int, previousEnabled: Boolean, nextEnabled: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        IconButton(
            enabled = previousEnabled,
            onClick = onPrevious,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = CorusColors.Accent),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(fm.corus.android.localization.CorusStrings.player_previous_track),
                modifier = Modifier.size(32.dp),
            )
        }
        Text(stringResource(R.string.gift_pager_count, index + 1, total), style = CorusFont.caption, color = CorusColors.Secondary)
        IconButton(
            enabled = nextEnabled,
            onClick = onNext,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = CorusColors.Accent),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(fm.corus.android.localization.CorusStrings.player_next_track),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private const val GIFT_COUNT_TOKEN = "__CORUS_GIFT_COUNT__"
private const val GIFT_SENDER_ONE_TOKEN = "__CORUS_GIFT_SENDER_ONE__"
private const val GIFT_SENDER_TWO_TOKEN = "__CORUS_GIFT_SENDER_TWO__"
private const val GIFT_TYPE_TOKEN = "__CORUS_GIFT_TYPE__"

/** Replaces formatting sentinels with iOS-sized username text while leaving
 * all localized connecting copy in its resource-defined order. */
internal fun emphasizedGiftAttribution(
    template: String,
    replacements: Map<String, String>,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (cursor < template.length) {
        val next = replacements.entries
            .mapNotNull { entry ->
                template.indexOf(entry.key, startIndex = cursor)
                    .takeIf { it >= 0 }
                    ?.let { index -> Triple(index, entry.key, entry.value) }
            }
            .minByOrNull { it.first }

        if (next == null) {
            append(template.substring(cursor))
            break
        }
        append(template.substring(cursor, next.first))
        withStyle(
            SpanStyle(
                fontSize = CorusFont.username.fontSize,
                fontWeight = CorusFont.username.fontWeight,
            ),
        ) { append(next.third) }
        cursor = next.first + next.second.length
    }
}

private fun localizedGiftReceiptTitle(
    context: android.content.Context,
    receipt: GiftReceipt,
): AnnotatedString {
    val sender = receipt.sender.ifBlank { context.getString(fm.corus.android.localization.CorusStrings.activity_someone) }
    val gift = GiftDefinition.from(receipt.type).sentPhrase(context)
    return emphasizedGiftAttribution(
        context.getString(R.string.gift_sender_sent, GIFT_SENDER_ONE_TOKEN, GIFT_TYPE_TOKEN),
        linkedMapOf(GIFT_SENDER_ONE_TOKEN to sender, GIFT_TYPE_TOKEN to gift),
    )
}

private fun localizedGiftAttribution(
    context: android.content.Context,
    summary: PostGiftSummary,
): AnnotatedString {
    val people = summary.senders.distinctBy { it.id }
    val count = context.getString(R.string.gift_count, summary.total)
    val first = people.firstOrNull()?.name ?: return emphasizedGiftAttribution(
        GIFT_COUNT_TOKEN,
        mapOf(GIFT_COUNT_TOKEN to count),
    )
    val replacements = linkedMapOf(
        GIFT_COUNT_TOKEN to count,
        GIFT_SENDER_ONE_TOKEN to first,
    )
    val template = when {
        summary.senderCount == 1 -> context.getString(
            R.string.gift_attribution_single,
            GIFT_SENDER_ONE_TOKEN,
            GIFT_COUNT_TOKEN,
        )
        summary.senderCount == 2 && people.size >= 2 -> {
            replacements[GIFT_SENDER_TWO_TOKEN] = people[1].name
            context.getString(
                R.string.gift_attribution_two,
                GIFT_COUNT_TOKEN,
                GIFT_SENDER_ONE_TOKEN,
                GIFT_SENDER_TWO_TOKEN,
            )
        }
        summary.senderCount != null && summary.senderCount > 2 -> context.getString(
            R.string.gift_attribution_others,
            GIFT_COUNT_TOKEN,
            GIFT_SENDER_ONE_TOKEN,
            summary.senderCount - 1,
        )
        else -> context.getString(
            R.string.gift_attribution_latest,
            GIFT_COUNT_TOKEN,
            GIFT_SENDER_ONE_TOKEN,
        )
    }
    return emphasizedGiftAttribution(template, replacements)
}

@Composable
private fun GiftReceiptSkeleton(total: Int) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (total > 1) GiftPager(0, total, false, false, {}, {})
        Column(
            Modifier.fillMaxWidth().shimmer(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            SkeletonBone(164, 164, 42)
            SkeletonBone(142, 28, 9)
            SkeletonBone(226, 17, 6)
            SkeletonBone(176, 17, 6)
        }
    }
}

@Composable
private fun SkeletonBone(width: Int, height: Int, radius: Int) {
    Box(Modifier.size(width.dp, height.dp).background(CorusColors.Skeleton, RoundedCornerShape(radius.dp)))
}
