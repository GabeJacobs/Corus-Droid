package fm.corus.android.ui.screens.live

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import javax.inject.Inject
import kotlinx.serialization.Serializable

internal class LiveAppleMusicPlaylists @Inject constructor(
    private val cloud: CloudFunctionsDataSource,
    private val http: HttpClient,
) {
    suspend fun load(link: LivePlaylistLink.AppleMusic): Pair<String, List<LiveTrack>> {
        val token = cloud.getAppleMusicDeveloperToken().token
        val playlistPath = "/v1/catalog/${link.storefront}/playlists/${link.id}"
        val playlist = get<AppleMusicPlaylistResponse>("$playlistPath?include=tracks", token).data.firstOrNull()
            ?: error("Apple Music playlist is missing.")
        val tracksPath = "$playlistPath/tracks"
        var page = playlist.relationships?.tracks ?: get<AppleMusicTrackPage>(tracksPath, token)
        val visited = mutableSetOf<String>()
        val tracks = buildList {
            while (true) {
                page.data.mapNotNullTo(this) { it.toLiveTrack(link.storefront) }
                val next = page.next ?: break
                check(next.startsWith("$tracksPath?") && visited.add(next)) { "Invalid Apple Music playlist page." }
                page = get<AppleMusicTrackPage>(next, token)
            }
        }
        return playlist.attributes.name to tracks
    }

    private suspend inline fun <reified T> get(path: String, token: String): T {
        val response = http.get("https://api.music.apple.com$path") { bearerAuth(token) }
        check(response.status.isSuccess()) { "Apple Music playlist request failed (${response.status.value})." }
        return response.body()
    }
}

@Serializable
private data class AppleMusicPlaylistResponse(val data: List<AppleMusicPlaylist>)

@Serializable
private data class AppleMusicPlaylist(
    val attributes: AppleMusicPlaylistAttributes,
    val relationships: AppleMusicPlaylistRelationships? = null,
)

@Serializable
private data class AppleMusicPlaylistAttributes(val name: String)

@Serializable
private data class AppleMusicPlaylistRelationships(val tracks: AppleMusicTrackPage? = null)

@Serializable
private data class AppleMusicTrackPage(val data: List<AppleMusicTrack>, val next: String? = null)

@Serializable
private data class AppleMusicTrack(
    val id: String,
    val type: String,
    val attributes: AppleMusicTrackAttributes? = null,
) {
    fun toLiveTrack(storefront: String): LiveTrack? {
        if (type != "songs" || id.isBlank()) return null
        val song = attributes ?: return null
        if (song.durationInMillis < 1_000 || song.name.isBlank()) return null
        val artwork = song.artwork?.url?.replace("{w}", "600")?.replace("{h}", "600")
        return LiveTrack(CymbalTrack(
            id = "am:$id",
            name = song.name,
            artistName = song.artistName,
            albumName = song.albumName,
            albumArtURL = artwork,
            albumArtLargeURL = artwork,
            durationMs = song.durationInMillis,
            isrc = song.isrc?.takeIf(String::isNotBlank),
            previewUrl = song.previews.firstOrNull()?.url,
            source = TrackSource.APPLEMUSIC,
            appleMusicId = id,
            appleMusicStorefront = storefront,
        ))
    }
}

@Serializable
private data class AppleMusicTrackAttributes(
    val name: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val durationInMillis: Int = 0,
    val isrc: String? = null,
    val artwork: AppleMusicAsset? = null,
    val previews: List<AppleMusicAsset> = emptyList(),
)

@Serializable
private data class AppleMusicAsset(val url: String? = null)
