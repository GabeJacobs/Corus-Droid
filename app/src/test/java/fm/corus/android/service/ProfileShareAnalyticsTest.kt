package fm.corus.android.service

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileShareAnalyticsTest {
    @Test fun `same canonical fixtures as iOS and web`() {
        val fixtures = javaClass.getResourceAsStream("/profile-share-analytics-fixtures.json")!!.bufferedReader().use {
            JsonParser.parseReader(it).asJsonArray
        }
        for (item in fixtures) {
            val fixture = item.asJsonObject
            val c = fixture.getAsJsonObject("context")
            val context = ProfileShareAnalytics.Context(
                sessionId = c["share_session_id"].asString, source = c["source"].asString,
                layout = c["layout"].asString, background = c["background"].asString, effect = c["effect"].asString,
                postCount = c["post_count"].takeUnless { it.isJsonNull }?.asInt, artworkCount = c["artwork_count"].asInt,
                fullAvailable = c["full_available"].asBoolean, largeAvailable = c["large_available"].asBoolean,
            )
            val extra = fixture.getAsJsonObject("extra").entrySet().associate { (key, value) ->
                key to if (value.asJsonPrimitive.isNumber) value.asDouble else value.asString
            }
            val actual = ProfileShareAnalytics.params(fixture["action"].asString, context, extra)
            val expected = fixture.getAsJsonObject("expected").entrySet().associate { (key, value) ->
                key to if (value.asJsonPrimitive.isNumber) value.asDouble else value.asString
            }
            val normalized = actual.mapValues { (_, value) -> if (value is Number) value.toDouble() else value }
            assertEquals(fixture["title"].asString, expected, normalized)
        }
    }

    @Test fun `rejects invalid session id and nonfinite durations`() {
        val c = ProfileShareAnalytics.Context("username", "action_row", "full", "blue", "none", 28, 28, true, true)
        val p = ProfileShareAnalytics.params("opened", c, mapOf("duration_ms" to Double.NaN, "username" to "private"))
        assertFalse(p.containsKey("share_session_id"))
        assertFalse(p.containsKey("duration_ms"))
        assertFalse(p.containsKey("username"))
    }

    @Test fun `discovery is allowlisted and separate from actual media funnel`() {
        val id = "00000000-0000-4000-8000-000000000001"
        val p = ProfileShareAnalytics.discoveryParams("locked_layout_tapped", id, "action_row", "full", 9, 19)
        assertEquals(19, p["posts_needed"])
        assertEquals("full", p["layout"])
        assertFalse(p.containsKey("effect"))
        assertFalse(p.containsKey("username"))
        assertEquals(emptyMap<String, Any>(), ProfileShareAnalytics.discoveryParams("posted", id, "action_row", "full", 9, 19))
    }
}
