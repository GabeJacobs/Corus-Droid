package fm.corus.android.ui.util

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import fm.corus.android.R
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalizedFormatResourcesTest {
    private val placeholders = Regex("%(?:(\\d+)\\$)?([A-Za-z@])")

    private fun context(tag: String): Context {
        val app = RuntimeEnvironment.getApplication()
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        })
    }

    private fun arguments(pattern: String): Array<Any> {
        val slots = mutableMapOf<Int, Any>()
        for ((index, match) in placeholders.findAll(pattern).withIndex()) {
            val slot = match.groupValues[1].toIntOrNull()?.minus(1) ?: index
            slots[slot] = when (match.groupValues[2]) {
                "s" -> "Sample"
                "d" -> 7
                else -> error("Unsupported bundled format: $pattern")
            }
        }
        return Array((slots.keys.maxOrNull() ?: -1) + 1) { slots[it] ?: "Sample" }
    }

    @Test fun `all localized string and plural templates accept their English argument types`() {
        val english = context("en")
        var checked = 0
        // Include legacy Portuguese/Chinese resource qualifiers used by system language.
        for (tag in listOf("en", "pt-BR", "pt-PT", "es", "ja", "zh-Hans", "zh-CN", "de", "fr", "ko", "it")) {
            val localized = context(tag)
            for (field in R.string::class.java.fields) {
                val id = field.getInt(null)
                val pattern = english.getString(id)
                if (!placeholders.containsMatchIn(pattern)) continue
                try {
                    localized.getString(id, *arguments(pattern))
                    checked++
                } catch (error: IllegalArgumentException) {
                    fail("$tag/${field.name}: ${error.message}")
                }
            }
            for (field in R.plurals::class.java.fields) {
                val id = field.getInt(null)
                for (quantity in listOf(0, 1, 2, 5, 21)) {
                    val pattern = english.resources.getQuantityString(id, quantity)
                    if (!placeholders.containsMatchIn(pattern)) continue
                    try {
                        localized.resources.getQuantityString(id, quantity, *arguments(pattern))
                        checked++
                    } catch (error: IllegalArgumentException) {
                        fail("$tag/${field.name}/$quantity: ${error.message}")
                    }
                }
            }
        }
        assertTrue("The audit must exercise bundled formatted resources", checked > 1_000)
    }
}
