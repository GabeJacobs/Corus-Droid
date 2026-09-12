package fm.corus.android.ui.screens.map

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.domain.MapPlaybackOwner
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.toQueuedTrack
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class MapScreenState(
    val listPages: Map<String, MapPeoplePage> = emptyMap(), val listLoading: Set<String> = emptySet(), val listErrors: Set<String> = emptySet(),
    val selectedCommunityId: String? = null, val cities: List<MapCitySummary> = emptyList(), val selected: MapCity? = null,
    val people: List<MapPerson> = emptyList(), val posts: Map<String, CymbalPost?> = emptyMap(),
    val filter: String = "all", val loading: Boolean = true, val peopleLoading: Boolean = false,
    val more: String? = null, val peopleReachedEnd: Boolean = false, val chat: MapChatStatus? = null, val chatLoading: Boolean = false,
    val user: CymbalUser? = null, val error: String? = null,
    val searchResults: List<MapCity> = emptyList(), val busy: Boolean = false,
    val mode: String? = null, val playing: MapPlaybackItem? = null, val preview: MapPreviewUsage = MapPreviewUsage(),
    val currentDeviceCityId: String? = null, val ownSource: String? = null,
    val ownPresenceReady: Boolean = false, val ownAudience: String = "off", val ownCity: MapCity? = null, val paywall: String? = null, val historyIndex: Int = -1, val blocked: Boolean = false, val sessionKey: Int = 0,
)
@HiltViewModel
class MapExploreViewModel @Inject constructor(
    val repository: MapRepository, private val users: UserRepository,
    private val auth: FirebaseAuth, val subscription: SubscriptionRepository,
    val remote: RemoteConfigService, val player: NowPlayingManager,
) : ViewModel() {
    private val mutable = MutableStateFlow(MapScreenState())
    private fun updateState(transform: (MapScreenState) -> MapScreenState) {
        mutable.update { previous ->
            val next = transform(previous)
            if (next.ownPresenceReady && next.ownAudience == "off") removeMapViewer(next, uid) else next
        }
    }
    private var refreshGeneration = 0
    private fun ownPresenceChanged(data: Map<String, Any?>?) {
        generation++; refreshGeneration++
        updateState { it.copy(ownPresenceReady = true, ownSource = data?.get("source") as? String,
            ownAudience = data?.get("audience") as? String ?: "off", ownCity = data?.let(MapCity::decode),
            peopleLoading = false, listLoading = emptySet()) }
        refresh()
    }
    fun community(id: String?) {
        generation++; refreshGeneration++
        updateState { it.copy(selectedCommunityId = id, selected = null, people = emptyList(), cities = emptyList(), listPages = emptyMap(), listLoading = emptySet(), listErrors = emptySet(), loading = true) }
        refresh()
    }
    private var deviceCityCheckedAt = 0L
    fun expireDeviceCity() { if (System.currentTimeMillis() - deviceCityCheckedAt > 90000) clearDeviceCity() }
    private var locationGeneration = 0
    fun clearDeviceCity() { locationGeneration++; updateState { it.copy(currentDeviceCityId = null) }; refreshChat() }
    fun updateDeviceCity(location: Location) = launch {
        val request = ++locationGeneration
        if (kotlin.math.abs(System.currentTimeMillis() - location.time) > 90000 || location.accuracy < 0 || location.accuracy > 5000) {
            clearDeviceCity(); return@launch
        }
        try {
            val city = repository.resolve(location)
            if (request != locationGeneration) return@launch
            deviceCityCheckedAt = location.time
            updateState { it.copy(currentDeviceCityId = city.cityId) }
            refreshChat()
            val s = mutable.value
            if (s.ownSource == "device" && s.ownCity != null && s.ownCity.cityId != city.cityId && s.ownAudience != "off") {
                s.user?.let { repository.share(it, city, s.ownAudience, "device") }
                refresh()
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (request == locationGeneration) clearDeviceCity() }
    }
    val state = mutable.asStateFlow()
    private val uid = auth.currentUser?.uid
    private var canonicalJob: kotlinx.coroutines.Job? = null
    private val canonicalCities = mutableMapOf<String,MapCity>()
    var savedCamera: MapCameraPosition? = null
    var selectedCountries: Set<String> = emptySet()
    var directorySearch = ""
    var directoryCollapsed: List<String> = emptyList()
    var directoryScrollIndex = 0
    var directoryScrollOffset = 0
    private var generation = 0
    private var searchGeneration = 0
    private var following = emptyList<String>(); private var taste = emptyList<String>()
    private var candidates = emptyList<Pair<MapCity, MapPerson>>(); private var nextCandidate = 0
    private var history = mutableListOf<MapPlaybackItem>(); private var advanceBusy = false
    private var pendingCities = mutableListOf<MapCitySummary>()
    private var rosterCursor: String? = null
    private val rosterCursors = mutableSetOf<String>()
    private val rosterMutex = kotlinx.coroutines.sync.Mutex()
    private var directoryJob: kotlinx.coroutines.Job? = null
    private var directoryExtended = false
    private var round = 0; private var roundFound = false
    private val pool = linkedMapOf<String, Pair<MapCity, MapPerson>>()
    private val shownPosts = mutableSetOf<String>()
    private data class PersonHistory(val posts: MutableList<CymbalPost> = mutableListOf(), var cursor: Long? = null, var done: Boolean = false)
    private val postHistories = mutableMapOf<String, PersonHistory>()
    private val postHistoryMutex = kotlinx.coroutines.sync.Mutex()
    private var pendingAction: (() -> Unit)? = null
    private var endedPost: String? = null
    private var prefetchedPost: Pair<String, Deferred<Result<CymbalPost?>>>? = null
    private var ownedPlayback: MapPlaybackOwner.Owner? = null
    val enabled get() = remote.mapEnabled && uid != null && auth.currentUser?.uid == uid
    val fullAccess get() = subscription.hasFullAccess
    init {
        viewModelScope.launch { fm.corus.android.domain.MapPostChanges.revisions.collect {
            if (enabled) {
                val ids = mutable.value.posts.keys.toList()
                if (ids.isNotEmpty()) launch {
                    val gen = generation
                    val posts = repository.latest(ids)
                    if (gen == generation && enabled) updateState { it.copy(posts = posts) }
                }
            }
        } }
        uid?.let { viewer ->
            viewModelScope.launch { repository.ownPresence(viewer).collect { data -> ownPresenceChanged(data) } }
            viewModelScope.launch { repository.previewUpdates(viewer).collect { usage -> updateState { it.copy(preview = it.preview.merge(usage)) } } }
        }
        viewModelScope.launch {
            subscription.hasFullAccessFlow.collect { allowed ->
                if (allowed) { val resume = pendingAction; pendingAction = null; updateState { it.copy(blocked = false) }; resume?.invoke() }
            }
        }
        viewModelScope.launch { remote.revision.collect { if (enabled) refresh() else if (mutable.value.mode != null) stop() } }
    }
    private fun launch(block: suspend () -> Unit): kotlinx.coroutines.Job {
      val requestedGeneration = generation
      return viewModelScope.launch {
        try { check(auth.currentUser?.uid == uid); block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (auth.currentUser?.uid == uid && requestedGeneration == generation) updateState { it.copy(error = e.message ?: "Please try again.", loading = false, peopleLoading = false, busy = false) } }
    }
    }
    fun refresh() = launch {
        if (!enabled) { updateState { it.copy(loading = false) }; return@launch }
        val current = uid ?: return@launch
        val request = ++refreshGeneration
        val user = users.fetchUserProfile(current)
        users.prefetchFollowingSet(current)
        following = users.followingIds.value.toList()
        if (fullAccess) taste = users.getSuggestedUsers(current).filter { it.isTasteMatch }.map { it.user.id }
        val cities = repository.cities(following, taste, mutable.value.selectedCommunityId)
        if (request != refreshGeneration) return@launch
        if (auth.currentUser?.uid == current) updateState { it.copy(cities = cities.map { value -> canonicalCities[value.city.cityId]?.let { city -> value.copy(city=value.city.copy(cityName=city.cityName)) } ?: value }, loading = false, user = user, error = null) }
        if(auth.currentUser?.uid != current) return@launch
        canonicalJob?.cancel()
        canonicalJob = viewModelScope.launch { repository.canonicalUpdates(cities.map { it.city.cityId }).collect { city ->
            if(auth.currentUser?.uid != uid) return@collect
            canonicalCities[city.cityId]=city
            updateState { state -> state.copy(cities=state.cities.map { if(it.city.cityId==city.cityId) it.copy(city=it.city.copy(cityName=city.cityName)) else it },selected=state.selected?.let { if(it.cityId==city.cityId)it.copy(cityName=city.cityName) else it },ownCity=state.ownCity?.let { if(it.cityId==city.cityId)it.copy(cityName=city.cityName) else it },playing=state.playing?.let { if(it.city.cityId==city.cityId)it.copy(city=it.city.copy(cityName=city.cityName)) else it }) }
        } }

    }
    fun filter(value: String) {
        if (value == mutable.value.filter) return
        repository.event("filter_changed", value = value)
        if (value == "tasteMatches" && !fullAccess) { pendingAction = { filter(value) }; updateState { it.copy(paywall = "MAP") }; return }
        if (mutable.value.mode != null) stop()
        generation++; updateState { it.copy(filter = value, selected = null, people = emptyList(), listPages = emptyMap(), listLoading = emptySet(), listErrors = emptySet()) }
    }
    fun select(city: MapCity) {
        repository.event("city_opened")
        if (mutable.value.mode != null) stop()
        val gen = ++generation
        updateState { it.copy(selected = city, people = emptyList(), posts = emptyMap(), peopleLoading = true, chatLoading = true, chat = null, more = null, peopleReachedEnd = false, error = null) }
        launch {
            try { val chat = repository.chat(city.cityId, mutable.value.currentDeviceCityId); if (gen == generation) updateState { it.copy(chat = chat) } }
            finally { if (gen == generation) updateState { it.copy(chatLoading = false) } }
        }
        launch {
            val page = repository.people(city, mutable.value.filter, following, taste, selectedCommunityId = mutable.value.selectedCommunityId)
            val people = sortedMapPeople(page.people)
            val posts = repository.latest(people.take(8).map { it.user.id })
            if (gen == generation) updateState { it.copy(people = people, posts = posts, peopleLoading = false, more = page.cursor, peopleReachedEnd = page.reachedEnd) }
        }
    }
    fun loadList(city: MapCity, next: Boolean = false) = launch {
        val s = mutable.value; val key = city.cityId; val gen = generation
        if (key in s.listLoading) return@launch
        if (!next && key in s.listPages && key !in s.listErrors) return@launch
        val cursor = if (next) s.listPages[key]?.cursor ?: return@launch else null
        updateState { it.copy(listLoading = it.listLoading + key, listErrors = it.listErrors - key) }
        try {
            val page = repository.people(city, s.filter, following, taste, cursor, s.selectedCommunityId)
            if (gen == generation) updateState { current ->
                val people = sortedMapPeople((if (next) current.listPages[key]?.people.orEmpty() else emptyList()) + page.people)
                current.copy(listPages = current.listPages + (key to MapPeoplePage(people, page.cursor?.takeUnless { it == cursor }, page.reachedEnd)))
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (gen == generation) updateState { it.copy(listErrors = it.listErrors + key) } }
        finally { if (gen == generation) updateState { it.copy(listLoading = it.listLoading - key) } }
    }
    fun visiblePeople(ids: List<String>) = launch {
        val gen = generation
        val missing = ids.filter { it !in mutable.value.posts }
        if (missing.isEmpty()) return@launch
        val posts = repository.latest(missing)
        if (gen == generation) updateState { it.copy(posts = it.posts + posts) }
    }
    fun more() = launch {
        val s = mutable.value; val city = s.selected ?: return@launch; val cursor = s.more ?: return@launch; val gen = generation
        updateState { it.copy(peopleLoading = true) }
        val page = repository.people(city, s.filter, following, taste, cursor, s.selectedCommunityId)
        val posts = repository.latest(page.people.map { it.user.id })
        if (gen == generation) updateState { it.copy(people = sortedMapPeople(it.people + page.people), posts = it.posts + posts, more = page.cursor.takeUnless { next -> next == cursor }, peopleReachedEnd = page.reachedEnd, peopleLoading = false) }
    }
    private fun refreshChat() {
        val city = mutable.value.selected ?: return
        val gen = generation; val current = mutable.value.currentDeviceCityId
        updateState { it.copy(chat = null, chatLoading = true) }
        launch { val chat = repository.chat(city.cityId, current)
            if (gen == generation && current == mutable.value.currentDeviceCityId) updateState { it.copy(chat = chat, chatLoading = false) } }
    }
    fun closeCity() { generation++; updateState { it.copy(selected = null) } }
    fun error(message: String?) { updateState { it.copy(error = message) } }
    fun search(text: String) {
        val gen = ++searchGeneration
        if (text.trim().length < 2) { updateState { it.copy(searchResults = emptyList()) }; return }
        launch { kotlinx.coroutines.delay(250); if (gen != searchGeneration) return@launch
            val result = repository.search(text)
            if (gen == searchGeneration) updateState { it.copy(searchResults = result) }
        }
    }
    fun stopSharing(done: () -> Unit) = launch { repository.stopSharing(); ownPresenceChanged(null); done() }
    fun share(city: MapCity, audience: String, source: String, done: () -> Unit) = launch {
        val user = mutable.value.user ?: throw IllegalStateException("Couldn’t load your profile. Please try again.")
        updateState { it.copy(busy = true) }
        try {
            repository.share(user, city, audience, source)
            repository.event("sharing_saved", value = source)
            done(); refresh()
        } finally { updateState { it.copy(busy = false) } }
    }
    fun resolve(location: Location, done: (MapCity) -> Unit) = launch { done(repository.resolve(location)) }
    fun join(city: MapCity, location: Location, done: (String) -> Unit) = launch { done(repository.join(city, location)) }
    fun returnedFromPaywall() { if (!fullAccess) pendingAction = null }
    fun dismissPaywall() { updateState { it.copy(paywall = null) } }
    fun start(mode: String, countryCodes: Set<String>, cityId: String? = null): kotlinx.coroutines.Job = launch {
        if (advanceBusy || mutable.value.busy) return@launch
        val requestedGeneration = generation
        repository.event("playback_requested", mode, count = countryCodes.size)
        updateState { it.copy(busy = true, error = null) }
        val usage = if (fullAccess) mutable.value.preview else repository.preparePreview()
        if (requestedGeneration != generation || !enabled) return@launch
        updateState { it.copy(preview = usage) }
        if (!fullAccess && usage.remaining(mode) == 0) { pendingAction = { start(mode, countryCodes, cityId) }; updateState { it.copy(paywall = if (mode == "listen") "MAP_LISTEN" else "MAP_WATCH", busy = false) }; return@launch }
        ++generation
        directoryJob?.cancel(); directoryJob = null; directoryExtended = false
        round = 0; roundFound = false; pool.clear(); shownPosts.clear(); postHistories.clear()
        prefetchedPost?.second?.cancel(); prefetchedPost = null
        val selected = mutable.value.cities.filter { (it.facets[mutable.value.filter]?.count ?: 0) > 0 && if (cityId != null) it.city.cityId == cityId else countryCodes.isEmpty() || it.city.countryCode in countryCodes }
        pendingCities = selected.toMutableList(); rosterCursor = null; rosterCursors.clear()
        candidates = emptyList(); nextCandidate = 0; history.clear(); endedPost = null
        updateState { it.copy(mode = mode, selected = null, busy = false, playing = null, historyIndex = -1, blocked = false, sessionKey = it.sessionKey + 1) }
        next()
    }
    private suspend fun loadDirectoryPage(gen: Int) = rosterMutex.withLock {
        if (gen != generation || !enabled) return@withLock
        val city = pendingCities.firstOrNull() ?: return@withLock
        val page = repository.people(city.city, mutable.value.filter, following, taste, rosterCursor, mutable.value.selectedCommunityId)
        if (gen != generation || !enabled) return@withLock
        val added = page.people.filter { it.user.id != uid && !pool.containsKey(it.user.id) }
            .shuffled().map { city.city to it }
        added.forEach { pool[it.second.user.id] = it }
        // Appending preserves an in-flight candidate; shuffle between advances.
        candidates = candidates + added
        directoryExtended = directoryExtended || added.isNotEmpty()
        if (page.cursor != null && rosterCursors.add(page.cursor)) rosterCursor = page.cursor
        else { pendingCities.removeAt(0); rosterCursor = null; rosterCursors.clear() }
    }
    private fun shuffleRemaining() {
        if (!directoryExtended) return
        candidates = candidates.take(nextCandidate) + candidates.drop(nextCandidate).shuffled()
        directoryExtended = false
    }
    private fun extendListeningDirectory(gen: Int) {
        if (directoryJob?.isActive == true || pendingCities.isEmpty()) return
        directoryJob = viewModelScope.launch {
            try {
                while (gen == generation && enabled && pendingCities.isNotEmpty()) loadDirectoryPage(gen)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                // Keep playback intact; Next retries the page that failed.
            }
        }
    }
    fun next() {
        if (advanceBusy) return
        val mode = mutable.value.mode ?: return
        if (!fullAccess && mutable.value.preview.remaining(mode) == 0) {
            pendingAction = { next() }; player.pause(); updateState { it.copy(blocked = true, paywall = if (mode == "listen") "MAP_LISTEN" else "MAP_WATCH") }; return
        }
        advanceBusy = true
        val gen = generation
        launch {
            updateState { it.copy(busy = true, error = null) }
            try {
                if (mutable.value.historyIndex < history.lastIndex) {
                    val index = mutable.value.historyIndex + 1; val item = history[index]
                    updateState { it.copy(playing = item, historyIndex = index) }; play(item, mode); return@launch
                }
                if (mode == "watch" && round == 0) {
                    while (gen == generation && enabled && pendingCities.isNotEmpty()) loadDirectoryPage(gen)
                    if (gen != generation || !enabled) return@launch
                }
                shuffleRemaining()
                while (gen == generation && enabled) {
                    if (nextCandidate >= candidates.size) {
                        val city = pendingCities.firstOrNull()
                        if (city == null) {
                            if (!roundFound || pool.isEmpty()) break
                            round++; roundFound = false; nextCandidate = 0
                            val nextRound = pool.values.shuffled().toMutableList()
                            if (nextRound.size > 1 && nextRound.first().second.user.id == mutable.value.playing?.post?.user?.id) java.util.Collections.swap(nextRound, 0, 1)
                            candidates = nextRound
                        } else {
                            loadDirectoryPage(gen)
                            if (gen != generation || !enabled) return@launch
                            directoryExtended = false
                            if (nextCandidate >= candidates.size) continue
                        }
                    }
                    val (city, person) = candidates[nextCandidate]
                    val key = "$gen:$round:$mode:${city.cityId}:${person.user.id}"
                    val cached = prefetchedPost?.takeIf { it.first == key }
                    prefetchedPost = null
                    val post = if (cached != null) cached.second.await().getOrThrow() else playablePost(person.user.id, mode, round)
                    if (gen != generation || !enabled) return@launch
                    if (post == null || post.id in shownPosts) { nextCandidate++; continue }
                    shownPosts.add(post.id); roundFound = true
                    if (gen != generation || !enabled) return@launch
                    nextCandidate++
                    val item = MapPlaybackItem(city.copy(cityName=canonicalCities[city.cityId]?.cityName ?: city.cityName), post.copy(user = person.user)); history.add(item)
                    updateState { it.copy(playing = item, historyIndex = history.lastIndex) }
                    play(item, mode)
                    prefetchNext(mode, gen)
                    if (mode == "listen") extendListeningDirectory(gen)
                    return@launch
                }
                if (gen != generation || !enabled) return@launch
                player.pause(); updateState { it.copy(error = "No more posts to play for this selection.") }
            } finally { if (gen == generation) { advanceBusy = false; updateState { it.copy(busy = false) } } }
        }
    }
    private suspend fun playablePost(personId: String, mode: String, depth: Int): CymbalPost? = postHistoryMutex.withLock {
        val entry = postHistories.getOrPut("$mode:$personId") { PersonHistory() }
        while (entry.posts.size <= depth && !entry.done) {
            val page = repository.posts(personId, mode, entry.cursor)
            val known = entry.posts.map { it.id }.toSet()
            entry.posts.addAll(page.filter {
                it.id !in known && if (mode == "listen") it.isTrack && it.track.source !in listOf(TrackSource.TIDAL, TrackSource.DEEZER)
                else it.isMovie && fm.corus.android.ui.components.youTubeVideoID(it.trailerURL) != null
            })
            val cursor = page.lastOrNull()?.timestamp?.time
            entry.done = page.size < 15 || cursor == null || cursor == entry.cursor
            entry.cursor = cursor
        }
        entry.posts.getOrNull(depth)
    }
    private fun prefetchNext(mode: String, gen: Int) {
        val (city, person) = candidates.getOrNull(nextCandidate) ?: return
        val depth = round
        prefetchedPost = "$gen:$round:$mode:${city.cityId}:${person.user.id}" to viewModelScope.async {
            try { Result.success(playablePost(person.user.id, mode, depth)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
        }
    }
    private suspend fun play(item: MapPlaybackItem, mode: String) {
        endedPost = null
        MapPlaybackOwner.current = null
        if (mode == "watch") { player.pause(); ownedPlayback = MapPlaybackOwner.Owner(item.post.id, { next() }, { started(item.post.id, mode) }, { stop(pausePlayer = false) }, { previous() }); MapPlaybackOwner.current = ownedPlayback; return }
        val track = item.post.toQueuedTrack()
        ownedPlayback = MapPlaybackOwner.Owner(track.trackId, { next() }, { started(item.post.id, mode) }, { stop(pausePlayer = false) }, { previous() })
        MapPlaybackOwner.current = ownedPlayback
        player.play(track = track, queue = listOf(track), mapOwned = true)
    }
    fun previous() = launch {
        if (advanceBusy) return@launch
        val i = mutable.value.historyIndex - 1
        if (i < 0) return@launch
        val stored = history[i]; val item = stored.copy(city = stored.city.copy(cityName = canonicalCities[stored.city.cityId]?.cityName ?: stored.city.cityName)); updateState { it.copy(playing = item, historyIndex = i, blocked = false) }; play(item, mutable.value.mode ?: "listen")
    }
    fun ended(id: String) { if (endedPost == id || mutable.value.playing?.post?.id != id) return; repository.event("playback_completed", mutable.value.mode ?: "listen"); endedPost = id; next() }
    fun started(id: String, mode: String) { if (auth.currentUser?.uid == uid && !fullAccess && mutable.value.playing?.post?.id == id) updateState { it.copy(preview = repository.record(mode, id, it.preview)) } }
    fun stop(pausePlayer: Boolean = true) { directoryJob?.cancel(); directoryJob = null; prefetchedPost?.second?.cancel(); prefetchedPost = null; pendingAction = null; generation++; if (ownedPlayback != null && MapPlaybackOwner.current === ownedPlayback) { MapPlaybackOwner.current = null; if (pausePlayer) player.pause() }; updateState { it.copy(mode = null, playing = null, busy = false) }; advanceBusy = false }
    override fun onCleared() { if (ownedPlayback != null && MapPlaybackOwner.current === ownedPlayback) { MapPlaybackOwner.current = null; player.pause() }; super.onCleared() }
}
