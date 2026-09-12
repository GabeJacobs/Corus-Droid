package fm.corus.android.ui.screens.profile

import fm.corus.android.ui.components.parityCopy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

internal fun trophyViewerAllowed(viewerId: String?, disabled: Boolean): Boolean = !disabled && viewerId in setOf("FUQZIrZR08T2Ux2vYpPzWx7B1rv1", "u3UmswvOg5c2r9zYlOidJYFzqbp2")
@HiltViewModel
class TrophyCaseViewModel @Inject constructor(private val auth: FirebaseAuth, val flags: RemoteConfigService, private val functions: FirebaseFunctions) : ViewModel() {
    val viewer get() = auth.currentUser?.uid
    val allowed get() = trophyViewerAllowed(viewer, flags.trophyCaseDisabled)
    private val cache = mutableMapOf<String, Pair<Long, Map<*, *>>>()
    suspend fun page(profile: String, media: String, cursor: Map<*, *>? = null): Map<*, *> {
        check(allowed)
        val uid = viewer; val key = "$uid:$profile:$media"
        if (cursor == null) cache[key]?.takeIf { System.currentTimeMillis() - it.first < 300_000 }?.let { return it.second }
        val args = mutableMapOf<String, Any>("userId" to profile, "sort" to "popular", "mediaType" to media)
        cursor?.let { args["cursor"] = it }
        val result = functions.getHttpsCallable("getProfileTrophies").call(args).await().getData() as? Map<*, *> ?: error("Please try again.")
        check(viewer == uid && allowed)
        if (cursor == null) cache[key] = System.currentTimeMillis() to result
        return result
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrophyCase(profile: CymbalUser, onPost: (String) -> Unit, model: TrophyCaseViewModel = hiltViewModel()) {
    val revision by model.flags.revision.collectAsState()
    if (!profile.showTrophies || !model.allowed) return
    var summary by remember(profile.id, model.viewer) { mutableStateOf<Map<*, *>?>(null) }
    var open by remember(profile.id) { mutableStateOf(false) }
    LaunchedEffect(profile.id, model.viewer, revision) { summary = runCatching { model.page(profile.id, "track") }.getOrNull() }
    val count = (summary?.get("total") as? Number)?.toInt() ?: fm.corus.android.domain.ProfileTrophySummary.count(model.viewer,profile.id) ?: return
    TextButton(onClick = { open = true }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("🏆 " + androidx.compose.ui.res.pluralStringResource(fm.corus.android.R.plurals.parity_trophy_count, count, count), color = Color(0xFFB78820)) }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }) {
        TrophyGrid(profile.id, summary, model, onPost = { open = false; onPost(it) })
    }
}
@Composable
private fun TrophyGrid(profileId: String, summary: Map<*, *>?, model: TrophyCaseViewModel, onPost: (String) -> Unit) {
    var category by remember { mutableStateOf("track") }
    var entries by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    var cursor by remember { mutableStateOf<Map<*, *>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(category, retry) {
        loading = true; error = false; entries = emptyList(); cursor = null
        try {
            val page = model.page(profileId, category)
            entries = (page["items"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
            cursor = page["nextCursor"] as? Map<*, *>
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
        finally { loading = false }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text("${summary?.get("total") ?: 0} trophies", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val categories = listOf("track" to "Music", "movie" to "Film") + if (model.flags.booksEnabled && (summary?.get("availableMediaTypes") as? List<*>)?.contains("book") == true) listOf("book" to "Books") else emptyList()
            categories.forEach { (value, label) -> FilterChip(category == value, onClick = { category = value }, label = { Text(parityCopy(label)) }, enabled = !loading, modifier = Modifier.weight(1f)) }
        }
        if (loading && entries.isEmpty()) Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        else if (error && entries.isEmpty()) TextButton(onClick = { retry++ }) { Text(parityCopy("Retry")) }
        else if (entries.isEmpty()) Text(parityCopy("No trophies yet"), modifier = Modifier.padding(vertical = 40.dp))
        else LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it["id"].toString() }) { entry ->
                AsyncImage(entry["artworkURL"], contentDescription = "${entry["title"]}, ${entry["subtitle"]}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(if (category == "track") 1f else 2f / 3f).clickable { onPost(entry["id"].toString()) })
            }
        }
        if (cursor != null) TextButton(enabled = !loading, onClick = {
            val requested = cursor ?: return@TextButton
            val requestedCategory = category
            loading = true; error = false
            scope.launch {
                try {
                    val page = model.page(profileId, requestedCategory, requested)
                    if (category == requestedCategory) {
                        entries = (entries + (page["items"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()).distinctBy { it["id"] }
                        cursor = (page["nextCursor"] as? Map<*, *>)?.takeUnless { it == requested }
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                finally { loading = false }
            }
        }) { Text(if (error) "Retry" else "See more") }
    }
}
