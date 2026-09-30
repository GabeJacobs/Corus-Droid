package fm.corus.android.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class ConcertShow(
    val id: String, val cityId: String, val title: String, val date: String,
    val time: String?, val venue: String, val city: String, val region: String,
    val address: String?, val imageUrl: String?, val url: String,
    val lineup: List<String>, val info: String?, val pleaseNote: String?,
    val matchedArtist: String?, val suggestionSource: String?,
    val eventStatus: String, val status: String? = null,
    val timezone: String? = null,
    val supportsAttendance: Boolean = true,
)

data class ConcertPage(
    val shows: List<ConcertShow>, val cityId: String?, val cityName: String?,
    val nextCursor: String?, val total: Int, val needsCity: Boolean,
    val availableGenres: List<String>,
    val hasPostedArtists: Boolean = true, val nearbyTotal: Int = 0,
)

data class ConcertPerson(
    val id: String, val name: String, val username: String,
    val avatarUrl: String?, val status: String,
)

data class ConcertAttendance(
    val status: String?, val goingCount: Int, val interestedCount: Int,
    val people: List<ConcertPerson>, val nextCursor: String?,
)

@Singleton
class ConcertRepository @Inject constructor(private val functions: FirebaseFunctions, private val auth: FirebaseAuth, @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context, private val messageRepository: MessageRepository) {
    private val sendScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("concert_discovery", 0)
    private var owner = auth.currentUser?.uid
    private val _discoveryFilter = MutableStateFlow(savedDiscoveryFilter())
    val discoveryFilter = _discoveryFilter.asStateFlow()
    fun hasHadForYouResults(): Boolean = prefs.getBoolean("hasHadForYouResults.${auth.currentUser?.uid}", false)
    fun markForYouResults() {
        prefs.edit().putBoolean("hasHadForYouResults.${auth.currentUser?.uid}", true).apply()
    }
    private fun savedDiscoveryFilter() = prefs.getString("filter.${auth.currentUser?.uid}", "forYou")
        ?.takeIf { it == "all" || it == "forYou" } ?: "forYou"
    fun selectDiscoveryFilter(filter: String) {
        if (filter != "all" && filter != "forYou") return
        prefs.edit().putString("filter.${auth.currentUser?.uid}", filter).apply()
        _discoveryFilter.value = filter
    }
    private val attendanceCache = java.util.concurrent.ConcurrentHashMap<String, ConcertAttendance>()
    fun rememberedAttendance(id: String) = attendanceCache[id]
    fun rememberAttendance(id: String, attendance: ConcertAttendance) { attendanceCache[id] = attendance }
    private val cachedShows = java.util.concurrent.ConcurrentHashMap<String, ConcertShow>()
    private val cachedPlans = java.util.concurrent.ConcurrentHashMap<String, ConcertShow>()
    /** False until getMyConcerts has succeeded for this account. Before that, cachedPlans holds only
     *  optimistic RSVPs, so the list must not read as complete. */
    private val _plansSynced = kotlinx.coroutines.flow.MutableStateFlow(false)
    val plansSynced = _plansSynced.asStateFlow()
    private val pendingPlans = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val interestWriteMutex = Mutex()
    private val interestWriteVersions = mutableMapOf<String, Long>()
    private var planMutationVersion = 0L
    private var planReadVersion = 0L
    private val _planUpdates = MutableStateFlow<List<ConcertShow>>(emptyList())
    val planUpdates = _planUpdates.asStateFlow()
    /** City the server fell back to (the viewer's map city) when no city was picked. Lets
     *  See All open on it instead of flashing "Choose your city" until its own load returns. */
    @Volatile var resolvedCity: Pair<String, String>? = null
        private set
    init {
        auth.addAuthStateListener {
            if (owner != it.currentUser?.uid) {
                owner = it.currentUser?.uid
                planMutationVersion++
                planReadVersion++
                cachedShows.clear(); cachedPlans.clear(); pendingPlans.clear(); attendanceCache.clear()
                _plansSynced.value = false
                resolvedCity = null
                interestWriteVersions.clear()
                _planUpdates.value = emptyList()
                _discoveryFilter.value = savedDiscoveryFilter()
            }
        }
    }
    fun cached(eventId: String): ConcertShow? = cachedShows[eventId] ?: cachedPlans[eventId]
    fun remember(show: ConcertShow) { cachedShows[show.id] = show }
    private val pendingDetailSource = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Analytics source for the next detail open of [eventId]; the opener registers it, the detail consumes it. */
    fun noteDetailSource(eventId: String, source: String) { pendingDetailSource[eventId] = source }
    fun takeDetailSource(eventId: String): String? = pendingDetailSource.remove(eventId)
    fun rememberedPlans(): List<ConcertShow> = cachedPlans.values.sortedBy { it.date }
    fun rememberPlan(show: ConcertShow, status: String?, optimistic: Boolean = false) {
        if (optimistic) {
            planMutationVersion++
            pendingPlans[show.id] = status ?: "none"
        }
        if (status == null || status == "none") cachedPlans.remove(show.id)
        else cachedPlans[show.id] = show.copy(status = status)
        _planUpdates.value = rememberedPlans()
    }
    private suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *> =
        functions.getHttpsCallable(name).call(data.filterValues { it != null }).await().getData() as? Map<*, *> ?: emptyMap<Any, Any>()

