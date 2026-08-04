package fm.corus.android.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds a track to the signed-in user's Spotify library ("Liked Songs") via
 * the Web API. The Android counterpart to the iOS "Add Saved Songs to
 * Library" feature.
 *
 * The access token passed in always comes from
 * [fm.corus.android.domain.SpotifyLibraryAuthService] (the `user-library-modify`
 * Web API OAuth session) — NEVER from [fm.corus.android.domain.SpotifyAuthService]'s
 * App Remote playback token, which lacks this scope.
 */
@Singleton
class SpotifyLibraryService @Inject constructor(
    private val httpClient: HttpClient,
) {
    sealed class SpotifyLibraryException(message: String) : Exception(message) {
        /** 401/403 — the token lacks `user-library-modify` (a stale token, or
         *  one granted before this scope existed). The caller should
         *  re-authenticate via SpotifyLibraryAuthService and retry. */
        object InsufficientScope : SpotifyLibraryException("Spotify needs permission to manage your library.")
        /** 429 — Spotify is throttling. */
        object RateLimited : SpotifyLibraryException("Spotify is busy. Try again in a moment.")
        class RequestFailed(message: String) : SpotifyLibraryException(message)
    }

    /** PUT /v1/me/tracks?ids=<id> — adds one track to the user's library. */
    suspend fun addTrackToLibrary(spotifyTrackId: String, accessToken: String) {
        val encodedId = URLEncoder.encode(spotifyTrackId, "UTF-8")
        val response = httpClient.put("$API_BASE/me/tracks?ids=$encodedId") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        ensureSuccess(response)
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        val code = response.status.value
        if (code == 401 || code == 403) throw SpotifyLibraryException.InsufficientScope
        if (code == 429) throw SpotifyLibraryException.RateLimited
        if (code !in 200..299) {
            val detail = runCatching { response.bodyAsText() }.getOrDefault("").take(200)
            throw SpotifyLibraryException.RequestFailed("Spotify $code: $detail")
        }
    }

    companion object {
        private const val API_BASE = "https://api.spotify.com/v1"
    }
}
