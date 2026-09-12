package fm.corus.android.ui.screens.map

import android.content.Context
import android.location.Location
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapRepository @Inject constructor(@ApplicationContext context: Context, private val auth: FirebaseAuth, private val db: FirebaseFirestore, private val functions: FirebaseFunctions, private val analytics: fm.corus.android.service.AnalyticsService) {
    fun event(action: String, mode: String = "map", value: String? = null, count: Int? = null, durationMs: Long? = null) {
        analytics.logEvent("map_event", buildMap {
            put("action", action); put("mode", mode)
            value?.let { put("value", it) }; count?.let { put("count", it) }
            durationMs?.let { put("duration_ms", it.coerceAtLeast(0)) }
        })
    }
    private val preferences = context.getSharedPreferences("map_preview", Context.MODE_PRIVATE)
    private val latestMutex = Mutex()
    private var latestOwner: String? = null
    private var latestRevision = -1L
    private val latestCache = mutableMapOf<String, Pair<Long, CymbalPost?>>()
    fun savedAudience(): String = currentUserId?.let { preferences.getString("$it.audience", "everyone") } ?: "everyone"
    fun rememberAudience(value: String) { currentUserId?.let { preferences.edit().putString("$it.audience", value).apply() } }
    val currentUserId get() = auth.currentUser?.uid
    fun ownPresence(uid: String) = callbackFlow {
        val listener = db.collection("map_presence").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) { trySend(null); return@addSnapshotListener }
            if (auth.currentUser?.uid == uid) trySend(snapshot?.data)
        }
        awaitClose { listener.remove() }
    }
    fun canonicalUpdates(ids: List<String>) = callbackFlow {
        val uid = currentUserId
        val listeners=ids.distinct().map { id -> db.collection("canonical_cities").document(id).addSnapshotListener { snapshot,error ->
            val data=snapshot?.data
            if(error == null && data != null && snapshot.metadata.isFromCache == false && currentUserId == uid && data["cityId"] == id) trySend(MapCity.decode(data))
        } }
        awaitClose { listeners.forEach { it.remove() } }
    }
    fun previewUpdates(uid: String) = callbackFlow {
        val listener = db.collection("users_v2").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null || auth.currentUser?.uid != uid) return@addSnapshotListener
            val data = snapshot?.get("settings.mapPlaybackPreview") as? Map<*, *>
            fun ids(key: String) = (data?.get(key) as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
            trySend(MapPreviewUsage(ids("listen"), ids("watch")))
        }
        awaitClose { listener.remove() }
    }
    @Suppress("UNCHECKED_CAST")
    suspend fun call(name: String, payload: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val uid = auth.currentUser?.uid ?: error("Please sign in.")
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            val result = functions.getHttpsCallable(name).call(payload).await().getData() as? Map<String, Any?> ?: error("Invalid response")
            check(auth.currentUser?.uid == uid) { "Account changed." }
            if (name.startsWith("getMap")) event("request_finished", "directory", "success", durationMs = android.os.SystemClock.elapsedRealtime()-started)
            if (name in listOf("resolveMapCity", "searchMapCities", "getMapCitySummaries", "getMapCityPeople")) requireMapCommunityVersion(result)
            return result
        } catch (error: Exception) {
            if (name.startsWith("getMap")) event("request_finished", "directory", "error", durationMs = android.os.SystemClock.elapsedRealtime()-started)
            throw error
        }
    }
    @Suppress("UNCHECKED_CAST")
    suspend fun cities(following: List<String>, taste: List<String>, selectedCommunityId: String? = null): List<MapCitySummary> {
        val out = linkedMapOf<String, MapCitySummary>(); var cursor: String? = null; val seen = mutableSetOf<String>()
        do {
            val response = call("getMapCitySummaries", mapOf("followingIds" to following, "tasteIds" to taste, "cursor" to cursor, "selectedCommunityId" to selectedCommunityId))
            (response["cities"] as? List<Map<String, Any?>>).orEmpty().forEach { d ->
                val city = MapCity.decode(d)
                val facets = (d["facets"] as? Map<String, Map<String, Any?>>).orEmpty().mapValues { (_, f) ->
                    MapFacet((f["count"] as? Number)?.toInt() ?: 0, (f["previews"] as? List<Map<String, Any?>>).orEmpty().map { person(it + city.payload()) }, f["includesViewer"] == true)
                }
                out[city.cityId] = MapCitySummary(city, facets, (d["parentCommunity"] as? Map<String, Any?>)?.let(MapCity::decode), (d["subdivisions"] as? List<Map<String, Any?>>).orEmpty().map(MapCity::decode))
            }
            cursor = response["nextCursor"] as? String
            check(cursor == null || seen.add(cursor!!)) { "Couldn’t load more cities. Please try again." }
        } while (cursor != null)
        return out.values.toList()
    }
    @Suppress("UNCHECKED_CAST")
    suspend fun people(city: MapCity, filter: String, following: List<String>, taste: List<String>, cursor: String? = null, selectedCommunityId: String? = null): MapPeoplePage {
        val response = call("getMapCityPeople", mapOf("cityId" to city.cityId, "filter" to filter, "followingIds" to following, "tasteIds" to taste, "cursor" to cursor, "selectedCommunityId" to selectedCommunityId))
        return MapPeoplePage((response["people"] as? List<Map<String, Any?>>).orEmpty().map { person(it + city.payload()) }, response["nextCursor"] as? String)
    }
    @Suppress("UNCHECKED_CAST")
    private fun person(d: Map<String, Any?>): MapPerson {
        val uid = d["uid"] as? String ?: ""
        val canonical = d["user"] as? Map<String, Any?>
        return MapPerson(MapCity.decode(d), CymbalUser.fromMap(uid, canonical ?: d), (d["updatedAt"] as? Number)?.toLong() ?: 0)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun search(query: String) = (call("searchMapCities", mapOf("query" to query, "limit" to 30))["cities"] as? List<Map<String, Any?>>).orEmpty().map(MapCity::decode)
    @Suppress("UNCHECKED_CAST")
    suspend fun resolve(location: Location): MapCity {
        val result = call("resolveMapCity", mapOf("latitude" to location.latitude, "longitude" to location.longitude))
        return MapCity.decode(result["city"] as? Map<String, Any?> ?: error("Couldn’t find your city. Choose another city."))
    }
    suspend fun share(user: CymbalUser, city: MapCity, audience: String, source: String) {
        check(auth.currentUser?.uid == user.id)
        db.collection("map_presence").document(user.id).set(city.payload() + mapOf("uid" to user.id, "username" to user.username, "displayName" to user.displayName, "bio" to user.bio, "avatarURL" to (user.avatarURL ?: ""), "avatarThumbURL" to (user.avatarThumbURL ?: ""), "audience" to audience, "source" to source, "updatedAt" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge()).await()
    }
    suspend fun stopSharing() { val uid = auth.currentUser?.uid ?: return; db.collection("map_presence").document(uid).delete().await() }
    suspend fun chat(cityId: String, currentCityId: String? = null): MapChatStatus { val d = call("getCityChat", mapOf("cityId" to cityId, "currentCityId" to currentCityId)); return MapChatStatus(d["threadId"] as? String ?: "", d["isMember"] == true, d["canJoin"] == true) }
    suspend fun join(city: MapCity, location: Location): String {
        val resolved = resolve(location)
        return call("joinCityChat", mapOf("cityId" to city.cityId, "location" to mapOf("cityId" to resolved.cityId, "latitude" to location.latitude, "longitude" to location.longitude, "accuracy" to location.accuracy, "timestamp" to location.time)))["threadId"] as? String ?: error("Couldn’t join city chat.")
    }
    @Suppress("UNCHECKED_CAST")
    suspend fun posts(userId: String, mode: String, beforeMs: Long? = null): List<CymbalPost> = (call("getProfilePosts", mapOf("userId" to userId, "pageSize" to 15, "beforeMs" to beforeMs, "mediaType" to if (mode == "listen") "track" else "movie"))["posts"] as? List<Map<String, Any?>>).orEmpty().map(CymbalPost::fromCloudData)
    @Suppress("UNCHECKED_CAST")
    suspend fun latest(ids: List<String>): Map<String, CymbalPost?> = latestMutex.withLock {
        val revision = fm.corus.android.domain.MapPostChanges.revisions.value
        if (latestRevision != revision) { latestCache.clear(); latestRevision = revision }
        val viewer = currentUserId ?: error("Please sign in.")
        if (latestOwner != viewer) { latestOwner = viewer; latestCache.clear() }
        val unique = ids.filter(String::isNotBlank).distinct()
        val now = System.currentTimeMillis()
        unique.filter { latestCache[it]?.let { cached -> now - cached.first < 60_000 } != true }.chunked(30).forEach { batch ->
            val results = (call("getMapLatestPosts", mapOf("userIds" to batch))["results"] as? List<Map<String, Any?>>).orEmpty()
            check(currentUserId == viewer && revision == fm.corus.android.domain.MapPostChanges.revisions.value)
            results.forEach { result ->
                val id = result["userId"] as? String ?: return@forEach
                if (result["status"] == "found" || result["status"] == "empty") {
                    val post = result["post"] as? Map<String, Any?>
                    val canonicalUser = result["user"] as? Map<String, Any?>
                    val value = post?.let { data -> CymbalPost.fromCloudData(if (canonicalUser != null) data + ("user" to (canonicalUser + ("id" to id))) else data) }
                    latestCache[id] = System.currentTimeMillis() to value
                }
            }
        }
        unique.mapNotNull { id -> latestCache[id]?.let { id to it.second } }.toMap()
    }
    @Suppress("UNCHECKED_CAST")
    suspend fun preparePreview(): MapPreviewUsage {
        val uid = auth.currentUser?.uid ?: error("Please sign in.")
        val data = db.collection("users_v2").document(uid).get(Source.SERVER).await().get("settings.mapPlaybackPreview") as? Map<*, *>
        fun ids(key: String) = (data?.get(key) as? List<*>)?.filterIsInstance<String>()?.filter(String::isNotBlank).orEmpty().toSet()
        check(auth.currentUser?.uid == uid)
        val usage = MapPreviewUsage(ids("listen") + preferences.getStringSet("$uid.listen", emptySet()).orEmpty(), ids("watch") + preferences.getStringSet("$uid.watch", emptySet()).orEmpty())
        db.collection("users_v2").document(uid).update(mapOf("settings.mapPlaybackPreview.listen" to FieldValue.arrayUnion(*usage.listen.toTypedArray()), "settings.mapPlaybackPreview.watch" to FieldValue.arrayUnion(*usage.watch.toTypedArray()))).await()
        return usage
    }
    fun record(mode: String, postId: String, usage: MapPreviewUsage): MapPreviewUsage {
        val uid = auth.currentUser?.uid ?: return usage
        if (usage.remaining(mode) == 0 || postId in usage.ids(mode)) return usage
        val next = usage.record(mode, postId)
        preferences.edit().putStringSet("$uid.$mode", next.ids(mode)).apply()
        db.collection("users_v2").document(uid).update("settings.mapPlaybackPreview.$mode", FieldValue.arrayUnion(postId))
        return next
    }
}
