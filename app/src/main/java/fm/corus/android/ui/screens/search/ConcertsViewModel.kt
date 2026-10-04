package fm.corus.android.ui.screens.search

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.ShareRecipient
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.ConcertAttendance
import fm.corus.android.data.repository.ConcertRepository
import fm.corus.android.data.repository.ConcertShow
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.screens.map.MapCity
import fm.corus.android.ui.screens.map.MapRepository
import fm.corus.android.ui.navigation.ArtistPageRoute
import fm.corus.android.ui.screens.feed.loadRecentShareRecipients
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
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
    private val messages: MessageRepository,
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
    private var detailJob: Job? = null
    private var openedFrom: Pair<String, String>? = null

    private val prefs = app.getSharedPreferences("concerts", 0)
    private val uid get() = auth.currentUser?.uid.orEmpty()
    private val cityKey get() = "selectedCity.$uid"
    private val _cityId = MutableStateFlow(prefs.getString(cityKey, null))
    val cityId = _cityId.asStateFlow()
    // With no picked city, start on the city the server already resolved (the preview's map-city
    // fallback) so See All doesn't open blank. Requests still send no city, so it tracks the map.
    private val _cityName = MutableStateFlow(
        if (_cityId.value == null) concerts.resolvedCity?.second.orEmpty()
        else prefs.getString("selectedCityName.$uid", null) ?: POPULAR_CONCERT_CITIES.firstOrNull { it.first == _cityId.value }?.second.orEmpty()
    )
    val cityName = _cityName.asStateFlow()
    private val _tab = MutableStateFlow(concerts.discoveryFilter.value)
    val tab = _tab.asStateFlow()
    private val _shows = MutableStateFlow<List<ConcertShow>>(emptyList())
    val shows = _shows.asStateFlow()
    private val _plans = MutableStateFlow(concerts.rememberedPlans())
    val plans = _plans.asStateFlow()
    val plansSynced = concerts.plansSynced
    private val _cursor = MutableStateFlow<String?>(null)
    val cursor = _cursor.asStateFlow()
    private val _total = MutableStateFlow(0)
    val total = _total.asStateFlow()
    private val _nearbyTotal = MutableStateFlow(0)
    val nearbyTotal = _nearbyTotal.asStateFlow()
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
    // Starts true: the list loads on open, and a false first frame renders an empty state.
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _pullRefreshing = MutableStateFlow(false)
    val pullRefreshing = _pullRefreshing.asStateFlow()
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
    private val _attendanceError = MutableStateFlow(false)
    val attendanceError = _attendanceError.asStateFlow()
    private val _recentShareContacts = MutableStateFlow<List<ShareRecipient>>(emptyList())
    val recentShareContacts = _recentShareContacts.asStateFlow()
    private val _shareSearchResults = MutableStateFlow<List<ShareRecipient>>(emptyList())
    val shareSearchResults = _shareSearchResults.asStateFlow()
    private val _isShareSearching = MutableStateFlow(false)
    val isShareSearching = _isShareSearching.asStateFlow()
    private val _isLoadingShareContacts = MutableStateFlow(true)
    val isLoadingShareContacts = _isLoadingShareContacts.asStateFlow()
    private var shareSearchJob: Job? = null
    private var loadJob: Job? = null
    private var selectedTabExplicitly = false
    private val _cityResults = MutableStateFlow<List<MapCity>>(emptyList())
    val cityResults = _cityResults.asStateFlow()
    private val _citySearching = MutableStateFlow(false)
    val citySearching = _citySearching.asStateFlow()

    suspend fun searchCities(query: String) {
        if (query.trim().length < 2) { _cityResults.value = emptyList(); _citySearching.value = false; return }
        mapRepository.cachedCitySearch(query, concerts = true)?.let { cities ->
            _cityResults.value = cities
            _citySearching.value = false
            log("city_search_completed", cityId = _cityId.value, result = "cache", count = cities.size, durationMs = 0)
            return
        }
        _citySearching.value = true
        val started = System.currentTimeMillis()
        try {
            delay(250)
            val cities = mapRepository.search(query.trim(), concerts = true)
            _cityResults.value = cities
            log("city_search_completed", cityId = _cityId.value, result = if (cities.isEmpty()) "no_results" else "network",
                count = cities.size, durationMs = System.currentTimeMillis() - started)
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

    /** Same `concert_event` schema as iOS (`ConcertAnalytics.log`); see service/ConcertAnalytics.kt. */
    fun log(
        action: String,
        show: ConcertShow? = null,
        source: String = "concerts_list",
        cityId: String? = _cityId.value,
        filter: String? = null,
        result: String? = null,
        provider: String? = null,
        method: String? = null,
        statusFrom: String? = null,
        statusTo: String? = null,
        dateRange: String? = null,
        genre: String? = null,
        count: Int? = null,
        durationMs: Long? = null,
    ) = analytics.logConcertEvent(
        action, source, show, cityId, filter, result, provider, method, statusFrom, statusTo,
        dateRange, genre, count, durationMs,
    )

    /** Filter/date/genre context every list-level event carries on iOS. */
    private fun listLog(action: String, source: String = "concerts_list", filter: String? = _tab.value, result: String? = null, count: Int? = null, durationMs: Long? = null) =
        log(action, source = source, filter = filter, result = result, dateRange = _dateRange.value, genre = _genre.value ?: "any", count = count, durationMs = durationMs)

    /** Result buckets shared with iOS (`previewResult` / `listResult`). */
    private fun previewResult(page: ConcertPage, filter: String): String = when {
        page.needsCity -> "needs_city"
        filter == "forYou" && !page.hasPostedArtists -> "no_posts"
        page.nearbyTotal == 0 -> "no_nearby"
        page.shows.isEmpty() -> "no_artist_matches"
        else -> if (filter == "all") "nearby_results" else "personalized_results"
    }

    fun logPreviewSeeAll() {
        val page = _previewPage.value
        log("preview_see_all_tapped", source = "search_music_preview", cityId = page?.cityId ?: _cityId.value,
            filter = discoveryFilter.value, result = when {
                _previewError.value && page == null -> "load_error"
                page != null -> previewResult(page, discoveryFilter.value)
                else -> "loading"
            }, count = page?.shows?.size)
    }

    fun logPreviewEmptyAction(result: String, filter: String? = null) =
        log("empty_action_tapped", source = "search_music_preview", filter = filter, result = result)

    fun logListEmptyAction(result: String) =
        log("empty_action_tapped", source = if (_tab.value == "myConcerts") "my_concerts" else "concerts_list",
            filter = _tab.value, result = result, dateRange = null)

    /** iOS `list_impression`: one event per distinct list state, not per recomposition. */
    private var lastListImpression = ""
    fun logListImpression() {
        val tab = _tab.value
        if (_loading.value && !(tab == "myConcerts" && concerts.rememberedPlans().isNotEmpty())) return
        val result = when {
            tab == "myConcerts" -> if (_error.value) "load_error" else if (_plans.value.isEmpty()) "empty" else "results"
            _needsCity.value -> "needs_city"
            _error.value -> "load_error"
            _shows.value.isEmpty() -> "empty"
            else -> "results"
        }
        val count = if (tab == "myConcerts") _plans.value.size else _shows.value.size
        val signature = if (tab == "myConcerts") "myConcerts|$result|$count"
        else "${_cityId.value}|$tab|${_dateRange.value}|${_genre.value}|${_suggestions.value}|$result"
        if (signature == lastListImpression) return
        lastListImpression = signature
        listLog("list_impression", result = result, count = count)
    }

    fun logCityPickerOpened() = log("city_picker_opened", filter = _tab.value)
    fun logFiltersOpened() = listLog("filters_opened")
    fun logLocationRequested() = log("location_requested")
    fun logLocationResolved(result: String, cityId: String? = null) = log("location_resolved", cityId = cityId, result = result)

    suspend fun preview(force: Boolean = false) {
        if (!enabled) return
        val firstResultsPending = !concerts.hasHadForYouResults()
        val filter = if (firstResultsPending) "forYou" else discoveryFilter.value
        val key = "$uid|${_cityId.value}|$filter|$firstResultsPending"
        if (!force && key == previewKey && _previewPage.value != null && System.currentTimeMillis() - previewAt < 60_000) return
        if (key != previewKey) _previewPage.value = null
        previewKey = key
        _previewLoading.value = true; _previewError.value = false
        try {
            var page = concerts.page(_cityId.value, filter, suggestions = true, preview = true)
            if (key != previewKey) return
            if (firstResultsPending && !page.needsCity) {
                if (page.shows.isNotEmpty()) {
                    concerts.markForYouResults()
                    concerts.selectDiscoveryFilter("forYou")
                } else if (page.nearbyTotal > 0) {
                    page = concerts.page(_cityId.value, "all", suggestions = false, preview = true)
                    if (key != previewKey) return
                    concerts.selectDiscoveryFilter("all")
                }
            }
            _previewPage.value = page; previewAt = System.currentTimeMillis()
            log("preview_impression", source = "search_music_preview", cityId = page.cityId ?: _cityId.value,
                filter = discoveryFilter.value, result = previewResult(page, discoveryFilter.value), count = page.shows.size)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (key == previewKey) {
                _previewError.value = true
                log("preview_impression", source = "search_music_preview", filter = discoveryFilter.value, result = "load_error")
            }
        }
        finally { if (key == previewKey) _previewLoading.value = false }
    }

    fun rememberDiscoveryTab(value: String) { concerts.selectDiscoveryFilter(value) }

    fun selectTab(value: String) { selectedTabExplicitly = true; concerts.selectDiscoveryFilter(value); _tab.value = value; listLog("tab_changed", filter = value); refresh() }
    fun selectCity(id: String, name: String) {
        prefs.edit().putString(cityKey, id).putString("selectedCityName.$uid", name).apply(); _cityId.value = id; _cityName.value = name
        _needsCity.value = false; refresh()
    }
    /** [result] is "search" for remote results and "popular" for the built-in list, as on iOS. */
    fun selectCity(id: String, name: String, result: String) {
        log("city_selected", cityId = id, result = result)
        selectCity(id, name)
    }
    fun selectCurrentLocation(location: Location, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val city = runCatching { mapRepository.resolve(location) }.getOrNull()
            if (city != null) {
                logLocationResolved("success", city.cityId)
                selectCity(city.cityId, city.cityName)
            } else logLocationResolved("no_city")
            done(city != null)
        }
    }
    fun setFilters(range: String, genre: String?, suggestions: Boolean) {
        _dateRange.value = range; _genre.value = genre; _suggestions.value = suggestions
        log("filters_applied", filter = _tab.value, result = if (suggestions) "recommendations_shown" else "recommendations_hidden",
            dateRange = range, genre = genre ?: "any")
        refresh()
    }
    fun pullRefresh() {
        if (!enabled || _pullRefreshing.value) return
        _pullRefreshing.value = true
        if (_tab.value == "myConcerts") log("pull_to_refresh", source = "my_concerts", filter = _tab.value)
        else listLog("pull_to_refresh")
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
                    log("my_concerts_loaded", source = "concerts_list", result = "success", count = _total.value,
                        durationMs = System.currentTimeMillis() - started)
                } else {
                    val probingFirstResults = next == null && !selectedTabExplicitly &&
                        requestedTab != "myConcerts" && !concerts.hasHadForYouResults() &&
                        requestedRange == "any" && requestedGenre == null && requestedSuggestions
                    var loadedTab = if (probingFirstResults) "forYou" else requestedTab
                    var result = concerts.page(requestedCity, loadedTab, next, requestedRange, requestedGenre, requestedSuggestions)
                    if (probingFirstResults && !result.needsCity) {
                        if (result.shows.isNotEmpty()) {
                            concerts.markForYouResults()
                            concerts.selectDiscoveryFilter("forYou")
                            _tab.value = "forYou"
                        } else if (result.nearbyTotal > 0) {
                            loadedTab = "all"
                            result = concerts.page(requestedCity, loadedTab, null, requestedRange, requestedGenre, requestedSuggestions)
                            concerts.selectDiscoveryFilter("all")
                            _tab.value = "all"
                        }
                    }
                    _shows.value = if (next == null) result.shows else (_shows.value + result.shows).distinctBy { it.id }
                    _cursor.value = result.nextCursor; _total.value = result.total
                    _nearbyTotal.value = result.nearbyTotal
                    _needsCity.value = result.needsCity; _genres.value = result.availableGenres
                    _hasPostedArtists.value = result.hasPostedArtists
                    if (!result.cityName.isNullOrEmpty()) _cityName.value = result.cityName
                    log(if (next == null) "page_loaded" else "page_appended", cityId = result.cityId ?: requestedCity,
                        filter = loadedTab, result = "network_fresh", dateRange = requestedRange, genre = requestedGenre ?: "any",
                        count = result.shows.size, durationMs = System.currentTimeMillis() - started)
                    val suggested = result.shows.count { it.suggestionSource == "tasteMatches" }
                    if (suggested > 0) log("suggestions_shown", cityId = result.cityId ?: requestedCity, filter = loadedTab, count = suggested)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _error.value = true
                if (requestedTab == "myConcerts") log("my_concerts_loaded", source = "concerts_list", result = "error", durationMs = System.currentTimeMillis() - started)
                else log("page_load_failed", cityId = requestedCity, filter = requestedTab, result = "error",
                    dateRange = requestedRange, genre = requestedGenre ?: "any", durationMs = System.currentTimeMillis() - started)
            }
            finally {
                if (loadJob === coroutineContext[Job]) {
                    _loading.value = false
                    _pullRefreshing.value = false
                }
            }
        }
    }
    fun open(eventId: String) {
        detailJob?.cancel()
        // Retries re-open the same event; keep the source the first open was given. With none registered
        // (push, link, activity) iOS reports "deep_link".
        val detailSource = concerts.takeDetailSource(eventId)?.also { openedFrom = eventId to it }
            ?: openedFrom?.takeIf { it.first == eventId }?.second ?: "deep_link"
        val openedAt = System.currentTimeMillis()
        _show.value = concerts.cached(eventId)
        val cachedStatus = concerts.rememberedPlans().firstOrNull { it.id == eventId }?.status
            ?: _show.value?.status
        _attendance.value = concerts.rememberedAttendance(eventId)
            ?: cachedStatus?.let { status ->
                ConcertAttendance(status, if (status == "going") 1 else 0, if (status == "interested") 1 else 0, emptyList(), null)
            }
        confirmedAttendance = _attendance.value
        _detailError.value = false
        _detailLoading.value = _show.value == null
        _attendanceError.value = false
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
                // iOS resolves deep links through a loader that reports its own success/failure and latency.
                if (detailSource == "deep_link") {
                    val elapsed = System.currentTimeMillis() - openedAt
                    if (_show.value != null) log("deep_link_loaded", _show.value, "deep_link", result = "success", durationMs = elapsed)
                    else log("deep_link_load_failed", null, "deep_link", cityId = null, result = "error", durationMs = elapsed)
                }
            }
            _detailLoading.value = false
            _show.value?.let { show ->
                log("detail_viewed", show, detailSource)
                if (!show.supportsAttendance) return@let
                val attendanceStarted = System.currentTimeMillis()
                try {
                    val result = concerts.attendance(show)
                    if (planRevision == revision) { confirmedAttendance = result; _attendance.value = result; concerts.rememberAttendance(eventId, result) }
                    log("attendance_loaded", show, "concert_detail", result = "success", count = result.people.size,
                        durationMs = System.currentTimeMillis() - attendanceStarted)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (planRevision == revision) _attendanceError.value = true
                    log("attendance_load_failed", show, "concert_detail", result = "error", durationMs = System.currentTimeMillis() - attendanceStarted)
                }
            }
        }
    }
    /**
     * [source] is where the row lives ("search_music_preview", "concerts_list", "my_concerts"); it is
     * also the source the detail screen reports for its `detail_viewed`.
     */
    fun select(show: ConcertShow, source: String = "concerts_list") {
        concerts.remember(show)
        concerts.noteDetailSource(show.id, source)
        when (source) {
            "search_music_preview" -> log("concert_selected", show, source, result = if (show.suggestionSource == "tasteMatches") "taste_matches" else "own_artist")
            "my_concerts" -> log("concert_selected", show, source, filter = "myConcerts", result = show.status)
            else -> log("concert_selected", show, source, filter = _tab.value,
                result = if (show.suggestionSource == "tasteMatches") "taste_matches" else null,
                dateRange = _dateRange.value, genre = _genre.value ?: "any")
        }
    }
    fun share(show: ConcertShow, onReady: (String?) -> Unit) {
        viewModelScope.launch { onReady(runCatching { concerts.prepare(show) }.getOrNull()) }
    }
    fun loadRecentShareContacts() {
        val userId = auth.currentUser?.uid ?: return
        loadRecentShareRecipients(
            userId = userId,
            messageRepository = messages,
            setContacts = { _recentShareContacts.value = it },
            setLoading = { _isLoadingShareContacts.value = it },
            scope = viewModelScope,
        )
    }
    fun searchShareUsers(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            shareSearchJob?.cancel()
            _shareSearchResults.value = emptyList()
            _isShareSearching.value = false
            return
        }
        shareSearchJob?.cancel()
        _shareSearchResults.value = emptyList()
        shareSearchJob = viewModelScope.launch {
            _isShareSearching.value = true
            delay(250)
            try {
                auth.currentUser?.uid?.let { userId ->
                    _shareSearchResults.value = messages.searchShareRecipients(userId, trimmed, users)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _shareSearchResults.value = emptyList()
            }
            _isShareSearching.value = false
        }
    }
    fun updatePlan(choice: String) {
        val show = _show.value ?: return
        // The buttons are available before getConcertAttendance returns.
        // Start from a neutral snapshot so the first tap can update immediately.
        val old = _attendance.value ?: ConcertAttendance(null, 0, 0, emptyList(), null)
        val next = if (old.status == choice) null else choice
        if ((ConcertCalendarPolicy.hasStarted(show.date, show.time, show.timezone) || show.eventStatus in listOf("canceled", "postponed")) && next != null) return
        _attendanceError.value = false
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
        val statusFrom = old.status ?: "none"
        val statusTo = next ?: "none"
        log("attendance_change_attempted", show, "concert_detail", statusFrom = statusFrom, statusTo = statusTo)
        concerts.sendInterest(show, next ?: "none") { result ->
            result.onSuccess {
                log("attendance_changed", show, "concert_detail", result = "success", statusFrom = statusFrom, statusTo = statusTo)
                confirmedAttendance = optimisticAttendance
                if (revision == planRevision) {
                    // The first tap may precede the initial attendance response.
                    // Refresh counts after saving without replacing a newer tap.
                    viewModelScope.launch {
                        runCatching { concerts.attendance(show) }.onSuccess { saved ->
                            if (revision == planRevision && _show.value?.id == show.id) {
                                confirmedAttendance = saved
                                _attendance.value = saved
                                concerts.rememberAttendance(show.id, saved)
                            }
                        }
                    }
                }
            }.onFailure {
                log("attendance_changed", show, "concert_detail", result = "error", statusFrom = statusFrom, statusTo = statusTo)
                if (revision == planRevision) {
                    val restored = confirmedAttendance ?: old
                    _attendance.value = restored; concerts.rememberAttendance(show.id, restored)
                    concerts.clearPending(show.id); concerts.rememberPlan(show, restored.status)
                    _plans.value = concerts.rememberedPlans(); _error.value = true
                }
            }
        }
    }
    fun loadMorePeople() {
        val show = _show.value ?: return
        val old = _attendance.value ?: return
        val cursor = old.nextCursor ?: return
        val started = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching { concerts.attendance(show, cursor, 20) }.onSuccess { next ->
                _attendance.value = old.copy(people = (old.people + next.people).distinctBy { it.id }, nextCursor = next.nextCursor)
                log("attendees_page_loaded", show, "concert_attendees", result = "success", count = next.people.size,
                    durationMs = System.currentTimeMillis() - started)
            }.onFailure {
                log("attendees_page_load_failed", show, "concert_attendees", result = "error", durationMs = System.currentTimeMillis() - started)
            }
        }
    }
    fun sendInvite(userId: String, note: String, onError: (Throwable) -> Unit) {
        val show = _show.value ?: return
        concerts.sendInviteInBackground(show, userId, note) { result ->
            result
                .onSuccess { log("share_completed", show, "share_sheet", result = "success", method = "direct_message") }
                .onFailure { log("share_completed", show, "share_sheet", result = "error", method = "direct_message"); onError(it) }
        }
    }
    /** [tapSource] is where the artist link lives on the detail page: hero, menu, lineup or supporting_artist. */
    fun resolveArtist(name: String, tapSource: String, onResolved: (ArtistPageRoute?) -> Unit) {
        viewModelScope.launch {
            val trimmed = name.trim()
            val artist = runCatching { cloud.resolveArtistByName(trimmed) }.getOrNull()
            // Concert lineups only contain names. The artist destination accepts
            // nm: IDs and builds a page by name when catalog search misses.
            val route = artist?.let { ArtistPageRoute(it.id, it.name, it.imageUrl) }
                ?: trimmed.takeIf { it.isNotEmpty() }?.let { ArtistPageRoute("nm:$it", it) }
            if (route != null) log("artist_tapped", _show.value, "concert_detail_$tapSource")
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
