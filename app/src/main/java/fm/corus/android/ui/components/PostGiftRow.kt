package fm.corus.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import fm.corus.android.R
import fm.corus.android.data.model.GiftDefinition
import fm.corus.android.data.model.GiftSender
import fm.corus.android.data.model.PostGiftSummary
import fm.corus.android.ui.screens.notifications.GiftNotificationArtwork
import fm.corus.android.ui.theme.CorusColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class GiftReceipt(
    val id: String,
    val sender: String,
    val type: String,
    val note: String?,
    val canThank: Boolean,
    val wasThanked: Boolean,
) {
    fun title(context: android.content.Context): String = context.getString(
        R.string.gift_sender_sent,
        sender.ifBlank { context.getString(R.string.gift_someone) },
        GiftDefinition.from(type).sentPhrase(context),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostGiftRow(postId: String, giftCount: Int) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var receipts by remember(postId, uid, giftCount) { mutableStateOf(emptyList<GiftReceipt>()) }
    var summary by remember(postId, uid, giftCount) { mutableStateOf<PostGiftSummary?>(null) }
    var cursor by remember(postId, uid, giftCount) { mutableStateOf<Map<*, *>?>(null) }
    var open by remember(postId, uid) { mutableStateOf(false) }
    var index by remember(postId, uid) { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var thankingId by remember { mutableStateOf<String?>(null) }
    var thankErrorId by remember { mutableStateOf<String?>(null) }
    var thankedIds by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    suspend fun load(reset: Boolean): Boolean {
        loading = true; error = false
        return try {
            val args = mutableMapOf<String, Any>("postId" to postId)
            if (!reset) cursor?.let { args["cursor"] = it }
            val data = FirebaseFunctions.getInstance("us-central1").getHttpsCallable("getPostGifts").call(args).await().getData() as? Map<*, *> ?: return false
            val rows = (data["gifts"] as? List<*>)?.mapNotNull { value ->
                val g = value as? Map<*, *> ?: return@mapNotNull null
                val id = g["giftId"] as? String ?: return@mapNotNull null
                GiftReceipt(
                    id,
                    (g["senderUsername"] as? String)?.takeIf { it.isNotBlank() } ?: g["senderDisplayName"] as? String ?: "Someone",
                    g["giftType"] as? String ?: "",
                    g["note"] as? String,
                    g["canThank"] as? Boolean ?: false,
                    g["wasThanked"] as? Boolean ?: false,
                )
            }.orEmpty()
            receipts = (if (reset) rows else receipts + rows).distinctBy { it.id }
            cursor = data["nextCursor"] as? Map<*, *>
            (data["summary"] as? Map<*, *>)?.let { s ->
                summary = PostGiftSummary((s["total"] as? Number)?.toInt() ?: giftCount, (s["senderCount"] as? Number)?.toInt(),
                    (s["senders"] as? List<*>)?.mapNotNull { value ->
                        val person = value as? Map<*, *> ?: return@mapNotNull null
                        val id = person["senderId"] as? String ?: return@mapNotNull null
                        GiftSender(id, (person["senderUsername"] as? String)?.takeIf { it.isNotBlank() } ?: person["senderDisplayName"] as? String ?: "Someone")
                    }.orEmpty())
            }
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true; false }
        finally { loading = false }
    }
    LaunchedEffect(postId, uid, giftCount) { if (giftCount > 0 && uid != null) load(true) }
    val currentSummary = summary ?: return
    val first = receipts.firstOrNull() ?: return
    Row(Modifier.fillMaxWidth().clickable { index = 0; open = true }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Row { receipts.map { it.type }.distinct().take(3).forEach { GiftNotificationArtwork(it, 34.dp) } }
        Text(if (currentSummary.total == 1) first.title(context) else localizedGiftAttribution(context, currentSummary), color = CorusColors.Text, modifier = Modifier.weight(1f))
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, containerColor = CorusColors.Background) {
        val gift = receipts.getOrElse(index) { first }
        val isThanked = gift.wasThanked || gift.id in thankedIds
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (currentSummary.total > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = index > 0, onClick = { index-- }) { Text("‹") }
                Text(stringResource(R.string.gift_pager_count, index + 1, currentSummary.total), color = CorusColors.Secondary)
                TextButton(enabled = !loading && (index + 1 < receipts.size || cursor != null), onClick = {
                    if (index + 1 < receipts.size) index++ else scope.launch { if (load(false) && index + 1 < receipts.size) index++ }
                }) { Text("›") }
            }
            GiftNotificationArtwork(gift.type, 164.dp)
            Text(GiftDefinition.from(gift.type).name(context), style = MaterialTheme.typography.headlineSmall, color = CorusColors.Text)
            Text(gift.title(context), color = CorusColors.Text)
            gift.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = CorusColors.Text) }
            if (gift.canThank || isThanked) {
                Button(
                    enabled = !isThanked && thankingId != gift.id,
                    onClick = {
                        thankErrorId = null
                        thankingId = gift.id
                        scope.launch {
                            try {
                                FirebaseFunctions.getInstance("us-central1")
                                    .getHttpsCallable("thankGift")
                                    .call(mapOf("postId" to postId, "giftId" to gift.id))
                                    .await()
                                thankedIds = thankedIds + gift.id
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                thankErrorId = gift.id
                            } finally {
                                thankingId = null
                            }
                        }
                    },
                ) {
                    Text(stringResource(
                        when {
                            isThanked -> R.string.gift_thanked
                            thankingId == gift.id -> R.string.gift_sending_thanks
                            else -> R.string.gift_say_thanks
                        }
                    ))
                }
            }
            if (thankErrorId == gift.id) {
                Text(stringResource(R.string.gift_thanks_failed), color = CorusColors.Secondary)
            }
            if (error) Text(stringResource(R.string.gift_refresh_details_error), color = CorusColors.Secondary)
            TextButton(onClick = { open = false }) { Text(stringResource(R.string.gift_close)) }
        }
    }
}

private fun localizedGiftAttribution(context: android.content.Context, summary: PostGiftSummary): String {
    val people = summary.senders.distinctBy { it.id }
    val count = context.getString(R.string.gift_count, summary.total)
    val first = people.firstOrNull()?.name ?: return count
    return when {
        summary.senderCount == 1 -> context.getString(R.string.gift_attribution_single, first, count)
        summary.senderCount == 2 && people.size >= 2 -> context.getString(R.string.gift_attribution_two, count, first, people[1].name)
        summary.senderCount != null && summary.senderCount > 2 -> context.getString(R.string.gift_attribution_others, count, first, summary.senderCount - 1)
        else -> context.getString(R.string.gift_attribution_latest, count, first)
    }
}
