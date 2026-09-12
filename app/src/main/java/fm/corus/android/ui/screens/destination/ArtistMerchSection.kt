package fm.corus.android.ui.screens.destination

import fm.corus.android.ui.components.parityCopy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.theme.CorusColors
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.net.URI
import java.text.NumberFormat
import java.util.Currency
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ArtistMerchViewModel @Inject constructor(val flags: RemoteConfigService, private val auth: FirebaseAuth, private val functions: FirebaseFunctions) : ViewModel() {
    val viewer get() = auth.currentUser
    suspend fun call(name: String, payload: Map<String, Any>): Map<*, *> {
        val uid = viewer?.uid ?: error("Sign in to continue")
        val result = functions.getHttpsCallable(name).call(payload).await().getData() as? Map<*, *> ?: error("Please try again")
        check(viewer?.uid == uid)
        return result
    }
}
private fun secureLink(value: String?): String? = value?.takeIf { runCatching { URI(it).scheme == "https" && !URI(it).host.isNullOrBlank() && URI(it).userInfo == null }.getOrDefault(false) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistMerchSection(artistId: String, artistName: String, viewModel: ArtistMerchViewModel = hiltViewModel()) {
    val revision by viewModel.flags.revision.collectAsState()
    if (!viewModel.flags.artistMerchEnabled || viewModel.viewer == null) return
    var catalog by remember(artistId) { mutableStateOf<Map<*, *>?>(null) }
    var claim by remember(artistId) { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    LaunchedEffect(artistId, revision) {
        if (!artistId.startsWith("bc:")) catalog = runCatching { viewModel.call("getArtistMerch", mapOf("artistId" to artistId)) }.getOrNull()
    }
    val catalogFresh = (catalog?.get("updatedAt") as? Number)?.toLong()?.let { System.currentTimeMillis()-it <= 86_400_000L } == true
    val products = (catalog?.takeIf { catalogFresh }?.get("products") as? List<*>)?.filterIsInstance<Map<*, *>>()?.filter { it["available"] == true && (it["price"] as? Number)?.toDouble()?.let { price -> price.isFinite() && price >= 0 } == true && (it["currency"] as? String)?.length == 3 && secureLink(it["url"] as? String) != null }.orEmpty()
    val store = secureLink(catalog?.get("storeUrl") as? String)
    if (products.isNotEmpty() || store != null) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(parityCopy("Merch & vinyl"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (store != null) TextButton(onClick = { uri.openUri(store) }) { Text("Shop official store ↗") }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(products, key = { it["id"].toString() }) { product ->
                    Column(Modifier.width(152.dp).clickable { secureLink(product["url"] as? String)?.let(uri::openUri) }) {
                        Surface(color = androidx.compose.ui.graphics.Color.White, shape = MaterialTheme.shapes.medium) {
                            val image = secureLink(product["imageUrl"] as? String)
                            if (image != null) AsyncImage(image, contentDescription = null, modifier = Modifier.size(152.dp), contentScale = ContentScale.Fit)
                            else Box(Modifier.size(152.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Gray, modifier = Modifier.size(36.dp)) }
                        }
                        Text(product["title"] as? String ?: "", maxLines = 2, modifier = Modifier.heightIn(min = 42.dp).padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        val price = runCatching { NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(product["currency"] as? String ?: "USD") }.format((product["price"] as? Number)?.toDouble() ?: 0.0) }.getOrDefault("")
                        Text((if (product["priceVaries"] == true) "From " else "") + price + " ↗", style = MaterialTheme.typography.bodySmall, color = CorusColors.Secondary)
                    }
                }
            }
        }
    }
    if (artistName.isNotBlank()) TextButton(onClick = { claim = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(parityCopy("Claim this artist page"), color = CorusColors.Secondary) }
    if (claim) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !submitting })
        ModalBottomSheet(onDismissRequest = { if (!submitting) claim = false }, sheetState = sheet) {
            ArtistClaimForm(artistId, artistName, viewModel, onBusy = { submitting = it }, done = { claim = false })
        }
    }
}

@Composable
private fun ArtistClaimForm(artistId: String, artistName: String, model: ArtistMerchViewModel, onBusy: (Boolean) -> Unit, done: () -> Unit) {
    var name by remember { mutableStateOf(model.viewer?.displayName.orEmpty()) }
    var email by remember { mutableStateOf(model.viewer?.email.orEmpty()) }
    var role by remember { mutableStateOf("Artist") }
    var url by remember { mutableStateOf("") }
    var explanation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    val requestId = remember { UUID.randomUUID().toString() }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (success) "Request submitted" else "Claim artist page", style = MaterialTheme.typography.headlineSmall)
        if (success) {
            Text("Your request will be reviewed. We’ll contact you at $email if we need more information.")
            Button(onClick = done, modifier = Modifier.fillMaxWidth()) { Text(parityCopy("Done")) }
        } else {
            Text(artistName, style = MaterialTheme.typography.titleLarge)
            Text(parityCopy("Represent this artist? Share a few details so we can review your request to claim this page."))
            OutlinedTextField(name, { name = it }, label = { Text(parityCopy("Full name")) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text(parityCopy("Contact email")) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            var rolesOpen by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { rolesOpen = true }, enabled = !busy) { Text("Role: $role") }
                DropdownMenu(expanded = rolesOpen, onDismissRequest = { rolesOpen = false }) {
                    listOf("Artist", "Manager", "Label representative", "Merch operator", "Other representative").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { role = option; rolesOpen = false }) }
                }
            }
            OutlinedTextField(url, { url = it }, label = { Text("Official website or social profile") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(explanation, { explanation = it }, label = { Text(parityCopy("How can we verify your connection to this artist?")) }, minLines = 4, enabled = !busy, modifier = Modifier.fillMaxWidth())
            Text(parityCopy("Link to an official page that supports your role and explain your affiliation (at least 20 characters). Please don’t include passwords or identity documents. Your details will be sent privately to the Corus team for review."), style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !busy && name.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() && secureLink(url) != null && explanation.trim().length >= 20, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true; onBusy(true); error = null
                scope.launch {
                    try {
                        model.call("submitArtistClaim", mapOf("requestId" to requestId, "artistId" to artistId, "artistName" to artistName, "name" to name.trim(), "email" to email.trim(), "role" to role, "verificationUrl" to url.trim(), "explanation" to explanation.trim()))
                        success = true
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Please try again." }
                    finally { busy = false; onBusy(false) }
                }
            }) { Text(if (busy) "Submitting…" else "Submit request") }
            Text(parityCopy("Submitting a request does not automatically verify you or give you control of the artist page."), style = MaterialTheme.typography.bodySmall)
        }
    }
}
