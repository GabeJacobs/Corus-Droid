package fm.corus.android.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves on the phone first, like iOS. Signed audio URLs are not persisted. */
@Singleton
class BandcampPlaybackService @Inject constructor(private val client: HttpClient) {
    suspend fun resolve(
        pageUrl: String?, id: String?, name: String, artist: String,
        fallback: suspend (String?) -> Pair<String, String?>?,
    ): Pair<String, String?>? {
        var page = validPage(pageUrl)
        if (page == null) {
            val rawId = id.orEmpty().removePrefix("bc:")
            val query = listOf(name, artist).filter { it.isNotBlank() }.joinToString(" ")
            if (rawId.isNotBlank() && query.isNotBlank()) page = attempt {
                val response = client.post(SEARCH_URL) {
                    header("User-Agent", USER_AGENT)
                    header("Accept", "application/json")
                    header("Origin", "https://bandcamp.com")
                    header("Referer", "https://bandcamp.com/search")
                    setBody(TextContent(buildJsonObject {
                        put("search_text", query)
                        put("search_filter", "t")
                        put("full_page", true)
                        put("fan_id", JsonNull)
                    }.toString(), ContentType.Application.Json))
                }
                if (response.status.value != 200) null else trackPage(response.bodyAsText(), rawId)
            }
        }
        if (page != null) {
            val stream = attempt {
                val response = client.get(page!!) {
                    header("User-Agent", USER_AGENT)
                    header("Accept", "text/html")
                }
                if (response.status.value != 200) null else streamUrl(response.bodyAsText())
            }
            if (stream != null) return stream to page
        }
        // Timeout only this fallback; parent cancellation must propagate.
        return withTimeoutOrNull(10_000) { fallback(page) }
    }

    private suspend fun <T> attempt(block: suspend () -> T?): T? = try {
        withTimeoutOrNull(8_000) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val SEARCH_URL = "https://bandcamp.com/api/bcsearch_public_api/1/autocomplete_elastic"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        internal fun validPage(raw: String?): String? {
            val value = raw?.trim() ?: return null
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            return value.takeIf {
                uri.scheme == "https" && uri.host.orEmpty().matches(Regex("[a-zA-Z0-9-]+\\.bandcamp\\.com")) &&
                    uri.userInfo == null && uri.port == -1 && uri.path.startsWith("/track/")
            }
        }

        internal fun trackPage(body: String, id: String): String? = runCatching {
            val rows = Json.parseToJsonElement(body).jsonObject["auto"]?.jsonObject?.get("results")?.jsonArray
            rows?.firstNotNullOfOrNull { row ->
                val item = row.jsonObject
                if (item["id"]?.jsonPrimitive?.content == id) validPage(item["item_url_path"]?.jsonPrimitive?.content)
                else null
            }
        }.getOrNull()

        internal fun streamUrl(html: String): String? = runCatching {
            val encoded = Regex("data-tralbum=\"([^\"]+)\"").find(html)?.groupValues?.get(1) ?: return null
            val decoded = encoded.replace("&quot;", "\"").replace("&#34;", "\"")
                .replace("&amp;", "&").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">")
            val info = Json.parseToJsonElement(decoded).jsonObject["trackinfo"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            if (info["streaming"]?.jsonPrimitive?.intOrNull == 0) return null
            val file = info["file"] as? JsonObject ?: return null
            val raw = (file["mp3-128"] as? JsonPrimitive)?.contentOrNull
                ?: (file["mp3-v0"] as? JsonPrimitive)?.contentOrNull ?: return null
            val url = if (raw.startsWith("//")) "https:$raw" else raw
            val uri = URI(url)
            url.takeIf { uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() }
        }.getOrNull()
    }
}
