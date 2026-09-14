package fm.corus.android.ui.screens.map

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MapListenPreviewLocalizationContractTest {
    @Test fun everySupportedLocaleHasConstantPreviewCopyWithoutRemainingCount() {
        val resources = sequenceOf(File("app/src/main/res"), File("src/main/res")).first { it.exists() }
        val locales = listOf("values", "values-de", "values-es", "values-fr", "values-it", "values-ja", "values-ko", "values-pt-rBR", "values-b+zh+Hans")
        locales.forEach { locale ->
            val xml = File(resources, "$locale/strings.xml").readText()
            val line = xml.lineSequence().single { it.contains("name=\"map_listen_mode_preview\"") }
            assertFalse("$locale must not show a remaining count", line.contains("%"))
            assertTrue("$locale must name Corus Club", line.contains("Corus Club"))
        }
        val english = File(resources, "values/strings.xml").readText()
        assertTrue(english.contains(">Listen mode preview · Explore Corus Club<"))
    }
}
