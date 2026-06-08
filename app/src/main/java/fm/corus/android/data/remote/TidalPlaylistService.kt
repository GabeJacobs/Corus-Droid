package fm.corus.android.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates a playlist on the signed-in user's **own** TIDAL account from a list
 * of TIDAL track ids. The Android counterpart to the iOS `TidalPlaylistService`.
 *
 * Auth is handled by `TidalAuthService` (the OAuth/PKCE session); this service
 * only makes the TIDAL Web API calls, with the access token passed in.
 *
 * ⚠️ VERIFY ON FIRST DEVICE TEST: the exact TIDAL v2 (JSON:API) request shapes
 * below — endpoint paths, the `countryCode` query param, the `accessType`
 * attribute, and the `relationships/items` body — are a best reading of the
 * `openapi.tidal.com/v2` API and could not be exercised before shipping (same
 * caveat as the iOS implementation). If TIDAL returns 4xx, the body/path tweaks
 * live entirely in this file.
 */
@Singleton
class TidalPlaylistService @Inject constructor(
    private val httpClient: HttpClient,
) {
    data class CreationResult(
        val url: String?,
        val addedCount: Int,
        val requestedCount: Int,
    )

    sealed class PlaylistException(message: String) : Exception(message) {
        /** 401/403 — token lacks `playlists.write` (logged in before the scope
         *  was added). The caller should re-authenticate and retry once. */
        object InsufficientScope : PlaylistException("TIDAL needs permission to manage your playlists.")
        object NoTracks : PlaylistException("None of these songs are available on TIDAL.")
        class RequestFailed(message: String) : PlaylistException(message)
    }

    suspend fun createPlaylist(
        name: String,
        description: String,
        tidalTrackIds: List<String>,
        token: String,
    ): CreationResult {
        if (tidalTrackIds.isEmpty()) throw PlaylistException.NoTracks

        val country = runCatching { currentUserCountry(token) }.getOrNull() ?: "US"
        val playlistId = createEmptyPlaylist(name, description, country, token)

        var added = 0
        tidalTrackIds.chunked(ADD_BATCH_SIZE).forEach { batch ->
            added += addItems(playlistId, batch, country, token)
        }

        // tidal.com/playlist/{id} is picked up by the TIDAL app via universal
        // links when installed, else opens the web player.
        return CreationResult(
            url = "https://tidal.com/playlist/$playlistId",
            addedCount = added,
            requestedCount = tidalTrackIds.size,
        )
    }

    // ── API calls (JSON:API) ──────────────────────────────────────────────

    /** GET /v2/users/me → the user's countryCode (required on v2 catalog/library
     *  calls). Returns null so the caller can default. */
    private suspend fun currentUserCountry(token: String): String? {
        val resp = httpClient.get("$API_BASE/users/me") { applyHeaders(token) }
        val attrs = parseBody(resp)["data"]?.jsonObject?.get("attributes")?.jsonObject ?: return null
        // The field has been seen as both `country` and `countryCode` — accept either.
        return (attrs["countryCode"] ?: attrs["country"])?.jsonPrimitive?.content
    }

    /** POST /v2/playlists → new playlist id. */
    private suspend fun createEmptyPlaylist(
        name: String,
        description: String,
        country: String,
        token: String,
    ): String {
        val body = buildJsonObject {
            putJsonObject("data") {
                put("type", "playlists")
                putJsonObject("attributes") {
                    put("name", name)
                    put("description", description)
                    put("accessType", "UNLISTED")
                }
            }
        }
        val resp = httpClient.post("$API_BASE/playlists?countryCode=$country") {
            applyHeaders(token)
            setBody(TextContent(body.toString(), JSON_API))
        }
        return parseBody(resp)["data"]?.jsonObject?.get("id")?.jsonPrimitive?.content
            ?: throw PlaylistException.RequestFailed("TIDAL didn't return a playlist id")
    }

    /** POST /v2/playlists/{id}/relationships/items → number added in this batch. */
    private suspend fun addItems(
        playlistId: String,
        trackIds: List<String>,
        country: String,
        token: String,
    ): Int {
        val body = buildJsonObject {
            putJsonArray("data") {
                trackIds.forEach { id ->
                    addJsonObject {
                        put("type", "tracks")
                        put("id", id)
                    }
                }
            }
        }
        val resp = httpClient.post("$API_BASE/playlists/$playlistId/relationships/items?countryCode=$country") {
            applyHeaders(token)
            setBody(TextContent(body.toString(), JSON_API))
        }
        ensureSuccess(resp)
        return trackIds.size
    }

    // ── HTTP ───────────────────────────────────────────────────────────────

    private fun HttpRequestBuilder.applyHeaders(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.Accept, JSON_API.toString())
    }

    /** Throws on non-2xx; maps 401/403 to [PlaylistException.InsufficientScope]. */
    private suspend fun ensureSuccess(resp: HttpResponse) {
        val code = resp.status.value
        if (code == 401 || code == 403) throw PlaylistException.InsufficientScope
        if (code !in 200..299) {
            val detail = runCatching { resp.bodyAsText() }.getOrDefault("").take(200)
            throw PlaylistException.RequestFailed("TIDAL $code: $detail")
        }
    }

    private suspend fun parseBody(resp: HttpResponse): JsonObject {
        ensureSuccess(resp)
        val text = resp.bodyAsText()
        return runCatching { JSON.parseToJsonElement(text).jsonObject }.getOrElse { JsonObject(emptyMap()) }
    }

    companion object {
        private const val API_BASE = "https://openapi.tidal.com/v2"
        private val JSON_API = ContentType("application", "vnd.api+json")
        /** Tracks per add-items request — TIDAL caps relationship batch size. */
        private const val ADD_BATCH_SIZE = 20
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
