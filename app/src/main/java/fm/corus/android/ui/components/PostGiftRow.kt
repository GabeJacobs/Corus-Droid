package fm.corus.android.ui.components

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.valentinilk.shimmer.shimmer
import fm.corus.android.data.model.GiftDefinition
import fm.corus.android.data.model.GiftSender
import fm.corus.android.data.model.PostGiftSummary
import fm.corus.android.data.model.PostGiftPreview
import fm.corus.android.ui.screens.notifications.GiftNotificationArtwork
import fm.corus.android.ui.theme.CorusColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class GiftReceipt(val id: String, val senderId: String, val sender: String, val type: String, val note: String?) {
    val title get() = "$sender sent ${GiftDefinition.from(type).sentPhrase}"
}

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
    onSenderTap: (String) -> Unit = {},
) {
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
    var sheetLoaded by remember(postId, uid) { mutableStateOf(false) }
    var sheetError by remember(postId, uid) { mutableStateOf(false) }
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
            sheetLoaded = true
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
    val rowArtworkSize = if (currentRowSummary.total <= 2) 34.dp else 30.dp
    val rowArtworkSpacing = if (currentRowSummary.total == 2) 1.dp else 3.dp
    Row(
        Modifier.fillMaxWidth().clickable {
            index = 0
            sheetReceipts = rowReceipts
            sheetSummary = rowSummary
            sheetCursor = null
            sheetLoaded = false
            sheetError = false
            open = true
        }.padding(start = 12.5.dp, end = 16.dp, top = 5.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.5.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(rowArtworkSpacing)) {
            rowReceipts.map { it.type }.distinct().take(3).forEach {
                GiftNotificationArtwork(it, rowArtworkSize)
            }
        }
        Text(
            if (currentRowSummary.total == 1) firstRowGift.title else currentRowSummary.attribution(),
            color = CorusColors.Text,
            modifier = Modifier.weight(1f),
        )
    }

    if (open) {
        LaunchedEffect(Unit) { loadSheet(reset = true) }
        val displayedTotal = sheetSummary?.total ?: giftCount
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            containerColor = CorusColors.Background,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (displayedTotal > 1) "Gifts" else "Gift", style = MaterialTheme.typography.titleLarge, color = CorusColors.Text)
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
                                Text("Couldn’t load gifts. Please try again.", color = CorusColors.Secondary)
                                TextButton(onClick = { scope.launch { loadSheet(reset = true) } }) { Text("Try Again") }
                            }
                        } else GiftReceiptSkeleton(displayedTotal)
                    } else {
                        val first = sheetReceipts.firstOrNull()
                        if (first == null) {
                            Text("No gifts yet", color = CorusColors.Secondary, modifier = Modifier.padding(48.dp))
                        } else {
                            val gift = sheetReceipts.getOrElse(index) { first }
                            Column(
                                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                                Text(GiftDefinition.from(gift.type).name, style = MaterialTheme.typography.headlineSmall, color = CorusColors.Text)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        gift.sender,
                                        color = CorusColors.Text,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.clickable(enabled = gift.senderId.isNotBlank()) {
                                            scope.launch {
                                                sheetState.hide()
                                                open = false
                                                onSenderTap(gift.senderId)
                                            }
                                        },
                                    )
                                    Text(" sent ${GiftDefinition.from(gift.type).sentPhrase}", color = CorusColors.Text)
                                }
                                AnimatedVisibility(
                                    visible = !gift.note.isNullOrBlank(),
                                    enter = fadeIn(tween(240)) + expandVertically(tween(240)),
                                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                                ) {
                                    gift.note?.takeIf { it.isNotBlank() }?.let { note ->
                                        Text(note, color = CorusColors.Text)
                                    }
                                }
                                if (sheetError) {
                                    Text("Couldn’t refresh Gift details.", color = CorusColors.Secondary)
                                    TextButton(onClick = { scope.launch { loadSheet(reset = true) } }) { Text("Try Again") }
                                }
                                TextButton(onClick = { open = false }) { Text("Close") }
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
        TextButton(enabled = previousEnabled, onClick = onPrevious) { Text("‹") }
        Text("${index + 1} of $total", color = CorusColors.Secondary)
        TextButton(enabled = nextEnabled, onClick = onNext) { Text("›") }
    }
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
