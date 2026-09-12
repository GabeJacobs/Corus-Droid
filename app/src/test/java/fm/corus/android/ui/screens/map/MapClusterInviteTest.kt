package fm.corus.android.ui.screens.map
import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.*
import org.junit.Test
class MapClusterInviteTest {
    private val city = MapCity("brooklyn", "Brooklyn", "NY", "US", 40.6, -73.9)
    private val user = CymbalUser("viewer", "gabe", "Gabe")
    private val person = MapPerson(city, user)
    private val page = MapPeoplePage(listOf(person), null)
    private val state = MapScreenState(user = user, ownPresenceReady = true,
        ownAudience = "everyone", ownCity = city, loading = false,
        cities = listOf(MapCitySummary(city, mapOf("all" to MapFacet(1, emptyList(), true)))))
    private fun visible(s: MapScreenState = state, p: MapPeoplePage? = page, loading: Boolean = false, failed: Boolean = false, uid: String? = user.id) =
        canInviteMapCluster(s, city, p, loading, failed, uid)
    @Test fun invitationRequiresConfirmedOwnSharingAndCurrentAccount() {
        assertTrue(visible())
        assertFalse(visible(state.copy(ownCity = null)))
        assertFalse(visible(state.copy(ownCity = city.copy(cityId = "other"))))
        assertFalse(visible(state.copy(ownAudience = "off")))
        assertFalse(visible(state.copy(ownPresenceReady = false)))
        assertFalse(visible(uid = "other-account"))
        val absent = MapPeoplePage(listOf(person.copy(user = user.copy(id = "other"))), null)
        assertFalse(visible(state.copy(cities = listOf(MapCitySummary(city, mapOf("all" to MapFacet(1, emptyList()))))), absent))
    }
    @Test fun invitationWaitsForTheRealFinalPageAndHidesOnError() {
        assertFalse(visible(p = null))
        assertFalse(visible(p = page.copy(cursor = "next")))
        assertFalse(visible(p = page.copy(cursor = null, reachedEnd = false)))
        assertFalse(visible(loading = true))
        assertFalse(visible(failed = true))
        assertFalse(visible(state.copy(error = "offline")))
        assertFalse(visible(p = page.copy(people = emptyList())))
        val incomplete = state.copy(cities = listOf(MapCitySummary(city, mapOf("all" to MapFacet(21, emptyList(), true)))))
        assertFalse(visible(incomplete))
        assertTrue(visible())
    }
}