    suspend fun page(
        cityId: String?, filter: String, cursor: String? = null,
        dateRange: String = "any", genre: String? = null, suggestions: Boolean = true,
        preview: Boolean = false,
    ): ConcertPage {
        val result = call("getConcertsPage", mapOf(
            "cityId" to cityId, "filter" to filter, "cursor" to cursor,
            "dateRange" to dateRange, "genre" to genre,
            "tasteSuggestionsEnabled" to suggestions, "preview" to preview,
            "limit" to if (preview) 3 else 20,
        ))
        val city = result["city"] as? Map<*, *>
        val page = ConcertPage(
            shows = (result["shows"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.toConcert()?.let { show -> if (show.cityId.isBlank()) show.copy(cityId = city?.get("cityId") as? String ?: "") else show } } ?: emptyList(),
            cityId = city?.get("cityId") as? String,
            cityName = city?.get("cityName") as? String,
            nextCursor = result["nextCursor"] as? String,
            total = (result["total"] as? Number)?.toInt() ?: 0,
            needsCity = result["needsCity"] as? Boolean ?: false,
            availableGenres = (result["availableGenres"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            hasPostedArtists = result["hasPostedArtists"] as? Boolean ?: true,
            nearbyTotal = (result["nearbyTotal"] as? Number)?.toInt() ?: 0,
        )
        page.shows.forEach(::remember)
        if (cityId.isNullOrBlank()) {
            resolvedCity = if (page.needsCity) null else page.cityId?.takeIf { it.isNotBlank() }?.let { it to page.cityName.orEmpty() }
        }
        return page
    }

    suspend fun myConcerts(): List<ConcertShow> {
        val mutationAtStart = planMutationVersion
        val readVersion = ++planReadVersion
        val plans = (call("getMyConcerts")["plans"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.toConcert() } ?: emptyList()
        // A response begun before a local RSVP change (or superseded by a newer
        // read) must not resurrect a plan that the user just removed.
        if (mutationAtStart != planMutationVersion || readVersion != planReadVersion) return rememberedPlans()
        val returned = plans.associateBy { it.id }
        pendingPlans.entries.removeIf { (id, status) ->
            (returned[id]?.status ?: "none") == status
        }
        val retained = cachedPlans.filterKeys { pendingPlans.containsKey(it) }
        cachedPlans.clear()
        plans.filter { it.status == "going" || it.status == "interested" }
            .forEach { cachedPlans[it.id] = it }
        retained.forEach { (id, show) -> cachedPlans[id] = show }
        pendingPlans.filterValues { it == "none" }.keys.forEach(cachedPlans::remove)
        _planUpdates.value = rememberedPlans()
        _plansSynced.value = true
        return rememberedPlans()
    }
    fun clearPending(eventId: String) { pendingPlans.remove(eventId) }

    suspend fun concert(eventId: String): ConcertShow {
        val show = call("getConcertById", mapOf("eventId" to eventId)).toConcert()
            ?: error("Concert is unavailable")
        remember(show)
        return show
    }

    suspend fun prepare(show: ConcertShow): String =
        call("prepareConcertPublicPage", mapOf("eventId" to show.id, "cityId" to show.cityId))["path"] as? String
            ?: error("Concert sharing is unavailable")

    suspend fun attendance(show: ConcertShow, cursor: String? = null, limit: Int = 4): ConcertAttendance {
        val data = call("getConcertAttendance", mapOf("eventId" to show.id, "cityId" to show.cityId, "cursor" to cursor, "limit" to limit))
        return ConcertAttendance(
            status = data["status"] as? String,
            goingCount = (data["goingCount"] as? Number)?.toInt() ?: 0,
            interestedCount = (data["interestedCount"] as? Number)?.toInt() ?: 0,
            people = (data["people"] as? List<*>)?.mapNotNull { value ->
                val row = value as? Map<*, *> ?: return@mapNotNull null
                val user = row["user"] as? Map<*, *> ?: return@mapNotNull null
                ConcertPerson(user["id"] as? String ?: return@mapNotNull null,
                    user["displayName"] as? String ?: user["username"] as? String ?: "",
                    user["username"] as? String ?: "",
                    user["avatarThumbURL"] as? String ?: user["avatarURL"] as? String,
                    row["status"] as? String ?: "")
            } ?: emptyList(),
            nextCursor = data["nextCursor"] as? String,
        )
    }

    suspend fun setInterest(show: ConcertShow, status: String) {
        call("setConcertInterest", mapOf("eventId" to show.id, "cityId" to show.cityId, "status" to status))
    }

    fun sendInterest(show: ConcertShow, status: String, onResult: (Result<Unit>) -> Unit) {
        val userId = owner
        val version = (interestWriteVersions[show.id] ?: 0L) + 1
        interestWriteVersions[show.id] = version
        sendScope.launch {
            interestWriteMutex.withLock {
                if (owner != userId || interestWriteVersions[show.id] != version) return@withLock
                val result = runCatching { setInterest(show, status) }
                if (owner == userId) onResult(result)
            }
        }
    }

    suspend fun invite(show: ConcertShow, userId: String, message: String): String {
        prepare(show)
        val fromUserId = auth.currentUser?.uid ?: error("Sign in to invite someone")
        val threadId = if (userId.startsWith("group:")) {
            userId.removePrefix("group:")
        } else {
            call("getOrCreateThread", mapOf("userId" to fromUserId, "otherUserId" to userId))["threadId"] as? String
                ?: error("Could not open conversation")
        }
        val fields = mapOf(
            "threadId" to threadId, "fromUserId" to fromUserId, "text" to "", "type" to "sharedConcert", "concertId" to show.id,
            "concertTitle" to show.title, "concertDate" to show.date, "concertTime" to (show.time ?: ""),
            "concertVenue" to show.venue, "concertCity" to show.city, "concertRegion" to show.region,
            "concertArtistName" to (show.lineup.firstOrNull() ?: show.title),
            "concertImageURL" to (show.imageUrl ?: ""),
        )
        call("sendMessage", fields)
        messageRepository.notifyMessageSent(fromUserId)
        if (message.isNotBlank()) {
            call("sendMessage", mapOf("threadId" to threadId, "fromUserId" to fromUserId, "type" to "text", "text" to message.trim()))
            messageRepository.notifyMessageSent(fromUserId)
        }
        return threadId
    }

    fun sendInviteInBackground(show: ConcertShow, userId: String, message: String, onResult: (Result<String>) -> Unit) {
        sendScope.launch { onResult(runCatching { invite(show, userId, message) }) }
    }
}

private fun Map<*, *>.toConcert(): ConcertShow? {
    val id = this["id"] as? String ?: return null
    return ConcertShow(
        id = id, cityId = this["cityId"] as? String ?: "", title = this["title"] as? String ?: "",
        date = this["date"] as? String ?: "", time = this["time"] as? String,
        venue = this["venue"] as? String ?: "", city = this["city"] as? String ?: "",
        region = this["region"] as? String ?: "", address = this["address"] as? String,
        imageUrl = this["imageUrl"] as? String, url = this["url"] as? String ?: "",
        lineup = (this["lineup"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        info = this["info"] as? String, pleaseNote = this["pleaseNote"] as? String,
        matchedArtist = this["matchedArtist"] as? String, suggestionSource = this["suggestionSource"] as? String,
        eventStatus = this["eventStatus"] as? String ?: "scheduled", status = this["status"] as? String,
        timezone = this["timezone"] as? String,
    )
}
