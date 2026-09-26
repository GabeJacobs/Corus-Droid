package fm.corus.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import fm.corus.android.data.model.GiftDefinition
import fm.corus.android.data.model.GiftSender
import fm.corus.android.data.model.PostGiftSummary
import fm.corus.android.ui.screens.notifications.GiftNotificationArtwork
import fm.corus.android.ui.theme.CorusColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class GiftReceipt(val id: String, val sender: String, val type: String, val note: String?) {
    val title get() = "$sender sent ${GiftDefinition.from(type).sentPhrase}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostGiftRow(postId: String, giftCount: Int) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var receipts by remember(postId, uid, giftCount) { mutableStateOf(emptyList<GiftReceipt>()) }
    var summary by remember(postId, uid, giftCount) { mutableStateOf<PostGiftSummary?>(null) }
    var cursor by remember(postId, uid, giftCount) { mutableStateOf<Map<*, *>?>(null) }
    var open by remember(postId, uid) { mutableStateOf(false) }
    var index by remember(postId, uid) { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
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
                GiftReceipt(id, (g["senderUsername"] as? String)?.takeIf { it.isNotBlank() } ?: g["senderDisplayName"] as? String ?: "Someone", g["giftType"] as? String ?: "", g["note"] as? String)
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
        Text(if (currentSummary.total == 1) first.title else currentSummary.attribution(), color = CorusColors.Text, modifier = Modifier.weight(1f))
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, containerColor = CorusColors.Background) {
        val gift = receipts.getOrElse(index) { first }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (currentSummary.total > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = index > 0, onClick = { index-- }) { Text("Previous") }
                Text("${index + 1} of ${currentSummary.total}", color = CorusColors.Secondary)
                TextButton(enabled = !loading && (index + 1 < receipts.size || cursor != null), onClick = {
                    if (index + 1 < receipts.size) index++ else scope.launch { if (load(false) && index + 1 < receipts.size) index++ }
                }) { Text("Next") }
            }
            GiftNotificationArtwork(gift.type, 164.dp)
            Text(GiftDefinition.from(gift.type).name, style = MaterialTheme.typography.headlineSmall, color = CorusColors.Text)
            Text(gift.title, color = CorusColors.Text)
            gift.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = CorusColors.Text) }
            if (error) Text("Couldn’t load the next gift. Tap Next to retry.", color = CorusColors.Secondary)
            TextButton(onClick = { open = false }) { Text("Close") }
        }
    }
}
