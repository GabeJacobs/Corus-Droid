package fm.corus.android.ui.screens.search

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.ConcertAttendance
import fm.corus.android.data.repository.ConcertRepository
import fm.corus.android.data.repository.ConcertShow
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.screens.map.MapCity
import fm.corus.android.ui.screens.map.MapRepository
import fm.corus.android.ui.navigation.ArtistPageRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import fm.corus.android.data.repository.ConcertPage
import fm.corus.android.data.repository.ConcertPerson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class ConcertsViewModel @Inject constructor(
    app: Application,
    private val concerts: ConcertRepository,
    private val users: UserRepository,
    private val cloud: CloudFunctionsDataSource,
    private val analytics: AnalyticsService,
    private val auth: FirebaseAuth,
    private val remoteConfig: RemoteConfigService,
    private val mapRepository: MapRepository,
) : AndroidViewModel(app) {
    val enabled get() = remoteConfig.concertsEnabled
    val calendarEnabled get() = remoteConfig.concertCalendarEnabled
    val discoveryFilter = concerts.discoveryFilter
    private val _previewPage = MutableStateFlow<ConcertPage?>(null)
    val previewPage = _previewPage.asStateFlow()
    private val _previewLoading = MutableStateFlow(false)
    val previewLoading = _previewLoading.asStateFlow()
    private val _previewError = MutableStateFlow(false)
    val previewError = _previewError.asStateFlow()
    private var previewKey: String? = null
    private var previewAt = 0L
    private val _hasPostedArtists = MutableStateFlow(true)
    val hasPostedArtists = _hasPostedArtists.asStateFlow()
    private var currentPerson: ConcertPerson? = null
    private var confirmedAttendance: ConcertAttendance? = null
    private var planRevision = 0
    private val planMutex = Mutex()
    private var detailJob: Job? = null

    private val prefs = app.getSharedPreferences("concerts", 0)
    private val uid get() = auth.currentUser?.uid.orEmpty()
    private val cityKey get() = "selectedCity.$uid"
    private val _cityId = MutableStateFlow(prefs.getString(cityKey, null))
    val cityId = _cityId.asStateFlow()
    private val _cityName = MutableStateFlow(prefs.getString("selectedCityName.$uid", null) ?: POPULAR_CONCERT_CITIES.firstOrNull { it.first == _cityId.value }?.second.orEmpty())
    val cityName = _cityName.asStateFlow()
    private val _tab = MutableStateFlow(concerts.discoveryFilter.value)
    val tab = _tab.asStateFlow()
    private val _shows = MutableStateFlow<List<ConcertShow>>(emptyList())
    val shows = _shows.asStateFlow()
    private val _plans = MutableStateFlow(concerts.rememberedPlans())
    val plans = _plans.asStateFlow()
    private val _cursor = MutableStateFlow<String?>(null)
    val cursor = _cursor.asStateFlow()
    private val _total = MutableStateFlow(0)
    val total = _total.asStateFlow()
    private val _needsCity = MutableStateFlow(false)
    val needsCity = _needsCity.asStateFlow()
    private val _genres = MutableStateFlow<List<String>>(emptyList())
    val genres = _genres.asStateFlow()
    private val _dateRange = MutableStateFlow("any")
    val dateRange = _dateRange.asStateFlow()
    private val _genre = MutableStateFlow<String?>(null)
    val genre = _genre.asStateFlow()
    private val _suggestions = MutableStateFlow(true)
    val suggestions = _suggestions.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow(false)
    val error = _error.asStateFlow()
    private val _show = MutableStateFlow<ConcertShow?>(null)
    val show = _show.asStateFlow()
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading = _detailLoading.asStateFlow()
    private val _detailError = MutableStateFlow(false)
    val detailError = _detailError.asStateFlow()
    private val _attendance = MutableStateFlow<ConcertAttendance?>(null)
    val attendance = _attendance.asStateFlow()
    private val _friends = MutableStateFlow<List<CymbalUser>>(emptyList())
    val friends = _friends.asStateFlow()
    private var loadJob: Job? = null
    private val _cityResults = MutableStateFlow<List<MapCity>>(emptyList())
    val cityResults = _cityResults.asStateFlow()
    private val _citySearching = MutableStateFlow(false)
    val citySearching = _citySearching.asStateFlow()

    suspend fun searchCities(query: String) {
        if (query.trim().length < 2) { _cityResults.value = emptyList(); _citySearching.value = false; return }
        _citySearching.value = true
        val started = System.currentTimeMillis()
        try {
            delay(250)
            val cities = mapRepository.search(query.trim())
            _cityResults.value = cities
            log("city_search_completed", source = "city_picker", extra = mapOf("count" to cities.size, "duration_ms" to (System.currentTimeMillis() - started)))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _cityResults.value = emptyList() }
        finally { _citySearching.value = false }
    }

    private val cityPreferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == cityKey || key == "selectedCityName.$uid") {
            _cityId.value = prefs.getString(cityKey, null)
            _cityName.value = prefs.getString("selectedCityName.$uid", "").orEmpty()
        }
    }
    init {
        prefs.registerOnSharedPreferenceChangeListener(cityPreferenceListener)
        viewModelScope.launch { concerts.planUpdates.collect { _plans.value = it } }
    }
    override fun onCleared() { prefs.unregisterOnSharedPreferenceChangeListener(cityPreferenceListener); super.onCleared() }

    fun log(action: String, show: ConcertShow? = null, source: String = "concerts_list", extra: Map<String, Any> = emptyMap()) {
        analytics.logEvent("concert_event", buildMap {
            put("action", action); put("source", source)
            show?.let { put("event_id", it.id); put("city_id", it.cityId) }
            putAll(extra)
        })
    }

    suspend fun preview(force: Boolean = false) {
        if (!enabled) return
        val key = "$uid|${_cityId.value}|${discoveryFilter.value}"
        if (!force && key == previewKey && _previewPage.value != null && System.currentTimeMillis() - previewAt < 60_000) return
        if (key != previewKey) _previewPage.value = null
        previewKey = key
        _previewLoading.value = true; _previewError.value = false
        try {
            val page = concerts.page(_cityId.value, discoveryFilter.value, suggestions = true, preview = true)
            if (key != previewKey) return
            _previewPage.value = page; previewAt = System.currentTimeMillis()
            log("preview_impression", source = "search_music_preview", extra = mapOf("filter" to discoveryFilter.value, "count" to page.shows.size))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (key == previewKey) _previewError.value = true }
        finally { if (key == previewKey) _previewLoading.value = false }
    }

    fun rememberDiscoveryTab(value: String) { concerts.selectDiscoveryFilter(value) }

    fun selectTab(value: String) { concerts.selectDiscoveryFilter(value); _tab.value = value; log("tab_changed", extra = mapOf("filter" to value)); refresh() }
    fun selectCity(id: String, name: String) {
        prefs.edit().putString(cityKey, id).putString("selectedCityName.$uid", name).apply(); _cityId.value = id; _cityName.value = name
        _needsCity.value = false; log("city_selected", extra = mapOf("city_id" to id)); refresh()
    }
    fun selectCurrentLocation(location: Location, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val city = runCatching { mapRepository.resolve(location) }.getOrNull()
            if (city != null) selectCity(city.cityId, city.cityName)
            done(city != null)
        }
    }
    fun setFilters(range: String, genre: String?, suggestions: Boolean) {
        _dateRange.value = range; _genre.value = genre; _suggestions.value = suggestions
        log("filters_applied", extra = mapOf("date_range" to range, "genre" to (genre ?: "any"), "recommendations" to suggestions))
        refresh()
    }
    fun refresh(next: String? = null) {
        if (!enabled) return
        if (next != null && _loading.value) return
        loadJob?.cancel()
        val requestedTab = _tab.value
        val requestedCity = _cityId.value
        val requestedRange = _dateRange.value
        val requestedGenre = _genre.value
        val requestedSuggestions = _suggestions.value
        loadJob = viewModelScope.launch {
            _loading.value = true; _error.value = false
            val started = System.currentTimeMillis()
            try {
                if (requestedTab == "myConcerts") {
                    _plans.value = concerts.myConcerts(); _total.value = _plans.value.size
                    log("my_concerts_loaded", source = "my_concerts", extra = mapOf("count" to _total.value))
                } else {
                    val result = concerts.page(requestedCity, requestedTab, next, requestedRange, requestedGenre, requestedSuggestions)
                    _shows.value = if (next == null) result.shows else (_shows.value + result.shows).distinctBy { it.id }
                    _cursor.value = result.nextCursor; _total.value = result.total
                    _needsCity.value = result.needsCity; _genres.value = result.availableGenres
                    _hasPostedArtists.value = result.hasPostedArtists
                    if (!result.cityName.isNullOrEmpty()) _cityName.value = result.cityName
                    log(if (next == null) "page_loaded" else "page_appended", extra = mapOf(
                        "filter" to requestedTab, "count" to result.shows.size,
                        "duration_ms" to (System.currentTimeMillis() - started)))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _error.value = true; log("page_load_failed") }
            finally { if (loadJob === coroutineContext[Job]) _loading.value = false }
        }
    }
    fun open(eventId: String) {
        detailJob?.cancel()
        _show.value = concerts.cached(eventId)
        _attendance.value = concerts.rememberedAttendance(eventId)
        confirmedAttendance = _attendance.value
        _detailError.value = false
        _detailLoading.value = _show.value == null
        val revision = planRevision
        detailJob = viewModelScope.launch {
            launch {
                runCatching { users.fetchUserProfile(uid) }.getOrNull()?.let { user ->
                    currentPerson = ConcertPerson(user.id, user.displayName, user.username, user.avatarThumbURL ?: user.avatarURL, "")
                    val current = _attendance.value
                    if (current?.status != null) {
                        _attendance.value = current.copy(people = listOf(currentPerson!!.copy(status = current.status)) + current.people.filterNot { it.id == uid })
                        concerts.rememberAttendance(eventId, _attendance.value!!)
                    }
                }
            }
            if (_show.value == null) {
                _show.value = runCatching { concerts.concert(eventId) }.getOrNull()
                    ?: runCatching { concerts.myConcerts().firstOrNull { it.id == eventId } }.getOrNull()
                _detailError.value = _show.value == null
            }
            _detailLoading.value = false
            _show.value?.let { show ->
                log("detail_viewed", show, "concert_detail")
                if (_attendance.value == null) {
                    val status = concerts.rememberedPlans().firstOrNull { it.id == eventId }?.status
                    _attendance.value = ConcertAttendance(status, if (status == "going") 1 else 0, if (status == "interested") 1 else 0, emptyList(), null)
                }
                try {
                    val result = concerts.attendance(show)
                    if (planRevision == revision) { confirmedAttendance = result; _attendance.value = result; concerts.rememberAttendance(eventId, result) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Keep cached/optimistic state usable. */ }
            }
        }
    }
    fun select(show: ConcertShow) { concerts.remember(show); log("concert_selected", show) }
    fun share(show: ConcertShow, onReady: (String?) -> Unit) {
        viewModelScope.launch { onReady(runCatching { concerts.prepare(show) }.getOrNull()) }
    }
    fun updatePlan(choice: String) {
        val show = _show.value ?: return
        val old = _attendance.value ?: return
        val next = if (old.status == choice) null else choice
        if ((ConcertCalendarPolicy.hasStarted(show.date, show.time, show.timezone) || show.eventStatus in listOf("canceled", "postponed")) && next != null) return
        val revision = ++planRevision
        val people = old.people.filterNot { it.id == uid }.toMutableList()
        if (next != null) currentPerson?.let { people.add(0, it.copy(status = next)) }
        _attendance.value = old.copy(
            people = people,
            status = next,
            goingCount = (old.goingCount + (if (next == "going") 1 else 0) - (if (old.status == "going") 1 else 0)).coerceAtLeast(0),
            interestedCount = (old.interestedCount + (if (next == "interested") 1 else 0) - (if (old.status == "interested") 1 else 0)).coerceAtLeast(0),
        )
        val optimisticAttendance = _attendance.value!!
        concerts.rememberAttendance(show.id, optimisticAttendance)
        concerts.rememberPlan(show, next, optimistic = true); _plans.value = concerts.rememberedPlans()
        log("rsvp_tapped", show, "concert_detail", mapOf("status_from" to (old.status ?: "none"), "status_to" to (next ?: "none")))
        viewModelScope.launch {
            planMutex.withLock {
                // Serialize writes so a slow earlier tap cannot overwrite a later choice.
                if (revision != planRevision) return@withLock
                runCatching { concerts.setInterest(show, next ?: "none") }
                    .onSuccess { confirmedAttendance = optimisticAttendance }
                    .onFailure {
                    if (revision == planRevision) {
                        val restored = confirmedAttendance ?: old
                        _attendance.value = restored; concerts.rememberAttendance(show.id, restored)
                        concerts.clearPending(show.id); concerts.rememberPlan(show, restored.status)
                        _plans.value = concerts.rememberedPlans(); _error.value = true
                    }
                }
            }
        }
    }
    fun loadMorePeople() {
        val show = _show.value ?: return
        val old = _attendance.value ?: return
        val cursor = old.nextCursor ?: return
        viewModelScope.launch { runCatching { concerts.attendance(show, cursor, 20) }.onSuccess { next ->
            _attendance.value = old.copy(people = (old.people + next.people).distinctBy { it.id }, nextCursor = next.nextCursor)
        } }
    }
    fun searchFriends(query: String) {
        viewModelScope.launch {
            _friends.value = if (query.length < 2) emptyList() else runCatching {
                users.searchUsers(query, limit = 15, includeFollowed = true).filter { it.id != uid }
            }.getOrDefault(emptyList())
        }
    }
    fun sendInvite(friend: CymbalUser, note: String, onSent: (String) -> Unit, onError: () -> Unit) {
        val show = _show.value ?: return
        viewModelScope.launch {
            runCatching { concerts.invite(show, friend.id, note) }
                .onSuccess { log("invite_sent", show, "concert_detail"); onSent(it) }
                .onFailure { log("invite_failed", show, "concert_detail"); onError() }
        }
    }
    fun resolveArtist(name: String, onResolved: (ArtistPageRoute?) -> Unit) {
        viewModelScope.launch {
            val artist = runCatching { cloud.resolveArtistByName(name) }.getOrNull()
            val route = artist?.let { ArtistPageRoute(it.id, it.name, it.imageUrl) }
            if (route != null) log("artist_tapped", _show.value, "concert_detail")
            onResolved(route)
        }
    }
}

val POPULAR_CONCERT_CITIES = listOf(
    "new-york-us" to "New York", "geonames-5368361" to "Los Angeles",
    "geonames-4887398" to "Chicago", "geonames-5391959" to "San Francisco",
    "geonames-4164138" to "Miami", "geonames-4671654" to "Austin",
    "geonames-4180439" to "Atlanta", "geonames-4930956" to "Boston",
    "geonames-5809844" to "Seattle", "geonames-6167865" to "Toronto",
    "geonames-3530597" to "Mexico City", "geonames-2643743" to "London",
    "geonames-2988507" to "Paris", "geonames-2950159" to "Berlin",
    "geonames-2147714" to "Sydney", "geonames-1850147" to "Tokyo",
)
