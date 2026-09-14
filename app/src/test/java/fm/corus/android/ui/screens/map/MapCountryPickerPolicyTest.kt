package fm.corus.android.ui.screens.map

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCountryPickerPolicyTest {
    @Test fun anywhereRowOmitsCountWhileCountryRowsKeepIt() {
        val root = sequenceOf(File("app/src/main"), File("src/main")).first { it.exists() }
        val source = File(root, "java/fm/corus/android/ui/screens/map/MapExploreScreen.kt").readText()
        val anywhere = source.substringAfter("// Match iOS: Anywhere").substringBefore("Row(Modifier.fillMaxWidth().padding(top = 24.dp")
        val countries = source.substringAfter("items(cities.map { it.city.countryCode }").substringBefore("item { OutlinedTextField")

        assertFalse(anywhere.contains("sumOf"))
        assertFalse(anywhere.contains("Text(count.toString()"))
        assertTrue(countries.contains("Text(count.toString()"))
    }

    @Test fun multipleSelectionStartsEmptyWithoutMutatingActiveScope() {
        val picker = mapCountryPickerOpened(setOf("US", "BR")).beginMultiple()

        assertEquals(setOf("US", "BR"), picker.activeCountryCodes)
        assertTrue(picker.pendingCountryCodes.isEmpty())
        assertFalse(picker.canStart)
    }

    @Test fun countriesToggleIndividuallyAndControlStartAvailability() {
        val empty = mapCountryPickerOpened(emptySet()).beginMultiple()
        val selected = empty.toggleCountry("br")
        val twoSelected = selected.toggleCountry("US")
        val deselected = twoSelected.toggleCountry("BR")

        assertEquals(setOf("BR"), selected.pendingCountryCodes)
        assertTrue(selected.canStart)
        assertEquals(setOf("BR", "US"), twoSelected.pendingCountryCodes)
        assertEquals(setOf("US"), deselected.pendingCountryCodes)
    }

    @Test fun cancellingDiscardsDraftAndPreservesActiveScope() {
        val cancelled = mapCountryPickerOpened(setOf("JP"))
            .beginMultiple()
            .toggleCountry("DE")
            .cancelMultiple()

        assertEquals(setOf("JP"), cancelled.activeCountryCodes)
        assertTrue(cancelled.pendingCountryCodes.isEmpty())
        assertFalse(cancelled.isSelectingMultiple)
        assertFalse(cancelled.canStart)
    }

    @Test fun startSubsetIsExactlyThePendingSelection() {
        val picker = mapCountryPickerOpened(setOf("US", "JP"))
            .beginMultiple()
            .toggleCountry("DE")
            .toggleCountry("pt")

        assertEquals(setOf("DE", "PT"), picker.pendingCountryCodes)
    }
}
