package fm.corus.android.ui.screens.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.valentinilk.shimmer.shimmer
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.domain.ProfileCollectionPolicy
import fm.corus.android.domain.ProfileTrophySummary
import fm.corus.android.domain.CollectionPlaybackQueue
import fm.corus.android.domain.CollectionPlaybackPage
import fm.corus.android.domain.PlaybackOrigin
import fm.corus.android.localization.CorusStrings
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.ProfileCollectionAnalytics
import fm.corus.android.ui.components.CorusModalBottomSheet
import fm.corus.android.ui.components.rememberGuardedSheetState
import fm.corus.android.ui.components.rememberReducedMotion
import fm.corus.android.ui.components.parityCopy
import fm.corus.android.ui.screens.notifications.GiftNotificationArtwork
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import android.os.SystemClock
import javax.inject.Inject

internal data class CollectionCounts(val trophies: Int?, val gifts: Int?)
internal data class CollectionItem(val id: String, val title: String, val subtitle: String, val artwork: String, val media: String, val date: Long, val post: CymbalPost? = null, val artworkFallback: String? = null)
internal data class CollectionPage(val items: List<CollectionItem>, val cursor: Map<*, *>?, val mediaTypes: List<String> = emptyList())

@HiltViewModel
class ProfileCollectionViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    val flags: RemoteConfigService,
    private val functions: FirebaseFunctions,
    private val posts: PostRepository,
    private val analytics: AnalyticsService,
) : ViewModel() {
    val viewer get() = auth.currentUser?.uid
    fun visible(profile: String) = ProfileCollectionPolicy.visible(flags.profileCollectionEnabled, viewer, profile)
    internal fun track(profile: String, session: String, action: String, section: String, media: String, details: Map<String, Any> = emptyMap()) {
        if (!visible(profile)) return
        analytics.logEvent(ProfileCollectionAnalytics.EVENT, ProfileCollectionAnalytics.params(action, ProfileCollectionAnalytics.Context(session, if (profile == viewer) "self" else "other", section, media), details))
    }
    private suspend fun call(name: String, profile: String, args: Map<String, Any>): Map<*, *> {
        check(visible(profile))
        val viewerId = viewer
        val result = functions.getHttpsCallable(name).call(args).await().getData() as? Map<*, *> ?: error("Invalid collection response")
        check(viewerId == viewer && visible(profile))
        return result
    }
    internal suspend fun summary(profile: String): CollectionCounts {
        val viewerId = viewer
        val raw = call("getProfileData", profile, mapOf("userId" to profile, "pageSize" to 1))
        val counts = CollectionCounts(ProfileCollectionPolicy.count(raw["trophyCount"]), ProfileCollectionPolicy.count(raw["profileGiftCount"]))
        ProfileTrophySummary.remember(viewerId, profile, counts.trophies, counts.gifts)
        return counts
    }
    internal fun cachedSummary(profile: String): CollectionCounts? = ProfileTrophySummary.counts(viewer, profile)
        ?.let { CollectionCounts(it.trophies, it.gifts) }
    internal suspend fun trophies(profile: String, media: String, cursor: Map<*, *>?): CollectionPage {
        val args = mutableMapOf<String, Any>("userId" to profile, "sort" to "popular", "mediaType" to media)
        cursor?.let { args["cursor"] = it }
        val raw = call("getProfileTrophies", profile, args)
        val items = (raw["items"] as? List<*>)?.mapNotNull { value ->
            val p = value as? Map<*, *> ?: return@mapNotNull null
            val id = p["id"] as? String ?: return@mapNotNull null
            CollectionItem(id, p["title"] as? String ?: "", p["subtitle"] as? String ?: "", p["artworkURL"] as? String ?: "", p["mediaType"] as? String ?: "track", (p["postedAtMs"] as? Number)?.toLong() ?: 0, artworkFallback = p["artworkFallbackURL"] as? String)
        }.orEmpty()
        return CollectionPage(items, (raw["nextCursor"] as? Map<*, *>)?.takeUnless { it == cursor }, (raw["availableMediaTypes"] as? List<*>)?.filterIsInstance<String>().orEmpty())
    }
    internal suspend fun gifts(profile: String, cursor: Map<*, *>?): CollectionPage {
        check(visible(profile)); val viewerId = viewer ?: error("Sign in required")
        var before = (cursor?.get("beforeMs") as? Number)?.toLong()
        val items = mutableListOf<CollectionItem>()
        // Bounded, resumable scan. Do not silently discard gifts on older posts.
        repeat(4) {
            val page = posts.getProfilePosts(profile, viewerId, limit = 30, lastTimestamp = before)
            check(viewerId == viewer && visible(profile))
            items += page.filter { it.giftCount > 0 }.map { p ->
                CollectionItem(p.id, p.displayTitle, p.displaySubtitle, p.displayImageLargeURL?.takeIf { it.isNotBlank() } ?: p.displayImageURL.orEmpty(), if (p.isMovie) "movie" else "track", p.timestamp.time, p, artworkFallback = p.displayImageURL)
            }
            val next = page.lastOrNull()?.timestamp?.time
            if (page.size < 30 || next == null || next == before) return CollectionPage(items, null)
            before = next
            if (items.isNotEmpty()) return CollectionPage(items, mapOf("beforeMs" to next))
        }
        return CollectionPage(items, before?.let { mapOf("beforeMs" to it) })
    }
    internal suspend fun giftTypes(profile: String, post: CymbalPost): List<String> {
        val raw = call("getPostGifts", profile, mapOf("postId" to post.id))
        val recent = (raw["gifts"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("giftType") as? String }.orEmpty()
        val types = ((raw["summary"] as? Map<*, *>)?.get("types") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("giftType") as? String }.orEmpty()
        return ProfileCollectionPolicy.giftLineup(recent.ifEmpty { post.recentGifts.map { it.giftType } }, types)
    }
    internal fun feedSession(profile: String, gifts: Boolean, media: String, items: List<CollectionItem>, cursor: Map<*, *>?, onPostEvent: (String) -> Unit = {}): CollectionFeedSession {
        val viewerId = checkNotNull(viewer)
        return CollectionFeedSession(profile, gifts, CollectionPage(items, cursor),
            fetchPost = { posts.getPostDetail(it, viewerId) },
            fetchPage = { next -> if (gifts) this.gifts(profile, next) else trophies(profile, media, next) },
            allowed = { viewer == viewerId && visible(profile) }, onPostEvent = onPostEvent)
    }
    internal fun playbackQueue(profile: String, section: String, media: String, items: List<CollectionItem>, initialCursor: Map<*, *>?): CollectionPlaybackQueue {
        val viewerId = checkNotNull(viewer)
        var nextCursor = initialCursor
        fun playbackPage(page: CollectionPage) = CollectionPlaybackPage(
            page.items.filter { it.media == "track" }.map { it.id },
            page.items.mapNotNull { it.post }, page.cursor != null)
        return CollectionPlaybackQueue(
            PlaybackOrigin.ProfileCollection(profile, UUID.randomUUID().toString()),
            playbackPage(CollectionPage(items, initialCursor)),
            fetchPost = { posts.getPostDetail(it, viewerId) },
            fetchPage = {
                val page = if (section == "gifts") gifts(profile, nextCursor) else trophies(profile, media, nextCursor)
                nextCursor = page.cursor
                playbackPage(page)
            }, allowed = { viewer == viewerId && visible(profile) },
        )
    }
}

@Composable
internal fun ProfileCollectionButton(profileId: String, size: Dp = 23.dp, buttonSide: Dp = 48.dp, tint: Color = CorusColors.Secondary, model: ProfileCollectionViewModel = hiltViewModel(), knownEmptyProfile: Boolean = false, onFeed: (String, Int) -> Unit) {
    val revision by model.flags.revision.collectAsState()
    var open by remember(profileId, model.viewer) { mutableStateOf(false) }
    if (!model.visible(profileId)) return
    IconButton(onClick = { open = true }, modifier = Modifier.size(buttonSide)) {
        ProfileHeaderTrophyIcon(contentDescription = stringResource(CorusStrings.profile_collection_open), tint = tint, modifier = Modifier.size(size))
    }
    if (open) key(profileId, model.viewer) {
        ProfileCollectionSheet(profileId, model, knownEmptyProfile, onDismiss = { open = false }, onFeed = onFeed)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCollectionSheet(profileId: String, model: ProfileCollectionViewModel, knownEmptyProfile: Boolean, onDismiss: () -> Unit, onFeed: (String, Int) -> Unit) {
    // A loaded profile with no posts cannot have trophies or received gifts.
    // Seed only this sheet; the fresh summary can still reveal posts from another device.
    var counts by remember { mutableStateOf(if (knownEmptyProfile) CollectionCounts(0, 0) else model.cachedSummary(profileId)) }
    var countsFailed by remember { mutableStateOf(false) }
    var countsLoading by remember { mutableStateOf(true) }
    var summaryRetry by remember { mutableIntStateOf(0) }
    var giftsSelected by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("track") }
    var mediaTypes by remember { mutableStateOf(listOf("track", "movie")) }
    var menuOpen by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(emptyList<CollectionItem>()) }
    var cursor by remember { mutableStateOf<Map<*, *>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }
    var restartRequired by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    var navigating by remember { mutableStateOf(false) }
    val sheetState = rememberGuardedSheetState(skipPartiallyExpanded = false, allowProgrammaticDismiss = { navigating })
    val lastVisibleItem by remember { derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 } }
    val session = remember { UUID.randomUUID().toString() }
    val started = remember { SystemClock.elapsedRealtime() }
    val currentContext by rememberUpdatedState((if (giftsSelected) "gifts" else "trophies") to (if (giftsSelected) "all" else category))
    fun track(action: String, details: Map<String, Any> = emptyMap(), section: String = currentContext.first, media: String = currentContext.second) { model.track(profileId, session, action, section, media, details) }
    val scope = rememberCoroutineScope()
    // A known empty profile can open directly to its empty state while refreshing.
    val combinedEmpty = !countsFailed && ProfileCollectionPolicy.empty(counts?.trophies, counts?.gifts)
    val categories = listOf("track", "movie") + if (model.flags.booksEnabled && "book" in mediaTypes) listOf("book") else emptyList()
    fun loadNextPage(manualRetry: Boolean = false) {
        if (loading || combinedEmpty || !model.visible(profileId) || (failed && !manualRetry)) return
        val requested = cursor ?: return
        val requestedCategory = category; val requestedGifts = giftsSelected
        val requestedGeneration = generation
        track(if (manualRetry) "retry_tapped" else "load_more_requested", mapOf("phase" to "collection"))
        loading = true; failed = false
        scope.launch {
            try {
                val page = if (requestedGifts) model.gifts(profileId, requested) else model.trophies(profileId, requestedCategory, requested)
                if (generation == requestedGeneration && category == requestedCategory && giftsSelected == requestedGifts) {
                    entries = (entries + page.items).distinctBy { it.id }; cursor = page.cursor?.takeUnless { it == requested }
                    track("load_succeeded", mapOf("phase" to "collection", "item_count" to entries.size))
                    if (entries.isEmpty() && cursor == null) track("empty_shown", mapOf("item_count" to 0))
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (generation == requestedGeneration && category == requestedCategory && giftsSelected == requestedGifts) {
                    track("load_failed", mapOf("phase" to "collection"))
                    restartRequired = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.code == com.google.firebase.functions.FirebaseFunctionsException.Code.FAILED_PRECONDITION
                    failed = true
                }
            } finally { if (generation == requestedGeneration && category == requestedCategory && giftsSelected == requestedGifts) loading = false }
        }
    }
    DisposableEffect(session) {
        track("opened")
        onDispose { track("dismissed", mapOf("duration_ms" to SystemClock.elapsedRealtime() - started)) }
    }
    LaunchedEffect(combinedEmpty) { if (combinedEmpty) track("empty_shown", mapOf("trophy_count" to 0, "gift_count" to 0), "all", "all") }
    LaunchedEffect(summaryRetry) {
        countsLoading = true; countsFailed = false
        try {
            counts = model.summary(profileId); countsFailed = counts?.trophies == null || counts?.gifts == null
            track(if (countsFailed) "load_failed" else "load_succeeded", buildMap { put("phase", "summary"); counts?.trophies?.let { put("trophy_count", it) }; counts?.gifts?.let { put("gift_count", it) } })
        }
        catch (e: CancellationException) { throw e } catch (_: Exception) { countsFailed = true; track("load_failed", mapOf("phase" to "summary")) }
        finally { countsLoading = false }
    }
    LaunchedEffect(counts?.gifts) { if (!ProfileCollectionPolicy.hasGifts(counts?.gifts)) giftsSelected = false }
    LaunchedEffect(categories) { if (category !in categories) category = "track" }
    LaunchedEffect(giftsSelected, category) { gridState.scrollToItem(0) }
    LaunchedEffect(giftsSelected, category, retry, combinedEmpty) {
        generation++
        entries = emptyList(); cursor = null; failed = false; loading = true; restartRequired = false
        try {
            if (!combinedEmpty) {
                val page = if (giftsSelected) model.gifts(profileId, null) else model.trophies(profileId, category, null)
                entries = page.items; cursor = page.cursor
                if (!giftsSelected && page.mediaTypes.isNotEmpty()) mediaTypes = page.mediaTypes
                track("load_succeeded", mapOf("phase" to "collection", "item_count" to page.items.size))
                if (page.items.isEmpty() && page.cursor == null) track("empty_shown", mapOf("item_count" to 0))
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true; track("load_failed", mapOf("phase" to "collection")) }
        finally { loading = false }
    }
    LaunchedEffect(cursor, loading, failed, entries.size, lastVisibleItem, combinedEmpty) {
        if (!combinedEmpty && shouldLoadCollectionPage(cursor != null, loading, failed, entries.size, lastVisibleItem)) loadNextPage()
    }
    CorusModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        // Let the grid draw behind the gesture area instead of reserving a white strip.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
      Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
        val onPost: (String) -> Unit = { id ->
            if (!navigating) {
                navigating = true
                val feed = model.feedSession(profileId, giftsSelected, category, entries, cursor,
                    onPostEvent = { action -> track(action, mapOf("phase" to "post")) })
                scope.launch {
                    try {
                        dismissCollectionThenNavigate(
                            hide = { sheetState.hide() }, isHidden = { !sheetState.isVisible },
                            dismiss = onDismiss,
                            navigate = { if (feed.isAllowed()) { ProfileFeedCache.collection = feed; onFeed(id, feed.segment) } },
                        )
                    } finally { navigating = false }
                }
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (combinedEmpty) {
                CollectionEmpty(Icons.Outlined.EmojiEvents, stringResource(CorusStrings.profile_collection_empty_title), stringResource(CorusStrings.profile_collection_empty_body))
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  if (countsLoading && (counts?.trophies == null || counts?.gifts == null)) {
                    CollectionSkeletonBar(Modifier.size(36.dp).testTag("collection_header_skeleton"))
                    Column {
                        CollectionSkeletonText(CorusFont.displayName, 1f, Modifier.width(132.dp))
                        Spacer(Modifier.height(4.dp))
                        CollectionSkeletonText(CorusFont.artistName, 1f, Modifier.width(96.dp))
                    }
                  } else {
                    Icon(if (giftsSelected) Icons.Filled.CardGiftcard else Icons.Filled.EmojiEvents, null, tint = if (giftsSelected) CorusColors.Accent else Color(0xFFFFC107), modifier = Modifier.size(36.dp))
                    Column {
                        val count = if (giftsSelected) counts?.gifts else counts?.trophies
                        if (count != null) Text(pluralStringResource(if (giftsSelected) CorusStrings.profile_collection_gift_count else CorusStrings.profile_collection_trophy_count, count, count), style = CorusFont.displayName, color = CorusColors.Text)
                        else if (countsLoading) CollectionSkeletonText(CorusFont.displayName, 1f, Modifier.width(132.dp))
                        else Text(stringResource(if (giftsSelected) CorusStrings.activity_filter_gifts else CorusStrings.profile_collection_trophies), style = CorusFont.displayName)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(if (giftsSelected) CorusStrings.profile_collection_received else CorusStrings.profile_collection_first_to_share), style = CorusFont.artistName, color = CorusColors.Secondary)
                    }
                  }
                }
                if (countsFailed && !countsLoading) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(CorusStrings.profile_collection_counts_error), style = CorusFont.artistName, color = CorusColors.Secondary)
                    TextButton(onClick = { track("retry_tapped", mapOf("phase" to "summary")); summaryRetry++ }) { Text(parityCopy("Retry")) }
                }
                // Resolve the section layout before revealing controls or artwork.
                // Cached counts can render immediately while the summary refreshes.
                if (!countsLoading || counts?.gifts != null) {
                    val hasGifts = ProfileCollectionPolicy.hasGifts(counts?.gifts)
                    if (hasGifts) {
                        CollectionSectionTabs(
                            giftsSelected = giftsSelected,
                            onSelectTrophies = { if (giftsSelected) track("section_changed", section = "trophies", media = category); giftsSelected = false },
                            onSelectGifts = { if (!giftsSelected) track("section_changed", section = "gifts", media = "all"); giftsSelected = true },
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    if (!giftsSelected) {
                        Box {
                            val mediaIcon = if (category == "movie") Icons.Filled.Movie else if (category == "book") Icons.Filled.MenuBook else Icons.Filled.MusicNote
                            Row(Modifier.clip(CircleShape).background(CorusColors.Secondary.copy(alpha = 0.08f)).clickable { menuOpen = true }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(mediaIcon, stringResource(CorusStrings.profile_collection_filter), Modifier.size(16.dp))
                                Text(parityCopy(if (category == "movie") "Film" else if (category == "book") "Books" else "Music"), style = CorusFont.artistName)
                                Icon(Icons.Filled.ExpandMore, null, Modifier.size(16.dp))
                            }
                            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                                categories.forEach { value -> DropdownMenuItem(text = { Text(parityCopy(if (value == "movie") "Film" else if (value == "book") "Books" else "Music")) }, onClick = { if (category != value) track("filter_changed", media = value); category = value; menuOpen = false }) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    when {
                        failed && entries.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(CorusStrings.profile_collection_load_error), color = CorusColors.Secondary, style = CorusFont.artistName)
                            TextButton(onClick = { track("retry_tapped", mapOf("phase" to "collection")); retry++ }) { Text(parityCopy("Retry")) }
                        }
                        !loading && !countsLoading && entries.isEmpty() && cursor == null -> {
                            val icon = if (giftsSelected) Icons.Filled.CardGiftcard else if (category == "movie") Icons.Filled.Movie else if (category == "book") Icons.Filled.MenuBook else Icons.Filled.MusicNote
                            CollectionEmpty(icon,
                                stringResource(if (giftsSelected) CorusStrings.profile_collection_no_gifts else if (category == "movie") CorusStrings.profile_collection_no_film else if (category == "book") CorusStrings.profile_collection_no_books else CorusStrings.profile_collection_no_music),
                                stringResource(if (giftsSelected) CorusStrings.profile_collection_gifts_empty else if (category == "movie") CorusStrings.profile_collection_film_empty else if (category == "book") CorusStrings.profile_collection_books_empty else CorusStrings.profile_collection_music_empty))
                        }
                        else -> LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().weight(1f), state = gridState, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
                            if ((loading || countsLoading) && entries.isEmpty()) {
                                items(18) { CollectionTileSkeleton(category) }
                            } else {
                                items(entries, key = { it.id }) { item -> CollectionTile(item, giftsSelected, profileId, model, { action, details -> track(action, details) }) { id -> track("post_tapped", mapOf("phase" to "post"), media = item.media); onPost(id) } }
                                // Pagination status scrolls with the grid; it must not resize it.
                                if (loading) item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CollectionSkeletonBar(Modifier.width(80.dp).height(16.dp)) }
                                }
                                if (failed) item(span = { GridItemSpan(maxLineSpan) }) {
                                    TextButton(onClick = {
                                        if (restartRequired) { track("retry_tapped", mapOf("phase" to "collection")); retry++ }
                                        else loadNextPage(manualRetry = true)
                                    }, modifier = Modifier.fillMaxWidth()) { Text(parityCopy("Retry")) }
                                }
                            }
                        }
                    }
                }
            }
        }
      }
    }
}

