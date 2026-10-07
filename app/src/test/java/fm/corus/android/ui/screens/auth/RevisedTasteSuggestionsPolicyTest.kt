package fm.corus.android.ui.screens.auth

import fm.corus.android.data.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RevisedTasteSuggestionsPolicyTest {
    @Test fun `partial matches retain backend order with only three secondary rows`() {
        val matches = (1..7).map { match(it) }
        assertFalse(matches.first().isTasteMatch)
        assertEquals(listOf("2", "3", "4"), RevisedTasteSuggestionsPolicy.secondary(matches).map { it.id })
        assertEquals(listOf("Artist 1"), RevisedTasteSuggestionsPolicy.sharedNames(matches.first()))
        assertTrue(RevisedTasteSuggestionsPolicy.secondary(emptyList()).isEmpty())
        assertTrue(RevisedTasteSuggestionsPolicy.secondary(matches.take(1)).isEmpty())
    }
    @Test fun `artwork and shared names deduplicate without dropping film matches`() {
        val m = match(1).copy(matchData = MusicMatchData(sharedArtistNames = listOf(" Artist ", "artist", ""),
            sharedDirectorNames = listOf("Director"), sharedTrackPreviews = listOf(
                SharedTrackPreview("a", "A", "Artist", albumArtURL = "art"),
                SharedTrackPreview("b", "B", "Artist", albumArtURL = "art")),
            sharedMoviePreviews = listOf(SharedMoviePreview("film", "Film", "Director", "poster"))))
        assertEquals(listOf("Artist", "Director"), RevisedTasteSuggestionsPolicy.sharedNames(m))
        assertEquals(listOf("art", "poster"), RevisedTasteSuggestionsPolicy.artwork(m))
    }
    @Test fun `discovery excludes every ranked match contacts and current account`() {
        val matches = (1..7).map { match(it) }
        val ids = RevisedTasteSuggestionsPolicy.discoveryExclusions(matches, listOf(match(8).user), "viewer")
        assertEquals((1..8).map(Int::toString).toSet() + "viewer", ids)
        assertEquals(emptySet<String>(), RevisedTasteSuggestionsPolicy.discoveryExclusions(emptyList(), emptyList(), null))
    }
    @Test fun `new treatment and control remain separate from resumed preview only sessions`() {
        val old = Json.decodeFromString<OnboardingFollowSession>("""{"revised":true,"debugBuild":false,"quizPickCount":3,"matchCount":8,"id":"old"}""")
        assertFalse(old.usesRevisedSuggestions)
        assertEquals("android_preview_v1", old.parameters()["ui_version"])
        val new = old.copy(uiVersion = OnboardingFollowSession.REVISED_UI_VERSION, minimumFollows = 3)
        assertTrue(new.usesRevisedSuggestions)
        assertFalse(new.copy(revised = false).usesRevisedSuggestions)
        assertEquals("android_revised_v1", new.parameters()["ui_version"])
        assertEquals(3, new.parameters()["minimum_follows"])
    }
    @Test fun `zero and completed follow requirements do not impose an extra gate`() {
        assertEquals(0, RevisedTasteSuggestionsPolicy.remaining(0, 0))
        assertEquals(0, RevisedTasteSuggestionsPolicy.remaining(0, -2))
        assertEquals(3, RevisedTasteSuggestionsPolicy.remaining(0, 3))
        assertEquals(2, RevisedTasteSuggestionsPolicy.remaining(1, 3))
        assertEquals(0, RevisedTasteSuggestionsPolicy.remaining(5, 3))
    }
    private fun match(i: Int) = SuggestedUserMatch(CymbalUser(id = "$i", username = "listener$i", displayName = "Listener $i"),
        MusicMatchData(sharedArtistNames = listOf("Artist $i")))
}
