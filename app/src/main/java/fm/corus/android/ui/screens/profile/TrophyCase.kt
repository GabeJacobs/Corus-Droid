package fm.corus.android.ui.screens.profile

import fm.corus.android.ui.components.CorusModalBottomSheet
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.ProfileCollectionAnalytics
import fm.corus.android.localization.CorusStrings
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.os.SystemClock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

internal fun trophyViewerAllowed(viewerId: String?, disabled: Boolean): Boolean = !disabled && viewerId in setOf("FUQZIrZR08T2Ux2vYpPzWx7B1rv1", "u3UmswvOg5c2r9zYlOidJYFzqbp2")
@HiltViewModel
class TrophyCaseViewModel @Inject constructor(private val auth: FirebaseAuth, val flags: RemoteConfigService, private val functions: FirebaseFunctions, private val posts: fm.corus.android.data.repository.PostRepository, private val analytics: AnalyticsService) : ViewModel() {
    val viewer get() = auth.currentUser?.uid
    val allowed get() = trophyViewerAllowed(viewer, flags.trophyCaseDisabled)
    internal fun track(profile: String, session: String, action: String, media: String, details: Map<String, Any> = emptyMap()) {
        if (!allowed || fm.corus.android.domain.ProfileCollectionPolicy.visible(flags.profileCollectionEnabled, viewer, profile)) return
        analytics.logEvent(ProfileCollectionAnalytics.EVENT, ProfileCollectionAnalytics.params(action,
            ProfileCollectionAnalytics.Context(session, if (profile == viewer) "self" else "other", "trophies", media), details))
    }
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
    private fun collectionPage(raw: Map<*, *>): CollectionPage = CollectionPage(
        (raw["items"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.mapNotNull { p ->
            val id = p["id"] as? String ?: return@mapNotNull null
            CollectionItem(id, p["title"] as? String ?: "", p["subtitle"] as? String ?: "",
                p["artworkURL"] as? String ?: "", p["mediaType"] as? String ?: "track",
                (p["postedAtMs"] as? Number)?.toLong() ?: 0)
        }.orEmpty(), raw["nextCursor"] as? Map<*, *>)
    internal fun feedSession(profile: String, media: String, entries: List<Map<*, *>>, cursor: Map<*, *>?, onPostEvent: (String) -> Unit = {}): CollectionFeedSession {
        val viewerId = checkNotNull(viewer)
        return CollectionFeedSession(profile, false, collectionPage(mapOf("items" to entries, "nextCursor" to cursor)),
            fetchPost = { posts.getPostDetail(it, viewerId) },
            fetchPage = { collectionPage(page(profile, media, it)) },
            allowed = { viewer == viewerId && allowed }, onPostEvent = onPostEvent)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrophyCase(profile: CymbalUser, onPost: (String) -> Unit, model: TrophyCaseViewModel = hiltViewModel(), onFeed: ((String, Int) -> Unit)? = null) {
    val revision by model.flags.revision.collectAsState()
    if (fm.corus.android.domain.ProfileCollectionPolicy.visible(model.flags.profileCollectionEnabled, model.viewer, profile.id)) return
    if (!profile.showTrophies || !model.allowed) return
    var summary by remember(profile.id, model.viewer) { mutableStateOf<Map<*, *>?>(null) }
    var open by remember(profile.id) { mutableStateOf(false) }
    LaunchedEffect(profile.id, model.viewer, revision) { summary = runCatching { model.page(profile.id, "track") }.getOrNull() }
    val count = (summary?.get("total") as? Number)?.toInt() ?: fm.corus.android.domain.ProfileTrophySummary.count(model.viewer,profile.id) ?: return
    TextButton(onClick = { open = true }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("🏆 " + androidx.compose.ui.res.pluralStringResource(fm.corus.android.R.plurals.parity_trophy_count, count, count), color = Color(0xFFB78820)) }
    var navigating by remember { mutableStateOf(false) }
    val sheetState = fm.corus.android.ui.components.rememberGuardedSheetState(skipPartiallyExpanded = false, allowProgrammaticDismiss = { navigating })
    val scope = rememberCoroutineScope()
    if (open) CorusModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
        TrophyGrid(profile.id, count, summary, model, onPost = { id, feed ->
            if (!navigating) {
                navigating = true
                scope.launch {
                    try {
                        dismissCollectionThenNavigate({ sheetState.hide() }, { !sheetState.isVisible }, { open = false }) {
                            if (feed.isAllowed()) {
                                if (onFeed != null) { ProfileFeedCache.collection = feed; onFeed(id, feed.segment) }
                                else onPost(id)
                            }
                        }
                    } finally { navigating = false }
                }
            }
        })
    }
}
@Composable
private fun TrophyGrid(profileId: String, count: Int, summary: Map<*, *>?, model: TrophyCaseViewModel, onPost: (String, CollectionFeedSession) -> Unit) {
    var category by remember { mutableStateOf("track") }
    var entries by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    var cursor by remember { mutableStateOf<Map<*, *>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val session = remember { UUID.randomUUID().toString() }
    val viewer = remember { model.viewer }
    val started = remember { SystemClock.elapsedRealtime() }
    val currentCategory by rememberUpdatedState(category)
    fun track(action: String, details: Map<String, Any> = emptyMap(), media: String = currentCategory) {
        if (viewer == model.viewer) model.track(profileId, session, action, media, details)
    }
    DisposableEffect(session) {
        track("opened")
        track("load_succeeded", mapOf("phase" to "summary", "trophy_count" to count))
        onDispose { track("dismissed", mapOf("duration_ms" to SystemClock.elapsedRealtime() - started)) }
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(category, retry) {
        loading = true; error = false; entries = emptyList(); cursor = null
        try {
            val page = model.page(profileId, category)
            entries = (page["items"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
            cursor = page["nextCursor"] as? Map<*, *>
            track("load_succeeded", mapOf("phase" to "collection", "item_count" to entries.size))
            if (entries.isEmpty() && cursor == null) track("empty_shown", mapOf("item_count" to 0))
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true; track("load_failed", mapOf("phase" to "collection")) }
        finally { loading = false }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text(pluralStringResource(CorusStrings.profile_collection_trophy_count, count, count), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val categories = listOf("track" to "Music", "movie" to "Film") + if (model.flags.booksEnabled && (summary?.get("availableMediaTypes") as? List<*>)?.contains("book") == true) listOf("book" to "Books") else emptyList()
            categories.forEach { (value, label) -> FilterChip(category == value, onClick = { if (category != value) track("filter_changed", media = value); category = value }, label = { Text(parityCopy(label)) }, enabled = !loading, modifier = Modifier.weight(1f)) }
        }
        if (loading && entries.isEmpty()) Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        else if (error && entries.isEmpty()) TextButton(onClick = { track("retry_tapped", mapOf("phase" to "collection")); retry++ }) { Text(stringResource(CorusStrings.common_retry)) }
        else if (entries.isEmpty()) Text(parityCopy("No trophies yet"), modifier = Modifier.padding(vertical = 40.dp))
        else LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it["id"].toString() }) { entry ->
                fm.corus.android.ui.components.CollectionArtwork(entry["artworkURL"] as? String ?: "", fallbackUrl = entry["artworkFallbackURL"] as? String, contentDescription = "${entry["title"]}, ${entry["subtitle"]}", modifier = Modifier.fillMaxWidth().aspectRatio(if (category == "track") 1f else 2f / 3f).clickable { track("post_tapped", mapOf("phase" to "post")); onPost(entry["id"].toString(), model.feedSession(profileId, category, entries, cursor, onPostEvent = { action -> track(action, mapOf("phase" to "post")) })) })
            }
        }
        if (cursor != null) TextButton(enabled = !loading, onClick = {
            val requested = cursor ?: return@TextButton
            val requestedCategory = category
            track(if (error) "retry_tapped" else "load_more_requested", mapOf("phase" to "collection"))
            loading = true; error = false
            scope.launch {
                try {
                    val page = model.page(profileId, requestedCategory, requested)
                    if (category == requestedCategory) {
                        entries = (entries + (page["items"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()).distinctBy { it["id"] }
                        cursor = (page["nextCursor"] as? Map<*, *>)?.takeUnless { it == requested }
                        track("load_succeeded", mapOf("phase" to "collection", "item_count" to entries.size))
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true; track("load_failed", mapOf("phase" to "collection")) }
                finally { loading = false }
            }
        }) { Text(if (error) stringResource(CorusStrings.common_retry) else parityCopy("See more")) }
    }
}