/** Mirrors iOS ProfileSegmentControl: full-segment underline and quiet inactive text. */
@Composable
private fun CollectionSectionTabs(
    giftsSelected: Boolean,
    onSelectTrophies: () -> Unit,
    onSelectGifts: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().selectableGroup()) {
        val labels = listOf(stringResource(CorusStrings.profile_collection_trophies), stringResource(CorusStrings.activity_filter_gifts))
        labels.forEachIndexed { index, label ->
            val selected = if (index == 0) !giftsSelected else giftsSelected
            Column(
                Modifier.weight(1f).selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = if (index == 0) onSelectTrophies else onSelectGifts,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, style = CorusFont.buttonSmall, color = if (selected) CorusColors.Text else CorusColors.Secondary, modifier = Modifier.padding(vertical = 8.dp))
                Box(Modifier.fillMaxWidth().height(2.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height(if (selected) 2.dp else .5.dp).background(if (selected) CorusColors.Text else CorusColors.Divider))
                }
            }
        }
    }
}

@Composable
private fun CollectionTile(item: CollectionItem, gift: Boolean, profile: String, model: ProfileCollectionViewModel, onEvent: (String, Map<String, Any>) -> Unit, onPost: (String) -> Unit) {
    val reduceMotion = rememberReducedMotion()
    var revealed by remember(item.id) { mutableStateOf(false) }
    val revealAlpha by animateFloatAsState(
        targetValue = if (reduceMotion || revealed) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 220),
        label = "Collection tile reveal",
    )
    LaunchedEffect(item.id) { revealed = true }
    CollectionTileLayout(
        media = item.media,
        // Fade artwork, badge and text together without moving existing rows.
        modifier = Modifier.graphicsLayer { alpha = revealAlpha }.clickable { onPost(item.id) },
        artwork = { fm.corus.android.ui.components.CollectionArtwork(item.artwork, fallbackUrl = item.artworkFallback, modifier = Modifier.fillMaxSize().background(CorusColors.Secondary.copy(alpha = 0.08f))) },
        badge = {
                if (gift && item.post != null) CollectionGiftBadge(profile, item.post, model, onEvent)
                else Box(Modifier.size(30.dp).background(Color.Black.copy(alpha = .85f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.EmojiEvents, null, tint = Color(0xFFFFC107), modifier = Modifier.size(20.dp)) }
        },
        title = { Text(item.title, style = CorusFont.songTitle, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = { Text(item.subtitle, style = CorusFont.artistName, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        date = { Text(if (item.date > 0) DateFormat.getDateInstance(DateFormat.LONG).format(Date(item.date)) else "", style = CorusFont.timestamp, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/** One geometry for placeholders and loaded tiles, including all three text lines. */
@Composable
internal fun CollectionTileLayout(
    media: String,
    modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit,
    badge: @Composable () -> Unit = {},
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    date: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.padding(bottom = 4.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(if (media == "track") 1f else 2f / 3f).clip(RoundedCornerShape(12.dp))) { artwork() }
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp)) { badge() }
        }
        title()
        Spacer(Modifier.height(4.dp))
        subtitle()
        Spacer(Modifier.height(4.dp))
        date()
    }
}
@Composable
private fun CollectionGiftBadge(profile: String, post: CymbalPost, model: ProfileCollectionViewModel, onEvent: (String, Map<String, Any>) -> Unit) {
    var kinds by remember(post.id) { mutableStateOf(ProfileCollectionPolicy.giftLineup(post.recentGifts.map { it.giftType })) }
    var loading by remember(post.id) { mutableStateOf(kinds.isEmpty()) }
    LaunchedEffect(post.id) {
        try { if (post.recentGifts.isEmpty() || post.giftCount > post.recentGifts.size) { kinds = model.giftTypes(profile, post); onEvent("load_succeeded", mapOf("phase" to "gift_details")) } }
        catch (e: CancellationException) { throw e } catch (_: Exception) { onEvent("load_failed", mapOf("phase" to "gift_details")) /* Keep the known preview, never invent a gift. */ }
        finally { loading = false }
    }
    Row(Modifier.background(Color.Black.copy(alpha = .85f), CircleShape).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        kinds.forEach { GiftNotificationArtwork(it, 28.dp) }
        if (kinds.isEmpty() && loading) CollectionSkeletonBar(Modifier.size(28.dp))
        Text(post.giftCount.toString(), style = CorusFont.timestamp, color = Color.White)
    }
}
@Composable
private fun CollectionEmpty(icon: ImageVector, title: String, message: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = 250.dp).padding(horizontal = 20.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).background(CorusColors.Secondary.copy(alpha = .08f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(26.dp), tint = CorusColors.Secondary) }
        Spacer(Modifier.height(16.dp)); Text(title, style = CorusFont.songTitle, color = CorusColors.Text)
        Spacer(Modifier.height(8.dp)); Text(message, style = CorusFont.artistName, color = CorusColors.Secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
@Composable
private fun CollectionSkeletonBar(modifier: Modifier) { Box(modifier.clip(RoundedCornerShape(4.dp)).shimmer().background(CorusColors.Secondary.copy(alpha = .12f))) }
@Composable
internal fun CollectionTileSkeleton(media: String, modifier: Modifier = Modifier) {
    CollectionTileLayout(
        media = media,
        modifier = modifier,
        artwork = { CollectionSkeletonBar(Modifier.fillMaxSize()) },
        title = { CollectionSkeletonText(CorusFont.songTitle, 1f) },
        subtitle = { CollectionSkeletonText(CorusFont.artistName, .7f) },
        date = { CollectionSkeletonText(CorusFont.timestamp, .8f) },
    )
}

/** Match PostDetailHeader/AlbumArt/SongInfo instead of the smaller feed card. */
@Composable
internal fun CollectionPostSkeleton(media: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(CorusSpacing.avatarMedium).clip(CircleShape).shimmer().background(CorusColors.Skeleton))
            Spacer(Modifier.width(CorusSpacing.sm))
            CollectionSkeletonText(CorusFont.username, 1f, Modifier.width(96.dp))
        }
        Box(Modifier.fillMaxWidth().aspectRatio(if (media == "movie") 2f / 3f else 1f).shimmer().background(CorusColors.Skeleton))
        Column(Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.lg).padding(top = CorusSpacing.md, bottom = CorusSpacing.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CollectionSkeletonText(CorusFont.songTitle, 1f)
            CollectionSkeletonText(CorusFont.artistName, .7f)
        }
    }
}

@Composable
private fun CollectionSkeletonText(style: TextStyle, widthFraction: Float, modifier: Modifier = Modifier) {
    val barHeight = with(LocalDensity.current) { style.fontSize.toDp() } * .75f
    Box(modifier.fillMaxWidth()) {
        // Measure with the actual font metrics, including accessibility font scaling.
        Text(" ", style = style, maxLines = 1)
        CollectionSkeletonBar(Modifier.align(Alignment.CenterStart).fillMaxWidth(widthFraction).height(barHeight))
    }
}
